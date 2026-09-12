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
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * A task that audits generated Groovydocs for relative links that resolve to a page which
 * was never generated. Links that Groovydoc itself is responsible for are reported as
 * warnings; links that this repository can fix - by putting the type on the Groovydoc
 * classpath or mapping its package to an external javadoc site - fail the build.
 *
 * @see GroovydocLinkAuditor
 */
abstract class AuditGroovydocLinksTask extends DefaultTask {

    private static final int MAX_REPORTED = 25

    @InputDirectory
    abstract DirectoryProperty getApiDocsDir()

    /**
     * Package prefixes with no published javadoc to link to. Links into these are reported
     * as neither a warning nor a violation.
     */
    @Input
    abstract SetProperty<String> getUnmappedPackages()

    @TaskAction
    void auditLinks() {
        File apiDir = apiDocsDir.get().asFile

        GroovydocLinkAuditor.Result result = GroovydocLinkAuditor.audit(apiDir, unmappedPackages.get())

        for (Map.Entry<GroovydocLinkAuditor.Category, List<String>> warning : result.warnings) {
            report(warning.key, warning.value, false)
        }

        List<String> violations = result.violations
        if (violations.isEmpty()) {
            logger.lifecycle "No unresolvable Groovydoc links found in ${apiDir.absolutePath}"
            return
        }

        report(GroovydocLinkAuditor.Category.UNMAPPED_TYPE, violations, true)
        throw new GradleException("Found ${violations.size()} Groovydoc links to types that were neither " +
                'generated nor mapped to an external javadoc site. Add the type to the Groovydoc classpath ' +
                "or map its package in the 'links' configuration; see the log for details.")
    }

    private void report(GroovydocLinkAuditor.Category category, List<String> messages, boolean error) {
        List<String> lines = ["Groovydoc: ${messages.size()} x ${category.description}".toString()]
        messages.take(MAX_REPORTED).each { lines << "  ${it}".toString() }
        if (messages.size() > MAX_REPORTED) {
            lines << "  ... and ${messages.size() - MAX_REPORTED} more".toString()
        }
        for (String line : lines) {
            if (error) {
                logger.error(line)
            } else {
                logger.warn(line)
            }
        }
    }
}
