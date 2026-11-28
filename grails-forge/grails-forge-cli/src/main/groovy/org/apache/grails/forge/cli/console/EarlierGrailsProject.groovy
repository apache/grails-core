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
package org.apache.grails.forge.cli.console

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.regex.Matcher
import java.util.regex.Pattern
import groovy.transform.CompileStatic
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.util.VersionInfo

/**
 * Tells a project of an earlier major version of Grails from one this command line is for. A project
 * of a version before {@link #FIRST_MAJOR}, the first version of this command line, is refused by the
 * commands of a project: its build has not what they run, and what they create is not written for it.
 * A project of that version or a later one, on a command line that is later still, is used, and the
 * user is told before a command writes into it what the version of the command line would have there.
 * The version is the {@code grailsVersion} of the {@code gradle.properties} of the project, as the
 * build of an application reads it.
 *
 * @since 9.0
 */
@CompileStatic
final class EarlierGrailsProject {

    private static final int MAX_PROPERTIES = 64 * 1024
    private static final String PROPERTIES = 'gradle.properties'
    private static final Pattern VERSION = Pattern.compile(/(?m)^[ \t]*grailsVersion[ \t]*[=:][ \t]*(\d{1,4})\.(\d{1,4})([A-Za-z0-9.\-]{0,40})[ \t]*$/)
    private static final List<String> SETTINGS = ['settings.gradle', 'settings.gradle.kts']
    private static final Pattern MAJOR = Pattern.compile(/^(\d{1,4})\..*/)

    /** The first major version of Grails whose projects this command line works with: the one it came with. */
    static final int FIRST_MAJOR = 9

    private EarlierGrailsProject() {
    }

    /**
     * @param from the directory a command is run in
     * @return one line that says why the commands of a project are not run, when the directory is in a
     * project of a major version of Grails before {@link #FIRST_MAJOR}, with or without a record under
     * {@code .grails}; {@code null} outside a project, in a project of that version or a later one, and
     * wherever the version cannot be told
     */
    static String refusal(Path from) {
        return refusal(from, VersionInfo.getGrailsVersion())
    }

    /**
     * @param version the version of Grails this command line is of
     */
    static String refusal(Path from, String version) {
        ConsoleProject project = ConsoleProject.find(from)
        String theirs = project == null ? null : versionOf(project)
        if (theirs == null || majorOf(theirs) >= FIRST_MAJOR) {
            return null
        }
        Matcher own = MAJOR.matcher(version ?: '')
        String ours = own.matches() ? own.group(1) : FIRST_MAJOR as String
        return "This project is on Grails ${theirs} (${PROPERTIES}), and the command line of Grails ${ours} works with projects of Grails ${FIRST_MAJOR} and later. " +
                "Use the command line of Grails ${majorOf(theirs)} for this project, or upgrade the project to Grails ${FIRST_MAJOR} first, as the upgrade notes of the " +
                'Grails guide describe; project migrate, which writes the record the commands of a project read, and project health, which says what the upgrade ' +
                'notes still ask of the project, are the commands of a project that run here'
    }

    /**
     * @return the version the project of a directory is on, when it is one before {@link #FIRST_MAJOR}, and {@code null} otherwise
     */
    static String earlierVersion(Path from) {
        ConsoleProject project = ConsoleProject.find(from)
        String theirs = project == null ? null : versionOf(project)
        return theirs == null || majorOf(theirs) >= FIRST_MAJOR ? null : theirs
    }

    /**
     * @param from the directory a command is run in
     * @return the version of Grails the project of the directory is on, as its build reads it, whichever
     * version that is; {@code null} outside a project and where none can be read
     * @since 9.0
     */
    static String projectVersion(Path from) {
        ConsoleProject project = ConsoleProject.find(from)
        return project == null ? null : versionOf(project)
    }

    private static int majorOf(String version) {
        return Integer.parseInt(version.substring(0, version.indexOf('.')))
    }

    /**
     * @param from the directory a command is run in
     * @return one line of guidance when the directory is in a project of an earlier major version of
     * Grails, and {@code null} otherwise: outside a project, in a project of this version, and
     * wherever the version cannot be told
     */
    static String guidance(Path from) {
        return guidance(from, VersionInfo.getGrailsVersion())
    }

    /**
     * @param version the version of Grails this command line is of
     */
    static String guidance(Path from, String version) {
        Matcher own = MAJOR.matcher(version ?: '')
        ConsoleProject project = ConsoleProject.find(from)
        if (project == null || !own.matches() || ProjectState.exists(project.directory)) {
            return null
        }
        String theirs = versionOf(project)
        int ownMajor = Integer.parseInt(own.group(1))
        if (theirs == null || majorOf(theirs) >= ownMajor || majorOf(theirs) < FIRST_MAJOR) {
            // the last is refused, and has been told why
            return null
        }
        return "This project is on Grails ${theirs} (${PROPERTIES}), and this is the command line of Grails ${ownMajor}: what its commands create is written " +
                "for Grails ${ownMajor}. Use the command line of Grails ${majorOf(theirs)} for this project, or upgrade the project to Grails ${ownMajor} first, " +
                'as the upgrade notes of the Grails guide describe'
    }

    // the version the project is on, as major.minor and what follows; null where none can be read
    private static String versionOf(ConsoleProject project) {
        try {
            // The version is in the gradle.properties of the project, or for a project of a build of
            // several in that of the build: the directories above are looked at up to the one that has
            // the settings of a build, and not beyond the repository.
            Matcher found = null
            Path each = project.directory
            for (int level = 0; each != null && found == null && level <= ConsoleProject.MAX_LEVELS; level++) {
                found = versionIn(each.resolve(PROPERTIES), project)
                if (SETTINGS.any { String settings -> Files.isRegularFile(each.resolve(settings)) } || Files.exists(each.resolve('.git'), LinkOption.NOFOLLOW_LINKS)) {
                    break
                }
                each = each.getParent()
            }
            return found == null ? null : "${found.group(1)}.${found.group(2)}${found.group(3)}".toString()
        } catch (IOException ignored) {
            return null
        }
    }

    // the version a file of properties gives, as Gradle reads it: the last of several; null when it gives none that is read
    private static Matcher versionIn(Path file, ConsoleProject project) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_PROPERTIES || !ConsoleProject.isOwnedByUser(file, project)) {
            return null
        }
        byte[] read = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS).withCloseable { InputStream stream -> stream.readNBytes(MAX_PROPERTIES + 1) }
        if (read.length > MAX_PROPERTIES) {
            return null
        }
        String text = new String(read, StandardCharsets.ISO_8859_1)
        Matcher last = null
        Matcher each = VERSION.matcher(text)
        int from = 0
        while (each.find(from)) {
            last = VERSION.matcher(text)
            last.find(each.start())
            from = each.end()
        }
        return last
    }
}
