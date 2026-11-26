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
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission
import java.util.function.Supplier
import groovy.transform.CompileStatic

/**
 * The file in which the build of a project writes down the class path of the commands that are run
 * as a process of their own, without Gradle: {@code .grails/cache/standalone-commands.json}, written
 * by the Gradle task {@code grailsStandaloneCommands} beside the list of the commands. The CLI reads
 * it for a command the list says is such a command, and for no other.
 *
 * <p>Starting a JVM on what the file names runs it, as starting the Gradle wrapper of the project
 * runs the build, so the file and everything it names is held to the rule the wrapper of a project
 * above is held to, in every project: the list of the commands, this file and the directories they
 * are in are the user's own and no links; every entry of the class path is a file or a directory
 * within the build of the project, the Gradle user home or the local Maven repository, and it and
 * every directory from it up to that place are owned by the user or by root and cannot be written
 * to by everybody. A directory named in {@code GRAILS_SAFE_DIRECTORY} is used whoever owns it, with
 * what is in it, as it is for a project. What is not so is not run, and one line says why.</p>
 *
 * <p>What is run is never older than what it is made from: a file that is older than the list of
 * the commands, a list that may be out of date, an entry that is no longer there, and, for a command
 * of the project itself, a source of the project that is newer than the file, all say that the build
 * has to run once before the command does.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class StandaloneCommandFile {

    /** Where the file is, in the directory of the project. */
    public static final String LOCATION = '.grails/cache/standalone-commands.json'

    /** The Gradle task that writes the file. */
    public static final String TASK = 'grailsStandaloneCommands'

    // These are the numbers of what writes the file, StandaloneCommandsDocument of grails-gradle-model: the
    // two are in two builds, and a specification holds them to be the same.

    /** How many bytes the file has, at most: as many as the task writes. */
    public static final int MAX_BYTES = 2 * 1024 * 1024

    /** How many entries a class path of the file has, at most. */
    public static final int MAX_ENTRIES = 4096

    /** How many characters a path of the file has, at most. */
    public static final int MAX_PATH = 1024

    /** How many directories of sources the file names, at most. */
    public static final int MAX_SOURCES = 256

    /** How many entries of the directories of sources are looked at for what was changed, at most. */
    public static final int MAX_WALKED = 100_000

    /** How deep a directory of sources is looked at, at most. */
    public static final int MAX_WALK_DEPTH = 64

    private static final String APPLICATION = 'application'
    private static final String DEPENDENCIES = 'dependencies'
    private static final int MAX_DEPTH = 4
    private static final int MAX_VALUES = 3 * MAX_ENTRIES
    private static final int MAX_REASON = 400
    private static final String GRADLE_USER_HOME = 'GRADLE_USER_HOME'
    private static final Supplier<List<Path>> PLACES = { places(System.getenv(GRADLE_USER_HOME), System.getProperty('user.home')) } as Supplier<List<Path>>

    private static volatile Supplier<List<Path>> otherPlaces = PLACES

    /** What there is of the file for a command. */
    static enum State {

        /** The command can be started with {@link #classpath}. */
        READY,

        /** The build has to run once before the command is started; {@link #reason} says why. */
        OUT_OF_DATE,

        /** Nothing of the file is run; {@link #reason} says why. */
        REFUSED,

        /** The build says that it runs the command itself; {@link #reason} says why. */
        THROUGH_GRADLE
    }

    /** What there is of the file for the command. */
    final State state

    /** Why the command is not started as it is, as the end of a sentence; {@code null} when it is. */
    final String reason

    /** The class path the command is started with, in its order; empty unless the command can be started. */
    final List<Path> classpath

    /** The version of Java the classes of the project are compiled for; 0 when the file does not say. */
    final int java

    private StandaloneCommandFile(State state, String reason, List<Path> classpath, int java) {
        this.state = state
        this.reason = reason
        this.classpath = classpath
        this.java = java
    }

    /**
     * Replaces the places outside the build of a project a class path entry can be in, for a specification.
     *
     * @param places the directories; {@code null} for the Gradle user home and the local Maven repository
     */
    static void setPlaces(List<Path> places) {
        otherPlaces = places == null ? PLACES : ({ places } as Supplier<List<Path>>)
    }

    /**
     * @param gradleUserHome what the environment variable {@code GRADLE_USER_HOME} says, or {@code null}
     * @param userHome the home directory of the user
     * @return the places outside the build of a project a class path entry can be in: the Gradle
     * user home and the local Maven repository of the user
     */
    static List<Path> places(String gradleUserHome, String userHome) {
        List<Path> places = []
        try {
            if (gradleUserHome != null && !gradleUserHome.trim().isEmpty() && Path.of(gradleUserHome).isAbsolute()) {
                places.add(Path.of(gradleUserHome))
            } else if (userHome != null && !userHome.isEmpty()) {
                places.add(Path.of(userHome, '.gradle'))
            }
            if (userHome != null && !userHome.isEmpty()) {
                places.add(Path.of(userHome, '.m2', 'repository'))
            }
        } catch (RuntimeException ignored) {
            // a path that is none names no place
        }
        return places
    }

    /**
     * Reads what the build wrote down for a command. Nothing is thrown.
     *
     * @param project the project
     * @param list the list of the commands of the project, as it was read
     * @param name the name of a command the list says runs as a process of its own
     * @return what there is of the file for the command
     */
    static StandaloneCommandFile read(ConsoleProject project, ApplicationCommandFile list, String name) {
        try {
            return reading(project, list, name)
        } catch (IOException | RuntimeException ignored) {
            return refused("${LOCATION} cannot be read")
        }
    }

    private static StandaloneCommandFile reading(ConsoleProject project, ApplicationCommandFile list, String name) throws IOException {
        Path directory = project.getDirectory()
        if (list.state == ApplicationCommandFile.State.STALE) {
            return outOfDate("${list.reason} is newer than ${ApplicationCommandFile.LOCATION}")
        }
        if (list.state != ApplicationCommandFile.State.CURRENT) {
            return outOfDate("${ApplicationCommandFile.LOCATION} is not there or is not used")
        }
        // the list says which commands these are, and is the user's own as this file is
        String distrusted = distrustStateFile(project, ApplicationCommandFile.LOCATION)
        if (distrusted != null) {
            return refused(distrusted)
        }
        Path file = directory.resolve(LOCATION)
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return outOfDate("there is no ${LOCATION}")
        }
        distrusted = distrustStateFile(project, LOCATION)
        if (distrusted != null) {
            return refused(distrusted)
        }
        BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes, LinkOption.NOFOLLOW_LINKS)
        if (attributes.size() > MAX_BYTES) {
            return refused("${LOCATION} has more than the ${MAX_BYTES} bytes it can have")
        }
        FileTime written = attributes.lastModifiedTime()
        if (written < Files.getLastModifiedTime(directory.resolve(ApplicationCommandFile.LOCATION), LinkOption.NOFOLLOW_LINKS)) {
            return outOfDate("${ApplicationCommandFile.LOCATION} is newer than ${LOCATION}")
        }
        byte[] bytes = Files.readAllBytes(file)
        if (bytes.length > MAX_BYTES) {
            return refused("${LOCATION} has more than the ${MAX_BYTES} bytes it can have")
        }
        Map<String, Object> document = parse(bytes)
        if (document == null) {
            return outOfDate("${LOCATION} is not the document of format 1 the build writes")
        }
        int java = document.get('java') instanceof Long ? (int) Math.max(0L, Math.min(1000L, (Long) document.get('java'))) : 0
        Object kind = ((Map) document.get('commands')).get(name)
        if (kind != APPLICATION && kind != DEPENDENCIES) {
            return outOfDate("${LOCATION} does not have the command")
        }
        Object throughGradle = document.get('throughGradle')
        if (throughGradle instanceof String) {
            return new StandaloneCommandFile(State.THROUGH_GRADLE, GradleRunner.line(shortened((String) throughGradle)), Collections.<Path>emptyList(), java)
        }
        List<String> entries = []
        if (kind == APPLICATION) {
            String changed = changedSince((List<String>) document.get('sources'), written)
            if (changed != null) {
                return outOfDate("${shown(directory, changed)} was changed after ${LOCATION} was written")
            }
            entries.addAll((List<String>) document.get('application'))
        }
        entries.addAll((List<String>) document.get('dependencies'))
        if (entries.isEmpty()) {
            return outOfDate("${LOCATION} has no class path for the command")
        }
        return checked(project, entries, java)
    }

    // the entries as the class path they are, each held to the rule of the class; the first that is not says how the command ends
    private static StandaloneCommandFile checked(ConsoleProject project, List<String> entries, int java) throws IOException {
        Path build = (project.getBuildRoot() ?: project.getDirectory()).toRealPath()
        List<Path> places = [build]
        for (Path place : otherPlaces.get()) {
            try {
                places.add(place.toRealPath())
            } catch (IOException ignored) {
                // a place that is not there has nothing in it
            }
        }
        // what was looked at already: a directory is above many entries
        Map<Path, String> looked = new HashMap<>()
        List<Path> classpath = []
        for (String entry : entries) {
            Path path = Path.of(entry)
            if (!path.isAbsolute() || path.normalize() != path) {
                return refused("an entry of the class path of ${LOCATION} is not a whole path that names its file itself")
            }
            Path real
            try {
                real = path.toRealPath()
            } catch (IOException ignored) {
                return outOfDate("${shown(project.getDirectory(), entry)} of the class path is no longer there")
            }
            BasicFileAttributes attributes = Files.readAttributes(real, BasicFileAttributes, LinkOption.NOFOLLOW_LINKS)
            if (!attributes.isRegularFile() && !attributes.isDirectory()) {
                return refused("${GradleRunner.line(entry)} of the class path is neither a file nor a directory")
            }
            if (!isSafe(real)) {
                Path place = places.find { Path each -> real.startsWith(each) }
                if (place == null) {
                    return refused("${GradleRunner.line(entry)} of the class path is not within the build of the project, the Gradle user home or the local Maven repository " +
                            "(${places.collect { Path each -> GradleRunner.line(each) }.join(', ')}), as a file of a repository that is a directory " +
                            '(maven { url = file(...) }) or of a local Maven repository in another place is. To use what is in it, name ' +
                            "${GradleRunner.line(elsewhere(entries, places, real))} in the environment variable ${ConsoleProject.SAFE_DIRECTORY}")
                }
                for (Path each = real; each != null && each.startsWith(place); each = each.getParent()) {
                    String distrusted = looked.computeIfAbsent(each) { Path looking -> distrust(looking) ?: '' }
                    if (!distrusted.isEmpty()) {
                        return refused("${distrusted} (it is, or is above, ${GradleRunner.line(entry)} of the class path)")
                    }
                }
            }
            classpath.add(real)
        }
        return new StandaloneCommandFile(State.READY, null, classpath.asImmutable(), java)
    }

    // The directory to name as safe for an entry that is in none of the places: the one that it and the
    // other such entries of the class path have in common, which for the jars of one repository is a
    // directory of that repository, and the directory of the entry where they have none worth naming
    // (the root of a file system, a directory right below it, or the home directory of the user).
    private static Path elsewhere(List<String> entries, List<Path> places, Path first) {
        Path common = first.getParent() ?: first
        for (String entry : entries) {
            Path real
            try {
                Path path = Path.of(entry)
                real = path.isAbsolute() ? path.toRealPath() : null
            } catch (IOException | RuntimeException ignored) {
                real = null
            }
            if (real == null || places.any { Path place -> real.startsWith(place) } || isSafe(real)) {
                continue
            }
            Path shared = common
            while (shared != null && !real.startsWith(shared)) {
                shared = shared.getParent()
            }
            // nor the home directory of the user or a directory above it: that would be all the user has
            if (shared == null || shared.getNameCount() < 2 || Path.of(System.getProperty('user.home', '/')).toAbsolutePath().startsWith(shared)) {
                return first.getParent() ?: first
            }
            common = shared
        }
        return common
    }

    // whether a directory the user named as safe is the path or above it
    private static boolean isSafe(Path real) {
        for (Path each = real; each != null; each = each.getParent()) {
            if (ConsoleProject.isSafeDirectory(each)) {
                return true
            }
        }
        return false
    }

    /**
     * Why a file or a directory is not one the user, or root, put there and nobody else can change.
     *
     * @param path a file or a directory, which is no link
     * @return why it is not run from, or {@code null} when it is the user's own or of root and
     * cannot be written to by everybody
     */
    static String distrust(Path path) {
        try {
            if (!ConsoleProject.isOwnedByUser(path) && !isOfRoot(path)) {
                return "${GradleRunner.line(path)} is owned neither by the user who runs the command nor by root"
            }
            if (path.getFileSystem().supportedFileAttributeViews().contains('posix') &&
                    Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS).contains(PosixFilePermission.OTHERS_WRITE)) {
                return "${GradleRunner.line(path)} can be written to by everybody (chmod o-w keeps the others out)"
            }
        } catch (IOException | RuntimeException ignored) {
            return "the owner and the mode of ${GradleRunner.line(path)} cannot be read"
        }
        return null
    }

    private static boolean isOfRoot(Path path) throws IOException {
        if (!path.getFileSystem().supportedFileAttributeViews().contains('unix')) {
            return false
        }
        Object owner = Files.getAttribute(path, 'unix:uid', LinkOption.NOFOLLOW_LINKS)
        return owner instanceof Number && ((Number) owner).intValue() == 0
    }

    // Why a file the build keeps in the project is not read as the user's own: it and the directories on
    // the way to it are no links, are owned by the user and cannot be written to by everybody. A project
    // named in GRAILS_SAFE_DIRECTORY is used whoever owns it, as its Gradle wrapper is.
    private static String distrustStateFile(ConsoleProject project, String location) throws IOException {
        Path each = project.getDirectory()
        boolean safe = ConsoleProject.isSafeDirectory(each)
        Path last = each.resolve(location)
        for (Path step : Path.of(location)) {
            each = each.resolve(step)
            BasicFileAttributes attributes = Files.readAttributes(each, BasicFileAttributes, LinkOption.NOFOLLOW_LINKS)
            if (attributes.isSymbolicLink() || (each == last ? !attributes.isRegularFile() : !attributes.isDirectory())) {
                return "${location} is, or is behind, a link or is not a file"
            }
            if (safe) {
                continue
            }
            if (!ConsoleProject.isOwnedByUser(each)) {
                return "${GradleRunner.line(each)} is not owned by the user who runs the command; " +
                        "name ${GradleRunner.line(project.getDirectory())} in the environment variable ${ConsoleProject.SAFE_DIRECTORY} to use it all the same"
            }
            if (each.getFileSystem().supportedFileAttributeViews().contains('posix') &&
                    Files.getPosixFilePermissions(each, LinkOption.NOFOLLOW_LINKS).contains(PosixFilePermission.OTHERS_WRITE)) {
                return "${GradleRunner.line(each)} can be written to by everybody, so what is in it need not be the user's (chmod o-w keeps the others out)"
            }
        }
        return null
    }

    // the document as the task writes it, or null
    private static Map<String, Object> parse(byte[] bytes) {
        for (byte each : bytes) {
            if (each < 0) {
                return null
            }
        }
        Object parsed
        try {
            parsed = BoundedJson.parse(new String(bytes, StandardCharsets.US_ASCII), MAX_DEPTH, MAX_VALUES, MAX_PATH)
        } catch (BoundedJson.Refused ignored) {
            return null
        }
        if (!(parsed instanceof Map)) {
            return null
        }
        Map<String, Object> document = (Map<String, Object>) parsed
        if (!(document.get('format') instanceof Long) || ((Long) document.get('format')).longValue() != 1L || !(document.get('commands') instanceof Map)) {
            return null
        }
        if (!isPaths(document.get(APPLICATION), MAX_ENTRIES) || !isPaths(document.get(DEPENDENCIES), MAX_ENTRIES) || !isPaths(document.get('sources'), MAX_SOURCES)) {
            return null
        }
        if (((List) document.get(APPLICATION)).size() + ((List) document.get(DEPENDENCIES)).size() > MAX_ENTRIES) {
            return null
        }
        return document
    }

    private static boolean isPaths(Object listed, int most) {
        if (!(listed instanceof List) || ((List) listed).size() > most) {
            return false
        }
        for (Object each : (List) listed) {
            if (!(each instanceof String) || !isPath((String) each)) {
                return false
            }
        }
        return true
    }

    // the rule of the task: a path of a bounded length, without a control character and without what separates the entries of a class path
    private static boolean isPath(String path) {
        if (path.isEmpty() || path.length() > MAX_PATH || path.indexOf(File.pathSeparator) >= 0) {
            return false
        }
        for (int i = 0; i < path.length(); i++) {
            int type = Character.getType(path.charAt(i))
            if (type == Character.CONTROL || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR) {
                return false
            }
        }
        return true
    }

    // The first of the directories of sources in which a directory or a file was changed after an
    // instant: a new or a deleted file changes its directory, a file that was written again only
    // itself. A link to a file counts with the time of that file; a link to a directory is not
    // followed. With more entries than are looked at it cannot be said that nothing was changed.
    private static String changedSince(List<String> sources, FileTime since) {
        int[] seen = [0] as int[]
        for (String source : sources) {
            Path top = Path.of(source)
            if (!top.isAbsolute() || !Files.isDirectory(top, LinkOption.NOFOLLOW_LINKS)) {
                continue
            }
            boolean[] changed = [false] as boolean[]
            try {
                Files.walkFileTree(top, Collections.<FileVisitOption>emptySet(), MAX_WALK_DEPTH, new SimpleFileVisitor<Path>() {

                    @Override
                    FileVisitResult preVisitDirectory(Path each, BasicFileAttributes attributes) {
                        FileTime modified
                        try {
                            modified = Files.getLastModifiedTime(each, LinkOption.NOFOLLOW_LINKS)
                        } catch (IOException ignored) {
                            modified = attributes.lastModifiedTime()
                        }
                        return look(modified)
                    }

                    @Override
                    FileVisitResult visitFile(Path each, BasicFileAttributes attributes) {
                        FileTime modified = attributes.lastModifiedTime()
                        if (attributes.isSymbolicLink()) {
                            try {
                                BasicFileAttributes behind = Files.readAttributes(each, BasicFileAttributes)
                                if (behind.isRegularFile()) {
                                    modified = behind.lastModifiedTime()
                                }
                            } catch (IOException ignored) {
                                // a link that leads nowhere counts as the link it is
                            }
                        }
                        return look(modified)
                    }

                    @Override
                    FileVisitResult visitFileFailed(Path each, IOException e) {
                        return FileVisitResult.CONTINUE
                    }

                    private FileVisitResult look(FileTime modified) {
                        if (++seen[0] > MAX_WALKED || modified.compareTo(since) > 0) {
                            changed[0] = true
                            return FileVisitResult.TERMINATE
                        }
                        return FileVisitResult.CONTINUE
                    }
                })
            } catch (IOException | RuntimeException ignored) {
                return source
            }
            if (changed[0]) {
                return source
            }
        }
        return null
    }

    // a path of the project as a path in the project, and any other as it is, in one line
    private static String shown(Path directory, String path) {
        try {
            Path named = Path.of(path)
            if (named.startsWith(directory)) {
                return GradleRunner.line(directory.relativize(named).join('/'))
            }
        } catch (RuntimeException ignored) {
            // shown as it is
        }
        return GradleRunner.line(path)
    }

    private static String shortened(String reason) {
        return reason.length() > MAX_REASON ? reason.substring(0, MAX_REASON) : reason
    }

    private static StandaloneCommandFile outOfDate(String reason) {
        return new StandaloneCommandFile(State.OUT_OF_DATE, reason, Collections.<Path>emptyList(), 0)
    }

    private static StandaloneCommandFile refused(String reason) {
        return new StandaloneCommandFile(State.REFUSED, reason, Collections.<Path>emptyList(), 0)
    }
}
