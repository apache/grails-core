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

import groovy.transform.CompileStatic

/**
 * Runs Gradle for a project through the Gradle wrapper of its build: the wrapper in the project
 * directory, or the one in the root directory of the build the project belongs to, the first
 * directory above it with a settings file. No wrapper outside the build is ever run, and neither is
 * a {@code gradle} found on the path.
 *
 * <p>Every run names the project with the Grails Gradle plugin's {@code grailsCommandProject} property,
 * so the tasks it runs run in that project only, and not also in the projects below it.</p>
 */
@CompileStatic
class ProjectGradle {

    private static final boolean WINDOWS = System.getProperty('os.name', '').toLowerCase(Locale.ENGLISH).contains('win')

    private static final List<String> SETTINGS_FILES = ['settings.gradle', 'settings.gradle.kts']

    /** The project property of the Grails Gradle plugin that runs its command tasks in one project only */
    static final String COMMAND_PROJECT_PROPERTY = 'grailsCommandProject'

    private final File projectDir
    private final File wrapper
    private final File rootDir

    ProjectGradle(File projectDir) {
        this.projectDir = projectDir
        this.wrapper = findWrapper(projectDir)
        this.rootDir = findSettingsDir(projectDir) ?: projectDir
    }

    /**
     * @return whether the project's build has a Gradle wrapper to run
     */
    boolean isAvailable() {
        wrapper != null
    }

    /**
     * @return the root directory of the build the project belongs to: the first directory, from the
     * project's upward, with a settings file, as Gradle finds it, or the project directory when there is none
     */
    File getRootDir() {
        rootDir
    }

    /**
     * @return the wrapper the build runs through, or none when it has none
     */
    File getWrapper() {
        wrapper
    }

    /**
     * Runs Gradle in the project quietly, with its output shown.
     *
     * @param arguments the tasks and arguments to run
     * @return the exit code of Gradle
     * @throws IOException when the wrapper cannot be started
     */
    int run(List<String> arguments) {
        start(arguments) { ProcessBuilder builder ->
            builder.inheritIO()
            false
        }
    }

    /**
     * Runs Gradle in the project quietly, with its output hidden.
     *
     * @param arguments the tasks and arguments to run
     * @return the exit code of Gradle
     * @throws IOException when the wrapper cannot be started
     */
    int runSilently(List<String> arguments) {
        start(arguments) { ProcessBuilder builder ->
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD)
            true
        }
    }

    private int start(List<String> arguments, Closure<?> redirect) {
        if (wrapper == null) {
            throw new IOException("The build of ${projectDir} has no Gradle wrapper")
        }
        List<String> command = [wrapper.path, '-p', projectDir.path, '-q', '--console=plain',
                "-P${COMMAND_PROJECT_PROPERTY}=${projectDir.path}".toString()]
        command.addAll(arguments)
        ProcessBuilder builder = new ProcessBuilder(command).directory(projectDir)
        boolean silent = redirect.call(builder) as boolean
        try {
            Process process = builder.start()
            if (silent) {
                // nobody sees a prompt of the build, as one for the password of a repository, nor answers it
                process.outputStream.close()
            }
            return process.waitFor()
        }
        catch (IllegalArgumentException e) {
            // ProcessBuilder refuses some arguments of a batch file, such as one with a quote inside it
            throw new IOException("Could not run ${wrapper}: ${e.message}", e)
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt()
            throw new IOException("Gradle was interrupted while running ${arguments}", e)
        }
    }

    private static File findWrapper(File projectDir) {
        String name = WINDOWS ? 'gradlew.bat' : 'gradlew'
        File own = new File(projectDir, name)
        if (own.isFile()) {
            return own
        }
        // the build's root is the first directory with a settings file, as Gradle finds it; a wrapper
        // further up belongs to something else
        File settingsDir = findSettingsDir(projectDir)
        File rootWrapper = settingsDir == null ? null : new File(settingsDir, name)
        rootWrapper?.isFile() ? rootWrapper : null
    }

    private static File findSettingsDir(File projectDir) {
        for (File dir = projectDir; dir != null; dir = dir.parentFile) {
            if (SETTINGS_FILES.any { new File(dir, it).isFile() }) {
                return dir
            }
        }
        null
    }
}
