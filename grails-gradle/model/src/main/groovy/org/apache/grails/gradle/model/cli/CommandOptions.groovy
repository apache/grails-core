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

import java.util.regex.Pattern

import groovy.transform.CompileStatic

/**
 * The options a plugin, or the application, declares for a command of the {@code grails} command
 * line: one JSON resource, {@code META-INF/grails-command-options.json}, in the jar of the plugin, in
 * its {@code -cli} companion or in the resources of the application. The build lists the declarations
 * in the catalog of the commands of the project ({@link CommandCatalogBuilder}) without loading a
 * class, and the command line reads them there: no code of a plugin runs for an option.
 *
 * <p>Format 1 is one JSON object with the members {@code format} (the number 1) and {@code options},
 * an array of objects. Each of those declares one option:</p>
 * <ul>
 * <li>{@code command}: the command the option is one of. {@code run-app} is the only command that
 * takes declared options in this version.</li>
 * <li>{@code name}: the long name of the option without its two hyphens, as {@code query-tail} for
 * {@code --query-tail}: lower case letters and digits in words joined by single hyphens, starting with
 * a letter, of {@link #MIN_NAME_LENGTH} to {@link #MAX_NAME_LENGTH} characters.</li>
 * <li>{@code description}: one line for the help of the command, at most
 * {@link #MAX_DESCRIPTION_LENGTH} characters, without {@code $}, {@code %} and {@code @}.</li>
 * <li>{@code type}: {@code boolean} for a switch, which is what an option without the member is, or
 * {@code string} for an option that takes a value.</li>
 * <li>{@code property}: the property of the started application the option sets, see {@link #isProperty}.</li>
 * <li>{@code value}: what a switch sets the property to; {@code true} without the member. An option
 * that takes a value has no such member.</li>
 * </ul>
 *
 * <p>A declaration is something a dependency wrote that ends on the command line of a JVM, so every
 * rule is checked and nothing of a declaration that breaks one is used or repeated. A reader ignores
 * a member it does not know.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class CommandOptions {

    /** The format this class reads. */
    public static final int FORMAT = 1

    /** Where the declarations are in a jar, or in a directory of classes or resources. */
    public static final String LOCATION = 'META-INF/grails-command-options.json'

    /** The command that takes declared options. */
    public static final String COMMAND_RUN_APP = 'run-app'

    /** The commands that take declared options. */
    public static final List<String> COMMANDS = [COMMAND_RUN_APP].asImmutable()

    /** The type of an option that is a switch. */
    public static final String TYPE_BOOLEAN = 'boolean'

    /** The type of an option that takes a value. */
    public static final String TYPE_STRING = 'string'

    /** The types an option can have. */
    public static final List<String> TYPES = [TYPE_BOOLEAN, TYPE_STRING].asImmutable()

    /** What a switch sets its property to when its declaration does not say. */
    public static final String DEFAULT_VALUE = 'true'

    /** The most bytes a resource of declarations has: as many options as an element declares fit, each with the longest texts written as escapes. */
    public static final int MAX_BYTES = 32 * 1024

    /** How deep the arrays and objects of the resource go at most: an option is three deep, and a member a later version adds can go two deeper. */
    public static final int MAX_DEPTH = 5

    /** The most options one element of the class path declares. */
    public static final int MAX_OPTIONS_PER_ORIGIN = 16

    /** The most options a catalog lists. */
    public static final int MAX_OPTIONS = 32

    /** The fewest characters the name of an option has. */
    public static final int MIN_NAME_LENGTH = 2

    /** The most characters the name of an option has. */
    public static final int MAX_NAME_LENGTH = 32

    /** The most characters the description of an option has. */
    public static final int MAX_DESCRIPTION_LENGTH = 200

    /** The most characters the name of a property has. */
    public static final int MAX_PROPERTY_LENGTH = 128

    /** The most characters the value of a switch has. */
    public static final int MAX_VALUE_LENGTH = 64

    /**
     * The properties no option sets, whatever their case is and with or without the hyphens and
     * underscores of their words: they select the environment or the directory of the application,
     * or are set by an option {@code run-app} has itself.
     */
    public static final List<String> DENIED_PROPERTIES = ['grails.env', 'base.dir', 'server.port', 'server.address', 'logging.config'].asImmutable()

    /**
     * What the name of a property no option sets starts with, whatever its case is and with or
     * without the hyphens and underscores of its words: the properties of the JVM and of its network
     * and its files, of the build and of the Groovy runtime, and those that say where the application
     * reads its configuration, its profiles and its classes from.
     */
    public static final List<String> DENIED_PROPERTY_PREFIXES = [
            'java.', 'javax.', 'jdk.', 'sun.', 'com.sun.', 'user.', 'os.', 'file.', 'line.', 'path.',
            'http.', 'https.', 'ftp.', 'socks.', 'org.gradle.', 'groovy.', 'loader.',
            'spring.config.', 'spring.profiles.', 'spring.main.', 'spring.application.', 'spring.devtools.', 'spring.cloud.',
            'server.ssl.', 'grails.config.', 'grails.project.',
    ].asImmutable()

    private static final Pattern OPTION_NAME = Pattern.compile('[a-z][a-z0-9]*(-[a-z0-9]+)*')

    private static final Pattern PROPERTY = Pattern.compile('[A-Za-z][A-Za-z0-9_-]*(\\.[A-Za-z0-9_-]+)+')

    private static final Pattern VALUE = Pattern.compile('[A-Za-z0-9][A-Za-z0-9._-]*')

    private static final String NOT_IN_DESCRIPTION = '$%@'

    private CommandOptions() {
    }

    /**
     * @param name the name of an option, without its hyphens
     * @return whether it can be the long name of an option: lower case letters and digits in words
     * joined by single hyphens, starting with a letter, of {@link #MIN_NAME_LENGTH} to
     * {@link #MAX_NAME_LENGTH} characters
     */
    static boolean isOptionName(String name) {
        name != null && name.length() >= MIN_NAME_LENGTH && name.length() <= MAX_NAME_LENGTH && OPTION_NAME.matcher(name).matches()
    }

    /**
     * @param property the name of a property
     * @return whether an option can set it: it is two or more parts of ASCII letters, digits, hyphens
     * and underscores joined by dots, the first starting with a letter, of at most
     * {@link #MAX_PROPERTY_LENGTH} characters, and it is none of {@link #DENIED_PROPERTIES} and starts
     * with none of {@link #DENIED_PROPERTY_PREFIXES}
     */
    static boolean isProperty(String property) {
        if (property == null || property.length() > MAX_PROPERTY_LENGTH || !PROPERTY.matcher(property).matches()) {
            return false
        }
        // as the application binds a name: without case, and without the hyphens and underscores of its words
        String bound = property.toLowerCase(Locale.ROOT).replace('-', '').replace('_', '')
        !DENIED_PROPERTIES.contains(bound) && !DENIED_PROPERTY_PREFIXES.any { String prefix -> bound.startsWith(prefix) }
    }

    /**
     * @param value what a switch sets its property to
     * @return whether it is a value a declaration can name: ASCII letters, digits, dots, hyphens and
     * underscores, starting with a letter or a digit, at most {@link #MAX_VALUE_LENGTH} characters
     */
    static boolean isValue(String value) {
        value != null && value.length() <= MAX_VALUE_LENGTH && VALUE.matcher(value).matches()
    }

    /**
     * @param description the description of an option
     * @return whether it is one line that can be shown in the help of a command: not blank, at most
     * {@link #MAX_DESCRIPTION_LENGTH} characters, none of them {@code $}, {@code %} or {@code @},
     * which the help reads as its own, or a character {@link CommandDescriptor#isDescription} refuses
     */
    static boolean isDescription(String description) {
        if (description == null || description.trim().isEmpty() || description.length() > MAX_DESCRIPTION_LENGTH ||
                !CommandDescriptor.isDescription(description)) {
            return false
        }
        for (int index = 0; index < description.length(); index++) {
            if (NOT_IN_DESCRIPTION.indexOf((int) description.charAt(index)) >= 0) {
                return false
            }
        }
        true
    }

    /**
     * Says why the resource of an element of the class path cannot be used. The message finishes the
     * sentence "its declarations ..." and repeats nothing of the resource.
     */
    static final class Refused extends Exception {

        Refused(String message) {
            super(message)
        }
    }

    /**
     * One option as it is declared.
     */
    static final class Declared {

        /** The command the option is one of, one of {@link CommandOptions#COMMANDS}. */
        final String command

        /** The long name of the option, without its hyphens. */
        final String name

        /** The description of the option. */
        final String description

        /** The type of the option, one of {@link CommandOptions#TYPES}. */
        final String type

        /** The property of the application the option sets. */
        final String property

        /** What a switch sets the property to; {@code null} for an option that takes a value. */
        final String value

        Declared(String command, String name, String description, String type, String property, String value) {
            this.command = command
            this.name = name
            this.description = description
            this.type = type
            this.property = property
            this.value = value
        }

        /**
         * @return whether another declaration says the same of the option
         */
        boolean sameAs(Declared other) {
            command == other.command && name == other.name && description == other.description && type == other.type &&
                    property == other.property && value == other.value
        }
    }

    /**
     * What a resource declares: the options that are as the format has them, and why each of the
     * others is not used.
     */
    static final class Read {

        /** The options, in the order of the resource. */
        final List<Declared> options = []

        /**
         * Why an option is not used, by its place in the resource, counted from 1. Each reason
         * finishes the sentence "it ..." and repeats nothing of the resource.
         */
        final Map<Integer, String> refused = new LinkedHashMap<Integer, String>()

        /** How many options the resource declares beyond {@link CommandOptions#MAX_OPTIONS_PER_ORIGIN}, which are not looked at. */
        int more
    }

    /**
     * Reads the declarations of one element of the class path: every rule of the format is checked,
     * and anything else is a {@link Refused} or a refused option, never another exception.
     *
     * @param bytes the content of the resource
     * @return what it declares
     * @throws Refused when it is not a resource of format 1
     */
    static Read read(byte[] bytes) throws Refused {
        if (bytes.length > MAX_BYTES) {
            throw new Refused("have more than ${MAX_BYTES} bytes")
        }
        Object parsed
        try {
            parsed = StrictJson.parse(bytes, MAX_DEPTH)
        } catch (StrictJson.Refused ignored) {
            throw new Refused("are not JSON in UTF-8 as the format has it (strict JSON, nested at most ${MAX_DEPTH} deep)")
        }
        if (!(parsed instanceof Map)) {
            throw new Refused('are not a JSON object')
        }
        Map document = (Map) parsed
        Object format = document.get('format')
        if (!(format instanceof StrictJson.Numeral) || !((StrictJson.Numeral) format).isInteger(FORMAT)) {
            throw new Refused("are not of format ${FORMAT}, the one this version of Grails reads")
        }
        Object options = document.get('options')
        if (!(options instanceof List)) {
            throw new Refused('have no array of options')
        }
        Read read = new Read()
        int place = 0
        for (Object each : (List) options) {
            place++
            if (place > MAX_OPTIONS_PER_ORIGIN) {
                read.more = ((List) options).size() - MAX_OPTIONS_PER_ORIGIN
                break
            }
            String why = each instanceof Map ? refusalOf((Map) each) : 'is not a JSON object'
            if (why != null) {
                read.refused.put(place, why)
                continue
            }
            Map option = (Map) each
            String type = option.get('type') == null ? TYPE_BOOLEAN : (String) option.get('type')
            String value = type == TYPE_BOOLEAN ? (option.get('value') == null ? DEFAULT_VALUE : (String) option.get('value')) : null
            read.options << new Declared((String) option.get('command'), (String) option.get('name'), (String) option.get('description'),
                    type, (String) option.get('property'), value)
        }
        read
    }

    /**
     * @param option the members of one declared option, as they were read
     * @return why the option is not used, as the end of a sentence that starts with the option and
     * repeats nothing of it but a word of a fixed set; {@code null} when it is as the format has it
     */
    static String refusalOf(Map option) {
        Object command = option.get('command')
        if (!(command instanceof String) || !COMMANDS.contains(command)) {
            return command instanceof String && CommandDescriptor.isCommandName((String) command) ?
                    "is for the command ${command}, which takes no declared options in this version of Grails (${COMMANDS.join(', ')} does)".toString() :
                    "names no command that takes declared options (${COMMANDS.join(', ')})".toString()
        }
        Object name = option.get('name')
        if (!(name instanceof String) || !isOptionName((String) name)) {
            return 'has a name that cannot be the name of an option (lower case letters and digits, in words joined by single hyphens, ' +
                    "without the hyphens in front, ${MIN_NAME_LENGTH} to ${MAX_NAME_LENGTH} characters)"
        }
        Object description = option.get('description')
        if (!(description instanceof String) || !isDescription((String) description)) {
            return "has no description that is one line of at most ${MAX_DESCRIPTION_LENGTH} characters that can be shown, without \$, % and @"
        }
        Object type = option.get('type')
        if (type != null && !TYPES.contains(type)) {
            return "has a type that is none of ${TYPES.join(', ')}"
        }
        Object property = option.get('property')
        if (!(property instanceof String) || !isProperty((String) property)) {
            return 'sets no property an option can set: a name of two or more parts joined by dots, of letters, digits, hyphens and underscores, ' +
                    "at most ${MAX_PROPERTY_LENGTH} characters, that is not a property of the JVM, of the build, or one that says which environment, " +
                    'configuration, profiles or classes the application is started with'
        }
        Object value = option.get('value')
        if (type == TYPE_STRING) {
            return value == null ? null : 'takes a value on the command line, and declares one as well, which only a switch does'
        }
        if (value != null && !(value instanceof String && isValue((String) value))) {
            return "sets a value that is not at most ${MAX_VALUE_LENGTH} letters, digits, dots, hyphens and underscores, as a string"
        }
        null
    }
}
