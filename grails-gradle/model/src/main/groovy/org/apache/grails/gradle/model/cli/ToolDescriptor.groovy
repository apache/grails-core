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

import grails.util.GrailsNameUtils

/**
 * The descriptor of one tool of an application or a plugin: a small JSON resource beside the
 * registration of the tool in {@code META-INF/grails-cli.factories}, from which a build lists the
 * tool without loading its class.
 *
 * <p>There is one resource for each tool class, {@code META-INF/grails-tools/<class name>.json},
 * so that compiling one class again writes one file and nothing has to be merged. Format 1 is one
 * JSON object with these members:</p>
 * <ul>
 *   <li>{@code format}, the number 1;</li>
 *   <li>{@code class}, the name of the tool class, which is the name of the resource;</li>
 *   <li>{@code name}, what is typed;</li>
 *   <li>{@code description}, optionally, one line;</li>
 *   <li>{@code target}, optionally: {@code application} for a tool the build runs, which is also what
 *       a descriptor without the member means, or {@code standalone} for one that is run as a process
 *       of its own, without the build and without the application;</li>
 *   <li>{@code startsApplication}, optionally and only of a tool of the target {@code application}:
 *       {@code true}, which is also what a descriptor without the member means, when the application
 *       is started around the tool, and {@code false} when the build runs the tool without the
 *       application. A tool of the target {@code standalone} never starts the application, whatever
 *       its descriptor says;</li>
 *   <li>{@code deprecated}, optionally: a list of what is deprecated about how the tool is written,
 *       {@code type} for a tool that implements a deprecated type and {@code folder} for one whose
 *       source is in a deprecated folder.</li>
 * </ul>
 *
 * <p>A reader ignores a member it does not know, and reads a descriptor only for a class the
 * factories file of the same jar or directory registers.</p>
 *
 * <p>This class holds what the writer of a descriptor (the compiler, in {@code grails-core-cli}) and
 * its reader (the catalog of the Grails Gradle plugin) have to agree on, the writing and the reading.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class ToolDescriptor {

    /** The format this class writes. */
    public static final int FORMAT = 1

    /** The directory of a jar, or of a classes directory, the descriptors are in. */
    public static final String DIRECTORY = 'META-INF/grails-tools'

    /** What the name of a descriptor ends with. */
    public static final String EXTENSION = '.json'

    /**
     * The key of {@code META-INF/grails-cli.factories} a tool class is registered under: the name of
     * the type every tool is of.
     */
    public static final String FACTORIES_KEY_TOOL = 'org.apache.grails.core.cli.GrailsTool'

    /**
     * The key of {@code META-INF/grails-cli.factories} the companions of Grails 8 register their
     * classes under, which is read as {@link #FACTORIES_KEY_TOOL} is, and no longer written.
     */
    public static final String FACTORIES_KEY_COMMAND = 'org.apache.grails.core.cli.ApplicationCommand'

    /** The keys a tool class can be registered under, in the order they are read. */
    public static final List<String> FACTORIES_KEYS = [FACTORIES_KEY_TOOL, FACTORIES_KEY_COMMAND].asImmutable()

    /** The member that says how a tool is run. */
    public static final String MEMBER_TARGET = 'target'

    /** The member that says whether the application is started around a tool the build runs. */
    public static final String MEMBER_STARTS_APPLICATION = 'startsApplication'

    /** The member that lists what is deprecated about how a tool is written. */
    public static final String MEMBER_DEPRECATED = 'deprecated'

    /**
     * The execution target of a tool the build runs: with the application started around it, or,
     * when it says that it does not start the application, with the class path of the project and
     * its directories and nothing else.
     */
    public static final String TARGET_APPLICATION = 'application'

    /**
     * The execution target of a tool that is run as a process of its own, by the command line,
     * without the build and without the application. A build that is asked to run it runs it
     * without the application.
     */
    public static final String TARGET_STANDALONE = 'standalone'

    /** The execution targets of the tools of a project, which are the ones a descriptor is read for. */
    public static final List<String> RUN_TARGETS = [TARGET_APPLICATION, TARGET_STANDALONE].asImmutable()

    /** The execution targets a descriptor can name. */
    public static final List<String> TARGETS = ['console', 'standalone', 'application', 'session'].asImmutable()

    /** What is deprecated about a tool that implements a deprecated type. */
    public static final String DEPRECATED_TYPE = 'type'

    /** What is deprecated about a tool whose source is in a deprecated folder. */
    public static final String DEPRECATED_FOLDER = 'folder'

    /** Everything that can be deprecated about how a tool is written, in the order it is written down. */
    public static final List<String> DEPRECATIONS = [DEPRECATED_TYPE, DEPRECATED_FOLDER].asImmutable()

    /** The most bytes a descriptor has. */
    public static final int MAX_BYTES = 16 * 1024

    /** How deep the arrays and objects of a descriptor go at most. */
    public static final int MAX_DEPTH = 4

    /** The most characters the name of a tool has. */
    public static final int MAX_NAME_LENGTH = 64

    /** The most characters the description of a tool has. */
    public static final int MAX_DESCRIPTION_LENGTH = 512

    /** The most characters the name of a tool class has. */
    public static final int MAX_CLASS_NAME_LENGTH = 255

    private static final Pattern COMMAND_NAME = Pattern.compile('[a-z][a-z0-9]*(-[a-z0-9]+)*')

    private static final Pattern CLASS_NAME = Pattern.compile('[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*')

    private static final String TOOL_SUFFIX = 'Tool'

    private static final String COMMAND_SUFFIX = 'Command'

    private static final char[] HEX = '0123456789abcdef'.toCharArray()

    private ToolDescriptor() {
    }

    /**
     * @param name what would be typed for a command
     * @return whether it can be the name of a command: lower case letters and digits in words joined
     * by single hyphens, starting with a letter, at most {@link #MAX_NAME_LENGTH} characters
     */
    static boolean isCommandName(String name) {
        name != null && name.length() <= MAX_NAME_LENGTH && COMMAND_NAME.matcher(name).matches()
    }

    /**
     * @param className the name of a class as a factories file has it
     * @return whether it is a class name a descriptor can be looked up for: ASCII letters, digits,
     * {@code _} and {@code $} in parts joined by dots, at most {@link #MAX_CLASS_NAME_LENGTH} characters
     */
    static boolean isClassName(String className) {
        className != null && className.length() <= MAX_CLASS_NAME_LENGTH && CLASS_NAME.matcher(className).matches()
    }

    /**
     * @param description the description of a command
     * @return whether it is one line that can be shown: at most {@link #MAX_DESCRIPTION_LENGTH}
     * characters, none of them a control or format character, a line separator or unassigned
     */
    static boolean isDescription(String description) {
        if (description == null || description.length() > MAX_DESCRIPTION_LENGTH) {
            return false
        }
        int index = 0
        while (index < description.length()) {
            int codePoint = description.codePointAt(index)
            int type = Character.getType(codePoint)
            if (type == Character.CONTROL || type == Character.FORMAT || type == Character.LINE_SEPARATOR ||
                    type == Character.PARAGRAPH_SEPARATOR || type == Character.SURROGATE ||
                    type == Character.PRIVATE_USE || type == Character.UNASSIGNED) {
                return false
            }
            index += Character.charCount(codePoint)
        }
        true
    }

    /**
     * The name a tool has when its class does not say otherwise, as {@code GrailsTool.getName()}
     * works it out: {@code CreateReportTool} is {@code create-report}. A class whose name does not
     * end with {@code Tool} is named without the ending {@code Command}, so that
     * {@code UrlMappingsReportCommand} is {@code url-mappings-report}.
     *
     * @param className the name of the tool class
     * @return the name, which is not necessarily one {@link #isCommandName} accepts
     */
    static String conventionName(String className) {
        String suffix = GrailsNameUtils.getShortName(className).endsWith(TOOL_SUFFIX) ? TOOL_SUFFIX : COMMAND_SUFFIX
        GrailsNameUtils.getScriptName(GrailsNameUtils.getLogicalName(className, suffix))
    }

    /**
     * @param className the name of a command class
     * @return where its descriptor is in a jar or a classes directory, or {@code null} for a name
     * {@link #isClassName} does not accept, which has no descriptor
     */
    static String location(String className) {
        isClassName(className) ? "${DIRECTORY}/${className}${EXTENSION}" as String : null
    }

    /**
     * Says why a descriptor cannot be used. The message finishes the sentence "its descriptor ..."
     * and repeats nothing of the descriptor, unless it is one of a fixed set of words.
     */
    static final class Refused extends Exception {

        Refused(String message) {
            super(message)
        }
    }

    /**
     * What a descriptor says of a tool.
     */
    static final class Described {

        /** What is typed for the tool. */
        final String name

        /** The description of the tool, or {@code null} when the descriptor has none. */
        final String description

        /** The execution target of the tool, one of {@link ToolDescriptor#RUN_TARGETS}. */
        final String target

        /**
         * Whether the application is started around the tool when it is run: never for a tool of the
         * target {@link ToolDescriptor#TARGET_STANDALONE}.
         */
        final boolean startsApplication

        /**
         * What is deprecated about how the tool is written, of {@link ToolDescriptor#DEPRECATIONS} and
         * in that order; never {@code null}, and not to be changed.
         */
        final List<String> deprecated

        Described(String name, String description) {
            this(name, description, TARGET_APPLICATION, true, Collections.<String> emptyList())
        }

        Described(String name, String description, String target, boolean startsApplication, List<String> deprecated) {
            this.name = name
            this.description = description
            this.target = target
            this.startsApplication = target == TARGET_APPLICATION && startsApplication
            this.deprecated = Collections.unmodifiableList(inOrder(deprecated))
        }
    }

    /**
     * Reads the descriptor of a class, which is something a dependency wrote: every rule of the
     * format is checked, and anything else is a {@link Refused}, never another exception.
     *
     * @param className the class the descriptor was found for
     * @param bytes the content of the descriptor
     * @return what it says
     * @throws Refused when it is not a descriptor of format 1 for that class that this version can use
     */
    static Described read(String className, byte[] bytes) throws Refused {
        if (bytes.length > MAX_BYTES) {
            throw new Refused("has more than ${MAX_BYTES} bytes")
        }
        Object parsed
        try {
            parsed = StrictJson.parse(bytes, MAX_DEPTH)
        } catch (StrictJson.Refused ignored) {
            throw new Refused("is not JSON in UTF-8 as the descriptor format has it (strict JSON, nested at most ${MAX_DEPTH} deep)")
        }
        if (!(parsed instanceof Map)) {
            throw new Refused('is not a JSON object')
        }
        Map descriptor = (Map) parsed
        Object format = descriptor.get('format')
        if (!(format instanceof StrictJson.Numeral) || !((StrictJson.Numeral) format).isInteger(FORMAT)) {
            throw new Refused("is not of format ${FORMAT}, the one this version of Grails reads")
        }
        if (descriptor.get('class') != className) {
            throw new Refused('is the descriptor of another class')
        }
        Object name = descriptor.get('name')
        if (!(name instanceof String) || !isCommandName((String) name)) {
            throw new Refused('has a name that cannot be the name of a command (lower case letters and digits, ' +
                    "in words joined by single hyphens, at most ${MAX_NAME_LENGTH} characters)")
        }
        Object description = descriptor.get('description')
        if (description != null && !(description instanceof String && isDescription((String) description))) {
            throw new Refused("has a description that is not one line of at most ${MAX_DESCRIPTION_LENGTH} characters that can be shown")
        }
        Object target = descriptor.get(MEMBER_TARGET)
        if (target != null && !(target instanceof String && RUN_TARGETS.contains(target))) {
            String why = target instanceof String && TARGETS.contains(target) ?
                    "is for the execution target ${target}, which this version of Grails cannot run".toString() :
                    'has an execution target that does not exist'
            throw new Refused(why)
        }
        Object startsApplication = descriptor.get(MEMBER_STARTS_APPLICATION)
        if (startsApplication != null && !(startsApplication instanceof Boolean)) {
            throw new Refused("has a ${MEMBER_STARTS_APPLICATION} that is neither true nor false")
        }
        Object deprecated = descriptor.get(MEMBER_DEPRECATED)
        if (deprecated != null && !(deprecated instanceof List && DEPRECATIONS.containsAll((List) deprecated))) {
            throw new Refused("has a ${MEMBER_DEPRECATED} that is not a list of ${DEPRECATIONS.join(' and ')}")
        }
        new Described((String) name, (String) description, target == null ? TARGET_APPLICATION : (String) target,
                startsApplication == null || (Boolean) startsApplication,
                deprecated == null ? Collections.<String> emptyList() : (List<String>) deprecated)
    }

    /**
     * Writes the descriptor of a tool the build runs with the application started around it, with
     * nothing deprecated about it, when {@link #read} would take it, so that nothing is written that
     * is then refused: no descriptor for a class name or a name the format does not have, and no
     * description that is not one line that can be shown.
     *
     * @param className the name of the tool class
     * @param name what is typed for the tool
     * @param description the description of the tool, or {@code null} when it is not known
     * @return the JSON document, or {@code null} when the tool cannot be described
     */
    static String describe(String className, String name, String description) {
        describe(className, name, description, TARGET_APPLICATION, true, Collections.<String> emptyList())
    }

    /**
     * Writes the descriptor of a tool, when {@link #read} would take it.
     *
     * @param className the name of the tool class
     * @param name what is typed for the tool
     * @param description the description of the tool, or {@code null} when it is not known
     * @param target the execution target of the tool, one of {@link #RUN_TARGETS}
     * @param startsApplication whether the application is started around the tool, which is written
     * for the target {@link #TARGET_APPLICATION} and for no other
     * @param deprecated what is deprecated about how the tool is written, of {@link #DEPRECATIONS};
     * {@code null} is nothing
     * @return the JSON document, or {@code null} when the tool cannot be described
     */
    static String describe(String className, String name, String description, String target, boolean startsApplication, List<String> deprecated) {
        if (!isClassName(className) || !isCommandName(name) || !RUN_TARGETS.contains(target) ||
                (deprecated != null && !DEPRECATIONS.containsAll(deprecated))) {
            return null
        }
        toJson(className, name, description != null && isDescription(description) ? description : null, target, startsApplication, deprecated)
    }

    /**
     * Writes a descriptor of format 1 of a tool the build runs with the application started around
     * it, with nothing deprecated about it. The same values give the same bytes: the members are in a
     * fixed order and everything outside printable ASCII is escaped.
     *
     * @param className the name of the tool class
     * @param name what is typed for the tool
     * @param description the description of the tool, or {@code null} when it is not known
     * @return the JSON document, in ASCII, with a line end
     */
    static String toJson(String className, String name, String description) {
        toJson(className, name, description, TARGET_APPLICATION, true, Collections.<String> emptyList())
    }

    /**
     * Writes a descriptor of format 1, whatever {@link #read} makes of it.
     *
     * @param className the name of the tool class
     * @param name what is typed for the tool
     * @param description the description of the tool, or {@code null} when it is not known
     * @param target the execution target of the tool
     * @param startsApplication whether the application is started around the tool, which is written
     * for the target {@link #TARGET_APPLICATION} and for no other
     * @param deprecated what is deprecated about how the tool is written; {@code null} and an empty
     * list are not written
     * @return the JSON document, in ASCII, with a line end
     */
    static String toJson(String className, String name, String description, String target, boolean startsApplication, List<String> deprecated) {
        StringBuilder json = new StringBuilder()
        json.append('{"format":').append(FORMAT)
        json.append(',"class":')
        appendString(json, className)
        json.append(',"name":')
        appendString(json, name)
        if (description != null) {
            json.append(',"description":')
            appendString(json, description)
        }
        json.append(',"').append(MEMBER_TARGET).append('":')
        appendString(json, target)
        if (target == TARGET_APPLICATION) {
            json.append(',"').append(MEMBER_STARTS_APPLICATION).append('":').append(startsApplication)
        }
        List<String> ordered = inOrder(deprecated)
        if (!ordered.isEmpty()) {
            json.append(',"').append(MEMBER_DEPRECATED).append('":[')
            for (int index = 0; index < ordered.size(); index++) {
                if (index > 0) {
                    json.append(',')
                }
                appendString(json, ordered[index])
            }
            json.append(']')
        }
        json.append('}\n')
        json.toString()
    }

    // each thing once: what the format knows first, in its order, then anything else as it was given
    private static List<String> inOrder(List<String> deprecated) {
        List<String> ordered = new ArrayList<String>()
        if (deprecated == null) {
            return ordered
        }
        for (String known : DEPRECATIONS) {
            if (deprecated.contains(known)) {
                ordered << known
            }
        }
        for (String other : deprecated) {
            if (!ordered.contains(other)) {
                ordered << other
            }
        }
        ordered
    }

    private static void appendString(StringBuilder json, String value) {
        json.append('"')
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index)
            if (character == ('"' as char) || character == ('\\' as char)) {
                json.append('\\').append(character)
            } else if (character < (0x20 as char) || character > (0x7e as char)) {
                int code = (int) character
                json.append('\\u')
                        .append(HEX[(code >> 12) & 0xf]).append(HEX[(code >> 8) & 0xf])
                        .append(HEX[(code >> 4) & 0xf]).append(HEX[code & 0xf])
            } else {
                json.append(character)
            }
        }
        json.append('"')
    }
}
