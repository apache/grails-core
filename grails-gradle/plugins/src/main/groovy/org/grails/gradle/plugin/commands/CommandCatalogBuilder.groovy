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
package org.grails.gradle.plugin.commands

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

import groovy.json.JsonException
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic

import org.apache.grails.gradle.model.cli.CommandDescriptor
import org.grails.io.support.FactoriesLoaderSupport

/**
 * Puts the catalog of the application commands of a project together from the jars and directories
 * of the class path its commands run with, without loading a class from them.
 *
 * <p>What is read is the registration of the commands ({@code META-INF/grails-cli.factories}; for the
 * commands of a Grails 7 plugin {@code META-INF/grails.factories}) and, for each registered class,
 * its descriptor ({@link CommandDescriptor}) from the same jar or directory. Everything that comes
 * from a dependency is held to a size, a count and a form; what is not as it has to be is left out
 * and named in the diagnostics of the document, and so is a name that two classes have: the catalog
 * does not choose between them.</p>
 *
 * @since 9.0
 */
@CompileStatic
class CommandCatalogBuilder {

    /** The format of the document, which is that of {@code help --json} of the command line. */
    public static final int FORMAT = 1

    /** The origin of a command of the project itself. */
    public static final String ORIGIN_APPLICATION = 'application'

    /** How a command with a descriptor is declared. */
    public static final String REGISTRATION_DESCRIPTOR = 'descriptor'

    /** How a command is declared that is registered without a descriptor: its name is the one its class name gives. */
    public static final String REGISTRATION_FACTORIES = 'factories'

    /** How a command of a Grails 7 plugin is declared: its name is the one its class name gives. */
    public static final String REGISTRATION_LEGACY = 'legacy'

    /** The most bytes of a factories file that are read. */
    public static final int MAX_FACTORIES_BYTES = 256 * 1024

    /** The most commands one jar or directory can register. */
    public static final int MAX_COMMANDS_PER_ORIGIN = 256

    /** The most commands a catalog has. */
    public static final int MAX_COMMANDS = 2048

    /** The most diagnostics a catalog has; one more line says how many were left out. */
    public static final int MAX_DIAGNOSTICS = 64

    private static final String COMMAND_KEY = 'org.apache.grails.core.cli.ApplicationCommand'
    private static final String LEGACY_COMMAND_KEY = 'grails.dev.commands.ApplicationCommand'
    private static final String PROVIDER_KEY = 'org.apache.grails.core.cli.ApplicationCommandProvider'
    private static final String LEGACY_PROVIDER = 'org.apache.grails.core.cli.compat.LegacyApplicationCommandProvider'
    private static final int MAX_ORIGIN_LENGTH = 200
    private static final int MAX_DEPTH = 4
    private static final String JAR = '.jar'

    private final List<Entry> entries = []
    private final Set<String> classes = [] as Set<String>
    private final Set<String> diagnostics = new TreeSet<String>()
    private final Set<String> legacyOrigins = new TreeSet<String>()
    private boolean legacySupport

    /**
     * Reads the commands of one element of the class path. Elements are added in the order of the
     * class path: a class that an earlier element registered is that element's.
     *
     * @param origin where the element comes from: {@link #ORIGIN_APPLICATION}, or the coordinates of an artifact
     * @param element a directory of classes or resources, or a jar; anything else has no commands
     */
    void add(String origin, File element) {
        String from = shown(origin)
        if (element.isDirectory()) {
            read(from, new DirectorySource(element))
        } else if (element.isFile() && element.name.endsWith(JAR)) {
            ZipFile jar
            try {
                jar = new ZipFile(element)
            } catch (IOException ignored) {
                diagnostics << "The jar of ${from} cannot be read, so its commands are not listed.".toString()
                return
            }
            try {
                read(from, new JarSource(jar))
            } finally {
                jar.close()
            }
        }
    }

