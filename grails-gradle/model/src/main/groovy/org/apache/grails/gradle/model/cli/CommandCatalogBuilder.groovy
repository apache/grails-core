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

import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

import groovy.transform.CompileStatic

import org.grails.io.support.FactoriesLoaderSupport

/**
 * Puts the catalog of the application commands of a project together from the jars and directories
 * of the class path its commands run with, without loading a class from them.
 *
 * <p>What is read is the registration of the commands ({@code META-INF/grails-cli.factories}; for the
 * commands of a Grails 7 plugin {@code META-INF/grails.factories}) and the descriptors of the
 * registered classes ({@link CommandDescriptor}). A class can be registered by more than one element
 * of the class path: the compiler copies a registration written by hand into the classes directory,
 * and a jar can register a class another jar has. The descriptor of a class is therefore looked for
 * in every element that registers it, and the first one in the order of the class path that has a
 * descriptor gives it, and is the origin of the command. Two descriptors of one class that differ
 * are not chosen between: the command is left out and named.</p>
 *
 * <p>Everything that comes from a dependency is held to a size, a count and a form; what is not as
 * it has to be is left out and named in the diagnostics of the document, and so is a name that two
 * classes have. The texts of the document are those of the descriptors, checked to be one line
 * without control characters and no more than that: who shows them treats them as text of a
 * stranger.</p>
 *
 * @since 9.0
 */
@CompileStatic
class CommandCatalogBuilder {

    /** The format of the document, which is that of {@code help --json} of the command line. */
    public static final int FORMAT = 1

    /**
     * Where the catalog of a project is, in the directory of the project: where the task of the build
     * writes it and the command line reads it. It is not in the build directory, which a build can
     * move, so that the command line finds it without running the build to ask where that is.
     */
    public static final String LOCATION = '.grails/cache/command-catalog.json'

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

    /** The most commands one element of the class path can register. */
    public static final int MAX_COMMANDS_PER_ORIGIN = 256

    /** The most commands a catalog has, and the most classes that are looked at. */
    public static final int MAX_COMMANDS = 512

    /** The most classes that are looked at for commands. */
    public static final int MAX_CLASSES = 2048

    /**
     * The most bytes a catalog has. What reads a catalog reads no more, so none is written that has
     * more: with the most commands, each with the longest name, description and origin in ASCII, a
     * catalog is under this, and one whose descriptions are written as escapes lists as many
     * commands as fit.
     */
    public static final int MAX_BYTES = 512 * 1024

    /** The most characters where a command comes from is written with. */
    public static final int MAX_ORIGIN_LENGTH = 120

    /** The most diagnostics a catalog has; one more line says how many were left out. */
    public static final int MAX_DIAGNOSTICS = 64

    /** The most characters a diagnostic has. */
    public static final int MAX_DIAGNOSTIC_LENGTH = 600

    /** The most test phases a catalog lists. */
    public static final int MAX_TEST_PHASES = 64

    /** The most characters the name of a test phase, and of its task, has. */
    public static final int MAX_TEST_PHASE_LENGTH = 64

    /** How many of the classes or origins that share something a diagnostic names; it counts the rest. */
    public static final int MAX_NAMED = 3

    private static final String COMMAND_KEY = 'org.apache.grails.core.cli.ApplicationCommand'
    private static final String LEGACY_COMMAND_KEY = 'grails.dev.commands.ApplicationCommand'
    private static final String PROVIDER_KEY = 'org.apache.grails.core.cli.ApplicationCommandProvider'
    private static final String LEGACY_PROVIDER = 'org.apache.grails.core.cli.compat.LegacyApplicationCommandProvider'
    private static final String LEGACY_PROVIDER_CLASS = 'org/apache/grails/core/cli/compat/LegacyApplicationCommandProvider.class'
    private static final String TEST_PHASE_NAME = '[a-z][a-z0-9]*(-[a-z0-9]+)*'
    private static final String TEST_TASK_NAME = '[A-Za-z][A-Za-z0-9_-]*'
    private static final List<String> ARCHIVES = ['.jar', '.zip'].asImmutable()

