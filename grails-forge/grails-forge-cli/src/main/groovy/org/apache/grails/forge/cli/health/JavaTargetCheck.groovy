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
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.options.JdkVersion

/**
 * {@code java-target}: each setting of the build file that names a version of Java, as
 * {@link ProjectEvidence} reads it for {@code project migrate} too, against the earliest Grails is
 * built for, and, for a record that {@code project migrate} wrote, the version of Java the record has
 * against the one the classes are compiled for. Each setting is named with its line.
 *
 * @since 9.0
 */
@CompileStatic
final class JavaTargetCheck extends HealthCheck {

    @Override
    String getName() {
        return 'java-target'
    }

    @Override
    List<HealthFinding> check(HealthContext context) {
        ProjectEvidence evidence = context.evidence
        String file = evidence.buildFile
        if (!evidence.buildRead) {
            return [skipped("The project has no ${file} that could be read, so the version of Java it compiles for is not known", [file])]
        }
        int minimum = HealthContext.minimumJava
        List<HealthFinding> findings = []
        List<ProjectEvidence.JavaSetting> below = evidence.javaSettings.findAll { ProjectEvidence.JavaSetting each -> each.version < minimum }
        if (!below.isEmpty()) {
            findings << found('release', HealthLevel.WARNING, "${file} names ${named(below)}, and Grails ${context.commandLineMajor} needs Java ${minimum} or later: " +
                    'a class of the project that uses one of Grails does not compile',
                    "In ${file}, set ${below.collect { ProjectEvidence.JavaSetting each -> "${each.setting} (line ${each.line})" }.join(' and ')} to Java ${minimum} or later",
                    null, [file], NOTE_GROOVY)
        }
        ProjectEvidence.JavaSetting compiled = evidence.javaSetting
        ProjectState record = context.selection
        if (compiled != null && record != null && !record.manifest.tracked && compiled.version != record.manifest.options.jdk.majorVersion() &&
                JdkVersion.values().any { JdkVersion each -> each.majorVersion() == compiled.version }) {
            String command = context.toType("project migrate --force --jdk ${compiled.version}")
            findings << found('record', HealthLevel.ADVICE, "The record says that the application is for Java ${record.manifest.options.jdk.majorVersion()} " +
                    "(${ProjectState.MANIFEST}), and ${file} compiles for ${named([compiled])}",
                    "${command}, with the name and the other options the record was written with, writes it again with the version of the build",
                    command, [ProjectState.MANIFEST, file], NOTE_RECORD)
        }
        if (!findings.isEmpty()) {
            return findings
        }
        if (compiled == null) {
            if (!evidence.notRead.isEmpty()) {
                return [skipped("${file} names no version of Java, and ${evidence.notRead.first()}", [file])]
            }
            return [ok("${file} names no version of Java: the build compiles for the JDK that runs Gradle, which runs Grails ${context.commandLineMajor} " +
                    "only from Java ${minimum} on", [file])]
        }
        List<ProjectEvidence.JavaSetting> others = evidence.javaSettings.findAll { ProjectEvidence.JavaSetting each -> !each.is(compiled) }
        return [ok("${file} compiles for ${named([compiled])}${others ? "; it also names ${named(others)}" : ''}", [file])]
    }

    // the versions and where the build names them, as project migrate says them: Java 17 (compileJava.options.release, line 68)
    private static String named(List<ProjectEvidence.JavaSetting> settings) {
        return settings.collect { ProjectEvidence.JavaSetting each -> "Java ${each.version} (${each})" }.join(' and ')
    }
}