    /**
     * @return the document: {@code format}, {@code complete}, {@code diagnostics} and {@code commands},
     * the same whenever the same elements were added in the same order
     */
    Map<String, Object> build() {
        Set<String> said = new TreeSet<String>(diagnostics)
        List<Entry> available = entries
        if (!legacySupport) {
            available = entries.findAll { Entry entry -> entry.registration != REGISTRATION_LEGACY }
            for (String origin : legacyOrigins) {
                said << ("${origin} has commands of Grails 7, which are not listed because they cannot be run: " +
                        'set grails { legacyCommandSupport = true }, or use a version of the plugin for this version of Grails.').toString()
            }
        }
        Map<String, List<Entry>> byName = new TreeMap<String, List<Entry>>()
        for (Entry entry : available) {
            byName.computeIfAbsent(entry.name) { String name -> new ArrayList<Entry>() } << entry
        }
        List<Map<String, Object>> commands = []
        for (Map.Entry<String, List<Entry>> named : byName) {
            List<Entry> sameName = named.value
            if (sameName.size() > 1) {
                List<String> providers = sameName.collect { Entry entry -> "${entry.className} of ${entry.origin}".toString() }.sort()
                said << ("The command ${named.key} is not listed because more than one class has that name: " +
                        "${providers.join(', ')}.").toString()
                continue
            }
            if (commands.size() == MAX_COMMANDS) {
                said << "There are more than ${MAX_COMMANDS} commands; the rest are not listed.".toString()
                break
            }
            Entry entry = sameName[0]
            Map<String, Object> command = new LinkedHashMap<String, Object>()
            command.name = entry.name
            command.aliases = []
            command.description = entry.description ?: ''
            command.origin = entry.origin
            command.target = CommandDescriptor.TARGET_APPLICATION
            command.registration = entry.registration
            commands << command
        }
        List<String> lines = new ArrayList<String>(said)
        if (lines.size() > MAX_DIAGNOSTICS) {
            int more = lines.size() - MAX_DIAGNOSTICS
            lines = new ArrayList<String>(lines.subList(0, MAX_DIAGNOSTICS))
            lines << "And ${more} more.".toString()
        }
        Map<String, Object> document = new LinkedHashMap<String, Object>()
        document.format = FORMAT
        document.complete = lines.isEmpty()
        document.diagnostics = lines
        document.commands = commands
        document
    }

    /**
     * @return the document as one line of JSON in ASCII, with a line end
     */
    String toJson() {
        JsonOutput.toJson(build()) + '\n'
    }

    private void read(String origin, Source source) {
        Properties registrations = factories(origin, source, FactoriesLoaderSupport.CLI_FACTORIES_RESOURCE_LOCATION)
        if (registrations != null) {
            if (names(registrations, PROVIDER_KEY).contains(LEGACY_PROVIDER)) {
                legacySupport = true
            }
            addCommands(origin, source, names(registrations, COMMAND_KEY), false)
        }
        Properties legacy = factories(origin, source, FactoriesLoaderSupport.FACTORIES_RESOURCE_LOCATION)
        if (legacy != null) {
            addCommands(origin, source, names(legacy, LEGACY_COMMAND_KEY), true)
        }
    }

    private void addCommands(String origin, Source source, List<String> classNames, boolean legacy) {
        int taken = 0
        for (String className : classNames) {
            if (!CommandDescriptor.isClassName(className)) {
                diagnostics << "A command that ${origin} registers is not listed because what is registered is not the name of a class.".toString()
                continue
            }
            if (taken == MAX_COMMANDS_PER_ORIGIN) {
                diagnostics << "${origin} registers more than ${MAX_COMMANDS_PER_ORIGIN} commands; the rest are not listed.".toString()
                break
            }
            taken++
            if (!classes.add(className)) {
                continue
            }
            Entry entry = legacy ? named(origin, className, REGISTRATION_LEGACY) : described(origin, source, className)
            if (entry != null) {
                entries << entry
                if (legacy) {
                    legacyOrigins << origin
                }
            }
        }
    }

