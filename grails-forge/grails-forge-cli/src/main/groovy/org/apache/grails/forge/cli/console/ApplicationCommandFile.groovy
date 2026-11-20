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
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.util.regex.Pattern
import groovy.transform.CompileStatic

/**
 * The file in which the build of a project lists the commands of the application and of its plugins:
 * {@code build/grails/command-catalog.json}, written by the Gradle task {@code grailsCommandCatalog}.
 * The CLI reads it and never writes it, and runs nothing to have it.
 *
 * <p>The file is in a directory every build writes to, so nothing in it is taken as it is: it is a
 * file, not a link and not behind one, of a bounded size; it is read strictly as the document the
 * task writes, every member is held to what the task can write, and a file that is anything else is
 * not used at all. Why not is one line that repeats nothing of the file.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class ApplicationCommandFile {

    /** Where the file is, in the directory of the project. */
    public static final String LOCATION = 'build/grails/command-catalog.json'

    /** The Gradle task that writes the file. */
    public static final String TASK = 'grailsCommandCatalog'

    /** How many bytes the file has, at most. */
    public static final int MAX_BYTES = 4 * 1024 * 1024

    /** How many commands the file has, at most: as many as the task writes. */
    public static final int MAX_COMMANDS = 2048

    /** How many lines the file has about what the build could not list: as many as the task writes, with its line that says how many more. */
    public static final int MAX_DIAGNOSTICS = 65

    /** How many characters a line about what the build could not list has, at most. */
    public static final int MAX_DIAGNOSTIC = 2048

    /** How many characters the name of a command has, at most. */
    public static final int MAX_NAME = 64

    /** How many other names a command has, at most. */
    public static final int MAX_ALIASES = 16

    /** How many characters the description of a command has, at most. */
    public static final int MAX_DESCRIPTION = 512

    /** How many characters the origin of a command has, at most. */
    public static final int MAX_ORIGIN = 200

    /** The origin of a command of the project itself, and the target of every command of the file. */
    public static final String APPLICATION = 'application'

    // the document is four deep; what a later task adds to a command and is not read here may go deeper
    private static final int MAX_DEPTH = 8
    private static final int MAX_VALUES = 100_000
    private static final int MAX_TEXT = 4096
    private static final Pattern NAME = Pattern.compile('[a-z][a-z0-9]*(-[a-z0-9]+)*')
    private static final String PART = '[A-Za-z0-9_][A-Za-z0-9_.+-]*'
    private static final Pattern ORIGIN = Pattern.compile(APPLICATION + '|file:' + PART + '|' + PART + ':' + PART + ':' + PART)
    private static final List<String> REGISTRATIONS = ['descriptor', 'factories', 'legacy']
    private static final List<String> OTHER_TARGETS = ['console', 'standalone', 'gradle', 'session']
    private static final List<Integer> NOT_SHOWN = [(int) Character.CONTROL, (int) Character.FORMAT, (int) Character.LINE_SEPARATOR,
                                                    (int) Character.PARAGRAPH_SEPARATOR, (int) Character.SURROGATE, (int) Character.PRIVATE_USE,
                                                    (int) Character.UNASSIGNED]
    // What the list of the commands is made from, as far as it is in a fixed place: when one of these
    // was changed after the file was written, the file may not say what the build would say now.
    private static final List<String> SOURCES = ['build.gradle', 'build.gradle.kts', 'settings.gradle', 'settings.gradle.kts', 'gradle.properties',
                                                 'grails-app/commands', 'build/classes/groovy/main/META-INF/grails-commands']

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

    private ApplicationCommandFile(State state, String reason, List<Entry> commands, boolean complete, List<String> diagnostics) {
        this.state = state
        this.reason = reason
        this.commands = commands
        this.complete = complete
        this.diagnostics = diagnostics
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
                return refused('is not in the build directory of the project')
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
            return changed == null ? read : new ApplicationCommandFile(State.STALE, changed, read.commands, read.complete, read.diagnostics)
        } catch (IOException | RuntimeException ignored) {
            return refused('cannot be read')
        }
    }

    private static ApplicationCommandFile missing() {
        return new ApplicationCommandFile(State.MISSING, null, Collections.<Entry>emptyList(), true, Collections.<String>emptyList())
    }

    private static ApplicationCommandFile refused(String reason) {
        return new ApplicationCommandFile(State.REFUSED, reason, Collections.<Entry>emptyList(), true, Collections.<String>emptyList())
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
        Object format = members.get('format')
        if (!(format instanceof BigDecimal) || ((BigDecimal) format).compareTo(BigDecimal.ONE) != 0) {
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
        if (((List) listed).size() > MAX_COMMANDS) {
            return refused("has more than the ${MAX_COMMANDS} commands it can have")
        }
        List<Entry> commands = []
        Set<String> names = new HashSet<>()
        int number = 0
        for (Object each : (List) listed) {
            number++
            if (!(each instanceof Map)) {
                return refused("has something that is no command as command ${number}")
            }
            Map<String, Object> command = (Map<String, Object>) each
            String wrong = wrongWith(command)
            if (wrong != null) {
                return refused("has ${wrong} for command ${number}")
            }
            Entry entry = new Entry((String) command.get('name'), command.containsKey('aliases') ? (List<String>) command.get('aliases') : Collections.<String>emptyList(),
                    command.containsKey('description') ? (String) command.get('description') : '', (String) command.get('origin'), (String) command.get('registration'))
            for (String name : entry.names()) {
                if (!names.add(name)) {
                    return refused("has a second command of the same name as command ${number}")
                }
            }
            commands.add(entry)
        }
        return new ApplicationCommandFile(State.CURRENT, null, commands.asImmutable(), (Boolean) members.get('complete'), ((List<String>) said).asImmutable())
    }

    // what a command of the file has that the task does not write, as something the file has; null when there is nothing
    private static String wrongWith(Map<String, Object> command) {
        if (!isName(command.get('name'))) {
            return "a name that is not lower-case letters and digits in words joined by single hyphens, starting with a letter, of at most ${MAX_NAME} characters,"
        }
        Object aliases = command.get('aliases')
        if (command.containsKey('aliases') && (!(aliases instanceof List) || ((List) aliases).size() > MAX_ALIASES || !((List) aliases).every { Object alias -> isName(alias) })) {
            return "other names that are not at most ${MAX_ALIASES} names of a command"
        }
        if (command.containsKey('description') && !isDescription(command.get('description'))) {
            return "a description that is not one line of at most ${MAX_DESCRIPTION} characters that can be shown"
        }
        Object origin = command.get('origin')
        if (!(origin instanceof String) || ((String) origin).length() > MAX_ORIGIN || !ORIGIN.matcher((String) origin).matches()) {
            return "an origin that is neither ${APPLICATION}, nor file: and the name of a file, nor group:artifact:version, in letters, digits and _.+- and of at most ${MAX_ORIGIN} characters,"
        }
        Object target = command.get('target')
        if (target != APPLICATION) {
            return target in OTHER_TARGETS ? "the target ${target}, which is not run from here," : 'a target that is none'
        }
        if (!(command.get('registration') in REGISTRATIONS)) {
            return "a registration that is none of ${REGISTRATIONS.join(', ')}"
        }
        return null
    }

    private static boolean isName(Object name) {
        return name instanceof String && ((String) name).length() <= MAX_NAME && NAME.matcher((String) name).matches()
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
            try {
                if (Files.getLastModifiedTime(project.resolve(source), LinkOption.NOFOLLOW_LINKS).compareTo(written) > 0) {
                    return source
                }
            } catch (IOException ignored) {
                // it is not there
            }
        }
        return null
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

        private Entry(String name, List<String> aliases, String description, String origin, String registration) {
            this.name = name
            this.aliases = new ArrayList<String>(aliases).asImmutable()
            this.description = description
            this.origin = origin
            this.registration = registration
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
