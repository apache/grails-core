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
 * Runs Gradle for a project: through the project's Gradle wrapper, found in the project directory or
 * the build's root above it, or through {@code gradle} on the path when the project has no wrapper.
 */
@CompileStatic
class ProjectGradle {

    private static final boolean WINDOWS = System.getProperty('os.name', '').toLowerCase(Locale.ENGLISH).contains('win')

    private final File projectDir
    private final File wrapper

    ProjectGradle(File projectDir) {
        this.projectDir = projectDir
        this.wrapper = findWrapper(projectDir)
    }

    /**
     * @return the root directory of the build the project belongs to: the directory of its Gradle
     * wrapper, or the project directory when it has none
     */
    File getRootDir() {
        wrapper?.parentFile ?: projectDir
    }

    /**
     * Runs Gradle in the project quietly, with its output shown.
     *
     * @param arguments the tasks and arguments to run
     * @return the exit code of Gradle
     */
    int run(List<String> arguments) {
        start(arguments) { ProcessBuilder builder -> builder.inheritIO() }
    }

    /**
     * Runs Gradle in the project quietly, with its output hidden.
     *
     * @param arguments the tasks and arguments to run
     * @return the exit code of Gradle
     */
    int runSilently(List<String> arguments) {
        start(arguments) { ProcessBuilder builder ->
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD)
        }
    }

    private int start(List<String> arguments, Closure<?> redirect) {
        List<String> command = [wrapper?.path ?: (WINDOWS ? 'gradle.bat' : 'gradle'),
                                '-p', projectDir.path, '-q', '--console=plain']
        command.addAll(arguments)
        ProcessBuilder builder = new ProcessBuilder(command).directory(projectDir)
        redirect.call(builder)
        try {
            return builder.start().waitFor()
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt()
            throw new IOException("Gradle was interrupted while running ${arguments}", e)
        }
    }

    private static File findWrapper(File projectDir) {
        String name = WINDOWS ? 'gradlew.bat' : 'gradlew'
        for (File dir = projectDir; dir != null; dir = dir.parentFile) {
            File candidate = new File(dir, name)
            if (candidate.isFile()) {
                return candidate
            }
        }
        null
    }
}