    private Entry described(String origin, Source source, String className) {
        byte[] bytes
        try {
            bytes = source.read(CommandDescriptor.location(className), CommandDescriptor.MAX_BYTES)
        } catch (IOException ignored) {
            return refused(origin, className, "has more than ${CommandDescriptor.MAX_BYTES} bytes or cannot be read")
        }
        if (bytes == null) {
            return named(origin, className, REGISTRATION_FACTORIES)
        }
        Object parsed
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString()
            if (depth(text) > MAX_DEPTH) {
                return refused(origin, className, 'is not a JSON object of the descriptor format')
            }
            parsed = new JsonSlurper().parseText(text)
        } catch (CharacterCodingException | JsonException | IllegalArgumentException ignored) {
            return refused(origin, className, 'is not JSON in UTF-8')
        }
        if (!(parsed instanceof Map)) {
            return refused(origin, className, 'is not a JSON object of the descriptor format')
        }
        Map descriptor = (Map) parsed
        Object format = descriptor.get('format')
        if (!(format instanceof Integer) || format != CommandDescriptor.FORMAT) {
            return refused(origin, className, "is not of format ${CommandDescriptor.FORMAT}, the one this version of the Grails Gradle plugin reads")
        }
        if (descriptor.get('class') != className) {
            return refused(origin, className, 'is the descriptor of another class')
        }
        Object name = descriptor.get('name')
        if (!(name instanceof String) || !CommandDescriptor.isCommandName((String) name)) {
            return refused(origin, className, 'has a name that cannot be the name of a command (lower case letters and digits, ' +
                    "in words joined by single hyphens, at most ${CommandDescriptor.MAX_NAME_LENGTH} characters)")
        }
        Object description = descriptor.get('description')
        if (description != null && !(description instanceof String && CommandDescriptor.isDescription((String) description))) {
            return refused(origin, className, "has a description that is not one line of at most ${CommandDescriptor.MAX_DESCRIPTION_LENGTH} characters that can be shown")
        }
        Object target = descriptor.get('target')
        if (target != null && target != CommandDescriptor.TARGET_APPLICATION) {
            String why = target instanceof String && CommandDescriptor.TARGETS.contains(target) ?
                    "is for the execution target ${target}, which this version of the Grails Gradle plugin cannot run" :
                    'has an execution target that does not exist'
            return refused(origin, className, why)
        }
        new Entry(name: (String) name, description: (String) description, origin: origin, className: className,
                registration: REGISTRATION_DESCRIPTOR)
    }

    private Entry named(String origin, String className, String registration) {
        String name = CommandDescriptor.conventionName(className)
        if (!CommandDescriptor.isCommandName(name)) {
            diagnostics << ("The command class ${className} of ${origin} is not listed: it has no descriptor, " +
                    'and the name its class name gives cannot be the name of a command.').toString()
            return null
        }
        new Entry(name: name, origin: origin, className: className, registration: registration)
    }

    private Entry refused(String origin, String className, String why) {
        diagnostics << "The command class ${className} of ${origin} is not listed: its descriptor ${why}.".toString()
        null
    }

    private Properties factories(String origin, Source source, String location) {
        byte[] bytes
        try {
            bytes = source.read(location, MAX_FACTORIES_BYTES)
        } catch (IOException ignored) {
            diagnostics << "The commands of ${origin} are not listed: its ${location} has more than ${MAX_FACTORIES_BYTES} bytes or cannot be read.".toString()
            return null
        }
        if (bytes == null) {
            return null
        }
        Properties properties = new Properties()
        try {
            properties.load(new ByteArrayInputStream(bytes))
        } catch (IOException | IllegalArgumentException ignored) {
            diagnostics << "The commands of ${origin} are not listed: its ${location} cannot be read as properties.".toString()
            return null
        }
        properties
    }

    private static List<String> names(Properties properties, String key) {
        String value = properties.getProperty(key)
        value ? value.split(',').collect { String name -> name.trim() }.findAll { String name -> name } : new ArrayList<String>()
    }

    // how deep the arrays and objects of a JSON text go, so that a text made to exhaust the stack of the parser is not parsed
    private static int depth(String json) {
        int depth = 0
        int deepest = 0
        boolean inString = false
        for (int index = 0; index < json.length(); index++) {
            char character = json.charAt(index)
            if (inString) {
                if (character == ('\\' as char)) {
                    index++
                } else if (character == ('"' as char)) {
                    inString = false
                }
            } else if (character == ('"' as char)) {
                inString = true
            } else if (character == ('{' as char) || character == ('[' as char)) {
                deepest = Math.max(deepest, ++depth)
            } else if (character == ('}' as char) || character == (']' as char)) {
                depth--
            }
        }
        deepest
    }

    // an origin is coordinates a build gives, which a line of a diagnostic and a terminal have to be able to take
    private static String shown(String origin) {
        String text = origin.length() > MAX_ORIGIN_LENGTH ? origin.substring(0, MAX_ORIGIN_LENGTH) : origin
        StringBuilder shown = new StringBuilder()
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index)
            shown.append(character > (0x20 as char) && character < (0x7f as char) && character != ('@' as char) ? character : ('?' as char))
        }
        shown.toString()
    }

    private static class Entry {
        String name
        String description
        String origin
        String className
        String registration
    }

    private static interface Source {

        /**
         * @return the content of a file, or {@code null} when there is none
         * @throws IOException when it has more than {@code max} bytes or cannot be read
         */
        byte[] read(String location, int max) throws IOException
    }

    private static class DirectorySource implements Source {

        private final File directory

        DirectorySource(File directory) {
            this.directory = directory
        }

        @Override
        byte[] read(String location, int max) throws IOException {
            File file = new File(directory, location)
            if (!file.isFile()) {
                return null
            }
            InputStream input = new FileInputStream(file)
            try {
                return bounded(input, max)
            } finally {
                input.close()
            }
        }
    }

    private static class JarSource implements Source {

        private final ZipFile jar

        JarSource(ZipFile jar) {
            this.jar = jar
        }

        @Override
        byte[] read(String location, int max) throws IOException {
            ZipEntry entry = jar.getEntry(location)
            if (entry == null || entry.isDirectory()) {
                return null
            }
            InputStream input = jar.getInputStream(entry)
            try {
                return bounded(input, max)
            } finally {
                input.close()
            }
        }
    }

    // the size an entry of a jar states is not relied on: what is read is counted
    private static byte[] bounded(InputStream input, int max) throws IOException {
        byte[] bytes = input.readNBytes(max + 1)
        if (bytes.length > max) {
            throw new IOException('too large')
        }
        bytes
    }
}
