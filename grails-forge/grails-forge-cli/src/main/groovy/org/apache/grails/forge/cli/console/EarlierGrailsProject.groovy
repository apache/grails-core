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
 * Tells a project of an earlier major version of Grails from one this command line is for, so that
 * the user is told before a command writes into it what this version of Grails would have there.
 * The version is the {@code grailsVersion} of the {@code gradle.properties} of the project, as the
 * build of an application reads it; a project that has the record of this generator under
 * {@code .grails} is one of this version whatever that file says.
 *
 * @since 9.0
 */
@CompileStatic
final class EarlierGrailsProject {

    private static final int MAX_PROPERTIES = 64 * 1024
    private static final String PROPERTIES = 'gradle.properties'
    private static final Pattern VERSION = Pattern.compile(/(?m)^[ \t]*grailsVersion[ \t]*[=:][ \t]*(\d{1,4})\.(\d{1,4})([A-Za-z0-9.\-]{0,40})[ \t]*$/)
    private static final Pattern MAJOR = Pattern.compile(/^(\d{1,4})\..*/)

    private EarlierGrailsProject() {
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
        Path file = project.directory.resolve(PROPERTIES)
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_PROPERTIES || !ConsoleProject.isOwnedByUser(file, project)) {
                return null
            }
            byte[] read = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS).withCloseable { InputStream stream -> stream.readNBytes(MAX_PROPERTIES + 1) }
            Matcher found = VERSION.matcher(new String(read, StandardCharsets.ISO_8859_1))
            if (read.length > MAX_PROPERTIES || !found.find()) {
                return null
            }
            int major = Integer.parseInt(found.group(1))
            int ownMajor = Integer.parseInt(own.group(1))
            if (major >= ownMajor) {
                return null
            }
            String theirs = "${found.group(1)}.${found.group(2)}${found.group(3)}"
            return "This project is on Grails ${theirs} (${PROPERTIES}), and this is the command line of Grails ${ownMajor}: what its commands create is written " +
                    "for Grails ${ownMajor}. Use the command line of Grails ${major} for this project, or upgrade the project to Grails ${ownMajor} first, " +
                    'as the upgrade notes of the Grails guide describe'
        } catch (IOException ignored) {
            return null
        }
    }
}
