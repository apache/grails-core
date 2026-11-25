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

import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.regex.Pattern
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
 * when it is the user's own, as git does with {@code safe.directory}: its directory is owned by the
 * user who runs the command and cannot be written to by everybody, and what makes it a
 * project is owned by that user and, where it is a file, has no other name (a hard link has the
 * owner of the file it is another name of). The same holds for a Gradle wrapper that is not in the
 * directory the command is run in, with the settings beside it and the jar and properties it runs.
 * Owners, modes and link counts are what is looked at: not access control lists, and nothing is held
 * between the look and the run. Windows has no modes, and there the owner alone is looked at, as git
 * does there: whom the access control list of a directory lets write to it is not read, and
 * {@link ProcessUser} says when what the group of the administrators owns is the user's own.</p>
 *
 * <p>A project can be a part of a larger Gradle build: its directory then has no settings of its own,
 * and the nearest directory above it that has them is the root of the build, which is where Gradle
 * itself takes the build to be. The Gradle wrapper of that root is the one that is run, and the
 * tasks a command runs are named by the path the project has in that build, so that they are the
 * tasks of this project and of no other. That path is the one the directories give, which the settings
 * of the build need not give the directory, so Gradle is made to check it before it runs anything: see
 * {@link #pathCheck}.</p>
 *
 * <p>The user who runs the command is the one {@link ProcessUser} establishes from the process. For
 * root, also under {@code sudo}, that is root, so that root does not run from below it what another
 * user owns. A directory named in the environment variable {@link #SAFE_DIRECTORY} is used whatever
 * it and what is in it are owned by: the way out where the files are another user's by design, as
 * for root in a container over a directory of the host.</p>
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
    // a directory name Gradle takes for the name of a project as it is
    private static final Pattern PROJECT_NAME = Pattern.compile(/[A-Za-z0-9_][A-Za-z0-9_.-]{0,199}/)
    private static final Predicate<Path> OWNED_BY_USER = { Path path -> ProcessUser.current().owns(path) } as Predicate<Path>

    /**
     * The environment variable that names the directories that are used whoever owns them: the
     * directories of projects, and of the roots of builds whose wrapper is run, as absolute paths
     * separated as the entries of {@code PATH} are. An entry is one directory, compared by its real
     * path; there are no patterns. The one value {@code *} is every directory.
     */
    public static final String SAFE_DIRECTORY = 'GRAILS_SAFE_DIRECTORY'

    private static final String EVERY_DIRECTORY = '*'
    // where the Grails projects of a build are looked for: not in what a build writes, and not deeper or wider than this
    private static final List<String> NOT_SOURCES = ['build', 'node_modules', 'out', 'target']
    private static final int MAX_DEPTH = 5
    private static final int MAX_ENTRIES = 20000
    private static final int MAX_NAMED = 10
    // what the wrapper script of a directory runs
    private static final String WRAPPER_JAR = 'gradle/wrapper/gradle-wrapper.jar'
    private static final List<String> WRAPPER_FILES = ['gradle', 'gradle/wrapper', WRAPPER_JAR, 'gradle/wrapper/gradle-wrapper.properties']
    // the directories of those, which the script reaches through whatever link they are
    private static final List<String> WRAPPER_DIRECTORIES = ['gradle', 'gradle/wrapper']
    private static final Supplier<String> ENVIRONMENT = { System.getenv(SAFE_DIRECTORY) } as Supplier<String>

    private static volatile Predicate<Path> ownedByUser = OWNED_BY_USER
    private static volatile Supplier<String> safeDirectories = ENVIRONMENT

    private final Path directory
    private final boolean above
    private Path buildRoot
    private boolean buildRootLooked
    // the path of the project in its build that a task was named by, see pathCheck
    private String namedBy

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
                            "A project above the current directory is used when it is the user's own; run the command in ${GradleRunner.line(each)} to use this one, " +
                            "or name ${GradleRunner.line(each)} in the environment variable ${SAFE_DIRECTORY}", null, false)
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
     * The Gradle build a directory is in, for a command that runs Gradle and needs no Grails project:
     * the directory when it has the settings of a build, or is below a directory that has them.
     *
     * @param from the directory Gradle is to run in
     * @return the directory as what Gradle is run for, or {@code null} when it is in no Gradle build
     */
    static ConsoleProject build(Path from) {
        ConsoleProject build = new ConsoleProject(from.toAbsolutePath().normalize(), false)
        return hasSettings(build.directory) || build.getBuildRoot() != null ? build : null
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
     * Replaces what the environment says of the directories that are used whoever owns them, for a
     * specification, which cannot change the environment it runs in.
     *
     * @param value what {@link #SAFE_DIRECTORY} is taken to be, empty for nothing; {@code null} for
     * what the environment says
     */
    static void setSafeDirectories(String value) {
        safeDirectories = value == null ? ENVIRONMENT : ({ value } as Supplier<String>)
    }

    /**
     * @param path a file or a directory of a project that is used, which the commands keep there
     * @param project the project
     * @return whether it is the user's to read and act on: owned by the user who runs the command, or
     * in a project whose directory {@link #SAFE_DIRECTORY} names, where the files are another user's
     * by design
     */
    static boolean isOwnedByUser(Path path, ConsoleProject project) {
        return ownedByUser.test(path) || isSafeDirectory(project.directory)
    }

    /**
     * @param directory a directory
     * @return whether {@link #SAFE_DIRECTORY} names it, or names every directory
     */
    static boolean isSafeDirectory(Path directory) {
        String value = safeDirectories.get()
        if (value == null || value.trim().isEmpty()) {
            return false
        }
        if (value.trim() == EVERY_DIRECTORY) {
            return true
        }
        Path real
        try {
            real = directory.toRealPath()
        } catch (IOException | RuntimeException ignored) {
            return false
        }
        for (String entry : value.split(Pattern.quote(File.pathSeparator))) {
            try {
                Path named = entry.isEmpty() ? null : Path.of(entry)
                // an entry is the directory it names and nothing else: no pattern, and no path that depends on where the command runs
                if (named != null && named.isAbsolute() && named.toRealPath() == real) {
                    return true
                }
            } catch (IOException | RuntimeException ignored) {
                // an entry that names nothing that is there names no directory
            }
        }
        return false
    }

    /**
     * @return the directory of the project
     */
    Path getDirectory() {
        return directory
    }

    /**
     * @return the root of the Gradle build the project is a part of: the nearest directory above it,
     * within the repository, that has the settings of a build. {@code null} for a project that has
     * settings itself, which is a build of its own, and where no directory above has any
     */
    Path getBuildRoot() {
        if (!buildRootLooked) {
            buildRootLooked = true
            if (!hasSettings(directory) && !isRepositoryRoot(directory)) {
                Path each = directory.getParent()
                for (int level = 1; each != null && level <= MAX_LEVELS; level++) {
                    if (hasSettings(each)) {
                        buildRoot = each
                        break
                    }
                    if (isRepositoryRoot(each)) {
                        break
                    }
                    each = each.getParent()
                }
            }
        }
        return buildRoot
    }

    /**
     * @return the path of the project in the build it is a part of, as {@code :apps:shop} for the
     * directory {@code apps/shop} of the root, which is the path Gradle gives a project that is
     * included by its directory. {@code null} for a project that is a build of its own, and where
     * a directory on the way has a name that is no name of a Gradle project
     */
    String getGradlePath() {
        Path root = getBuildRoot()
        if (root == null) {
            return null
        }
        StringBuilder path = new StringBuilder()
        for (Path step : root.relativize(directory)) {
            String name = step.toString()
            if (!(name ==~ PROJECT_NAME)) {
                return null
            }
            path.append(':').append(name)
        }
        return path.length() == 0 ? null : path.toString()
    }

    /**
     * @param name the name of a task of the project, as {@code bootRun}
     * @return the task as a command gives it to Gradle: by the path of the project where the project is
     * a part of a larger build, and as the task of the root where the project is the root of a build,
     * which the settings of a build in its directory make it, so that it is the task of this project
     * and of no project below or beside it: Gradle runs a task that is named without a path in every
     * project of the build that has one of the name, a plugin that is included in the build of an
     * application for one. By its name alone otherwise, where nothing says what the project is in
     * the build Gradle finds for it
     */
    String task(String name) {
        String path = getGradlePath()
        if (path == null && getBuildRoot() == null && SETTINGS.any { String settings -> Files.isRegularFile(directory.resolve(settings), LinkOption.NOFOLLOW_LINKS) }) {
            namedBy = ':'
            return ":${name}".toString()
        }
        if (path != null) {
            namedBy = path
        }
        return path == null ? name : "${path}:${name}".toString()
    }

    /**
     * What Gradle is given besides the arguments of a command, so that the tasks the command named by
     * the path of this project ({@link #task}) are run only where that path is this project: the path is
     * the one the directories give, or the root project where the directory has the settings of a build,
     * and the settings of a build can give the directory another path and that path to another directory.
     * See {@link ProjectPathCheck}.
     *
     * @return the arguments, none where no task was named by a path
     * @throws IOException with one line, when what Gradle is given for it cannot be written; Gradle is not
     * to be run then, since the path would be a guess
     */
    List<String> pathCheck() throws IOException {
        return namedBy == null ? Collections.<String>emptyList() : ProjectPathCheck.arguments(this, namedBy)
    }

    /**
     * The Gradle wrapper of the project: the one in its directory, or for a project that is part of a
     * larger build the nearest one above it, within the repository. The wrapper is a file, or a link
     * to a file in the directory the link is in or below it. A wrapper that is not in the directory the
     * command is run in is also the user's own, with the jar and the properties it runs, and one above
     * the project is in a directory that is the user's own and has the settings of a Gradle build, which
     * make it the root of a build of several projects and are the user's own too. Such a wrapper that is
     * a link names its file itself, through no second link, and the file and every directory from the
     * link down to it are the user's own as well: who can write to a directory on the way decides
     * which file the link leads to. So are the jar and the properties beside that file, which are the
     * ones the script runs, besides those beside the link. The directories {@code gradle} and
     * {@code gradle/wrapper} of either place are held to the same where they are links: what they lead
     * to is a directory within the directory of the wrapper, named itself, and it and every directory on
     * the way to it are the user's own, as an ordinary {@code gradle} directory there is.
     *
     * @param windows whether the script for Windows is asked for
     * @return the wrapper script, or why there is none that is run
     */
    Wrapper gradleWrapper(boolean windows) {
        String name = wrapperName(windows)
        Path root = getBuildRoot()
        if (root != null && Files.isRegularFile(root.resolve(name))) {
            // the build decides which Gradle it is built with: a wrapper that was left in the project is not the build's
            return wrapperIn(root, root.resolve(name), true, windows)
        }
        Path each = directory
        for (int level = 0; each != null && level <= MAX_LEVELS; level++) {
            Path wrapper = each.resolve(name)
            if (Files.isRegularFile(wrapper)) {
                return wrapperIn(each, wrapper, level > 0, windows)
            }
            if (isRepositoryRoot(each)) {
                return new Wrapper(null, noWrapper(name, 'in a directory above it in the same repository'), null)
            }
            each = each.getParent()
        }
        return new Wrapper(null, noWrapper(name, each == null ? 'in a directory above it' : "in one of the ${MAX_LEVELS} directories above it, which is as far as is looked"), null)
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

    // The wrapper found in a directory, or why it is not run. A wrapper that is a link is followed to its
    // file here, once: that file is the one that is held to the rule of a wrapper above, and the one whose
    // directory has the jar the script runs, which is what a process is known by to be this wrapper.
    private Wrapper wrapperIn(Path holder, Path wrapper, boolean aboveProject, boolean windows) {
        Path target = null
        String refused = null
        if (aboveProject && !hasSettings(holder)) {
            refused = "${GradleRunner.line(holder)} is above the project and has no ${SETTINGS.join(' or ')}, so it is not the root of a build the project is part of"
        } else if (Files.isSymbolicLink(wrapper)) {
            try {
                target = wrapper.toRealPath()
                if (!target.startsWith(holder.toRealPath())) {
                    refused = "it is a link to a file outside ${GradleRunner.line(holder)}"
                }
            } catch (IOException ignored) {
                refused = 'it is a link that cannot be followed'
            }
        }
        refused = refused ?: refuseWrapper(holder, wrapper, target, aboveProject)
        if (refused != null) {
            return new Wrapper(null, "The Gradle wrapper ${GradleRunner.line(wrapper)} is not run for the project in ${GradleRunner.line(directory)}: ${refused}", null)
        }
        // The script finds the jar it runs from where its file really is: a link does not take the jar with
        // it. The batch file of Windows finds its jar from where it was started, which is beside the link.
        Path home = target == null || windows ? wrapper.getParent() : target.getParent()
        return new Wrapper(wrapper, null, home.resolve(WRAPPER_JAR))
    }

    // why the wrapper found in a directory is not the user's own where it has to be, or null when it is run;
    // target is the file a wrapper that is a link leads to, within the directory
    private String refuseWrapper(Path holder, Path wrapper, Path target, boolean aboveProject) {
        if (above || aboveProject) {
            // the script, the settings that make the directory the root of a build, and what the script runs
            List<String> looked = [wrapper.getFileName().toString()]
            if (aboveProject) {
                looked.addAll(SETTINGS)
            }
            looked.addAll(WRAPPER_FILES)
            String distrusted = distrust(holder, looked)
            if (distrusted == null && !isSafeDirectory(holder)) {
                distrusted = distrustLinkedDirectories(holder, holder)
                if (distrusted == null && target != null) {
                    distrusted = distrustLinked(holder, wrapper, target, 'it', 'file')
                }
                if (distrusted == null && target != null) {
                    // The script finds the jar it runs from where its file really is, and the jar reads the
                    // properties beside itself: a link does not take them with it. Those beside the link are
                    // what the batch file of Windows runs, which finds its jar from where it was started.
                    distrusted = distrust(target.getParent(), WRAPPER_FILES) ?: distrustLinkedDirectories(holder, target.getParent())
                }
            }
            if (distrusted != null) {
                return "${distrusted}; name ${GradleRunner.line(holder)} in the environment variable ${SAFE_DIRECTORY} to run it all the same"
            }
        }
        return null
    }

    // why a directory above, with what is looked at in it, is not the user's own; null when it is
    private static String distrust(Path directory, List<String> names) {
        if (isSafeDirectory(directory)) {
            return null
        }
        String distrusted = distrustEntry(directory)
        if (distrusted != null) {
            return distrusted
        }
        for (String name : names) {
            Path file = directory.resolve(name)
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                distrusted = distrustEntry(file)
                if (distrusted != null) {
                    return distrusted
                }
            }
        }
        return null
    }

    // Why a directory of the wrapper in a directory above, which the script reaches through whatever
    // link it is, is not the user's own, where it is a link: what it leads to is held to what an
    // ordinary directory there is held to, and so is the way to it, as for a wrapper that is a link.
    private static String distrustLinkedDirectories(Path holder, Path directory) {
        for (String name : WRAPPER_DIRECTORIES) {
            Path link = directory.resolve(name)
            if (!Files.isSymbolicLink(link)) {
                continue
            }
            Path target
            try {
                target = link.toRealPath()
                if (!target.startsWith(holder.toRealPath())) {
                    return "${GradleRunner.line(link)} is a link to a directory outside ${GradleRunner.line(holder)}"
                }
            } catch (IOException | RuntimeException ignored) {
                return "${GradleRunner.line(link)} is a link that cannot be followed"
            }
            String distrusted = distrustLinked(holder, link, target, GradleRunner.line(link), 'directory')
            if (distrusted != null) {
                return distrusted
            }
        }
        return null
    }

    // Why what a link in a directory above leads to is not the user's own. It is held to what a file
    // or a directory there is held to, and so is the way to it: whoever can write to a directory on the
    // way, or put a second link there, decides what the link leads to, and can make that any file of the
    // user. So the link names its file by directories alone, and each of them is the user's own. A link
    // that names a path of its own is read from the directory it really is in, as the system reads it.
    private static String distrustLinked(Path holder, Path link, Path target, String subject, String kind) {
        Path root
        Path named
        try {
            root = holder.toRealPath()
            named = Files.readSymbolicLink(link)
            named = named.isAbsolute() ? within(root, named) : root.relativize(link.getParent().toRealPath()).resolve(named)
        } catch (IOException | RuntimeException ignored) {
            return "${subject} is a link that cannot be read, so it is not known to lead to a ${kind} of the user's own"
        }
        // the path a link is followed to has no link in it: the one the link names is that path only when it has none either
        if (named == null || root.resolve(named).normalize() != target) {
            return "${subject} is a link that leads to its ${kind} through another link, and where a second link leads need not be the user's choice (link it to the ${kind} itself)"
        }
        List<Path> way = []
        for (Path each = target.getParent(); each != null && each != root && each.startsWith(root); each = each.getParent()) {
            way.add(0, each)
        }
        for (Path each : way) {
            String distrusted = distrustEntry(each)
            if (distrusted != null) {
                return distrusted
            }
        }
        return distrustEntry(target)
    }

    // what a whole path names in a directory, as a path in that directory; null when it does not lead through it
    private static Path within(Path directory, Path named) throws IOException {
        for (int count = 1; count < named.getNameCount(); count++) {
            Path start = named.getRoot().resolve(named.subpath(0, count))
            if (Files.exists(start) && Files.isSameFile(start, directory)) {
                return named.subpath(count, named.getNameCount())
            }
        }
        return null
    }

    // Why a directory or a file above is not the user's own. A directory that everybody can write to is
    // one they can put anything into, whoever owns it. A file with another name can be a file of
    // someone else that was given a name here: it then has the owner of that file, which for root is
    // root, so the owner alone says nothing of who put it there.
    private static String distrustEntry(Path path) {
        if (!ownedByUser.test(path)) {
            return notOwned(path)
        }
        try {
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                if (path.getFileSystem().supportedFileAttributeViews().contains('posix')) {
                    Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS)
                    // a group that can write is taken to be the user's own: with a group for each user, which
                    // is common, every directory the user creates is writable by it
                    if (permissions.contains(PosixFilePermission.OTHERS_WRITE)) {
                        return "${GradleRunner.line(path)} can be written to by everybody, so what is in it need not be the user's (chmod o-w keeps the others out)"
                    }
                }
            } else if (path.getFileSystem().supportedFileAttributeViews().contains('unix')) {
                Object names = Files.getAttribute(path, 'unix:nlink', LinkOption.NOFOLLOW_LINKS)
                if (names instanceof Number && ((Number) names).intValue() > 1) {
                    return "${GradleRunner.line(path)} has another name elsewhere (a hard link), so it need not be a file the user put there"
                }
            }
        } catch (IOException | RuntimeException ignored) {
            return "the mode of ${GradleRunner.line(path)} cannot be read, so it is not known to be the user's alone"
        }
        return null
    }

    private static String notOwned(Path path) {
        if (ownedByUser.is(OWNED_BY_USER) && !ProcessUser.current().isKnown()) {
            return "the user who runs the command could not be established (asked for: ${ProcessUser.SOURCES.join(', ')}), so ${GradleRunner.line(path)} is not known to be the user's own"
        }
        String owner
        try {
            owner = Files.getOwner(path, LinkOption.NOFOLLOW_LINKS).getName()
        } catch (IOException | UnsupportedOperationException | SecurityException ignored) {
            return "the owner of ${GradleRunner.line(path)} cannot be read, so it is not known to be the user who runs the command"
        }
        String compared = ownedByUser.is(OWNED_BY_USER) ? GradleRunner.line(ProcessUser.current().comparedWith(path)) : ''
        return "${GradleRunner.line(path)} is owned by ${GradleRunner.line(owner)}, not by the user who runs the command${compared}"
    }

    private static boolean isProject(Path directory) {
        if (Files.isDirectory(directory.resolve(SOURCES))) {
            return true
        }
        return RECORDS.any { String record -> Files.isRegularFile(directory.resolve(record), LinkOption.NOFOLLOW_LINKS) }
    }

    private static boolean hasSettings(Path directory) {
        return SETTINGS.any { String settings -> Files.isRegularFile(directory.resolve(settings)) }
    }

    /**
     * The Grails projects below the root of a Gradle build, for the line that says where a command of
     * a project can be run. Only the names of directories are read: no link is followed, what a build
     * writes and what is hidden is not looked into, and nothing is looked for inside a project.
     *
     * @param root the root of the build
     * @return the directories of the projects as paths in the root, sorted, and {@code null} in the
     * last place when there were more directories than are looked at
     */
    private static List<String> projectsBelow(Path root) {
        List<String> found = []
        int[] looked = [0] as int[]
        boolean complete = projectsBelow(root, root, 0, found, looked)
        found.sort()
        if (!complete) {
            found.add(null)
        }
        return found
    }

    private static boolean projectsBelow(Path root, Path directory, int depth, List<String> found, int[] looked) {
        List<Path> entries = []
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path entry : stream) {
                if (++looked[0] > MAX_ENTRIES) {
                    return false
                }
                String name = entry.getFileName().toString()
                if (!name.startsWith('.') && !(name in NOT_SOURCES) && Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                    entries.add(entry)
                }
            }
        } catch (IOException | RuntimeException ignored) {
            return true
        }
        boolean complete = true
        for (Path entry : entries) {
            if (isProject(entry)) {
                found.add(root.relativize(entry).collect { Path step -> step.toString() }.join('/'))
            } else if (depth + 1 < MAX_DEPTH) {
                complete &= projectsBelow(root, entry, depth + 1, found, looked)
            }
        }
        return complete
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
         * @param command the command that was not run because no project is used, as it is typed
         * @return the one line that says so, the same for every command: that there is no project, or
         * that the one above is not used and why
         */
        String none(String command) {
            String needs = "${command} is a command of a Grails project"
            if (untrusted != null) {
                return "${untrusted}. ${needs}, and was not run"
            }
            String inBuild = inBuild(needs)
            if (inBuild != null) {
                return inBuild
            }
            String above = 'above it'
            if (repository != null) {
                above = 'above it in the same repository'
            } else if (limit) {
                above = "in the ${MAX_LEVELS} directories above it, which is as far as is looked"
            }
            return "${needs}; there is none in ${GradleRunner.line(from)} or ${above}"
        }

        // In a Gradle build that has Grails projects and at a directory that is in none of them, the
        // command is not run for one of them by a guess: which ones there are is said instead. A root
        // above the current directory is read only when it is the user's own.
        private String inBuild(String needs) {
            ConsoleProject build = build(from)
            if (build == null) {
                return null
            }
            Path root = build.getBuildRoot() ?: from
            if (root != from && distrust(root, SETTINGS) != null) {
                return null
            }
            List<String> projects = projectsBelow(root)
            boolean more = !projects.isEmpty() && projects.last() == null
            projects.removeAll { String project -> project == null }
            if (projects.isEmpty()) {
                return null
            }
            String where = root == from ? "${GradleRunner.line(from)} is the root of a Gradle build and is no Grails project itself" :
                    "${GradleRunner.line(from)} is in the Gradle build of ${GradleRunner.line(root)} and in no Grails project"
            String named = projects.take(MAX_NAMED).collect { String project -> GradleRunner.line(project) }.join(', ')
            String rest = projects.size() > MAX_NAMED ? " and ${projects.size() - MAX_NAMED} more" : (more ? ', and there may be more: not every directory was looked at' : '')
            return "${needs}; ${where}. ${projects.size() == 1 ? 'The Grails project' : 'The Grails projects'} of the build: ${named}${rest}. " +
                    "Run the command in the directory of ${projects.size() == 1 ? 'that project' : 'the one that is meant'}" +
                    (root == from ? ", as in: cd ${GradleRunner.line(projects.first())}" : '')
        }
    }

    /** The Gradle wrapper of a project, or why none is run. */
    static final class Wrapper {

        /** The wrapper script, or {@code null}. */
        final Path file

        /** Why no wrapper is run, as one line without the full stop; {@code null} when there is one. */
        final String refusal

        /**
         * The jar the wrapper script runs, or {@code null} when there is no wrapper: {@code gradle/wrapper/gradle-wrapper.jar}
         * beside the script, which for a script that is a link is beside the file the link leads to, since the
         * script looks for it from where its file really is. The batch file of Windows looks beside the link.
         */
        final Path jar

        private Wrapper(Path file, String refusal, Path jar) {
            this.file = file
            this.refusal = refusal
            this.jar = jar
        }
    }
}
