/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.doc.gradle

import java.net.URLDecoder
import java.nio.file.Path
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Scans generated Groovydoc for relative links that resolve to a page which was never
 * generated, and sorts them by cause.
 *
 * <p>The actionable cause is {@link Category#UNMAPPED_TYPE}: Groovydoc could not load a
 * fully qualified type and no external javadoc was mapped for its package, so it emitted a
 * link to a {@code fully.qualified.Name.html} page that does not exist. Either the type
 * belongs on the Groovydoc classpath or its package needs an entry in the {@code links}
 * configuration.</p>
 *
 * <p>The remaining causes are defects in Groovydoc's own templates and type resolution that
 * nothing in this repository can influence, so they are reported without failing the build.
 * See {@link Category} for the individual cases.</p>
 *
 * <p>Kept free of the Gradle API so it can be unit tested directly -
 * {@code build-logic/docs-core} deliberately keeps Gradle classes off the test compile
 * classpath.</p>
 */
class GroovydocLinkAuditor {

    /**
     * Pages Groovydoc's templates link to but never generate.
     */
    static final List<String> UNGENERATED_PAGES = [
            'allclasses-noframe.html',
            'constant-values.html',
            'overview-tree.html'
    ].asImmutable()

    enum Category {

        /** The type was never loaded and its package has no external javadoc mapping. */
        UNMAPPED_TYPE('unresolved type with no external javadoc mapping', true),

        /** The target exists, but the link was written relative to the wrong directory. */
        WRONG_RELATIVE_PATH('link written relative to the wrong directory', false),

        /** Groovydoc emitted a link to one of its own pages that it never generates. */
        UNGENERATED_PAGE('link to a page Groovydoc never generates', false),

        /** Groovydoc could not work out the package of a type it referenced by simple name. */
        UNQUALIFIED_TYPE('type referenced by simple name that Groovydoc could not qualify', false)

        final String description
        final boolean actionable

        private Category(String description, boolean actionable) {
            this.description = description
            this.actionable = actionable
        }
    }

    private static final Pattern HREF_PATTERN = Pattern.compile(/(?i)href\s*=\s*(['"])(.*?)\1/)

    /** Markup inside an HTML comment is never rendered, so its hrefs are not real links. */
    private static final Pattern HTML_COMMENT_PATTERN = Pattern.compile(/(?s)<!--.*?-->/)

    /** A '%' that does not start a well-formed escape marks an href that was never percent-encoded. */
    private static final Pattern MALFORMED_ESCAPE_PATTERN = Pattern.compile(/(?i)%(?![0-9A-F]{2})/)

    /** A dotted file name whose leading segments are a package, e.g. {@code org.gradle.api.Project.html}. */
    private static final Pattern QUALIFIED_TYPE_PAGE =
            Pattern.compile(/^[a-z][A-Za-z0-9_]*(\.[a-z][A-Za-z0-9_]*)*\.[A-Z][A-Za-z0-9_.$]*\.html$/)

    private static final List<String> ABSOLUTE_PREFIXES =
            ['http://', 'https://', '//', 'mailto:', 'javascript:', 'data:'].asImmutable()

    static Result audit(File apiDir, Collection<String> unmappedPackages = []) {
        Result result = new Result()
        Path apiPath = apiDir.toPath().toAbsolutePath().normalize()

        apiDir.eachFileRecurse { File file ->
            if (file.file && file.name.endsWith('.html')) {
                auditFile(file, apiPath, unmappedPackages, result)
            }
        }

        result
    }

    static List<String> findViolations(File apiDir, Collection<String> unmappedPackages = []) {
        audit(apiDir, unmappedPackages).violations
    }

    private static void auditFile(File file, Path apiPath, Collection<String> unmappedPackages, Result result) {
        Path filePath = file.toPath().toAbsolutePath().normalize()
        Path currentPath = filePath.parent
        String page = apiPath.relativize(filePath).toString()

        // Groovydoc repeats the same link on a page - the nav bar at the top and bottom, a
        // type in both the summary and detail sections - so each href is audited only once.
        Set<String> seen = []
        Matcher matcher = HREF_PATTERN.matcher(HTML_COMMENT_PATTERN.matcher(file.text).replaceAll(''))
        while (matcher.find()) {
            String href = matcher.group(2).trim()
            if (!seen.add(href)) {
                continue
            }
            String target = targetOf(href)
            if (!target) {
                continue
            }

            Path resolved = currentPath.resolve(target).normalize()
            if (resolved.toFile().exists()) {
                continue
            }

            Category category = categorize(target, resolved, apiPath)
            if (category == Category.UNMAPPED_TYPE && isIgnored(resolved.fileName.toString(), unmappedPackages)) {
                continue
            }
            result.add(category, "${page} -> ${href}".toString())
        }
    }

    private static Category categorize(String target, Path resolved, Path apiPath) {
        String name = resolved.fileName.toString()
        if (apiPath.resolve(stripParentSegments(target)).normalize().toFile().exists()) {
            return Category.WRONG_RELATIVE_PATH
        }
        if (name in UNGENERATED_PAGES) {
            return Category.UNGENERATED_PAGE
        }
        if (QUALIFIED_TYPE_PAGE.matcher(name).matches()) {
            return Category.UNMAPPED_TYPE
        }
        Category.UNQUALIFIED_TYPE
    }

    private static String stripParentSegments(String target) {
        String stripped = target
        while (stripped.startsWith('./') || stripped.startsWith('../')) {
            stripped = stripped.substring(stripped.indexOf('/') + 1)
        }
        stripped
    }

    private static boolean isIgnored(String name, Collection<String> unmappedPackages) {
        unmappedPackages.any { name.startsWith(it) }
    }

    private static String targetOf(String href) {
        // Doc comments carrying GSP or GString markup end up here as an href that was never a link.
        if (!href || href.contains('${') || href.startsWith('#')) {
            return null
        }
        String lower = href.toLowerCase()
        if (ABSOLUTE_PREFIXES.any { lower.startsWith(it) }) {
            return null
        }
        String target = href.split('#')[0].split(/\?/)[0]
        target ? percentDecode(target) : null
    }

    /**
     * Decodes percent escapes in a path. Unlike a plain {@link URLDecoder} call, a '+' stays a
     * literal '+' - that decoding belongs to query strings, not paths - and an href with a
     * stray '%' is taken literally instead of raising an error.
     */
    private static String percentDecode(String target) {
        if (!target.contains('%') || MALFORMED_ESCAPE_PATTERN.matcher(target).find()) {
            return target
        }
        URLDecoder.decode(target.replace('+', '%2B'), 'UTF-8')
    }

    static class Result {

        final Map<Category, List<String>> byCategory = new TreeMap<Category, List<String>>()

        void add(Category category, String message) {
            byCategory.computeIfAbsent(category) { [] } << message
        }

        List<String> getViolations() {
            byCategory.findAll { it.key.actionable }.collectMany { it.value }
        }

        Map<Category, List<String>> getWarnings() {
            byCategory.findAll { !it.key.actionable }
        }
    }
}
