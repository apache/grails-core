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

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import groovy.transform.CompileStatic
import org.apache.grails.forge.core.state.ProjectState

/**
 * The Grails project a directory is in, for the commands that work on a project rather than create one:
 * the nearest directory, from the given one upwards, that has a {@code grails-app} directory or the
 * record of a generated project. The search does not leave the repository the directory is in, so a
 * project that happens to lie above a checkout is not taken for the project of what is checked out.
 *
 * @since 9.0
 */
@CompileStatic
final class ConsoleProject {

    /** How many directories above the given one are looked at, at most. */
    public static final int MAX_LEVELS = 32

    private static final List<String> RECORDS = [ProjectState.MANIFEST, ProjectState.EARLIER_RECORD, 'grails-cli.yml']

    private final Path directory

    private ConsoleProject(Path directory) {
        this.directory = directory
    }

    /**
     * @param from the directory to start at
     * @return the project the directory is in, or {@code null} when it is in none
     */
    static ConsoleProject find(Path from) {
        Path each = from.toAbsolutePath().normalize()
        for (int level = 0; each != null && level <= MAX_LEVELS; level++) {
            if (isProject(each)) {
                return new ConsoleProject(each)
            }
            if (isRepositoryRoot(each)) {
                return null
            }
            each = each.getParent()
        }
        return null
    }

    /**
     * @return the directory of the project
     */
    Path getDirectory() {
        return directory
    }

    /**
     * The Gradle wrapper of the project: the one in its directory, or for a project that is part of a
     * larger build the nearest one above it, within the repository.
     *
     * @param windows whether the script for Windows is asked for
     * @return the wrapper script, or {@code null} when there is none
     */
    Path findGradleWrapper(boolean windows) {
        String name = wrapperName(windows)
        Path each = directory
        for (int level = 0; each != null && level <= MAX_LEVELS; level++) {
            Path wrapper = each.resolve(name)
            if (Files.isRegularFile(wrapper)) {
                return wrapper
            }
            if (isRepositoryRoot(each)) {
                return null
            }
            each = each.getParent()
        }
        return null
    }

    /**
     * @param windows whether the script for Windows is asked for
     * @return the file name of the Gradle wrapper script
     */
    static String wrapperName(boolean windows) {
        return windows ? 'gradlew.bat' : 'gradlew'
    }

    private static boolean isProject(Path directory) {
        if (Files.isDirectory(directory.resolve('grails-app'))) {
            return true
        }
        return RECORDS.any { String record -> Files.isRegularFile(directory.resolve(record), LinkOption.NOFOLLOW_LINKS) }
    }

    // .git is a directory in a clone and a file in a work tree or a submodule
    private static boolean isRepositoryRoot(Path directory) {
        return Files.exists(directory.resolve('.git'), LinkOption.NOFOLLOW_LINKS)
    }
}
