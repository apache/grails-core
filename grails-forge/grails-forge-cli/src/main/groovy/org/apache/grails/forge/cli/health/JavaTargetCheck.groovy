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
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.options.JdkVersion

/**
 * {@code java-target}: the versions of Java the build file compiles for, see {@link BuildFacts},
 * against the earliest Grails is built for, and, for a record that {@code project migrate} wrote,
 * the version of Java the record has.
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
        BuildFacts build = context.build
        if (!build.read) {
            return [skipped("The project has no ${build.file} that could be read, so the version of Java it compiles for is not known", [build.file])]
        }
        int minimum = HealthContext.minimumJava
        List<HealthFinding> findings = []
        List<BuildFacts.JavaTarget> below = build.javaTargets.findAll { BuildFacts.JavaTarget target -> target.version < minimum }
        if (!below.isEmpty()) {
            findings << found('release', HealthLevel.WARNING, "${build.file} compiles for Java ${below.join(', ')}, and Grails ${context.commandLineMajor} is " +
                    "built for Java ${minimum}: a class of the project that uses one of Grails does not compile",
                    "Set ${below*.setting.unique().join(' and ')} to ${minimum} or later in ${build.file}", null, [build.file], NOTE_GROOVY)
        }
        BuildFacts.JavaTarget compiled = build.compiledFor
        ProjectState record = context.selection
        if (compiled != null && record != null && !record.manifest.tracked && compiled.version != record.manifest.options.jdk.majorVersion() &&
                JdkVersion.values().any { JdkVersion each -> each.majorVersion() == compiled.version }) {
            String command = context.toType("project migrate --force --jdk ${compiled.version}")
            findings << found('record', HealthLevel.ADVICE, "The record says that the application is for Java ${record.manifest.options.jdk.majorVersion()} " +
                    "(${ProjectState.MANIFEST}), and ${build.file} compiles for Java ${compiled}",
                    "${command}, with the name and the other options the record was written with, writes it again with the version of the build",
                    command, [ProjectState.MANIFEST, build.file], NOTE_RECORD)
        }
        if (!findings.isEmpty()) {
            return findings
        }
        if (build.javaTargets.isEmpty() && !context.evidence.notRead.isEmpty()) {
            return [skipped("${build.file} names no version of Java, and ${context.evidence.notRead.first()}", [build.file])]
        }
        return [ok(build.javaTargets.isEmpty() ? "${build.file} names no version of Java: the build compiles for the JDK that runs Gradle, which runs Grails " +
                "${context.commandLineMajor} only from Java ${minimum} on" : "${build.file} compiles for Java ${build.javaTargets.join(', ')}", [build.file])]
    }
}
