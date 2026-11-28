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

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.function.Function
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.ConsoleProject
import org.apache.grails.forge.cli.console.EarlierGrailsProject
import org.apache.grails.forge.cli.console.ProjectEvidence
import org.apache.grails.forge.core.state.ProjectState
import org.apache.grails.forge.core.state.ProjectStateException
import org.grails.forge.options.JdkVersion
import org.grails.forge.util.VersionInfo

/**
 * What the checks of {@code project health} read the project through: its directory and the files of
 * it they share, each read once, as text and within bounds, when a check first asks for it. Nothing
 * of the project is run, and nothing is asked of the network.
 *
 * @since 9.0
 */
@CompileStatic
final class HealthContext {

    /** How many bytes a file of the project that is read has, at most. */
    static final int MAX_FILE = 1024 * 1024

    private static final Function<String, String> SYSTEM = { String name -> System.getenv(name) } as Function<String, String>
    private static volatile Function<String, String> environment = SYSTEM

    /** The project. */
    final ConsoleProject project

    /** The directory of the project. */
    final Path directory

    /** The version of Grails this command line is of. */
    final String commandLineVersion

    /** The home directory of the user. */
    final Path home

    /** Whether the project is built on Windows, where the Gradle wrapper is {@code gradlew.bat}. */
    final boolean windows

    /** What the checks that ran before found, in their order. */
    final List<HealthFinding> found = []

    private final Function<String, String> typed
    private boolean versionRead
    private String projectVersion
    private ProjectEvidence evidence
    private boolean recordRead
    private ProjectState record
    private String recordFailure
    private boolean selectionRead
    private ProjectState selection

    /**
     * @param typed given a command of the command line, the command line to type for it where it is said
     */
    HealthContext(ConsoleProject project, Path home, boolean windows, Function<String, String> typed) {
        this.project = project
        this.directory = project.directory
        this.commandLineVersion = VersionInfo.getGrailsVersion()
        this.home = home
        this.windows = windows
        this.typed = typed
    }

    /**
     * Replaces what the environment says, for a specification, which cannot change the environment it
     * runs in.
     *
     * @param values the variables there are, or {@code null} for those of the environment
     */
    static void setEnvironment(Map<String, String> values) {
        environment = values == null ? SYSTEM : ({ String name -> values.get(name) } as Function<String, String>)
    }

    /**
     * @return the value of a variable of the environment, or {@code null}
     */
    String environment(String name) {
        return environment.apply(name)
    }

    /**
     * @param command a command of the command line, as {@code project migrate}
     * @return the command line to type for it
     */
    String toType(String command) {
        return typed.apply(command)
    }

    /**
     * @return the version of Grails the project is on, as its build reads it from {@code gradle.properties};
     * {@code null} where none can be read
     */
    String getProjectVersion() {
        if (!versionRead) {
            versionRead = true
            projectVersion = EarlierGrailsProject.projectVersion(directory)
        }
        return projectVersion
    }

    /**
     * @return the major version of Grails the command line is of
     */
    int getCommandLineMajor() {
        return Versions.major(commandLineVersion) ?: EarlierGrailsProject.FIRST_MAJOR
    }

    /**
     * @return whether the project is on a major version of Grails before the one of the command line
     */
    boolean isOnEarlierMajor() {
        Integer major = Versions.major(getProjectVersion())
        return major != null && major < getCommandLineMajor()
    }

    /**
     * @return the earliest version of Java Grails is built for: the lowest that the create commands take
     */
    static int getMinimumJava() {
        return JdkVersion.values().collect { JdkVersion each -> each.majorVersion() }.min()
    }

    /**
     * @return what the files of the project say of the application, see {@link ProjectEvidence}: the build file
     * is read there, as {@code project migrate} reads it
     */
    ProjectEvidence getEvidence() {
        if (evidence == null) {
            evidence = ProjectEvidence.of(directory)
        }
        return evidence
    }

    /**
     * @return the record of the project under {@code .grails}, read as the commands read it, with the
     * index of its baseline; {@code null} where there is none or it does not read, see {@link #getRecordFailure()}
     */
    ProjectState getRecord() {
        readRecord()
        return record
    }

    /**
     * @return why the record of the project does not read, in one line; {@code null} where it reads or there is none
     */
    String getRecordFailure() {
        readRecord()
        return recordFailure
    }

    /**
     * @return the manifest and the lock of the record, read as {@code project show} reads them: an
     * option a record of an earlier build lacks is taken to be the default. {@code null} where there
     * is no record or they do not read
     */
    ProjectState getSelection() {
        if (!selectionRead) {
            selectionRead = true
            try {
                selection = ProjectState.exists(directory) ? ProjectState.readSelection(directory, true) : null
            } catch (ProjectStateException ignored) {
                // said by the check of the record, from the record as the commands read it
                selection = null
            }
        }
        return selection
    }

    private void readRecord() {
        if (recordRead) {
            return
        }
        recordRead = true
        if (!hasRecordFiles()) {
            return
        }
        try {
            record = ProjectState.read(directory)
        } catch (ProjectStateException e) {
            recordFailure = e.getMessage() ?: e.getClass().getSimpleName()
        }
    }

    /**
     * @return whether the project has a file of the record under {@code .grails}, whole or not
     */
    boolean hasRecordFiles() {
        return [ProjectState.MANIFEST, ProjectState.LOCK, ProjectState.BASELINE_INDEX].any { String file -> Files.exists(directory.resolve(file), LinkOption.NOFOLLOW_LINKS) }
    }

    /**
     * @return the path of a file within the project, with {@code /} between its steps; a file above
     * the project, as the wrapper of a build of several projects, starts with {@code ../}
     */
    String relative(Path file) {
        return directory.relativize(file).join('/')
    }

    /**
     * @return the text of a file that is a file and not a link, of at most {@code most} bytes; {@code null} otherwise
     */
    static String text(Path file, int most) {
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > most) {
                return null
            }
            byte[] read = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS).withCloseable { InputStream stream -> stream.readNBytes(most + 1) }
            return read.length > most ? null : new String(read, StandardCharsets.UTF_8)
        } catch (IOException ignored) {
            return null
        }
    }

    /**
     * @return a file of properties, read as Java reads one; {@code null} where there is no such file
     */
    static Properties properties(Path file) {
        String text = text(file, MAX_FILE)
        if (text == null) {
            return null
        }
        Properties properties = new Properties()
        try {
            properties.load(new StringReader(text))
        } catch (IllegalArgumentException ignored) {
            // a malformed escape: what was read before it is kept
        }
        return properties
    }
}