    // class name -> where it is registered, in the order of the class path
    private final Map<String, List<Candidate>> registered = new LinkedHashMap<String, List<Candidate>>()
    private final Map<String, String> legacy = new LinkedHashMap<String, String>()
    private final Set<String> diagnostics = new TreeSet<String>()
    private boolean legacySupport
    // the task of each test phase by the name of the phase; null when the phases of the project were not given
    private Map<String, String> testPhases
    // the options the elements of the class path declare for commands
    private final CommandOptionCatalog commandOptions = new CommandOptionCatalog()

    /**
     * Reads the commands of one element of the class path. Elements are added in the order of the class path.
     *
     * @param origin where the element comes from: {@link #ORIGIN_APPLICATION}, or the coordinates of an artifact
     * @param element a directory of classes or resources, or an archive
     */
    void add(String origin, File element) {
        add(origin, [element])
    }

    /**
     * Reads the commands of one element of the class path that is in several places, as the output
     * of a project is in a classes directory and a resources directory: what one of them registers
     * can have its descriptor in another.
     *
     * @param origin where the element comes from
     * @param parts directories or archives; a file that is not an archive has no commands
     */
    void add(String origin, Collection<File> parts) {
        String from = shown(origin)
        List<Source> sources = []
        try {
            for (File part : parts) {
                if (part.isDirectory()) {
                    sources << new DirectorySource(part)
                } else if (part.isFile()) {
                    // a JVM reads any file of its class path as an archive, whatever its name is
                    try {
                        sources << new ArchiveSource(new ZipFile(part))
                    } catch (IOException ignored) {
                        String name = part.name.toLowerCase(Locale.ROOT)
                        if (ARCHIVES.any { String ending -> name.endsWith(ending) }) {
                            say("The archive of ${from} cannot be read, so its commands are not listed.")
                        }
                    }
                }
            }
            read(from, sources)
        } finally {
            for (Source source : sources) {
                source.close()
            }
        }
    }

    /**
     * Gives the test phases of the project, which the document then lists as {@code testPhases}: the
     * name of each, which is the switch {@code grails test-app} selects it with, and its Gradle task.
     * A phase is listed in the order it is given in. One whose name cannot be a switch (lower-case
     * letters and digits in words joined by single hyphens, starting with a letter), or whose task
     * has a name that is not letters, digits, hyphens and underscores, is not listed: it is run by
     * its task. A document without {@code testPhases} says nothing of the phases of the project.
     *
     * @param phases the Gradle task of each phase, by the name of the phase
     */
    void testPhases(Map<String, String> phases) {
        Map<String, String> listed = new LinkedHashMap<String, String>()
        for (Map.Entry<String, String> phase : phases) {
            if (listed.size() < MAX_TEST_PHASES && phase.key.length() <= MAX_TEST_PHASE_LENGTH && phase.key ==~ TEST_PHASE_NAME &&
                    phase.value.length() <= MAX_TEST_PHASE_LENGTH && phase.value ==~ TEST_TASK_NAME) {
                listed.put(phase.key, phase.value)
            }
        }
        testPhases = listed
    }

    /**
     * Says that a class path the commands run with could not be resolved completely: what could be
     * resolved is added as it always is, and the commands of what is missing from it cannot be
     * listed, so the document is not complete. A build that compiles the project does not need that
     * class path, and does not fail for it; the command that is run with it does.
     *
     * @param classPath the name the build gives the class path, such as {@code runtimeClasspath}
     * @param failure what the build says could not be resolved, which is made one line of printable ASCII
     */
    void unresolved(String classPath, String failure) {
        say("${printable(classPath)} cannot be resolved completely (gradle dependencies --configuration ${printable(classPath)} says why), " +
                "so the commands of what is missing from it are not listed: ${printable(failure)}")
    }

