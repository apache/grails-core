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
 * that decide the project's build and its own commands; otherwise the task is run first, so a plugin
 * added to the build brings its commands with it. When the build fails, the commands of the last
 * manifest are kept, and the build is not run for them again until one of those files changes.</p>
 */
@CompileStatic
class ProjectCommands {

    /** The task of the Grails Gradle plugin that writes the manifest */
    static final String MANIFEST_TASK = 'generateCommandManifest'

    /** The project property that tells the task where to write the manifest */
    static final String MANIFEST_PROPERTY = 'grailsCommandManifest'

    /** Where the manifest is kept, relative to the project directory */
    static final String MANIFEST_PATH = 'build/grails/commands.json'

    /** Kept next to the manifest when the build could not write it, so the build is not run again for nothing */
    static final String FAILED_PATH = 'build/grails/commands.failed'

    /** The newest version of the manifest's format this CLI reads */
    static final int FORMAT_VERSION = 1

    private static final List<String> BUILD_FILE_SUFFIXES = ['.gradle', '.gradle.kts', '.properties', '.toml']

    /** Directories of the build whose files change the build: scripts applied from them, and build logic */
    private static final List<String> BUILD_DIRS = ['gradle', 'buildSrc']

    /** Directories under the build directories that the build writes, rather than reads */
    private static final List<String> OUTPUT_DIRS = ['build', '.gradle']

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
     * @return the commands, or none when the directory holds no Grails project or none could be read
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
     * @return the commands, or none when the directory holds no Grails project or none could be read
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
        if (runBuild && outdated(manifest)) {
            update(manifest, console)
        }
        manifest.isFile() ? read(manifest, console) : []
    }

    private void update(File manifest, ConsoleOutput console) {
        File failed = new File(projectDir, FAILED_PATH)
        console.out('Reading the commands of the project from its build...')
        String problem
        try {
            int exitCode = gradle.runSilently([MANIFEST_TASK, "-P${MANIFEST_PROPERTY}=${MANIFEST_PATH}".toString()])
            problem = exitCode == 0 ? null : "its build failed with exit code ${exitCode}"
        }
        catch (IOException e) {
            problem = e.message
        }
        if (problem == null && manifest.isFile()) {
            // the task is up to date when the classpath is, without writing the manifest again
            manifest.setLastModified(System.currentTimeMillis())
            failed.delete()
            return
        }
        failed.parentFile.mkdirs()
        failed.setText((problem ?: 'its build wrote no manifest') + '\n', 'UTF-8')
        String kept = manifest.isFile() ? 'The commands it listed before are offered' : 'Its commands are not offered'
        console.warning("Could not read the commands of the project: ${problem ?: 'its build wrote no manifest'}. ${kept}, " +
                'and the build is not run for them again until a build file changes. A project of Grails 9 or ' +
                "later lists them with its ${MANIFEST_TASK} Gradle task; run it to see what went wrong.")
    }

    private List<ProjectCommand> read(File manifest, ConsoleOutput console) {
        try {
            Object parsed = new JsonSlurper().parse(manifest, 'UTF-8')
            if (!(parsed instanceof Map)) {
                throw new IllegalArgumentException('it is not a JSON object')
            }
            Map<String, Object> content = (Map<String, Object>) parsed
            Object version = content.version
            if (!(version instanceof Integer)) {
                throw new IllegalArgumentException('it has no format version')
            }
            if ((int) version > FORMAT_VERSION) {
                throw new IllegalArgumentException("it was written in format ${version}, by a newer Grails, and this " +
                        "Grails Forge CLI reads up to format ${FORMAT_VERSION}")
            }
            Object commands = content.commands
            if (!(commands instanceof List)) {
                throw new IllegalArgumentException('it has no list of commands')
            }
            return ((List<Object>) commands).collect { Object command -> create(command) }
        }
        catch (JsonException | RuntimeException e) {
            console.warning("Could not read the commands of the project from ${manifest}, so they are not offered: ${e.message}")
            return []
        }
    }

    private ProjectCommand create(Object command) {
        if (!(command instanceof Map) || !(((Map) command).name instanceof String) || !((Map) command).name) {
            throw new IllegalArgumentException("a command has no name: ${command}")
        }
        new ProjectCommand((Map<String, Object>) command, gradle)
    }

    private boolean outdated(File manifest) {
        File failed = new File(projectDir, FAILED_PATH)
        long lastRead = Math.max(manifest.isFile() ? manifest.lastModified() : 0L, failed.isFile() ? failed.lastModified() : 0L)
        if (lastRead == 0L) {
            return true
        }
        ([projectDir, gradle.rootDir] as LinkedHashSet<File>).any { File dir -> changedBuild(dir, lastRead) } ||
                changedSources(lastRead)
    }

    /**
     * Whether the commands of the application changed. When their directory is gone, {@code grails-app},
     * which every project has, changed when it was removed.
     */
    private boolean changedSources(long since) {
        File sources = new File(projectDir, COMMAND_SOURCES)
        sources.isDirectory() ? changed(sources, since) : sources.parentFile.lastModified() > since
    }

    private static boolean changedBuild(File dir, long since) {
        dir.listFiles()?.any { File file ->
            file.isFile() && BUILD_FILE_SUFFIXES.any { String suffix -> file.name.endsWith(suffix) } &&
                    file.lastModified() > since
        } || BUILD_DIRS.any { String name -> changed(new File(dir, name), since) }
    }

    /**
     * Whether a file in the directory, or the directory itself or one below it, changed: a directory
     * changes when a file in it is added, deleted or renamed.
     */
    private static boolean changed(File dir, long since) {
        if (!dir.isDirectory()) {
            return false
        }
        if (dir.lastModified() > since) {
            return true
        }
        dir.listFiles()?.any { File file ->
            file.isDirectory() ? !(file.name in OUTPUT_DIRS) && changed(file, since) : file.lastModified() > since
        }
    }
}
