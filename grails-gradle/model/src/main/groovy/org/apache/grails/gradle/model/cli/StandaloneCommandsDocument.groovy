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
package org.apache.grails.gradle.model.cli

import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime

import groovy.transform.CompileStatic

/**
 * What the build of a project writes down for the commands of the project that are run as a process
 * of their own, without Gradle ({@code StandaloneCommand}): the class path the {@code grails} command
 * line starts each of them with, so that it does not have to ask the build for it.
 *
 * <p>The document is a file beside the catalog of the commands, {@link #LOCATION}, and not a part of
 * the catalog: the catalog has no path of this machine in it and is the same bytes for the same
 * commands on every machine, so a build cache can hold it; this file is nothing but paths of this
 * machine, and is never taken from a build cache.</p>
 *
 * <p>Format 1 is one JSON object in ASCII with these members:</p>
 * <ul>
 *   <li>{@code format}, the number 1;</li>
 *   <li>{@code commands}, an object with a member for each command of the catalog whose target is
 *       {@code standalone}: its name, and which class path it runs with, {@link #APPLICATION} for a
 *       command of the project itself and {@link #DEPENDENCIES} for a command of a plugin;</li>
 *   <li>{@code application}, the directories the classes and resources of the project are in, as
 *       absolute paths; empty when no command runs with them;</li>
 *   <li>{@code dependencies}, the files of what the project depends on at run time and of its command
 *       line tier, in the order of the class path, as absolute paths. A command of the project runs
 *       with {@code application} and then {@code dependencies}, a command of a plugin with
 *       {@code dependencies} alone;</li>
 *   <li>{@code sources}, the directories of the sources {@code application} is made from, as absolute
 *       paths; empty when no command runs with {@code application};</li>
 *   <li>{@code java}, the version of Java the classes of the project are compiled for, a number;</li>
 *   <li>{@code throughGradle}, only where the commands cannot be given a class path that the command
 *       line can keep current by itself: one line that says why. The lists are empty then, and the
 *       command line runs the commands with Gradle.</li>
 * </ul>
 *
 * <p>A project without such a command has a document without a path in it.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class StandaloneCommandsDocument {

    /** The format this class writes. */
    public static final int FORMAT = 1

    /** Where the document of a project is, in the directory of the project. */
    public static final String LOCATION = '.grails/cache/standalone-commands.json'

    /** The class path of a command of the project itself: the output of the project, then what it depends on. */
    public static final String APPLICATION = 'application'

    /** The class path of a command of a plugin: what the project depends on, without its own output. */
    public static final String DEPENDENCIES = 'dependencies'

    /** The most bytes a document has. */
    public static final int MAX_BYTES = 2 * 1024 * 1024

    /** The most entries a class path of a document has. */
    public static final int MAX_ENTRIES = 4096

    /** The most characters a path of a document has. */
    public static final int MAX_PATH_LENGTH = 1024

    /** The most directories of sources a document names. */
    public static final int MAX_SOURCES = 256

    /** The most characters the line has that says why the commands are run with Gradle. */
    public static final int MAX_REASON_LENGTH = 400

    /** How many entries of the directories of sources are looked at for what was changed, at most. */
    public static final int MAX_WALKED = 100_000

    /** How deep a directory of sources is looked at, at most. */
    public static final int MAX_WALK_DEPTH = 64

    private static final int CATALOG_DEPTH = 8

    private StandaloneCommandsDocument() {
    }

    /**
     * @param catalog the catalog of the commands of a project, as the build wrote it
     * @return the commands of it that are run as a process of their own, by name, in the order of the
     * catalog: {@link #APPLICATION} for a command of the project and {@link #DEPENDENCIES} for any
     * other; none for a catalog that cannot be read
     */
    static Map<String, String> commandsOf(byte[] catalog) {
        Map<String, String> commands = new LinkedHashMap<String, String>()
        Object parsed
        try {
            parsed = StrictJson.parse(catalog, CATALOG_DEPTH)
        } catch (StrictJson.Refused ignored) {
            return commands
        }
        Object listed = parsed instanceof Map ? ((Map) parsed).get('commands') : null
        if (!(listed instanceof List)) {
            return commands
        }
        for (Object each : (List) listed) {
            if (!(each instanceof Map)) {
                continue
            }
            Map command = (Map) each
            Object name = command.get('name')
            if (name instanceof String && CommandDescriptor.isCommandName((String) name) &&
                    command.get('target') == CommandDescriptor.TARGET_STANDALONE) {
                commands.put((String) name, command.get('origin') == CommandCatalogBuilder.ORIGIN_APPLICATION ? APPLICATION : DEPENDENCIES)
            }
        }
        commands
    }

    /**
     * Writes the document. A project without a command that runs as a process of its own has a
     * document without a path; so has one whose commands are run with Gradle, for the reason given
     * or because a class path cannot be written down within the bounds of the format.
     *
     * @param commands the commands, as {@link #commandsOf} gives them
     * @param application the directories of the output of the project
     * @param dependencies the files of what the project depends on, in the order of the class path
     * @param sources the directories of the sources of the project
     * @param java the version of Java the classes of the project are compiled for
     * @param throughGradle why the commands are run with Gradle, or {@code null} when they need not be
     * @return the JSON document, in ASCII, with a line end
     */
    static String write(Map<String, String> commands, List<String> application, List<String> dependencies, List<String> sources,
                        int java, String throughGradle) {
        boolean ofApplication = commands.containsValue(APPLICATION)
        List<String> none = Collections.<String> emptyList()
        String reason = null
        if (!commands.isEmpty()) {
            reason = throughGradle ?: unwritable(ofApplication ? application : none, dependencies, ofApplication ? sources : none)
        }
        String written = document(commands, application, dependencies, sources, java, reason)
        if (written.length() > MAX_BYTES) {
            written = document(commands, application, dependencies, sources, java, "the class path of the commands does not fit into ${MAX_BYTES} bytes".toString())
        }
        written
    }

    private static String document(Map<String, String> commands, List<String> application, List<String> dependencies, List<String> sources, int java, String reason) {
        boolean paths = !commands.isEmpty() && reason == null
        boolean ofApplication = paths && commands.containsValue(APPLICATION)
        Map<String, Object> document = new LinkedHashMap<String, Object>()
        document.format = FORMAT
        document.commands = new LinkedHashMap<String, String>(commands)
        document.application = ofApplication ? new ArrayList<String>(application) : []
        document.dependencies = paths ? new ArrayList<String>(dependencies) : []
        document.sources = ofApplication ? new ArrayList<String>(sources) : []
        document.java = java
        if (reason != null) {
            document.throughGradle = oneLine(reason)
        }
        StrictJson.write(document) + '\n'
    }

    // why the paths cannot be written down as the format has them; null when they can
    private static String unwritable(List<String> application, List<String> dependencies, List<String> sources) {
        if (application.size() + dependencies.size() > MAX_ENTRIES) {
            return "the class path of the commands has more than ${MAX_ENTRIES} entries"
        }
        if (sources.size() > MAX_SOURCES) {
            return "the project has more than ${MAX_SOURCES} directories of sources"
        }
        List<String> all = new ArrayList<String>(application)
        all.addAll(dependencies)
        all.addAll(sources)
        for (String path : all) {
            if (!isPath(path)) {
                return "a path of the class path of the commands has more than ${MAX_PATH_LENGTH} characters, " +
                        'a control character or the character that separates the entries of a class path'
            }
        }
        null
    }

    /**
     * @param path a path of a document
     * @return whether it is one the format has: at most {@link #MAX_PATH_LENGTH} characters, none of
     * them a control character, a line separator or the character that separates the entries of a class path
     */
    static boolean isPath(String path) {
        if (path == null || path.isEmpty() || path.length() > MAX_PATH_LENGTH || path.indexOf(File.pathSeparator) >= 0) {
            return false
        }
        for (int index = 0; index < path.length(); index++) {
            int type = Character.getType(path.charAt(index))
            if (type == Character.CONTROL || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR) {
                return false
            }
        }
        true
    }

    // one line of printable ASCII of a bounded length, whatever went into it
    private static String oneLine(String text) {
        StringBuilder line = new StringBuilder()
        for (int index = 0; index < text.length() && line.length() < MAX_REASON_LENGTH; index++) {
            char character = text.charAt(index)
            line.append(character >= (0x20 as char) && character < (0x7f as char) ? character : ('?' as char))
        }
        line.toString()
    }

    /**
     * @param sources directories of sources; one that is not there has nothing that was changed
     * @param since an instant
     * @return the first of the directories in which a directory or a file was changed after the
     * instant, or which has more than {@link #MAX_WALKED} entries with the ones before it, so that it
     * cannot be said that nothing was; {@code null} when none was. A new or a deleted file changes
     * its directory, a file that was written again only itself. A link to a file counts with the time
     * of that file; a link to a directory is not followed.
     */
    static String changedSince(Collection<String> sources, FileTime since) {
        int[] seen = [0] as int[]
        for (String source : sources) {
            Path top = new File(source).toPath()
            if (!Files.isDirectory(top, LinkOption.NOFOLLOW_LINKS)) {
                continue
            }
            Walk walk = new Walk(since, seen)
            try {
                Files.walkFileTree(top, Collections.<FileVisitOption> emptySet(), MAX_WALK_DEPTH, walk)
            } catch (IOException | RuntimeException ignored) {
                return source
            }
            if (walk.changed) {
                return source
            }
        }
        null
    }

    @CompileStatic
    private static final class Walk extends SimpleFileVisitor<Path> {

        private final FileTime since
        private final int[] seen
        boolean changed

        Walk(FileTime since, int[] seen) {
            this.since = since
            this.seen = seen
        }

        @Override
        FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
            // asked of the directory itself: what the directory above lists of it is brought up to date later on NTFS
            FileTime modified
            try {
                modified = Files.getLastModifiedTime(directory, LinkOption.NOFOLLOW_LINKS)
            } catch (IOException ignored) {
                modified = attributes.lastModifiedTime()
            }
            look(modified)
        }

        @Override
        FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
            FileTime modified = attributes.lastModifiedTime()
            if (attributes.isSymbolicLink()) {
                try {
                    BasicFileAttributes behind = Files.readAttributes(file, BasicFileAttributes)
                    if (behind.isRegularFile()) {
                        modified = behind.lastModifiedTime()
                    }
                } catch (IOException ignored) {
                    // a link that leads nowhere counts as the link it is
                }
            }
            look(modified)
        }

        @Override
        FileVisitResult visitFileFailed(Path file, IOException e) {
            FileVisitResult.CONTINUE
        }

        private FileVisitResult look(FileTime modified) {
            if (++seen[0] > MAX_WALKED || modified > since) {
                changed = true
                return FileVisitResult.TERMINATE
            }
            FileVisitResult.CONTINUE
        }
    }
}
