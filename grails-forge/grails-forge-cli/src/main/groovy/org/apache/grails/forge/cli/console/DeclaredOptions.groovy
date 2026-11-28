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

import java.util.regex.Pattern
import groovy.transform.CompileStatic
import picocli.CommandLine

/**
 * The options the plugins of a project, and the project itself, declare for a command of the CLI,
 * as the build of the project lists them in {@link ToolCatalogFile}: its member
 * {@code commandOptions}. An option sets a property of the application the command starts, and is
 * nothing else: no code of a plugin is run for it.
 *
 * <p>The file is in a directory every build writes to, and what it lists was declared by a jar, so
 * an option is used only when it is as the task that writes the file writes one: the rules of
 * {@code CommandOptions} of the Grails Gradle plugin are checked here again, and a specification
 * holds the two to be the same. An option that is not is left out, and counted.</p>
 *
 * <p>An option that is given becomes one argument of the build, the system property
 * {@code grails.} and the name of the property, which the Grails Gradle plugin hands the application
 * without that prefix: the way {@code --port} and a {@code -Dgrails.NAME=VALUE} of the command take.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class DeclaredOptions {

    /** The member of the file that lists the options. */
    public static final String MEMBER = 'commandOptions'

    /** The type of an option that is a switch. */
    public static final String BOOLEAN = 'boolean'

    /** The type of an option that takes a value. */
    public static final String STRING = 'string'

    /** How many options of the file are used, at most: as many as the task writes. */
    public static final int MAX_OPTIONS = 32

    /** How many characters the name of an option has, at least. */
    public static final int MIN_NAME = 2

    /** How many characters the name of an option has, at most. */
    public static final int MAX_NAME = 32

    /** How many characters the description of an option has, at most. */
    public static final int MAX_DESCRIPTION = 200

    /** How many characters the name of a property has, at most. */
    public static final int MAX_PROPERTY = 128

    /** How many characters the value of a switch has, at most. */
    public static final int MAX_VALUE = 64

    /** The properties no option sets, as the task has them. */
    public static final List<String> DENIED_PROPERTIES = ['grails.env', 'base.dir', 'server.port', 'server.address', 'logging.config'].asImmutable()

    /** What the name of a property no option sets starts with, as the task has it. */
    public static final List<String> DENIED_PROPERTY_PREFIXES = [
            'java.', 'javax.', 'jdk.', 'sun.', 'com.sun.', 'user.', 'os.', 'file.', 'line.', 'path.',
            'http.', 'https.', 'ftp.', 'socks.', 'org.gradle.', 'groovy.', 'loader.',
            'spring.config.', 'spring.profiles.', 'spring.main.', 'spring.application.', 'spring.devtools.', 'spring.cloud.',
            'server.ssl.', 'grails.config.', 'grails.project.',
    ].asImmutable()

    /** What the system property of the build starts with that the Grails Gradle plugin hands the application without it. */
    public static final String BUILD_PREFIX = 'grails.'

    private static final Pattern NAME = Pattern.compile('[a-z][a-z0-9]*(-[a-z0-9]+)*')
    private static final Pattern PROPERTY = Pattern.compile('[A-Za-z][A-Za-z0-9_-]*(\\.[A-Za-z0-9_-]+)+')
    private static final Pattern VALUE = Pattern.compile('[A-Za-z0-9][A-Za-z0-9._-]*')
    private static final Pattern LONG_OPTION = Pattern.compile('--[a-z][a-z0-9]*(-[a-z0-9]+)*')
    private static final String NOT_IN_DESCRIPTION = '$%@'
    private static final List<Integer> NOT_SHOWN = [(int) Character.CONTROL, (int) Character.FORMAT, (int) Character.LINE_SEPARATOR,
                                                    (int) Character.PARAGRAPH_SEPARATOR, (int) Character.SURROGATE, (int) Character.PRIVATE_USE,
                                                    (int) Character.UNASSIGNED]
    private static final String LONG = '--'
    private static final int SYNOPSIS_WIDTH = 100
    private static final String SYNOPSIS_INDENT = '        '

    /** No options: of a file that is not used, or lists none. */
    static final DeclaredOptions NONE = new DeclaredOptions(Collections.<Declared>emptyList(), 0)

    /** The options of the file that are used, in its order. */
    final List<Declared> options

    /** How many options of the file are not used, because they are not as the task writes them. */
    final int notUsed

    private DeclaredOptions(List<Declared> options, int notUsed) {
        this.options = options
        this.notUsed = notUsed
    }

    /** One option as the file lists it. */
    static final class Declared {

        /** The command the option is one of. */
        final String command

        /** The long name of the option, without its hyphens. */
        final String name

        /** What the declaration says the option is for. */
        final String description

        /** {@link DeclaredOptions#BOOLEAN} or {@link DeclaredOptions#STRING}. */
        final String type

        /** The property of the application the option sets. */
        final String property

        /** What a switch sets the property to; {@code null} for an option that takes a value. */
        final String value

        /** Where the declaration comes from: the coordinates of an artifact, or {@code application}. */
        final String origin

        Declared(String command, String name, String description, String type, String property, String value, String origin) {
            this.command = command
            this.name = name
            this.description = description
            this.type = type
            this.property = property
            this.value = value
            this.origin = origin
        }

        /** @return the option as it is typed */
        String getOption() {
            return LONG + name
        }

        /** @return whether the option takes a value */
        boolean isValued() {
            return type == STRING
        }

        /** @return what the option does, for the help: the property it sets and, for a switch, to what */
        String getSets() {
            return valued ? "sets ${property}".toString() : "sets ${property}=${value}".toString()
        }

        /** @return who declares the option, in words */
        String getDeclarer() {
            return origin == ToolCatalogFile.APPLICATION ? 'the application' : origin
        }
    }

    /**
     * Reads the options a file lists. Nothing is thrown, and nothing of an option that is not as
     * the task writes one is used.
     *
     * @param listed the member of the file, as it was parsed
     * @return the options; {@link #NONE} for a file without the member
     */
    static DeclaredOptions read(Object listed) {
        if (!(listed instanceof List)) {
            return NONE
        }
        List<Declared> options = []
        Set<String> names = new HashSet<>()
        int notUsed = 0
        for (Object each : (List) listed) {
            Declared option = options.size() < MAX_OPTIONS && each instanceof Map ? declared((Map) each) : null
            if (option == null || !names.add(option.command + ' ' + option.name)) {
                notUsed++
                continue
            }
            options.add(option)
        }
        return new DeclaredOptions(options.asImmutable(), notUsed)
    }

    private static Declared declared(Map option) {
        Object command = option.get('command')
        Object name = option.get('name')
        Object description = option.get('description')
        Object type = option.get('type')
        Object property = option.get('property')
        Object value = option.get('value')
        Object origin = option.get('origin')
        boolean typed = type == BOOLEAN ? isValue(value) : type == STRING && value == null
        if (command != RunAppCommand.NAME || !isName(name) || !isDescription(description) || !isProperty(property) || !typed || !ToolCatalogFile.isOrigin(origin)) {
            return null
        }
        return new Declared((String) command, (String) name, (String) description, (String) type, (String) property, (String) value, (String) origin)
    }

    /**
     * @param name what a file says the name of an option is
     * @return whether the task can have written it
     */
    static boolean isName(Object name) {
        return name instanceof String && ((String) name).length() >= MIN_NAME && ((String) name).length() <= MAX_NAME && NAME.matcher((String) name).matches()
    }

    /**
     * @param property what a file says the property of an option is
     * @return whether the task can have written it: a name of parts joined by dots that is none of
     * {@link #DENIED_PROPERTIES} and starts with none of {@link #DENIED_PROPERTY_PREFIXES}, whatever
     * its case and with or without the hyphens and underscores of its words
     */
    static boolean isProperty(Object property) {
        if (!(property instanceof String) || ((String) property).length() > MAX_PROPERTY || !PROPERTY.matcher((String) property).matches()) {
            return false
        }
        String bound = ((String) property).toLowerCase(Locale.ROOT).replace('-', '').replace('_', '')
        return !DENIED_PROPERTIES.contains(bound) && !DENIED_PROPERTY_PREFIXES.any { String prefix -> bound.startsWith(prefix) }
    }

    /**
     * @param value what a file says a switch sets its property to
     * @return whether the task can have written it
     */
    static boolean isValue(Object value) {
        return value instanceof String && ((String) value).length() <= MAX_VALUE && VALUE.matcher((String) value).matches()
    }

    /**
     * @param description what a file says an option is for
     * @return whether the task can have written it: one line that can be shown, without what the help reads as its own
     */
    static boolean isDescription(Object description) {
        if (!(description instanceof String) || ((String) description).trim().isEmpty() || ((String) description).length() > MAX_DESCRIPTION) {
            return false
        }
        String text = (String) description
        int index = 0
        while (index < text.length()) {
            int codePoint = text.codePointAt(index)
            if (Character.getType(codePoint) in NOT_SHOWN || NOT_IN_DESCRIPTION.indexOf(codePoint) >= 0) {
                return false
            }
            index += Character.charCount(codePoint)
        }
        return true
    }

    /**
     * What a command does with the options that are declared for it.
     */
    static interface Taking {

        /**
         * @return what was given for each declared option on the command line: the value of the
         * property it sets
         */
        Map<Declared, String> getDeclaredGiven()

        /**
         * @return what was added to the command, which it keeps for its help and its refusals
         */
        Added getDeclared()

        void setDeclared(Added added)
    }

    /** What became of the options a file lists for a command. */
    static final class Added {

        /** The file of the build. */
        final ToolCatalogFile file

        /** The options that are options of the command, by what is typed for each. */
        final Map<String, Declared> options = new LinkedHashMap<>()

        /** The options that are none because the command has an option of the name itself, by what would be typed for each. */
        final Map<String, Declared> taken = new LinkedHashMap<>()

        Added(ToolCatalogFile file) {
            this.file = file
        }
    }

    /**
     * Gives a command the options the build of the project lists for it: each becomes an option of
     * the command, which its help, the document for a tool and the completion then show, with who
     * declares it and the property it sets. An option the command has itself, by that name or by the
     * name with one hyphen the Grails shell took, stays the command's: the declared one is not added.
     *
     * @param root the command line of the CLI
     * @param name the name of the command
     * @param file the file of the build
     * @return what there is to say of the declared options where the commands of the application are spoken of, one line each
     */
    static List<String> addTo(CommandLine root, String name, ToolCatalogFile file) {
        CommandLine command = root.getSubcommands().get(name)
        if (command == null || !(command.getCommandSpec().userObject() instanceof Taking)) {
            return Collections.<String>emptyList()
        }
        CommandLine.Model.CommandSpec spec = command.getCommandSpec()
        Taking taking = (Taking) spec.userObject()
        Added added = new Added(file)
        taking.setDeclared(added)
        List<String> notes = []
        for (Declared option : file.declaredOptions.options) {
            if (option.command != name) {
                continue
            }
            if (spec.findOption(option.option) != null || spec.findOption('-' + option.name) != null) {
                added.taken.put(option.option, option)
                notes.add("The option ${option.option} that ${option.declarer} declares for ${name} is not an option here: ${name} has an option of that name itself".toString())
                continue
            }
            // one chain, as the builder of picocli is typed: a switch takes no value, and its label is never shown
            spec.addOption(CommandLine.Model.OptionSpec.builder(option.option)
                    .type(option.valued ? String : boolean)
                    .arity(option.valued ? '1' : '0')
                    .paramLabel('VALUE')
                    // who declares it on a line of its own: coordinates are long, and the help breaks a line where it is full
                    .description(option.description, "(${shown(option.declarer)}: ${option.sets})".toString())
                    .setter(new Given(taking.getDeclaredGiven(), option))
                    .build())
            added.options.put(option.option, option)
        }
        if (file.declaredOptions.notUsed > 0) {
            notes.add("${file.declaredOptions.notUsed} of the options ${ToolCatalogFile.LOCATION} lists for commands are not used: they are not as the Gradle task ${ToolCatalogFile.TASK} writes them".toString())
        }
        usage(root, spec, added, notes)
        return notes
    }

    // the usage line of the command with the declared options in it, and below its help what there is to say of them
    private static void usage(CommandLine root, CommandLine.Model.CommandSpec spec, Added added, List<String> notes) {
        String[] synopsis = spec.usageMessage().customSynopsis()
        if (synopsis != null && synopsis.length > 1 && !added.options.isEmpty()) {
            List<String> lines = new ArrayList<String>(synopsis.toList())
            List<String> more = []
            String line = SYNOPSIS_INDENT
            for (Declared option : added.options.values()) {
                String each = option.valued ? "[${option.option}=VALUE]".toString() : "[${option.option}]".toString()
                if (line.length() > SYNOPSIS_INDENT.length() && line.length() + 1 + each.length() > SYNOPSIS_WIDTH) {
                    more.add(line)
                    line = SYNOPSIS_INDENT
                }
                line = line.length() > SYNOPSIS_INDENT.length() ? line + ' ' + each : line + each
            }
            more.add(line)
            lines.addAll(lines.size() - 1, more)
            spec.usageMessage().customSynopsis(lines as String[])
        }
        List<String> footer = ['']
        footer.addAll(notes.collect { String note -> shown(note) })
        String gradle = UsageHelp.toType(root, "gradle ${ToolCatalogFile.TASK}")
        switch (added.file.state) {
            case ToolCatalogFile.State.MISSING:
            case ToolCatalogFile.State.REFUSED:
                footer.add("The options the plugins of the project declare for ${spec.name()} are options here once the build has listed them: ${gradle}".toString())
                break
            case ToolCatalogFile.State.STALE:
                footer.add(("The options the plugins of the project declare for ${spec.name()} may have changed: ${shown(added.file.reason)} is newer than " +
                        "${ToolCatalogFile.LOCATION}. To list them again: ${gradle}").toString())
                break
            default:
                break
        }
        if (footer.size() > 1) {
            spec.usageMessage().footer(footer as String[])
        }
    }

    /**
     * What is said of an option a command does not have, where plugins can declare options for it.
     *
     * @param spec the command
     * @param added what was added to the command; {@code null} where no project was looked at
     * @param option the option that was typed, without a value
     * @return one sentence without its full stop, or {@code null} for what cannot be the name of a declared option
     */
    static String advice(CommandLine.Model.CommandSpec spec, Added added, String option) {
        if (option == null || !LONG_OPTION.matcher(option).matches() || option.length() > MAX_NAME + LONG.length()) {
            return null
        }
        String name = spec.name()
        CommandLine root = spec.root().commandLine()
        if (added == null) {
            return "A plugin of an application can declare options of ${name}, which are options in the project of that application once its build has listed them"
        }
        Declared taken = added.taken.get(option)
        if (taken != null) {
            return "An option of that name is declared by ${taken.declarer}, and is not an option here: ${name} has an option of that name itself"
        }
        String gradle = UsageHelp.toType(root, "gradle ${ToolCatalogFile.TASK}")
        switch (added.file.state) {
            case ToolCatalogFile.State.CURRENT:
                return added.options.isEmpty() ? "The application and its plugins declare no options of ${name}, as the build of the project lists them" :
                        "The application and its plugins declare these options of ${name}: ${added.options.keySet().join(', ')}"
            case ToolCatalogFile.State.STALE:
                return "The options the application and its plugins declare for ${name} may have changed since the build listed them. To list them again: ${gradle}"
            default:
                return "A plugin of the application can declare options of ${name}, which are options here once the build of the project has listed them: ${gradle}"
        }
    }

    /**
     * @param taking a command that takes declared options
     * @param option an option of the command, by its longest name
     * @return what the document for a tool says of the option beside what it says of every option:
     * its description as it is declared, who declares it, the property it sets and, for a switch,
     * to what; {@code null} for an option the command has itself
     */
    static Map<String, Object> describe(Taking taking, String option) {
        Declared declared = taking.getDeclared() == null ? null : taking.getDeclared().options.get(option)
        if (declared == null) {
            return null
        }
        Map<String, Object> described = new LinkedHashMap<>()
        described.put('description', declared.description)
        described.put('origin', declared.origin)
        described.put('property', declared.property)
        if (!declared.valued) {
            described.put('value', declared.value)
        }
        return described
    }

    /**
     * @param taking a command that takes declared options
     * @param given the system properties the command line gives the build itself, by name
     * @return the arguments of the build for the declared options that were given, one each: a
     * property the command line names itself with {@code -D} is left as it is given there
     */
    static List<String> arguments(Taking taking, Map<String, String> given) {
        List<String> arguments = []
        taking.getDeclaredGiven().each { Declared option, String value ->
            String property = BUILD_PREFIX + option.property
            if (!given.containsKey(property)) {
                arguments.add("-D${property}=${value}".toString())
            }
        }
        return arguments
    }

    // a text of a file in the help, which reads a percent sign as the start of a format
    private static String shown(String text) {
        return text.replace('%', '%%')
    }

    // what a declared option does when it is given: the property it sets has a value
    private static final class Given implements CommandLine.Model.ISetter {

        private final Map<Declared, String> given
        private final Declared option

        Given(Map<Declared, String> given, Declared option) {
            this.given = given
            this.option = option
        }

        @Override
        <T> T set(T value) {
            if (option.valued ? value instanceof String : Boolean.TRUE == value) {
                given.put(option, option.valued ? (String) value : option.value)
            } else {
                given.remove(option)
            }
            return null
        }
    }
}