    /**
     * @return the document: {@code format}, {@code complete}, {@code diagnostics} and {@code commands},
     * {@code testPhases} when the phases were given, and {@code commandOptions} when an element of the
     * class path declares an option for a command ({@link CommandOptions}); the same whenever the same
     * elements were added in the same order. What there is to say of declared options is among the
     * diagnostics, after what there is to say of the commands, and leaves {@code complete}, which is
     * about the commands, as it is
     */
    Map<String, Object> build() {
        document(MAX_COMMANDS)
    }

    // the document with no more than a number of commands: the first by name, and a line for the others
    private Map<String, Object> document(int most) {
        Set<String> said = new TreeSet<String>(diagnostics)
        List<Entry> entries = []
        for (Map.Entry<String, List<Candidate>> registration : registered) {
            Entry entry = resolve(registration.key, registration.value, said)
            if (entry != null) {
                entries << entry
            }
        }
        Set<String> legacyOrigins = new TreeSet<String>()
        for (Map.Entry<String, String> registration : legacy) {
            if (registered.containsKey(registration.key)) {
                continue
            }
            if (!legacySupport) {
                legacyOrigins << registration.value
                continue
            }
            Entry entry = named(registration.value, registration.key, REGISTRATION_LEGACY, said)
            if (entry != null) {
                entries << entry
            }
        }
        for (String origin : legacyOrigins) {
            said << line("${origin} has commands of Grails 7, which are not listed because they cannot be run: " +
                    'set grails { legacyCommandSupport = true }, or use a version of the plugin for this version of Grails.')
        }
        Map<String, List<Entry>> byName = new TreeMap<String, List<Entry>>()
        for (Entry entry : entries) {
            byName.computeIfAbsent(entry.name) { String name -> new ArrayList<Entry>() } << entry
        }
        List<Map<String, Object>> commands = []
        for (Map.Entry<String, List<Entry>> named : byName) {
            List<Entry> sameName = named.value
            if (sameName.size() > 1) {
                List<String> providers = sameName.collect { Entry entry -> "${entry.className} of ${entry.origin}".toString() }.sort()
                said << line("The command ${named.key} is not listed because more than one class has that name: ${some(providers)}.")
                continue
            }
            Entry entry = sameName[0]
            Map<String, Object> command = new LinkedHashMap<String, Object>()
            command.name = entry.name
            command.aliases = []
            command.description = entry.description ?: ''
            command.origin = entry.origin
            command.target = entry.target ?: CommandDescriptor.TARGET_APPLICATION
            command.registration = entry.registration
            commands << command
        }
        if (commands.size() > most) {
            // by name, so that the same commands are left out whatever the order of the class path is
            said << line("There are ${commands.size()} commands, more than a catalog lists" +
                    (most < MAX_COMMANDS ? " in ${MAX_BYTES} bytes" : " (${MAX_COMMANDS})") +
                    ": the last ${commands.size() - most} by name are not listed.")
            commands = new ArrayList<Map<String, Object>>(commands.subList(0, most))
        }
        List<String> lines = new ArrayList<String>(said)
        boolean complete = lines.isEmpty()
        Set<String> saidOfOptions = new TreeSet<String>()
        List<Map<String, Object>> options = commandOptions.entries(saidOfOptions)
        for (String each : saidOfOptions) {
            lines << line(each)
        }
        if (lines.size() > MAX_DIAGNOSTICS) {
            int more = lines.size() - MAX_DIAGNOSTICS
            lines = new ArrayList<String>(lines.subList(0, MAX_DIAGNOSTICS))
            lines << "And ${more} more.".toString()
        }
        Map<String, Object> document = new LinkedHashMap<String, Object>()
        document.format = FORMAT
        document.complete = complete
        document.diagnostics = lines
        document.commands = commands
        if (testPhases != null) {
            List<Map<String, Object>> phases = []
            for (Map.Entry<String, String> phase : testPhases) {
                Map<String, Object> entry = new LinkedHashMap<String, Object>()
                entry.name = phase.key
                entry.task = phase.value
                phases << entry
            }
            document.testPhases = phases
        }
        if (!options.isEmpty()) {
            document.commandOptions = options
        }
        document
    }

