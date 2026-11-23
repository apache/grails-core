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

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.ProjectEvidence
import org.apache.grails.forge.core.state.ProjectState

/**
 * {@code leftover}: what an earlier version of Grails read and Grails 9 does not, and is still there:
 * {@code ~/.grails/settings.groovy}, the record of an earlier version beside {@code .grails},
 * {@code grails.profile} in the configuration of the application, and the environment variable
 * {@code GRAILS_PREFERRED_SHELL}. Of the configuration only the name of the key is said, never its value.
 *
 * @since 9.0
 */
@CompileStatic
final class LeftoverCheck extends HealthCheck {

    static final String SETTINGS = '~/.grails/settings.groovy'
    static final String PREFERRED_SHELL = 'GRAILS_PREFERRED_SHELL'

    @Override
    String getName() {
        return 'leftover'
    }

    @Override
    List<HealthFinding> check(HealthContext context) {
        List<HealthFinding> findings = []
        ProjectEvidence evidence = context.evidence
        Path settings = context.home.resolve('.grails').resolve('settings.groovy')
        if (Files.exists(settings, LinkOption.NOFOLLOW_LINKS)) {
            findings << found('settings-groovy', HealthLevel.WARNING, "${SETTINGS} is there, and Grails ${context.commandLineMajor} does not read it: the repositories " +
                    'and the proxies it names are not used', 'Give the repositories in the environment variable GRAILS_REPO_URL, and the proxies as -Dhttps.proxyHost and ' +
                    "-Dhttps.proxyPort in GRAILS_OPTS and as systemProp.https.proxyHost in the gradle.properties of Gradle; then remove ${SETTINGS}", null, [SETTINGS],
                    NOTE_SETTINGS_GROOVY)
        }
        ProjectState selection = context.selection
        if (evidence.earlierRecord != null && ProjectState.exists(context.directory)) {
            List<String> different = selection == null || evidence.earlierRecordRefused != null ? [] as List<String> : differences(evidence, selection)
            if (different.isEmpty()) {
                findings << found('earlier-record', HealthLevel.ADVICE, "${evidence.earlierRecord}, the record of an earlier version, is beside .grails, which the " +
                        'commands go by', "Remove ${evidence.earlierRecord} unless a tool of yours reads it", null, [evidence.earlierRecord], NOTE_NO_PROFILE_DEPENDENCY)
            } else {
                findings << found('earlier-record-differs', HealthLevel.WARNING, "${evidence.earlierRecord}, the record of an earlier version, is beside .grails and " +
                        "does not name the same ${different.join(' and ')}: the commands go by .grails, and warn of it",
                        "Remove ${evidence.earlierRecord} unless a tool of yours reads it, and then only after it says what .grails says", null,
                        [evidence.earlierRecord, ProjectState.MANIFEST], NOTE_NO_PROFILE_DEPENDENCY)
            }
        }
        if (evidence.configuredProfile != null) {
            findings << found('profile-config', HealthLevel.ADVICE, "${ProjectEvidence.CONFIGURATION} sets grails.profile, which Grails ${context.commandLineMajor} does " +
                    'not read: an application has no profile', "Remove grails.profile from ${ProjectEvidence.CONFIGURATION}", null, [ProjectEvidence.CONFIGURATION],
                    NOTE_NO_PROFILE)
        }
        String shell = context.environment(PREFERRED_SHELL)
        if (shell != null && !shell.trim().isEmpty()) {
            findings << found('preferred-shell', HealthLevel.ADVICE, "The environment variable ${PREFERRED_SHELL} is set, and Grails ${context.commandLineMajor} does not " +
                    'read it: there is one command line', "Unset ${PREFERRED_SHELL}, in the profile of your shell and in the jobs that set it", null, [], NOTE_ONE_COMMAND_LINE)
        }
        return findings ?: [ok("Nothing is left that an earlier version read: no ${SETTINGS}, no record of an earlier version beside .grails, no grails.profile in " +
                "the configuration and no ${PREFERRED_SHELL}")]
    }

    // what the earlier record says otherwise than the record of this version, which the commands warn of
    private static List<String> differences(ProjectEvidence evidence, ProjectState selection) {
        List<String> different = []
        if (evidence.recordedPackage != null && evidence.recordedPackage != selection.manifest.defaultPackage) {
            different << 'default package'
        }
        if (evidence.recordedFeatures != null && new HashSet<String>(evidence.recordedFeatures) != new HashSet<String>(selection.lock.featureNames)) {
            different << 'features'
        }
        return different
    }
}
