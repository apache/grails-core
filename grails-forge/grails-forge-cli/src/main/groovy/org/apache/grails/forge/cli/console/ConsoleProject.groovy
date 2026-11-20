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
import java.nio.file.attribute.UserPrincipal
import java.util.function.Predicate
import groovy.transform.CompileStatic
import org.apache.grails.forge.core.state.ProjectState

/**
 * The Grails project a directory is in, for the commands that work on a project rather than create one:
 * the nearest directory, from the given one upwards, that has a {@code grails-app} directory or the
 * record of a generated project. The search does not leave the repository the directory is in, so a
 * project that happens to lie above a checkout is not taken for the project of what is checked out.
 *
 * <p>The directory a command is run in is the project when it is one: the user chose to run there. A
 * project above it is found without the user having named it, and whoever can write to a directory
 * above can put one there, in a shared temporary directory for one. So a project above is used only
 * when it is the user's own, as git does with {@code safe.directory}: its directory and what makes it
 * a project are owned by the user who runs the command. The same holds for a Gradle wrapper that is
 * not in the directory the command is run in.</p>
 *
 * <p>The user who runs the command is the one who owns a file the command creates: the identity the
 * system gives to whatever the command writes, on every platform. For root, also under {@code sudo},
 * that is root, so that root does not run from below it what another user owns; on Windows an elevated
 * process creates files owned by the Administrators group, and takes for its own what that group owns.
 * Where the owner of a file cannot be read, nothing above the current directory is used.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class ConsoleProject {

    /** How many directories above the given one are looked at, at most. */
    public static final int MAX_LEVELS = 32

    private static final String SOURCES = 'grails-app'
    private static final List<String> RECORDS = [ProjectState.MANIFEST, ProjectState.EARLIER_RECORD, 'grails-cli.yml']
    // what makes a directory a project, and what the commands read to know what the project is
    private static final List<String> MARKERS = [SOURCES, ProjectState.DIRECTORY, ProjectState.MANIFEST, ProjectState.LOCK, ProjectState.EARLIER_RECORD, 'grails-cli.yml']
    private static final List<String> SETTINGS = ['settings.gradle', 'settings.gradle.kts']
    private static final Predicate<Path> OWNED_BY_USER = { Path path -> CurrentUser.owns(path) } as Predicate<Path>

    private static volatile Predicate<Path> ownedByUser = OWNED_BY_USER

    private final Path directory
    private final boolean above

    private ConsoleProject(Path directory, boolean above) {
        this.directory = directory
        this.above = above
    }

    /**
     * @param from the directory to start at
     * @return the project the directory is in, or {@code null} when it is in none that is used
     */
    static ConsoleProject find(Path from) {
        return search(from).project
    }

    /**
     * Looks for the project a directory is in, and keeps why there is none.
     *
     * @param from the directory to start at
     * @return what was found
     */
    static Search search(Path from) {
        Path start = from.toAbsolutePath().normalize()
        Path each = start
        for (int level = 0; each != null && level <= MAX_LEVELS; level++) {
            if (isProject(each)) {
                if (level == 0) {
                    return new Search(start, new ConsoleProject(each, false), null, null, false)
                }
                String distrusted = distrust(each, MARKERS)
                if (distrusted != null) {
                    return new Search(start, null, "The Grails project in ${GradleRunner.line(each)}, above ${GradleRunner.line(start)}, is not used: ${distrusted}. " +
                            "A project above the current directory is used when it is the user's own; run the command in ${GradleRunner.line(each)} to use this one", null, false)
                }
                return new Search(start, new ConsoleProject(each, true), null, null, false)
            }
            if (isRepositoryRoot(each)) {
                return new Search(start, null, null, each, false)
            }
            each = each.getParent()
        }
        return new Search(start, null, null, null, each != null)
    }

    /**
     * The directory a command that works on a project reads and writes in when it is run in the given
     * directory: the directory of the project the given one is in, so that the command does the same
     * in every directory of a project.
     *
     * @param from the directory the command is run in
     * @return the directory of the project, or the given directory when it is in no project that is used
     */
    static Path directoryFor(Path from) {
        ConsoleProject project = find(from)
        return project == null ? from : project.directory
    }

    /**
     * Replaces what tells whether a file is the user's own, for a specification: no test can create a
     * file that another user owns.
     *
     * @param owned given a file or a directory, whether the user who runs the command owns it;
     * {@code null} for what the system says
     */
    static void setOwnedByUser(Predicate<Path> owned) {
        ownedByUser = owned == null ? OWNED_BY_USER : owned
    }

    /**
     * @param path a file or a directory; a link is the link itself
     * @return whether the user who runs the command owns it
     */
    static boolean isOwnedByUser(Path path) {
        return ownedByUser.test(path)
    }

    /**
     * @return the directory of the project
     */
    Path getDirectory() {
        return directory
    }

    /**
     * The Gradle wrapper of the project: the one in its directory, or for a project that is part of a
     * larger build the nearest one above it, within the repository. The wrapper is a file, or a link
     * to a file in the same directory. A wrapper that is not in the directory the command is run in is
     * also the user's own, and one above the project is in a directory that is the user's own and has
     * the settings of a Gradle build, which make it the root of a build of several projects.
     *
     * @param windows whether the script for Windows is asked for
     * @return the wrapper script, or why there is none that is run
     */
    Wrapper gradleWrapper(boolean windows) {
        String name = wrapperName(windows)
        Path each = directory
        for (int level = 0; each != null && level <= MAX_LEVELS; level++) {
            Path wrapper = each.resolve(name)
            if (Files.isRegularFile(wrapper)) {
                String refused = refuseWrapper(each, wrapper, level > 0)
                return refused == null ? new Wrapper(wrapper, null) :
                        new Wrapper(null, "The Gradle wrapper ${GradleRunner.line(wrapper)} is not run for the project in ${GradleRunner.line(directory)}: ${refused}")
            }
            if (isRepositoryRoot(each)) {
                return new Wrapper(null, noWrapper(name, 'in a directory above it in the same repository'))
            }
            each = each.getParent()
        }
        return new Wrapper(null, noWrapper(name, each == null ? 'in a directory above it' : "in one of the ${MAX_LEVELS} directories above it, which is as far as is looked"))
    }

    /**
     * @param relative a directory of the sources of the project, as a path within the project
     * @return whether the directory is reached through a link or lies outside the project: what is
     * in it is then not of this project
     */
    boolean isLinked(String relative) {
        Path each = directory
        for (Path step : Path.of(relative)) {
            each = each.resolve(step)
            if (Files.isSymbolicLink(each)) {
                return true
            }
        }
        try {
            return Files.exists(each) && !each.toRealPath().startsWith(directory.toRealPath())
        } catch (IOException ignored) {
            return true
        }
    }

    /**
     * @param windows whether the script for Windows is asked for
     * @return the file name of the Gradle wrapper script
     */
    static String wrapperName(boolean windows) {
        return windows ? 'gradlew.bat' : 'gradlew'
    }

    private String noWrapper(String name, String where) {
        return "The project in ${GradleRunner.line(directory)} has no Gradle wrapper: there is no ${name} in it or ${where}"
    }

    // why the wrapper found in a directory is not run, or null when it is
    private String refuseWrapper(Path holder, Path wrapper, boolean aboveProject) {
        if (aboveProject) {
            if (!SETTINGS.any { String settings -> Files.isRegularFile(holder.resolve(settings)) }) {
                return "${GradleRunner.line(holder)} is above the project and has no ${SETTINGS.join(' or ')}, so it is not the root of a build the project is part of"
            }
            String distrusted = distrust(holder, Collections.<String>emptyList())
            if (distrusted != null) {
                return distrusted
            }
        }
        Path target = null
        if (Files.isSymbolicLink(wrapper)) {
            try {
                target = wrapper.toRealPath()
                if (!target.startsWith(holder.toRealPath())) {
                    return "it is a link to a file outside ${GradleRunner.line(holder)}"
                }
            } catch (IOException ignored) {
                return 'it is a link that cannot be followed'
            }
        }
        if (above || aboveProject) {
            if (!ownedByUser.test(wrapper)) {
                return notOwned(wrapper)
            }
            if (target != null && !ownedByUser.test(target)) {
                return notOwned(target)
            }
        }
        return null
    }

    // why a directory above, with what is looked at in it, is not the user's own; null when it is
    private static String distrust(Path directory, List<String> names) {
        if (!ownedByUser.test(directory)) {
            return notOwned(directory)
        }
        for (String name : names) {
            Path file = directory.resolve(name)
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS) && !ownedByUser.test(file)) {
                return notOwned(file)
            }
        }
        return null
    }

    private static String notOwned(Path path) {
        String owner
        try {
            owner = Files.getOwner(path, LinkOption.NOFOLLOW_LINKS).getName()
        } catch (IOException | UnsupportedOperationException | SecurityException ignored) {
            return "the owner of ${GradleRunner.line(path)} cannot be read, so it is not known to be the user who runs the command"
        }
        return "${GradleRunner.line(path)} is owned by ${GradleRunner.line(owner)}, not by the user who runs the command"
    }

    private static boolean isProject(Path directory) {
        if (Files.isDirectory(directory.resolve(SOURCES))) {
            return true
        }
        return RECORDS.any { String record -> Files.isRegularFile(directory.resolve(record), LinkOption.NOFOLLOW_LINKS) }
    }

    // .git is a directory in a clone and a file in a work tree or a submodule
    private static boolean isRepositoryRoot(Path directory) {
        return Files.exists(directory.resolve('.git'), LinkOption.NOFOLLOW_LINKS)
    }

    /** What looking for the project of a directory came to. */
    static final class Search {

        /** The directory the search started at. */
        final Path from

        /** The project, or {@code null} when none is used. */
        final ConsoleProject project

        /** Why the project above the directory is not used, as one line without the full stop; {@code null} when there is no such project. */
        final String untrusted

        private final Path repository
        private final boolean limit

        private Search(Path from, ConsoleProject project, String untrusted, Path repository, boolean limit) {
            this.from = from
            this.project = project
            this.untrusted = untrusted
            this.repository = repository
            this.limit = limit
        }

        /**
         * @param notDone what did not happen because no project is used, as a sentence without the full stop
         * @return the one line that says there is no project, or that the one above is not used and why
         */
        String none(String notDone) {
            if (untrusted != null) {
                return "${untrusted}. ${notDone}"
            }
            String above = 'a directory above it'
            if (repository != null) {
                above = 'a directory above it in the same repository'
            } else if (limit) {
                above = "one of the ${MAX_LEVELS} directories above it, which is as far as is looked,"
            }
            return "There is no Grails project here: neither ${GradleRunner.line(from)} nor ${above} has a grails-app directory " +
                    "or the record of a generated project. ${notDone}"
        }
    }

    /** The Gradle wrapper of a project, or why none is run. */
    static final class Wrapper {

        /** The wrapper script, or {@code null}. */
        final Path file

        /** Why no wrapper is run, as one line without the full stop; {@code null} when there is one. */
        final String refusal

        private Wrapper(Path file, String refusal) {
            this.file = file
            this.refusal = refusal
        }
    }

    // the user who runs the command, found when something above the current directory is first looked at
    private static final class CurrentUser {

        private static final UserPrincipal USER = find()

        static boolean owns(Path path) {
            if (USER == null) {
                return false
            }
            try {
                return USER == Files.getOwner(path, LinkOption.NOFOLLOW_LINKS)
            } catch (IOException | UnsupportedOperationException | SecurityException ignored) {
                return false
            }
        }

        private static UserPrincipal find() {
            try {
                Path created = Files.createTempFile('grails-user', null)
                try {
                    return Files.getOwner(created, LinkOption.NOFOLLOW_LINKS)
                } finally {
                    Files.deleteIfExists(created)
                }
            } catch (IOException | UnsupportedOperationException | SecurityException ignored) {
                // nothing can be created where temporary files go: the home directory is the user's as well
            }
            try {
                String home = System.getProperty('user.home')
                return home == null ? null : Files.getOwner(Path.of(home))
            } catch (IOException | UnsupportedOperationException | SecurityException | IllegalArgumentException ignored) {
                return null
            }
        }
    }
}