    /**
     * @return the document as one line of JSON in ASCII, with a line end
     */
    String toJson() {
        // what reads the catalog reads a bounded number of bytes: fewer commands are listed until it is within them
        int most = MAX_COMMANDS
        String json = StrictJson.write(document(most)) + '\n'
        while (json.length() > MAX_BYTES && most > 0) {
            most = Math.min(most, ((List) document(most).commands).size())
            most -= Math.max(1, most.intdiv(8))
            json = StrictJson.write(document(most)) + '\n'
        }
        json
    }

    private Entry resolve(String className, List<Candidate> candidates, Set<String> said) {
        // A class the application registers is the application's: what it says of it holds, also when
        // that is nothing, and a jar that comes later on the class path does not give the command of
        // the application another name by carrying a descriptor for its class.
        List<Candidate> own = candidates.findAll { Candidate candidate -> candidate.origin == ORIGIN_APPLICATION }
        if (!own.isEmpty() && own.every { Candidate candidate -> candidate.digest == own[0].digest }) {
            Candidate application = own[0]
            List<String> elsewhere = candidates.findAll { Candidate candidate ->
                candidate.origin != ORIGIN_APPLICATION && candidate.digest != null && candidate.digest != application.digest
            }.collect { Candidate candidate -> candidate.origin }.unique()
            if (elsewhere) {
                said << line("The command class ${className} is listed as the application has it: another descriptor for it, in ${some(elsewhere)}, is not used.")
            }
            if (application.digest == null) {
                return named(application.origin, className, REGISTRATION_FACTORIES, said)
            }
            if (application.refusal != null) {
                said << line("The command class ${className} of ${application.origin} is not listed: its descriptor ${application.refusal}.")
                return null
            }
            return new Entry(name: application.described.name, description: application.described.description, origin: application.origin,
                    className: className, registration: REGISTRATION_DESCRIPTOR, target: application.described.target)
        }
        List<Candidate> described = candidates.findAll { Candidate candidate -> candidate.digest != null }
        if (described.isEmpty()) {
            return named(candidates[0].origin, className, REGISTRATION_FACTORIES, said)
        }
        Candidate first = described[0]
        List<String> others = described.findAll { Candidate candidate -> candidate.digest != first.digest }
                .collect { Candidate candidate -> candidate.origin }.unique()
        if (others) {
            said << line("The command class ${className} is not listed: it has descriptors that differ, in ${some([first.origin] + others)}.")
            return null
        }
        if (first.refusal != null) {
            said << line("The command class ${className} of ${first.origin} is not listed: its descriptor ${first.refusal}.")
            return null
        }
        new Entry(name: first.described.name, description: first.described.description, origin: first.origin,
                className: className, registration: REGISTRATION_DESCRIPTOR, target: first.described.target)
    }

    private static Entry named(String origin, String className, String registration, Set<String> said) {
        String name = CommandDescriptor.conventionName(className)
        if (!CommandDescriptor.isCommandName(name)) {
            said << line("The command class ${className} of ${origin} is not listed: it has no descriptor, " +
                    'and the name its class name gives cannot be the name of a command.')
            return null
        }
        new Entry(name: name, origin: origin, className: className, registration: registration)
    }

