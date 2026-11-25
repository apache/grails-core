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

import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.util.regex.Pattern
import groovy.transform.CompileStatic

/**
 * The file in which the build of a project lists the commands of the application and of its plugins:
 * {@code .grails/cache/command-catalog.json}, written by the Gradle task {@code grailsCommandCatalog}.
 * The CLI reads it and never writes it, and runs nothing to read it: every build that compiles the
 * project writes it, and the CLI has the build write it only for a name that is no command yet
 * ({@link ApplicationCommands#list}).
 *
 * <p>The file is in the directory of the project and not in its build directory, which the build of
 * the project can move: where that is cannot be known without running the build, so the task writes
 * the file in this one place in every project, and the CLI reads it there and nowhere else. A file
 * that an earlier build left in a build directory is not read.</p>
 *
 * <p>The file is in a directory every build writes to, so nothing in it is taken as it is: it is a
 * file, not a link and not behind one, of a bounded size, and it is read strictly as the document the
 * task writes. A file that is not such a document is not used at all. A command in it that is not as
 * the task writes one is left out, alone, and the others are used; why is one line that repeats
 * nothing of the file.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class ApplicationCommandFile {

    /** Where the file is, in the directory of the project, whatever the build directory of the project is. */
    public static final String LOCATION = '.grails/cache/command-catalog.json'

    /** The Gradle task that writes the file. */
    public static final String TASK = 'grailsCommandCatalog'

    /** How many bytes the file has, at most. */
    // These are the numbers of what writes the file, CommandCatalogBuilder of grails-gradle-model, which
    // writes no file that is refused here for its size: the two are in two builds, and a specification
    // holds them to be the same and reads the largest file the writer writes.

    /** How many bytes the file has, at most: as many as the task writes. */
    public static final int MAX_BYTES = 512 * 1024

    /** How many commands of the file are used, at most: as many as the task writes, the first by name. */
    public static final int MAX_COMMANDS = 512

    /** How many lines the file has about what the build could not list: as many as the task writes, with its line that says how many more. */
    public static final int MAX_DIAGNOSTICS = 65

    /** How many characters a line about what the build could not list has, at most. */
    public static final int MAX_DIAGNOSTIC = 2048

    /** How many characters the name of a command has, at most. */
    public static final int MAX_NAME = 64

    /** How many test phases of the file are used, at most: as many as the task writes. */
    public static final int MAX_TEST_PHASES = 64

    /** How many other names a command has, at most. */
    public static final int MAX_ALIASES = 16

    /** How many characters the description of a command has, at most. */
    public static final int MAX_DESCRIPTION = 512

    /** How many characters the origin of a command has, at most: as many as the task writes. */
    public static final int MAX_ORIGIN = 120

    /** How many of the commands that are left out are named by their place in the file; the others are counted. */
    public static final int MAX_LEFT_OUT = 3

    /** How many entries of a directory of commands are looked at for what was changed, at most. */
    public static final int MAX_WALKED = 4096

    /** How deep a directory of commands is looked at for what was changed, at most. */
    public static final int MAX_WALK_DEPTH = 24

    /** The origin of a command of the project itself, and the target of a command that runs with the application started around it. */
    public static final String APPLICATION = 'application'

    /** The target of a command the build runs without the application: with the class path of the project, and nothing of it started. */
    public static final String GRADLE = 'gradle'

    // the document is four deep; what a later task adds to a command and is not read here may go deeper
    private static final int MAX_DEPTH = 8
    private static final int MAX_VALUES = 100_000
    private static final int MAX_TEXT = 4096
    private static final Pattern NAME = Pattern.compile('[a-z][a-z0-9]*(-[a-z0-9]+)*')
    private static final Pattern TASK_NAME = Pattern.compile('[A-Za-z][A-Za-z0-9_-]*')
    private static final List<String> REGISTRATIONS = ['descriptor', 'factories', 'legacy']
    private static final List<String> OTHER_TARGETS = ['console', 'standalone', 'session']
    private static final List<Integer> NOT_SHOWN = [(int) Character.CONTROL, (int) Character.FORMAT, (int) Character.LINE_SEPARATOR,
                                                    (int) Character.PARAGRAPH_SEPARATOR, (int) Character.SURROGATE, (int) Character.PRIVATE_USE,
                                                    (int) Character.UNASSIGNED]
    // What the list of the commands is made from, as far as it is in a fixed place: when one of these
    // was changed after the file was written, the file may not say what the build would say now.
    private static final List<String> SOURCES = ['build.gradle', 'build.gradle.kts', 'settings.gradle', 'settings.gradle.kts', 'gradle.properties']
    // The directories the commands of the application are in, as sources and as what the compiler says
    // of them: every directory below them and every file of these endings is looked at, since a
    // command is written into the directory of its package. What the compiler says is looked for where
    // it is unless the build moves its build directory, which is not known here; the sources are where they are.
    private static final Map<String, String> COMMAND_DIRECTORIES = ['grails-app/commands': '.groovy', 'build/classes/groovy/main/META-INF/grails-commands': '.json']

    /** What there is of the file. */
    static enum State {

        /** There is no file: the task has not run. */
        MISSING,

        /** There is something that is not used. */
        REFUSED,

        /** The file is used, and something it is made from was changed after it was written. */
        STALE,

        /** The file is used. */
        CURRENT
    }

    /** What there is of the file. */
    final State state

    /**
     * For a file that is not used why not, as the end of a sentence that starts with the file; for a
     * file that may be out of date what was changed after it, as a path in the project.
     */
    final String reason

    /** The commands of the file, in its order; none when it is not used. */
    final List<Entry> commands

    /** Whether the build listed every command, as the file says. */
    final boolean complete

    /** What the build could not list, as the file says: one line each, of printable ASCII. */
    final List<String> diagnostics

    /** The commands of the file that are not used, and why: one line each, with nothing of the file in it. */
    final List<String> leftOut

    /**
     * The test phases of the project, as the file says: the Gradle task of each by the name of the
     * phase, which is the switch of {@code test-app} for it, in the order of the file. {@code null}
     * when the file is not used or says nothing of the phases.
     */
    final Map<String, String> testPhases

    /**
     * The options the application and its plugins declare for commands of the CLI, as the file
     * lists them: none when the file is not used or lists none.
     */
    final DeclaredOptions declaredOptions

    private ApplicationCommandFile(State state, String reason, List<Entry> commands, boolean complete, List<String> diagnostics, List<String> leftOut,
                                   Map<String, String> testPhases = null, DeclaredOptions declaredOptions = DeclaredOptions.NONE) {
        this.testPhases = testPhases
        this.declaredOptions = declaredOptions
        this.state = state
        this.reason = reason
        this.commands = commands
        this.complete = complete
        this.diagnostics = diagnostics
        this.leftOut = leftOut
    }

    /**
     * Reads the file of a project. Nothing is thrown: a file that cannot be read is a file that is not used.
     *
     * @param project the directory of the project
     * @return what there is of the file
     */
    static ApplicationCommandFile read(Path project) {
        try {
            Path file = project
            for (Path step : Path.of(LOCATION)) {
                // a directory on the way that is a link leads to where the build of the project did not write
                if (file != project && !Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
                    return Files.isSymbolicLink(file) ? refused("is behind ${project.relativize(file).join('/')}, which is a link") : missing()
                }
                file = file.resolve(step)
            }
            BasicFileAttributes attributes
            try {
                attributes = Files.readAttributes(file, BasicFileAttributes, LinkOption.NOFOLLOW_LINKS)
            } catch (IOException ignored) {
                return missing()
            }
            if (attributes.isSymbolicLink()) {
                return refused('is a link')
            }
            // a pipe or a device is not opened: reading one need not end
            if (!attributes.isRegularFile()) {
                return refused('is not a file')
            }
            if (file.toRealPath() != project.toRealPath().resolve(LOCATION)) {
                return refused('is not in the directory of the project')
            }
            if (attributes.size() > MAX_BYTES) {
                return refused("has more than the ${MAX_BYTES} bytes it can have")
            }
            byte[] bytes = bytesOf(file, attributes.size())
            if (bytes == null) {
                return refused('became longer while it was read')
            }
            ApplicationCommandFile read = parse(bytes)
            String changed = read.state == State.CURRENT ? changedSince(project, attributes.lastModifiedTime()) : null
            return changed == null ? read : new ApplicationCommandFile(State.STALE, changed, read.commands, read.complete, read.diagnostics, read.leftOut, read.testPhases, read.declaredOptions)
        } catch (IOException | RuntimeException ignored) {
            return refused('cannot be read')
        }
    }

    private static ApplicationCommandFile missing() {
        return new ApplicationCommandFile(State.MISSING, null, Collections.<Entry>emptyList(), true, Collections.<String>emptyList(), Collections.<String>emptyList())
    }

    private static ApplicationCommandFile refused(String reason) {
        return new ApplicationCommandFile(State.REFUSED, reason, Collections.<Entry>emptyList(), true, Collections.<String>emptyList(), Collections.<String>emptyList())
    }

    // the bytes the file had when its size was read, and no more however long it has become since; null when it has more
    private static byte[] bytesOf(Path file, long size) throws IOException {
        SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)
        try {
            ByteBuffer buffer = ByteBuffer.allocate((int) size + 1)
            while (buffer.hasRemaining() && channel.read(buffer) != -1) {
                continue
            }
            if (!buffer.hasRemaining()) {
                return null
            }
            byte[] bytes = new byte[buffer.position()]
            buffer.flip()
            buffer.get(bytes)
            return bytes
        } finally {
            channel.close()
        }
    }

    private static ApplicationCommandFile parse(byte[] bytes) {
        for (byte each : bytes) {
            if (each < 0) {
                return refused('is not the ASCII the task writes')
            }
        }
        Object document
        try {
            document = BoundedJson.parse(new String(bytes, StandardCharsets.US_ASCII), MAX_DEPTH, MAX_VALUES, MAX_TEXT)
        } catch (BoundedJson.Refused e) {
            return refused(e.getMessage())
        }
        if (!(document instanceof Map)) {
            return refused('is not a JSON object')
        }
        Map<String, Object> members = (Map<String, Object>) document
        // the number as it is written: 1.0 and 1e0 are not what the task writes
        if (!(members.get('format') instanceof Long) || ((Long) members.get('format')).longValue() != 1L) {
            return refused('is not of format 1, the one that is read here')
        }
        if (!(members.get('complete') instanceof Boolean)) {
            return refused('does not say with true or false whether it is complete')
        }
        Object said = members.get('diagnostics')
        if (!(said instanceof List) || ((List) said).size() > MAX_DIAGNOSTICS || !((List) said).every { Object line -> isDiagnostic(line) }) {
            return refused("has diagnostics that are not at most ${MAX_DIAGNOSTICS} lines of at most ${MAX_DIAGNOSTIC} characters of printable ASCII")
        }
        Object listed = members.get('commands')
        if (!(listed instanceof List)) {
            return refused('has no list of commands')
        }
        // One command that is not as the task writes it is left out, and the others are used: a jar
        // with an unusual name does not take the commands of every other plugin away.
        List<Entry> commands = []
        List<String> leftOut = []
        int notUsed = 0
        Set<String> names = new HashSet<>()
        int number = 0
        for (Object each : (List) listed) {
            number++
            String wrong
            Entry entry = null
            if (commands.size() == MAX_COMMANDS) {
                leftOut.add("The commands of ${LOCATION} after the first ${MAX_COMMANDS} are not used: ${((List) listed).size() - number + 1} of them".toString())
                break
            }
            if (!(each instanceof Map)) {
                wrong = 'it is no command'
            } else {
                Map<String, Object> command = (Map<String, Object>) each
                wrong = wrongWith(command)
                if (wrong == null) {
                    entry = new Entry((String) command.get('name'), command.containsKey('aliases') ? (List<String>) command.get('aliases') : Collections.<String>emptyList(),
                            command.containsKey('description') ? (String) command.get('description') : '', (String) command.get('origin'), (String) command.get('registration'),
                            (String) command.get('target'))
                    if (entry.names().any { String name -> names.contains(name) } || entry.names().toSet().size() < entry.names().size()) {
                        wrong = 'it has a name that a command before it has'
                    }
                }
            }
            if (wrong != null) {
                if (notUsed++ < MAX_LEFT_OUT) {
                    leftOut.add("Command ${number} of ${LOCATION} is not used: ${wrong}".toString())
                }
                continue
            }
            names.addAll(entry.names())
            commands.add(entry)
        }
        if (notUsed > MAX_LEFT_OUT) {
            leftOut.add(MAX_LEFT_OUT, "${notUsed - MAX_LEFT_OUT} more commands of ${LOCATION} are not used, for reasons like these".toString())
        }
        return new ApplicationCommandFile(State.CURRENT, null, commands.asImmutable(), (Boolean) members.get('complete'), ((List<String>) said).asImmutable(), leftOut.asImmutable(),
                testPhases(members.get('testPhases')), DeclaredOptions.read(members.get(DeclaredOptions.MEMBER)))
    }

    // The phases a file lists, each as the task writes one: a phase that is not is left out, since it
    // is then run by its task and not by a switch. null for a file that lists none at all.
    private static Map<String, String> testPhases(Object listed) {
        if (!(listed instanceof List)) {
            return null
        }
        Map<String, String> phases = new LinkedHashMap<>()
        for (Object each : (List) listed) {
            if (phases.size() == MAX_TEST_PHASES) {
                break
            }
            if (each instanceof Map) {
                Object name = ((Map) each).get('name')
                Object task = ((Map) each).get('task')
                if (isName(name) && task instanceof String && ((String) task).length() <= MAX_NAME && TASK_NAME.matcher((String) task).matches()) {
                    phases.putIfAbsent((String) name, (String) task)
                }
            }
        }
        return Collections.unmodifiableMap(phases)
    }

    // what a command of the file has that the task does not write, as what is said of the command; null when there is nothing
    private static String wrongWith(Map<String, Object> command) {
        if (!isName(command.get('name'))) {
            return "its name is not lower-case letters and digits in words joined by single hyphens, starting with a letter, of at most ${MAX_NAME} characters"
        }
        Object aliases = command.get('aliases')
        if (command.containsKey('aliases') && (!(aliases instanceof List) || ((List) aliases).size() > MAX_ALIASES || !((List) aliases).every { Object alias -> isName(alias) })) {
            return "its other names are not at most ${MAX_ALIASES} names of a command"
        }
        if (command.containsKey('description') && !isDescription(command.get('description'))) {
            return "its description is not one line of at most ${MAX_DESCRIPTION} characters that can be shown"
        }
        if (!isOrigin(command.get('origin'))) {
            return "its origin is not at most ${MAX_ORIGIN} characters of printable ASCII without a space, an @ or a dollar sign, or is a word the command line keeps for itself"
        }
        Object target = command.get('target')
        if (target != APPLICATION && target != GRADLE) {
            return target in OTHER_TARGETS ? "its target is ${target}, which is not run from here" : 'its target is none there is'
        }
        if (!(command.get('registration') in REGISTRATIONS)) {
            return "its registration is none of ${REGISTRATIONS.join(', ')}"
        }
        return null
    }

    private static boolean isName(Object name) {
        return name instanceof String && ((String) name).length() <= MAX_NAME && NAME.matcher((String) name).matches()
    }

    /**
     * The rule of the task for where a command comes from. The task writes the coordinates of an
     * artifact, or {@code file:} and the name of a file, with every character that is not printable
     * ASCII, and every space, {@code @} and dollar sign, replaced, and no more than this many
     * characters. What an origin is made of is not held to anything else: it is the name of a jar or
     * of a project, which can be almost anything, and it is only shown.
     *
     * @param origin what a file says where a command comes from
     * @return whether the task can have written it
     */
    static boolean isOrigin(Object origin) {
        if (!(origin instanceof String) || ((String) origin).isEmpty() || ((String) origin).length() > MAX_ORIGIN) {
            return false
        }
        String text = (String) origin
        for (int i = 0; i < text.length(); i++) {
            char each = text.charAt(i)
            if (each <= (' ' as char) || each > ('~' as char) || each == ('@' as char) || each == ('$' as char)) {
                return false
            }
        }
        // what the CLI says of its own commands, and of those of the application, is not said by a file in another spelling
        return !text.equalsIgnoreCase(CommandCatalog.BUILT_IN) && (text == APPLICATION || !text.equalsIgnoreCase(APPLICATION))
    }

    private static boolean isDiagnostic(Object line) {
        if (!(line instanceof String) || ((String) line).length() > MAX_DIAGNOSTIC) {
            return false
        }
        String text = (String) line
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) < (' ' as char) || text.charAt(i) > ('~' as char)) {
                return false
            }
        }
        return true
    }

    // the rule of the task: one line that can be shown
    private static boolean isDescription(Object description) {
        if (!(description instanceof String) || ((String) description).length() > MAX_DESCRIPTION) {
            return false
        }
        String text = (String) description
        int index = 0
        while (index < text.length()) {
            int codePoint = text.codePointAt(index)
            if (Character.getType(codePoint) in NOT_SHOWN) {
                return false
            }
            index += Character.charCount(codePoint)
        }
        return true
    }

    // the first of the places the list is made from that was changed after the file was written
    private static String changedSince(Path project, FileTime written) {
        for (String source : SOURCES) {
            FileTime modified = modifiedBehind(project.resolve(source))
            if (modified != null && modified.compareTo(written) > 0) {
                return source
            }
        }
        for (Map.Entry<String, String> directory : COMMAND_DIRECTORIES.entrySet()) {
            if (isChangedBelow(project.resolve(directory.key), directory.value, written)) {
                return directory.key
            }
        }
        return null
    }

    // When the file a path leads to was changed, whatever links lead to it, which is the file the build
    // reads: when a link itself was made says nothing of it. null when there is no file, which a link
    // that leads nowhere is. The task that writes the file reads the times in the same way.
    private static FileTime modifiedBehind(Path path) {
        try {
            return Files.getLastModifiedTime(path)
        } catch (IOException ignored) {
            return null
        }
    }

    // when the file a link leads to was changed; null when it leads to no file
    private static FileTime fileBehind(Path link) {
        try {
            BasicFileAttributes behind = Files.readAttributes(link, BasicFileAttributes)
            return behind.isRegularFile() ? behind.lastModifiedTime() : null
        } catch (IOException ignored) {
            return null
        }
    }

    // Whether a directory, a directory below it or a file of the ending in one of them was changed
    // after an instant: a new or a deleted file changes its directory, a file that was written again
    // only itself. A link to a file of the ending counts as that file does; a link to a directory is
    // not followed. No more is looked at than a bounded number of entries; a directory that has more
    // is taken for changed, since it cannot be said that it is not.
    private static boolean isChangedBelow(Path top, String ending, FileTime since) {
        if (!Files.isDirectory(top, LinkOption.NOFOLLOW_LINKS)) {
            return false
        }
        boolean[] changed = [false] as boolean[]
        int[] seen = [0] as int[]
        try {
            Files.walkFileTree(top, Collections.<FileVisitOption>emptySet(), MAX_WALK_DEPTH, new SimpleFileVisitor<Path>() {

                @Override
                FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    return look(changedAt(directory, attributes), true)
                }

                @Override
                FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (attributes.isSymbolicLink()) {
                        FileTime behind = file.getFileName().toString().endsWith(ending) ? fileBehind(file) : null
                        return look(behind ?: attributes.lastModifiedTime(), behind != null)
                    }
                    // at the depth that is the last looked at, a directory is given as a file
                    boolean counts = attributes.isDirectory() || (attributes.isRegularFile() && file.getFileName().toString().endsWith(ending))
                    return look(attributes.isDirectory() ? changedAt(file, attributes) : attributes.lastModifiedTime(), counts)
                }

                @Override
                FileVisitResult visitFileFailed(Path file, IOException e) {
                    return FileVisitResult.CONTINUE
                }

                // When a directory was changed is asked of the directory itself. What the walk has of it is
                // what the directory above lists, and NTFS brings that up to date only later: a file that
                // was deleted a moment ago would not be seen there on Windows.
                private FileTime changedAt(Path directory, BasicFileAttributes listed) {
                    try {
                        return Files.getLastModifiedTime(directory, LinkOption.NOFOLLOW_LINKS)
                    } catch (IOException ignored) {
                        return listed.lastModifiedTime()
                    }
                }

                private FileVisitResult look(FileTime modified, boolean counts) {
                    if (++seen[0] > MAX_WALKED || (counts && modified.compareTo(since) > 0)) {
                        changed[0] = true
                        return FileVisitResult.TERMINATE
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (IOException | RuntimeException ignored) {
            return true
        }
        return changed[0]
    }

    /** A command of the file. */
    static final class Entry {

        /** What is typed for the command. */
        final String name

        /** The other names of the command. */
        final List<String> aliases

        /** One line that can be shown, or the empty text when the build does not know one. */
        final String description

        /** Where the command comes from: {@code application}, {@code file:<name>} or {@code group:artifact:version}. */
        final String origin

        /** How the command is registered, which says how much the build knows of it. */
        final String registration

        /** How the build runs the command: {@link #APPLICATION} with the application, {@link #GRADLE} without it. */
        final String target

        private Entry(String name, List<String> aliases, String description, String origin, String registration, String target = APPLICATION) {
            this.name = name
            this.aliases = new ArrayList<String>(aliases).asImmutable()
            this.description = description
            this.origin = origin
            this.registration = registration
            this.target = target
        }

        /**
         * @return whether the build runs the command without starting the application
         */
        boolean isGradle() {
            return target == GRADLE
        }

        /**
         * @param name what is typed for the command
         * @return a script of the application as a command: all that is known of one is the name its file gives it
         */
        static Entry script(String name) {
            return new Entry(name, Collections.<String>emptyList(), '', APPLICATION, ScriptCommandFiles.REGISTRATION)
        }

        /**
         * @return whether the command is a script of the application, which the build runs without the application
         */
        boolean isScript() {
            return registration == ScriptCommandFiles.REGISTRATION
        }

        /**
         * @return every name of the command, the name itself first
         */
        List<String> names() {
            List<String> names = [name]
            names.addAll(aliases)
            return names
        }
    }
}
