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
package org.grails.gradle.plugin.core

import java.nio.charset.StandardCharsets

import groovy.transform.CompileStatic

import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Checks the Forge feature pack of a plugin with Forge itself: the whole descriptor, every file it
 * names, and every template, which is compiled and rendered for applications that are made up.
 *
 * <p>Forge runs in a JVM of its own, started with the class path of the {@code grailsForgePackTooling}
 * configuration and nothing else. It is not loaded into the build: Forge needs the Groovy and the GSP
 * compiler of the Grails it belongs to, which are not those of Gradle, and the configuration is left out
 * of what the Grails plugins do to the versions of every other configuration of the project. The JVM is
 * told the directory of the pack and ends with 0 for a pack without problems, having written the one
 * line {@link #CHECKED_LINE} to its standard output; with 1 for a pack with problems, having written
 * them there, one on a line; and with anything else when it could not check. An end with 0 without that
 * line, or with another line beside it, is not a pack that was checked: something ended the JVM before
 * Forge was done, or wrote where only the check is to write. The line keeps an accident from passing for
 * a check. It does not keep a template from writing the line itself and ending the JVM: the templates of
 * a pack are code of its author, who is the one running the check.</p>
 *
 * @since 9.0
 */
@CompileStatic
@DisableCachingByDefault(because = 'A pack is checked faster than its result would be fetched')
abstract class CheckForgePackTask extends ForgePackToolTask {

    /** The class of Forge that checks a pack. */
    public static final String MAIN_CLASS = 'org.apache.grails.forge.core.pack.PackCheck'

    /** What Forge writes, and nothing else, for a pack it checked and found no problem with: a pattern. */
    public static final String CHECKED_LINE = /feature pack checked: templates=\d+ applications=\d+/

    private static final int VALID = 0
    private static final int PROBLEMS = 1
    private static final int MAX_OTHER_LINES = 3

    /** Written when the pack has no problems, so that a pack that is as it was is not checked again. */
    @OutputFile
    abstract RegularFileProperty getMarkerFile()

    @TaskAction
    void check() {
        File marker = markerFile.get().asFile
        marker.delete()
        requirePackWithoutLinks()
        requireTooling('checked', 'checks', 'check')
        Launched result = launch(MAIN_CLASS, [packDirectory.get().asFile.absolutePath])
        List<String> written = result.written
        List<String> said = result.said
        List<String> summaries = written.findAll { String line -> line ==~ CHECKED_LINE }
        if (result.exitValue == VALID && written.size() == 1 && summaries.size() == 1) {
            said.each { String line -> logger.info(line) }
            logger.info('Forge: {}', written[0])
            marker.setText("${packState.get()}\n", StandardCharsets.UTF_8.name())
            return
        }
        if (result.exitValue == PROBLEMS && written) {
            throw new GradleException("The feature pack in ${SOURCE} cannot be used:\n" +
                    written.collect { String problem -> '  - ' + problem }.join('\n'))
        }
        String why
        List<String> shown = said
        if (result.exitValue == VALID && summaries.size() == 1) {
            List<String> others = written - summaries
            why = 'It checked the pack and found no problem, and other lines were written to the standard output, which only the ' +
                    "check itself is to write, so it cannot be told that the check was not ended early: ${some(others.collect { String line -> quoted(line) }, MAX_OTHER_LINES)}"
        } else if (result.exitValue == VALID) {
            why = 'It ended as a check without problems does, without saying that it had checked the pack: a template may have ' +
                    "ended the JVM, or the Forge of the ${TOOLING} configuration is older than this version of the Grails Gradle plugin expects"
            shown = written + said
        } else {
            // with no output of its own, the JVM may not have got as far as Forge
            why = launcherHint(result.errors, MAIN_CLASS)
            if (why == null && result.exitValue == PROBLEMS) {
                why = 'It ended as a check that found problems does, without naming one: a template may have ended the JVM'
            }
        }
        throw new GradleException("The feature pack in ${SOURCE} could not be checked (Forge ended with ${result.exitValue})" +
                "${why ? '. ' + why : ''}${shown ? ':\n' + shown.collect { String line -> '  ' + line }.join('\n') : ''}")
    }
}