    private void read(String origin, List<Source> sources) {
        Set<String> commandClasses = new LinkedHashSet<String>()
        Set<String> legacyClasses = new LinkedHashSet<String>()
        for (Source source : sources) {
            Properties registrations = factories(origin, source, FactoriesLoaderSupport.CLI_FACTORIES_RESOURCE_LOCATION)
            if (registrations != null) {
                // the bridge that runs the commands of Grails 7 is there when its class is, not when something names it
                if (names(registrations, PROVIDER_KEY).contains(LEGACY_PROVIDER) && source.has(LEGACY_PROVIDER_CLASS)) {
                    legacySupport = true
                }
                commandClasses.addAll(names(registrations, COMMAND_KEY))
            }
            Properties legacyRegistrations = factories(origin, source, FactoriesLoaderSupport.FACTORIES_RESOURCE_LOCATION)
            if (legacyRegistrations != null) {
                legacyClasses.addAll(names(legacyRegistrations, LEGACY_COMMAND_KEY))
            }
            declaredOptions(origin, source)
        }
        for (String className : taken(origin, commandClasses)) {
            List<Candidate> candidates = registered.get(className)
            if (candidates == null) {
                if (registered.size() + legacy.size() >= MAX_CLASSES) {
                    say("More than ${MAX_CLASSES} classes are registered as commands; the rest are not looked at.")
                    break
                }
                candidates = new ArrayList<Candidate>()
                registered.put(className, candidates)
            }
            boolean found = false
            for (Source source : sources) {
                Candidate candidate = descriptor(origin, source, className)
                if (candidate != null) {
                    found = true
                    // a class registered again and again is kept once for each descriptor that differs, and no more
                    if (!candidates.any { Candidate known -> known.digest == candidate.digest }) {
                        candidates << candidate
                    }
                }
            }
            if (!found && candidates.isEmpty()) {
                candidates << new Candidate(origin: origin)
            }
        }
        for (String className : taken(origin, legacyClasses)) {
            if (!legacy.containsKey(className)) {
                if (registered.size() + legacy.size() >= MAX_CLASSES) {
                    say("More than ${MAX_CLASSES} classes are registered as commands; the rest are not looked at.")
                    break
                }
                legacy.put(className, origin)
            }
        }
    }

    // the class names an element registers that are class names, as many as an element can register
    private List<String> taken(String origin, Set<String> classNames) {
        List<String> taken = []
        for (String className : classNames) {
            if (!CommandDescriptor.isClassName(className)) {
                say("A command that ${origin} registers is not listed because what is registered is not the name of a class.")
                continue
            }
            if (taken.size() == MAX_COMMANDS_PER_ORIGIN) {
                say("${origin} registers more than ${MAX_COMMANDS_PER_ORIGIN} commands; the rest are not listed.")
                break
            }
            taken << className
        }
        taken
    }

    private static Candidate descriptor(String origin, Source source, String className) {
        byte[] bytes
        try {
            bytes = source.read(CommandDescriptor.location(className), CommandDescriptor.MAX_BYTES)
        } catch (IOException ignored) {
            // every descriptor that cannot be read is the same descriptor: one that is refused
            return new Candidate(origin: origin, digest: 'unreadable',
                    refusal: "has more than ${CommandDescriptor.MAX_BYTES} bytes or cannot be read")
        }
        if (bytes == null) {
            return null
        }
        Candidate candidate = new Candidate(origin: origin, digest: MessageDigest.getInstance('SHA-256').digest(bytes).encodeHex().toString())
        try {
            candidate.described = CommandDescriptor.read(className, bytes)
        } catch (CommandDescriptor.Refused refused) {
            candidate.refusal = refused.message
        }
        candidate
    }

    // the options an element declares for commands, which is one resource of it
    private void declaredOptions(String origin, Source source) {
        if (!source.has(CommandOptions.LOCATION)) {
            return
        }
        byte[] bytes
        try {
            bytes = source.read(CommandOptions.LOCATION, CommandOptions.MAX_BYTES)
        } catch (IOException ignored) {
            bytes = null
        }
        commandOptions.add(origin, bytes)
    }

    private Properties factories(String origin, Source source, String location) {
        byte[] bytes
        try {
            bytes = source.read(location, MAX_FACTORIES_BYTES)
        } catch (IOException ignored) {
            say("The commands of ${origin} are not listed: its ${location} has more than ${MAX_FACTORIES_BYTES} bytes or cannot be read.")
            return null
        }
        if (bytes == null) {
            return null
        }
        Properties properties = new Properties()
        try {
            properties.load(new ByteArrayInputStream(bytes))
        } catch (IOException | IllegalArgumentException ignored) {
            say("The commands of ${origin} are not listed: its ${location} cannot be read as properties.")
            return null
        }
        properties
    }

