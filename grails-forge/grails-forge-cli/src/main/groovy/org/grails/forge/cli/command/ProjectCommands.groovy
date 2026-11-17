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
package org.grails.forge.cli.command

import groovy.json.JsonException
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic

import org.grails.forge.io.ConsoleOutput

/**
 * Finds the commands of the Grails project in a directory: those of the application and of its
 * plugins, which {@code runCommand} runs.
 *
 * <p>The project's build describes them in a manifest, written by the Grails Gradle plugin's
 * {@code generateCommandManifest} task. The manifest is read as long as it is newer than the files
 * that decide the project's dependencies and its own commands; otherwise the task is run first, so
 * a plugin added to the build brings its commands with it.</p>
 */
@CompileStatic
class ProjectCommands {

    /** The task of the Grails Gradle plugin that writes the manifest */
    static final String MANIFEST_TASK = 'generateCommandManifest'

    /** Where the task writes the manifest, relative to the project directory */
    static final String MANIFEST_PATH = 'build/grails/commands.json'

    private static final List<String> BUILD_FILES = ['build.gradle', 'build.gradle.kts', 'settings.gradle',
                                                     'settings.gradle.kts', 'gradle.properties',
                                                     'gradle/libs.versions.toml']

    /** Where an application keeps its own commands */
    private static final String COMMAND_SOURCES = 'grails-app/commands'

    private final File projectDir
    private final ProjectGradle gradle

    ProjectCommands(File projectDir) {
        this(projectDir, new ProjectGradle(projectDir))
    }

    ProjectCommands(File projectDir, ProjectGradle gradle) {
        this.projectDir = projectDir
        this.gradle = gradle
    }

    /**
     * @return whether the directory holds a Grails project built with Gradle
     */
    boolean isGrailsProject() {
        new File(projectDir, 'grails-app').isDirectory() &&
                (new File(projectDir, 'build.gradle').isFile() || new File(projectDir, 'build.gradle.kts').isFile())
    }

    /**
     * Reads the commands of the project, after updating the manifest when it is missing or older than
     * the project's build or its commands.
     *
     * @param console where to say that the build is being run, and why the commands could not be read
     * @return the commands, or none when the directory holds no Grails project or the build failed
     */
    List<ProjectCommand> load(ConsoleOutput console) {
        load(console, true)
    }

    /**
     * Reads the commands of the project.
     *
     * @param console where to say that the build is being run, and why the commands could not be read
     * @param runBuild whether the manifest may be updated by running the build; when not, a manifest
     * that is there is read as it is, and none means no commands
     * @return the commands, or none when the directory holds no Grails project or the build failed
     */
    List<ProjectCommand> load(ConsoleOutput console, boolean runBuild) {
        if (!grailsProject) {
            return []
        }
        if (!gradle.available) {
            console.warning('The build of the project has no Gradle wrapper, so its commands are not offered.')
            return []
        }
        File manifest = new File(projectDir, MANIFEST_PATH)
        if (!runBuild) {
            return manifest.isFile() ? read(manifest, console) : []
        }
        if (outdated(manifest)) {
            console.out('Reading the commands of the project from its build...')
            int exitCode = gradle.runSilently([MANIFEST_TASK])
            if (exitCode != 0 || !manifest.isFile()) {
                console.warning("Could not read the commands of the project. Run its ${MANIFEST_TASK} Gradle task to see why.")
                return []
            }
        }
        read(manifest, console)
    }

    private List<ProjectCommand> read(File manifest, ConsoleOutput console) {
        Map<String, Object> parsed
        try {
            parsed = (Map<String, Object>) new JsonSlurper().parse(manifest, 'UTF-8')
        }
        catch (JsonException | ClassCastException e) {
            console.warning("Could not read the commands of the project from ${manifest}: ${e.message}")
            return []
        }
        List<Map<String, Object>> commands = (parsed.commands ?: []) as List<Map<String, Object>>
        commands.collect { Map<String, Object> command -> new ProjectCommand(command, gradle) }
    }

    private boolean outdated(File manifest) {
        if (!manifest.isFile()) {
            return true
        }
        long written = manifest.lastModified()
        List<File> buildFiles = (BUILD_FILES.collect { new File(projectDir, it) } +
                BUILD_FILES.collect { new File(gradle.rootDir, it) }).unique()
        buildFiles.any { File file -> file.lastModified() > written } ||
                newerFile(new File(projectDir, COMMAND_SOURCES), written)
    }

    private static boolean newerFile(File dir, long than) {
        if (!dir.isDirectory()) {
            return false
        }
        boolean newer = false
        dir.eachFileRecurse { File file ->
            if (!newer && file.isFile() && file.lastModified() > than) {
                newer = true
            }
        }
        newer
    }
}
