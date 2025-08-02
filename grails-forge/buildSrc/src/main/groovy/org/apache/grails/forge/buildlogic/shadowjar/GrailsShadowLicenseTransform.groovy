/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.grails.forge.buildlogic.shadowjar

import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.tasks.Input

import java.util.regex.Pattern

/**
 * supports combining into a single license file.
 */
@CompileStatic
class GrailsShadowLicenseTransform implements Transformer {

    private static final List<Pattern> LICENSE_PATTERNS = [
            ~'(?i)META-INF/[^/]*LICENSE[^/]*',
            ~'(?i)META-INF/LICENSES/.*',
            ~'(?i)[^/]*LICENSE[^/]*',
            ~'(?i)LICENSES/.*'
    ]

    private static final String LICENSE_PATH = 'META-INF/LICENSE'

    private LinkedHashMap<String, LicenseHolder> licenses = [:]

    @Input
    String licenseAppendixEnding = 'LIMITATIONS UNDER THE LICENSE.'

    @Input
    String licenseTermsEnding = 'END OF TERMS AND CONDITIONS'

    @Input
    String licenseTermsStart = 'APACHE LICENSE VERSION 2.0'

    @Input
    String licenseText // to be loaded by file

    @Input
    Boolean separators = false

    @Override
    boolean canTransformResource(FileTreeElement element) {
        def path = element.relativePath.pathString
        LICENSE_PATTERNS.any { pattern -> pattern.matcher(path).matches() }
    }

    @Override
    // Multiple assignments without list expressions on the right hand side are unsupported in static type checking mode
    @CompileDynamic
    void transform(TransformerContext context) {
        if (!licenses) {
            // Add our license as previously seen so we can dedupe - this transformer only applies to the copy of other jars
            def (grailsLicense, grailsIndexMappings) = normalize(licenseText)
            licenses[grailsLicense] = new LicenseHolder(license: licenseText, indexMappings: grailsIndexMappings)
        }

        context.is.withReader {
            BufferedReader reader = new BufferedReader(it)

            def license = stripJavaBlockComment(reader.text)
            def (String normalized, List<Integer> indexMappings) = normalize(license)

            // resect Apache License
            String resected = resectLicense(license, normalized, indexMappings)
            if (!resected.trim()) {
                return // only contained duplicated license terms with the ASF license
            }

            def (String resectedNormalized, List<Integer> resectedIndexMappings) = normalize(resected)
            def previouslySeen = getVariations(resectedNormalized).any { licenses.containsKey(it) }
            if (!previouslySeen) {
                licenses[resectedNormalized] = new LicenseHolder(license: resected, indexMappings: resectedIndexMappings)
            }
        }
    }

    private static String stripJavaBlockComment(String text) {
        if (!text.startsWith('/*')) {
            return text
        }

        return text
                .replaceAll('^/\\*+|\\*+/\\s*$', '') // opening & closing comment
                .readLines()
                .collect {
                    it.replaceFirst(/^(\s*\*)?/, '').trim()
                } // leading whitespace & *
                .join('\n')
    }

    private static Tuple2<String, List<Integer>> normalize(String license) {
        def sb = new StringBuilder()
        List<Integer> indexMappings = [] // each char in sb maps to original index

        boolean previousWhitespace = false
        for (int i = 0; i < license.length(); i++) {
            char c = license.charAt(i)
            if (c.isWhitespace()) {
                if (!previousWhitespace) {
                    sb.append(' ')
                    indexMappings << i
                    previousWhitespace = true
                }
            } else {
                sb.append(Character.toUpperCase(c))
                indexMappings << i
                previousWhitespace = false
            }
        }

        String normalized = sb.toString().trim()
        int startTrim = sb.indexOf(normalized)
        int endTrim = startTrim + normalized.length()
        new Tuple2<String, List<Integer>>(normalized, indexMappings[startTrim..<endTrim])
    }

    private String resectLicense(String license, String normalized, List<Integer> indexMappings) {
        if (!normalized.startsWith(licenseTermsStart.toUpperCase())) {
            return license // not ASF license, return as is
        }

        // try to search on the appendix first
        String endOfLicenseMarker = normalize(licenseAppendixEnding).v1
        int end1Index = normalized.indexOf(endOfLicenseMarker)
        if (end1Index >= 0) {
            // license included the appendix
            def originalEnding = indexMappings[end1Index + endOfLicenseMarker.size() - 1] + 1
            if (originalEnding > license.length()) {
                // only the license is present
                return null
            }

            return license.substring(originalEnding)
        }

        // try to search on the terms ending
        String endMarker = normalize(licenseTermsEnding).v1
        int end2Index = normalized.indexOf(endMarker)
        if (end2Index >= 0) {
            // bare license
            def originalEnding = indexMappings[end2Index + endMarker.size() - 1] + 1
            if (originalEnding > license.length()) {
                // only the license is present
                return null
            }

            return license.substring(originalEnding)
        }

        license
    }

    private static List<String> getVariations(String license) {
        [license.trim()].collectMany {
            [it, it.replace('http://', 'https://'), it.replace('https://', 'http://')]
        }
    }

    @Override
    boolean hasTransformedResource() {
        true
    }

    @Override
    void modifyOutputStream(ZipOutputStream os, boolean preserveFileTimestamps) {
        ZipEntry zipEntry = new ZipEntry(LICENSE_PATH)
        zipEntry.time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, zipEntry.time)
        os.putNextEntry(zipEntry)

        os.withPrintWriter { writer ->
            licenses.entrySet().withIndex().each { license ->
                if (license.v1.value == null) {
                    return // skip the license that will be copied by shadow from our existing jars
                }

                writer.println(license.v1.value.license)
                if (separators && license.v2 < licenses.size() - 1) {
                    writer.println("-------------------------${license.v2}---------------------------")
                }
            }

            writer.flush()
        }

        licenses = [:]
    }

    private static class LicenseHolder {

        String license
        List<Integer> indexMappings
    }
}