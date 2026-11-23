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
package org.apache.grails.forge.cli.health

import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.ProjectEvidence

/**
 * {@code removed-dependency}: what the build file declares that Grails 9 has no version of, so that
 * the build fails: a {@code profile} dependency, the Grails shell and the profiles, the Gradle plugins
 * of the profiles, and the terminals of JLine for JNA and Jansi. The build file is read as
 * {@link ProjectEvidence} reads it.
 *
 * @since 9.0
 */
@CompileStatic
final class RemovedDependencyCheck extends HealthCheck {

    private static final List<String> SHELL = ['org.apache.grails:grails-shell-cli', 'org.grails:grails-shell'].asImmutable()
    private static final List<String> PROFILE_GROUPS = ['org.apache.grails.profiles', 'org.grails.profiles'].asImmutable()
    private static final List<String> PROFILE_PLUGINS = ['grails-profile', 'grails-publish-profile'].asImmutable()
    private static final List<String> TERMINALS = ['org.jline:jline-terminal-jna', 'org.jline:jline-terminal-jansi'].asImmutable()

    @Override
    String getName() {
        return 'removed-dependency'
    }

    @Override
    List<HealthFinding> check(HealthContext context) {
        ProjectEvidence evidence = context.evidence
        String file = evidence.buildFile
        if (!evidence.buildRead) {
            return [skipped("The project has no ${file} that could be read, so its dependencies are not known", [file])]
        }
        List<HealthFinding> findings = []
        if (evidence.profileDependency != null) {
            findings << found('profile', HealthLevel.ERROR, "${file} declares the profile dependency ${evidence.profileDependency}: Grails " +
                    "${context.commandLineMajor} has no profiles, and the build fails while it is configured",
                    "Remove the profile line from the dependencies of ${file}; nothing takes its place", null, [file], NOTE_SHELL_NOT_PUBLISHED)
        }
        List<String> shell = evidence.dependencies.findAll { String each -> each in SHELL || PROFILE_GROUPS.contains(each.substring(0, each.indexOf(':'))) }.toList()
        if (!shell.isEmpty()) {
            findings << found('shell', HealthLevel.ERROR, "${file} declares ${shell.join(', ')}, of the Grails shell and the profiles, which have no version for Grails " +
                    "${context.commandLineMajor}: the dependency is not resolved", "Remove ${shell.size() == 1 ? 'it' : 'them'} from ${file}", null, [file],
                    NOTE_SHELL_NOT_PUBLISHED)
        }
        List<String> plugins = PROFILE_PLUGINS.findAll { String each -> each in evidence.gradlePlugins }
        if (!plugins.isEmpty()) {
            findings << found('profile-plugin', HealthLevel.ERROR, "${file} applies the Gradle ${plugins.size() == 1 ? 'plugin' : 'plugins'} " +
                    "${plugins.collect { String each -> 'org.apache.grails.gradle.' + each }.join(' and ')}, which Grails ${context.commandLineMajor} does not have",
                    "Remove the plugin ${plugins.size() == 1 ? 'id' : 'ids'} from ${file}: the plugin that holds a feature pack is built and published as any other",
                    null, [file], NOTE_SHELL_NOT_PUBLISHED)
        }
        List<String> terminals = evidence.dependencies.findAll { String each -> each in TERMINALS }.toList()
        if (!terminals.isEmpty()) {
            findings << found('jline', HealthLevel.ERROR, "${file} declares ${terminals.join(', ')}, which JLine 4 does not have",
                    "Remove ${terminals.size() == 1 ? 'it' : 'them'} from ${file}: JLine finds the terminal through its other providers", null, [file], NOTE_JLINE)
        }
        if (!findings.isEmpty()) {
            return findings
        }
        if (!evidence.notRead.isEmpty()) {
            return [skipped("${file} declares none of them where it was read, and ${evidence.notRead.first()}", [file])]
        }
        return [ok("${file} declares no profile dependency, no artifact of the Grails shell or of the profiles, no plugin of the profiles and no terminal of " +
                'JLine for JNA or Jansi', [file])]
    }
}
