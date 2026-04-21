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

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import java.nio.file.*
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * A task that audits generated Groovydocs for malformed navigation links.
 * Specifically targets 'phantom' links and relative path issues in navigation files
 * like overview-summary.html, deprecated-list.html, and help-doc.html.
 */
abstract class AuditGroovydocLinksTask extends DefaultTask {

    @InputDirectory
    abstract DirectoryProperty getApiDocsDir()

    @TaskAction
    void auditLinks() {
        File apiDir = apiDocsDir.get().asFile
        if (!apiDir.exists()) {
            logger.warn "API documentation directory does not exist: ${apiDir.absolutePath}"
            return
        }

        // Patterns common to Groovydoc navigation failures
        Map<Pattern, String> replacements = [
            (Pattern.compile(/href='([^']+?)\/deprecated-list\.html'/)): "href='deprecated-list.html'",
            (Pattern.compile(/href='([^']+?)\/help-doc\.html'/)): "href='help-doc.html'",
            (Pattern.compile(/href='([^']+?)\/index-all\.html'/)): "href='index-all.html'",
            (Pattern.compile(/href='([^']+?)\/overview-summary\.html'/)): "href='overview-summary.html'"
        ]

        List<String> violations = []

        apiDir.eachFileRecurse { File file ->
            if (file.name.endsWith(".html")) {
                String content = file.text
                
                replacements.each { pattern, replacement ->
                    Matcher matcher = pattern.matcher(content)
                    if (matcher.find()) {
                        violations << "Malformed nav link in ${file.name}: ${matcher.group(0)}"
                    }
                }
                
                // Check specific inner class path issues like Query/Order.Direction.html -> Query.Order.Direction.html
                Pattern innerClassPattern = Pattern.compile(/href='([^']+?)\/([A-Z][A-Za-z0-9_]*?)\/([A-Z][A-Za-z0-9_.]*?\.html)'/)
                Matcher innerMatcher = innerClassPattern.matcher(content)
                while (innerMatcher.find()) {
                    String relPath = innerMatcher.group(1)
                    String outer = innerMatcher.group(2)
                    String inner = innerMatcher.group(3)
                    
                    Path currentPath = file.toPath().parent
                    Path targetPath = currentPath.resolve(relPath).resolve("${outer}.${inner}").normalize()
                    
                    if (Files.exists(targetPath)) {
                        violations << "Malformed inner class link in ${file.name}: ${innerMatcher.group(0)}"
                    }
                }
            }
        }
        
        if (!violations.isEmpty()) {
            violations.take(10).each { logger.error(it) }
            if (violations.size() > 10) logger.error("... and ${violations.size() - 10} more")
            throw new org.gradle.api.GradleException("Found ${violations.size()} malformed links in Groovydoc. Please fix the source issue rather than patching. See logs for details.")
        } else {
            logger.lifecycle "No malformed Groovydoc links found in ${apiDir.absolutePath}"
        }
    }
}
