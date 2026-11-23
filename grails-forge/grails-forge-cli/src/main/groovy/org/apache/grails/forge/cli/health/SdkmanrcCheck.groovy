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
import groovy.transform.CompileStatic
import org.grails.forge.util.VersionInfo

/**
 * {@code sdkmanrc}: the versions the {@code .sdkmanrc} of the project, or of the root of its build,
 * selects for {@code sdk env}: Java, the {@code grails} command line, Gradle and Groovy. A project
 * without the file has nothing to check: Forge does not write one.
 *
 * @since 9.0
 */
@CompileStatic
final class SdkmanrcCheck extends HealthCheck {

    private static final String FILE = '.sdkmanrc'
    private static final int MAX_SDKMANRC = 64 * 1024
    // what SDKMAN! names a version by: no space, nothing a terminal acts on
    private static final String CANDIDATE_VERSION = /[A-Za-z0-9][A-Za-z0-9._+\-]{0,59}/

    @Override
    String getName() {
        return 'sdkmanrc'
    }

    @Override
    List<HealthFinding> check(HealthContext context) {
        Path file = [context.directory, context.project.buildRoot].findResult { Path each ->
            each != null && HealthContext.text(each.resolve(FILE), MAX_SDKMANRC) != null ? each.resolve(FILE) : null
        }
        if (file == null) {
            return [ok('The project has no .sdkmanrc')]
        }
        String name = context.relative(file)
        Map<String, String> selected = [:]
        for (String line : HealthContext.text(file, MAX_SDKMANRC).readLines()) {
            String trimmed = line.trim()
            int equals = trimmed.indexOf('=')
            if (trimmed.startsWith('#') || equals < 1) {
                continue
            }
            String value = trimmed.substring(equals + 1).trim()
            if (value ==~ CANDIDATE_VERSION) {
                selected.put(trimmed.substring(0, equals).trim(), value)
            }
        }
        List<HealthFinding> findings = []
        int minimumJava = HealthContext.minimumJava
        Integer java = Versions.major(selected.get('java'))
        if (java != null && java < minimumJava) {
            findings << found('java', HealthLevel.WARNING, "${name} selects Java ${selected.get('java')} (java), and Grails ${context.commandLineMajor} runs on Java " +
                    "${minimumJava} or later", "Set java in ${name} to a JDK ${minimumJava} or later, as sdk list java names them", null, [name], NOTE_GROOVY)
        }
        String grails = selected.get('grails')
        if (grails != null && context.projectVersion != null && grails != context.projectVersion) {
            findings << found('grails', HealthLevel.WARNING, "${name} selects Grails ${grails} (grails), and the project is on Grails ${context.projectVersion} " +
                    '(gradle.properties): sdk env selects another grails command line than the one of the project',
                    "Set grails in ${name} to ${context.projectVersion}, or remove the line: ./grailsw starts the command line of the version of the project",
                    null, [name, 'gradle.properties'], NOTE_ONE_COMMAND_LINE)
        }
        String gradle = selected.get('gradle')
        Integer comparedGradle = Versions.compare(gradle, VersionInfo.getMinimumGradleVersion())
        if (comparedGradle != null && comparedGradle < 0) {
            findings << found('gradle', HealthLevel.ADVICE, "${name} selects Gradle ${gradle} (gradle), and Grails ${context.commandLineMajor} is built with Gradle " +
                    "${VersionInfo.getMinimumGradleVersion()} or later: the Gradle wrapper decides the Gradle of the build, and this one is what gradle wrapper and a " +
                    'build without ./gradlew run', "Set gradle in ${name} to ${VersionInfo.getGradleVersion()}", null, [name], NOTE_GRADLE_WRAPPER)
        }
        Integer groovy = Versions.major(selected.get('groovy'))
        Integer ofTheBom = Versions.major(bomGroovy())
        if (groovy != null && ofTheBom != null && groovy != ofTheBom) {
            findings << found('groovy', HealthLevel.ADVICE, "${name} selects Groovy ${selected.get('groovy')} (groovy), and Grails ${context.commandLineMajor} builds " +
                    "with Groovy ${bomGroovy()}, which the Grails BOM manages: the groovy command of sdk env is not the Groovy of the project",
                    "Set groovy in ${name} to ${bomGroovy()}, or remove the line", null, [name], NOTE_GROOVY)
        }
        return findings ?: [ok("${name} selects nothing that differs from what the project is built and run with", [name])]
    }

    private static String bomGroovy() {
        try {
            return VersionInfo.getBomVersion('groovy')
        } catch (IllegalArgumentException ignored) {
            return null
        }
    }
}
