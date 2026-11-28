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
import org.apache.grails.forge.cli.console.WrapperFiles

/**
 * {@code grails-wrapper}: the Grails wrapper of the project, {@code grailsw}, {@code grailsw.bat} and
 * {@code grails-wrapper.jar}: the version of Grails its jar says it is of, against the version of the
 * command line and the one the project is on; whether its files are those this command line has;
 * and what an update of it that did not end left beside it. A project without one has nothing to
 * check: the installed {@code grails} runs its commands.
 *
 * @since 9.0
 */
@CompileStatic
final class GrailsWrapperCheck extends HealthCheck {

    private static final String UPDATE = './grailsw update-wrapper'

    @Override
    String getName() {
        return 'grails-wrapper'
    }

    @Override
    List<HealthFinding> check(HealthContext context) {
        Path directory = WrapperFiles.directoryOf(context.project)
        if (directory == null) {
            return [ok("The project has no Grails wrapper (${WrapperFiles.JAR}): the installed grails runs its commands")]
        }
        List<String> files = WrapperFiles.NAMES.collect { String name -> context.relative(directory.resolve(name)) }
        List<HealthFinding> findings = []
        List<String> beside = WrapperFiles.NAMES.findAll { String name -> Files.exists(directory.resolve(name + WrapperFiles.BESIDE), LinkOption.NOFOLLOW_LINKS) }
                .collect { String name -> context.relative(directory.resolve(name + WrapperFiles.BESIDE)) }
        if (!beside.isEmpty()) {
            findings << found('leftover', HealthLevel.WARNING, "${beside.join(' and ')} ${beside.size() == 1 ? 'is' : 'are'} beside the Grails wrapper: an update of the " +
                    'wrapper is not complete until the wrapper is next run', "Run ./grailsw once (./grailsw --version will do), which puts ${beside.size() == 1 ? 'it' : 'them'} in " +
                    "place, and do not commit ${beside.size() == 1 ? 'it' : 'them'}", './grailsw --version', beside, NOTE_ONE_COMMAND_LINE)
        }
        String version = WrapperFiles.versionOf(directory)
        if (version == null) {
            if (findings.isEmpty()) {
                return [skipped("${context.relative(directory.resolve(WrapperFiles.JAR))} does not say which version of Grails it is of", files)]
            }
            return findings
        }
        Integer major = Versions.major(version)
        String own = context.commandLineVersion
        if (major != null && major < context.commandLineMajor) {
            String fix = context.onEarlierMajor ? "Once grailsVersion in gradle.properties names Grails ${own}, run ${UPDATE} and commit ${WrapperFiles.NAMES.join(', ')}" :
                    "Run ${UPDATE} and commit ${WrapperFiles.NAMES.join(', ')}"
            findings << found('version', HealthLevel.WARNING, "The Grails wrapper is that of Grails ${version} (${WrapperFiles.JAR}), and the project needs the wrapper of " +
                    "Grails ${context.commandLineMajor} to start the command line of Grails ${context.commandLineMajor}", fix, UPDATE, files, NOTE_ONE_COMMAND_LINE)
        } else if (context.projectVersion != null && version != context.projectVersion) {
            findings << found('outdated', HealthLevel.ADVICE, "The Grails wrapper is that of Grails ${version} (${WrapperFiles.JAR}), and the project is on Grails " +
                    "${context.projectVersion} (gradle.properties)", "${UPDATE} writes the wrapper of the version of the project; commit the files it names",
                    UPDATE, files, NOTE_ONE_COMMAND_LINE)
        } else if (version == own) {
            List<String> differing = differing(directory)
            if (!differing.isEmpty()) {
                String update = context.toType(WrapperFiles.COMMAND)
                findings << found('outdated', HealthLevel.ADVICE, "The Grails wrapper says it is of Grails ${version}, and ${differing.join(', ')} " +
                        "${differing.size() == 1 ? 'is' : 'are'} not what this command line of Grails ${own} has", "${update} writes the wrapper of this command line; " +
                        'commit the files it names', update, differing.collect { String name -> context.relative(directory.resolve(name)) }, NOTE_ONE_COMMAND_LINE)
            }
        }
        return findings ?: [ok("The Grails wrapper is that of Grails ${version}", files)]
    }

    private static List<String> differing(Path directory) {
        try {
            return WrapperFiles.differing(directory)
        } catch (IllegalStateException ignored) {
            // a command line without the wrapper it writes has nothing to compare with
            return []
        }
    }
}