    private static List<String> names(Properties properties, String key) {
        String value = properties.getProperty(key)
        value ? value.split(',').collect { String name -> name.trim() }.findAll { String name -> name } : new ArrayList<String>()
    }

    private void say(String diagnostic) {
        diagnostics << line(diagnostic)
    }

    // a diagnostic is one line of a bounded length, whatever went into it
    private static String line(String diagnostic) {
        diagnostic.length() > MAX_DIAGNOSTIC_LENGTH ? diagnostic.substring(0, MAX_DIAGNOSTIC_LENGTH - 3) + '...' : diagnostic
    }

    // the first few of a list, and how many there are beside them
    private static String some(List<String> all) {
        all.size() <= MAX_NAMED ? all.join(', ') : "${all.subList(0, MAX_NAMED).join(', ')} and ${all.size() - MAX_NAMED} more"
    }

    // a text the build gives as one line a terminal can take: what is not printable ASCII is a question mark, and a run of white space one space
    private static String printable(String text) {
        StringBuilder printable = new StringBuilder()
        boolean space = false
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index)
            if (Character.isWhitespace(character)) {
                space = printable.length() > 0
                continue
            }
            if (space) {
                printable.append(' ' as char)
                space = false
            }
            printable.append(character > (0x20 as char) && character < (0x7f as char) ? character : ('?' as char))
        }
        printable.toString()
    }

    // an origin is coordinates a build gives, which a line of a diagnostic and a terminal have to be able to take
    private static String shown(String origin) {
        String text = origin.length() > MAX_ORIGIN_LENGTH ? origin.substring(0, MAX_ORIGIN_LENGTH) : origin
        StringBuilder shown = new StringBuilder()
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index)
            shown.append(character > (0x20 as char) && character < (0x7f as char) && character != ('@' as char) &&
                    character != ('$' as char) ? character : ('?' as char))
        }
        shown.toString()
    }

    private static class Entry {
        String name
        String description
        String origin
        String className
        String registration
        // what the descriptor says, or null for a command that is listed from its registration alone
        String target
    }

    // one place a class is registered: with the descriptor found there (read, or refused), or without one
    private static class Candidate {
        String origin
        String digest
        CommandDescriptor.Described described
        String refusal
    }

    private static interface Source {

        /**
         * @return the content of a file, or {@code null} when there is none
         * @throws IOException when it has more than {@code max} bytes or cannot be read
         */
        byte[] read(String location, int max) throws IOException

        boolean has(String location)

        void close()
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

        @Override
        boolean has(String location) {
            new File(directory, location).isFile()
        }

        @Override
        void close() {
        }
    }

    private static class ArchiveSource implements Source {

        private final ZipFile archive

        ArchiveSource(ZipFile archive) {
            this.archive = archive
        }

        @Override
        byte[] read(String location, int max) throws IOException {
            ZipEntry entry = archive.getEntry(location)
            if (entry == null || entry.isDirectory()) {
                return null
            }
            InputStream input = archive.getInputStream(entry)
            try {
                return bounded(input, max)
            } finally {
                input.close()
            }
        }

        @Override
        boolean has(String location) {
            ZipEntry entry = archive.getEntry(location)
            entry != null && !entry.isDirectory()
        }

        @Override
        void close() {
            try {
                archive.close()
            } catch (IOException ignored) {
                // nothing more is read from it
            }
        }
    }

    // the size an entry of an archive states is not relied on: what is read is counted
    private static byte[] bounded(InputStream input, int max) throws IOException {
        byte[] bytes = input.readNBytes(max + 1)
        if (bytes.length > max) {
            throw new IOException('too large')
        }
        bytes
    }
}
