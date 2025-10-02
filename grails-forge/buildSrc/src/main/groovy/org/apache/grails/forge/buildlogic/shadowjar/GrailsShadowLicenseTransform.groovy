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
 * This transformer assists in combining all known licenses into a single META-INF/LICENSE file. Please note that the
 * shadow plugin will initially copy all dependencies that are in the local project and then it will copy all other external
 * dependencies. Transformers only apply to the copied jar file dependencies, and not to the local project dependencies.
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

    /**
     * Whether this transformer can process the given resource. If set to true, it's expected the transformer will
     * write the resource.
     */
    @Override
    boolean canTransformResource(FileTreeElement element) {
        def path = element.relativePath.pathString
        LICENSE_PATTERNS.any { pattern -> pattern.matcher(path).matches() }
    }

    /**
     * Parses any file that matches the license patterns and extracts the license text, deduplicating & combining where
     * possible.
     *
     * @param context contains the input stream of the resource to transform
     */
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

    /**
     * Some libraries ship with a license.header file that contains a Java block comment. This method strips the
     * Java block comment syntax and returns the text without the comment.
     */
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

    /**
     * Normalizes the license text by collapsing whitespace and uppercasing all characters. It also returns a mapping
     * of the normalized text to the original license text, where each character in the normalized text maps to its
     * original index in the license text. This allows us to index into the normalized text from sections of the license text
     * for deduplication purposes.
     *
     * @param license the original license text
     * @return a tuple containing the normalized license text and a list of index mappings
     */
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

    /**
     * For a given license, this method will extract the duplicate license text and return the remaining text
     * @param license the original license text
     * @param normalized a normalized version of the license text, with all whitespace collapsed and all characters uppercased
     * @param indexMappings a mapping of the normalized text to the original license text, where each character in the normalized text maps to its original index in the license text
     * @return either null if no additional license text was found or the additional license text
     */
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

    /**
     * Some licenses mix http & https links, handles simple variations of the license to ensure the license can be
     * deduplicated.
     *
     * @param license the license text
     * @return a list of variations of the license text
     */
    private static List<String> getVariations(String license) {
        [license.trim()].collectMany {
            [it, it.replace('http://', 'https://'), it.replace('https://', 'http://')]
        }
    }

    /**
     * Whether this transformer will modify the output stream.
     */
    @Override
    boolean hasTransformedResource() {
        // Must always be true since we want to write the LICENSE file and all license files originate from jar files
        // after our project restructure
        true
    }

    /**
     * Writes the combined license file to the output stream. The file will be written to
     * META-INF/LICENSE and will contain all licenses found in the project
     *
     * @param os the jar file output stream
     * @param preserveFileTimestamps whether to preserve file timestamps in the output jar
     */
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