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

import java.nio.file.Path
import java.util.regex.Matcher
import java.util.regex.Pattern
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.ConsoleProject
import org.grails.forge.util.VersionInfo

/**
 * {@code gradle-wrapper}: the Gradle wrapper the commands of the project run, which the
 * {@code grails} command runs as it finds it, and the version of Gradle its
 * {@code gradle/wrapper/gradle-wrapper.properties} names in {@code distributionUrl}, against the
 * earliest version Grails is built with and the version {@code create-app} generates.
 *
 * @since 9.0
 */
@CompileStatic
final class GradleWrapperCheck extends HealthCheck {

    private static final String PROPERTIES = 'gradle/wrapper/gradle-wrapper.properties'
    private static final String DISTRIBUTION = 'distributionUrl'
    private static final String CHECKSUM = 'distributionSha256Sum'
    // the version a distribution of Gradle is named by, at the end of its address
    private static final Pattern VERSION = Pattern.compile(/gradle-(\d{1,4}\.\d{1,4}(?:\.\d{1,4})?(?:-[A-Za-z0-9.\-]{1,40})?)-(?:bin|all)\.zip$/)

    @Override
    String getName() {
        return 'gradle-wrapper'
    }

    @Override
    List<HealthFinding> check(HealthContext context) {
        String generated = VersionInfo.getGradleVersion()
        String minimum = VersionInfo.getMinimumGradleVersion()
        String command = "./gradlew wrapper --gradle-version ${generated}"
        ConsoleProject.Wrapper wrapper = context.project.gradleWrapper(context.windows)
        String script = ConsoleProject.wrapperName(context.windows)
        if (wrapper.file == null) {
            return [found('missing', HealthLevel.ERROR, wrapper.refusal + '; the commands that build, test and run the project run nothing',
                    "Commit the Gradle wrapper (gradlew, gradlew.bat and gradle/wrapper): gradle wrapper --gradle-version ${generated}, run with an installed Gradle, writes it",
                    "gradle wrapper --gradle-version ${generated}", [script], NOTE_GRADLE_WRAPPER)]
        }
        Path file = wrapper.file.getParent().resolve(PROPERTIES)
        String properties = context.relative(file)
        Properties read = HealthContext.properties(file)
        String distribution = read == null ? null : read.getProperty(DISTRIBUTION)?.trim()
        if (!distribution) {
            return [found('missing', HealthLevel.ERROR, "The Gradle wrapper ${context.relative(wrapper.file)} names no Gradle: there is no ${properties} beside it that has a " +
                    "${DISTRIBUTION}, so the commands that build, test and run the project run nothing",
                    "Write the wrapper again: ${command}, or gradle wrapper --gradle-version ${generated} with an installed Gradle", command, [properties], NOTE_GRADLE_WRAPPER)]
        }
        Matcher named = VERSION.matcher(distribution)
        if (!named.find()) {
            return [skipped("The ${DISTRIBUTION} of ${properties} does not end with the name of a distribution of Gradle (gradle-VERSION-bin.zip), so the version of " +
                    'Gradle it names is not known', [properties])]
        }
        String version = named.group(1)
        List<HealthFinding> findings = []
        if (Versions.compare(version, minimum) < 0) {
            findings << found('version', HealthLevel.ERROR, "The Gradle wrapper names Gradle ${version} (${properties}), and Grails ${context.commandLineMajor} is built " +
                    "with Gradle ${minimum} or later", "Set the ${DISTRIBUTION} of ${properties} to Gradle ${generated}, as ${command} does, and commit the wrapper",
                    command, [properties], NOTE_GRADLE_WRAPPER)
        } else if (Versions.compare(version, generated) < 0) {
            findings << found('older', HealthLevel.ADVICE, "The Gradle wrapper names Gradle ${version} (${properties}), and create-app generates applications with Gradle " +
                    "${generated}", "${command}, and commit the wrapper", command, [properties], NOTE_GRADLE_WRAPPER)
        }
        if (!read.getProperty(CHECKSUM)?.trim()) {
            findings << found('checksum', HealthLevel.ADVICE, "${properties} has no ${CHECKSUM}, so the wrapper does not check the distribution of Gradle it downloads",
                    "Add the SHA-256 of the distribution that gradle.org publishes for Gradle ${version}, as ./gradlew wrapper --gradle-version ${version} " +
                            '--gradle-distribution-sha256-sum SHA256 writes it', null, [properties], null)
        }
        return findings ?: [ok("The Gradle wrapper names Gradle ${version} (${properties}) and checks the distribution it downloads", [properties])]
    }
}
