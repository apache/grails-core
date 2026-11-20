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
 * The descriptor of one application command: a small JSON resource beside the registration of the
 * command in {@code META-INF/grails-cli.factories}, from which a build lists the command without
 * loading its class.
 *
 * <p>There is one resource for each command class, {@code META-INF/grails-commands/<class name>.json},
 * so that compiling one class again writes one file and nothing has to be merged. Format 1 is one
 * JSON object with the members {@code format} (the number 1), {@code class} (the name of the command
 * class, which is the name of the resource), {@code name} (what is typed), and optionally
 * {@code description} (one line) and {@code target} ({@code application}). A reader ignores a member
 * it does not know, and reads a descriptor only for a class the factories file of the same jar or
 * directory registers.</p>
 *
 * <p>This class holds what the writer of a descriptor (the compiler, in {@code grails-core-cli}) and
 * its reader (the Grails Gradle plugin) have to agree on. It writes; it does not read.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class CommandDescriptor {

    /** The format this class writes. */
    public static final int FORMAT = 1

    /** The directory of a jar, or of a classes directory, the descriptors are in. */
    public static final String DIRECTORY = 'META-INF/grails-commands'

    /** What the name of a descriptor ends with. */
    public static final String EXTENSION = '.json'

    /** The execution target of a command that runs with the application context. */
    public static final String TARGET_APPLICATION = 'application'

    /** The execution targets a descriptor can name. */
    public static final List<String> TARGETS = ['console', 'standalone', 'application', 'gradle', 'session'].asImmutable()

    /** The most bytes a descriptor has. */
    public static final int MAX_BYTES = 16 * 1024

    /** The most characters the name of a command has. */
    public static final int MAX_NAME_LENGTH = 64

    /** The most characters the description of a command has. */
    public static final int MAX_DESCRIPTION_LENGTH = 512

    /** The most characters the name of a command class has. */
    public static final int MAX_CLASS_NAME_LENGTH = 255

    private static final Pattern COMMAND_NAME = Pattern.compile('[a-z][a-z0-9]*(-[a-z0-9]+)*')

    private static final Pattern CLASS_NAME = Pattern.compile('[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*')

    private static final String COMMAND_SUFFIX = 'Command'

    private static final char[] HEX = '0123456789abcdef'.toCharArray()

    private CommandDescriptor() {
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
     * The name a command has when its class does not say otherwise, as
     * {@code ApplicationCommand.getName()} works it out: {@code UrlMappingsReportCommand} is
     * {@code url-mappings-report}.
     *
     * @param className the name of the command class
     * @return the name, which is not necessarily one {@link #isCommandName} accepts
     */
    static String conventionName(String className) {
        GrailsNameUtils.getScriptName(GrailsNameUtils.getLogicalName(className, COMMAND_SUFFIX))
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
     * Writes a descriptor of format 1. The same values give the same bytes: the members are in a
     * fixed order and everything outside printable ASCII is escaped.
     *
     * @param className the name of the command class
     * @param name what is typed for the command
     * @param description the description of the command, or {@code null} when it is not known
     * @return the JSON document, in ASCII, with a line end
     */
    static String toJson(String className, String name, String description) {
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
        json.append(',"target":')
        appendString(json, TARGET_APPLICATION)
        json.append('}\n')
        json.toString()
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
