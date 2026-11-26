/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package grails.ui.command

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import groovy.json.JsonException
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic

/**
 * The arguments a command JVM is started with, where the Grails Gradle plugin hands it the file of the
 * {@code argsFile} project property by its path, in the system property {@link #ARGS_FILE_SYSTEM_PROPERTY},
 * so the arguments in the file, a password among them, are on neither the JVM's command line nor in the
 * configuration cache of the build.
 *
 * @since 9.0
 */
@CompileStatic
final class CommandArguments {

    /** The system property naming a JSON file whose array of strings follows the other arguments */
    static final String ARGS_FILE_SYSTEM_PROPERTY = 'grails.cli.argsFile'

    private CommandArguments() {
    }

    /**
     * Returns the arguments with those of the file {@link #ARGS_FILE_SYSTEM_PROPERTY} names, when it names
     * one, put before the last argument, the application class. The system properties the file sets with
     * {@code -Dname=value} are set as the file is read, so the command runs in the environment its own
     * arguments name, also when the file changed since the build read it.
     *
     * @param args the arguments the JVM was started with
     * @return the arguments of the command
     * @throws IllegalArgumentException when the file cannot be read or does not hold one JSON array of strings
     */
    static String[] expand(String[] args) {
        String file = System.getProperty(ARGS_FILE_SYSTEM_PROPERTY)
        if (!file) {
            return args
        }
        // the application need not see it
        System.clearProperty(ARGS_FILE_SYSTEM_PROPERTY)
        List<String> fromFile = read(Paths.get(file))
        systemProperties(fromFile).each { String name, String value -> System.setProperty(name, value) }
        List<String> expanded = args.toList()
        expanded.addAll(Math.max(expanded.size() - 1, 0), fromFile)
        expanded as String[]
    }

    /**
     * Returns the words that are not system properties, {@code -Dname=value}, which the JVM of the
     * command was started with, as the command line parser reads them.
     *
     * @param words the words of a command line
     * @return the other words
     */
    static List<String> withoutSystemProperties(List<String> words) {
        words.findAll { String word -> systemProperty(word) == null }
    }

    private static Map<String, String> systemProperties(List<String> words) {
        Map<String, String> properties = [:]
        for (String word : words) {
            Map.Entry<String, String> property = systemProperty(word)
            if (property != null) {
                properties[property.key] = property.value
            }
        }
        properties
    }

    /** The property a word sets, as the command line parser reads it: trimmed, without the quotes around it */
    private static Map.Entry<String, String> systemProperty(String word) {
        String trimmed = word?.trim() ?: ''
        if (trimmed.length() > 1 && trimmed.startsWith('"') && trimmed.endsWith('"')) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim()
        }
        int equals = trimmed.indexOf('=')
        if (!trimmed.startsWith('-D') || equals < 3) {
            return null
        }
        new AbstractMap.SimpleImmutableEntry<String, String>(trimmed.substring(2, equals), trimmed.substring(equals + 1))
    }

    private static List<String> read(Path file) {
        String content
        try {
            content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Could not read the arguments of the command from ${file}: ${e.message}", e)
        }
        Object parsed
        try {
            parsed = content.trim() ? new JsonSlurper().parseText(content) : null
        }
        catch (JsonException | IllegalArgumentException ignored) {
            // the parser's message quotes the file, which can hold a password, so it is left out
            parsed = null
        }
        if (!(parsed instanceof List) || ((List) parsed).any { !(it instanceof String) } || !onlyWhitespaceAfterArray(content)) {
            throw new IllegalArgumentException("The arguments file ${file} does not hold one JSON array of strings")
        }
        (List<String>) parsed
    }

    /** Whether only white space follows the array the text starts with: its brackets are matched outside strings */
    private static boolean onlyWhitespaceAfterArray(String content) {
        int depth = 0
        boolean inString = false
        for (int i = content.indexOf('['); i >= 0 && i < content.length(); i++) {
            char c = content.charAt(i)
            if (inString) {
                if (c == ('\\' as char)) {
                    i++
                }
                else if (c == ('"' as char)) {
                    inString = false
                }
            }
            else if (c == ('"' as char)) {
                inString = true
            }
            else if (c == ('[' as char) || c == ('{' as char)) {
                depth++
            }
            else if (c == (']' as char) || c == ('}' as char)) {
                depth--
                if (depth == 0) {
                    return content.substring(i + 1).trim().isEmpty()
                }
            }
        }
        false
    }
}
