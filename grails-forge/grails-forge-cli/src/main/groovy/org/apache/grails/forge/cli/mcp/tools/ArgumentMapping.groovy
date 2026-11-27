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
package org.apache.grails.forge.cli.mcp.tools

import java.nio.charset.StandardCharsets

import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.CommandCatalog
import org.apache.grails.forge.cli.mcp.McpException
import org.apache.grails.forge.cli.mcp.McpTool

/**
 * Turns the arguments of a call of a tool of {@code grails mcp} into the command line the call runs,
 * after holding them to the tool's input schema. Nothing runs before they are: a call with an argument
 * the schema does not have, one it requires and that is not given, a value of the wrong type or not
 * among the values the schema allows, is refused with a message that names the argument.
 *
 * <p>The command line is the words of the command, the options its policy gives on every call, the
 * options of the call in the order of the schema, and then its parameters in their order:</p>
 * <ul>
 * <li>a switch that is {@code true} is {@code --name}; one that is {@code false} gives nothing;</li>
 * <li>a value is one argument, {@code --name=value}, so that a value that begins with {@code -} is never
 * read as an option, and each value of an array is one such argument;</li>
 * <li>a key with a value is {@code -Dkey=value}; a key is not empty and has no {@code =};</li>
 * <li>a parameter is one argument after the options, and one that begins with {@code -} is refused, so
 * that it is never read as an option, except where it is another program's argument: the arguments of a
 * tool that declares none, and those {@code run-tool} gives a tool.</li>
 * </ul>
 *
 * <p>Besides, every argument that begins with {@code @} is given with a second {@code @} in front of it,
 * which the command line reads as one {@code @}: it would otherwise read it as the name of a file whose
 * lines are arguments, and put whatever file the user can read into the call and its answer. A value
 * with the character U+0000, or with half of a character of two code units, is refused, and so is a call
 * that comes to more than {@value #MAX_ARGUMENTS} arguments or more than {@value #MAX_BYTES} bytes. A
 * value that names a feature, and has {@code :} or {@code #} in it, names a plugin by its coordinates:
 * its feature pack is code, and is refused over MCP.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class ArgumentMapping implements McpTool.Mapping {

    /** The most arguments a call comes to. */
    public static final int MAX_ARGUMENTS = 4096

    /** The most bytes, in UTF-8, the arguments of a call come to. */
    public static final int MAX_BYTES = 1024 * 1024

    /** What the command line reads as the name of a file of arguments, and as itself when there are two. */
    public static final String AT = '@'

    private final String tool
    private final List<String> command
    private final InputSchemas.Schema schema
    private final CommandPolicy.JsonForm json

    /**
     * @param tool the name of the tool, for the messages
     * @param command the words of the command and the options given on every call
     * @param schema the input schema of the tool
     * @param json whether the command writes a JSON document
     */
    ArgumentMapping(String tool, List<String> command, InputSchemas.Schema schema, CommandPolicy.JsonForm json) {
        this.tool = tool
        this.command = Collections.unmodifiableList(new ArrayList<String>(command))
        this.schema = schema
        this.json = json
    }

    /**
     * @return the input schema the arguments are held to
     */
    InputSchemas.Schema getSchema() {
        return schema
    }

    @Override
    List<String> commandLine(Map<String, Object> arguments) throws McpException {
        for (String given : arguments.keySet()) {
            if (schema.property(given) == null) {
                throw refused(given, "${tool} has no such argument; ${schema.properties.isEmpty() ? 'it takes none' : 'it takes ' + schema.properties*.name.join(', ')}")
            }
        }
        List<String> line = new ArrayList<>(command)
        List<String> parameters = []
        for (InputSchemas.Property property : schema.properties) {
            if (!arguments.containsKey(property.name)) {
                if (property.required) {
                    throw refused(property.name, 'it is required, and was not given')
                }
                continue
            }
            Object value = arguments.get(property.name)
            switch (property.kind) {
                case InputSchemas.Property.Kind.SWITCH:
                    if (!(value instanceof Boolean)) {
                        throw refused(property.name, 'true or false is expected')
                    }
                    if (value == Boolean.TRUE) {
                        line.add(property.option)
                    }
                    break
                case InputSchemas.Property.Kind.VALUE:
                    line.add("${property.option}=${scalar(property, value)}".toString())
                    break
                case InputSchemas.Property.Kind.VALUES:
                    for (Object each : list(property, value)) {
                        line.add("${property.option}=${scalar(property, each)}".toString())
                    }
                    break
                case InputSchemas.Property.Kind.ENTRIES:
                    line.addAll(entries(property, value))
                    break
                case InputSchemas.Property.Kind.PARAMETER:
                    parameters.add(parameter(property, value))
                    break
                case InputSchemas.Property.Kind.PARAMETERS:
                case InputSchemas.Property.Kind.ARGUMENTS:
                    for (Object each : list(property, value)) {
                        parameters.add(parameter(property, each))
                    }
                    break
            }
        }
        if (json == CommandPolicy.JsonForm.WITH_DRY_RUN && jsonResult(arguments)) {
            line.add(CommandPolicy.JSON)
        }
        line.addAll(parameters)
        List<String> given = line.collect { String argument -> escaped(argument) }
        if (given.size() > MAX_ARGUMENTS) {
            throw new McpException(McpException.INVALID_PARAMS, "The call comes to ${given.size()} arguments of the command line, and grails mcp gives a command ${MAX_ARGUMENTS} at most".toString())
        }
        long bytes = 0L
        for (String argument : given) {
            bytes += argument.getBytes(StandardCharsets.UTF_8).length
        }
        if (bytes > MAX_BYTES) {
            throw new McpException(McpException.INVALID_PARAMS, "The arguments of the call come to ${bytes} bytes, and grails mcp gives a command ${MAX_BYTES} at most".toString())
        }
        return given
    }

    @Override
    boolean jsonResult(Map<String, Object> arguments) {
        switch (json) {
            case CommandPolicy.JsonForm.ALWAYS:
                return true
            case CommandPolicy.JsonForm.WITH_DRY_RUN:
                return arguments.get(CommandPolicy.DRY_RUN) == Boolean.TRUE && arguments.get(CommandPolicy.DIFF) != Boolean.TRUE
            default:
                return false
        }
    }

    /**
     * @param argument an argument of the command line
     * @return the argument as the command line reads it as itself: with a second {@code @} in front of
     * one that begins with {@code @}, which it would otherwise read as the name of a file of arguments
     */
    static String escaped(String argument) {
        return argument.startsWith(AT) ? AT + argument : argument
    }

    private List<Object> list(InputSchemas.Property property, Object value) throws McpException {
        if (!(value instanceof List)) {
            throw refused(property.name, "a list of ${described(property.type)} values is expected")
        }
        List<Object> values = (List<Object>) value
        if (values.size() < property.minItems) {
            throw refused(property.name, "at least ${property.minItems} ${property.minItems == 1 ? 'value is' : 'values are'} expected")
        }
        if (property.maxItems >= 0 && values.size() > property.maxItems) {
            throw refused(property.name, "at most ${property.maxItems} ${property.maxItems == 1 ? 'value is' : 'values are'} expected")
        }
        return values
    }

    private String parameter(InputSchemas.Property property, Object value) throws McpException {
        String text = property.kind == InputSchemas.Property.Kind.ARGUMENTS ? string(property, value) : scalar(property, value)
        if (!property.passedAsIs && text.startsWith('-')) {
            throw refused(property.name, 'a value that begins with - is refused, so that it is never read as an option')
        }
        return text
    }

    private List<String> entries(InputSchemas.Property property, Object value) throws McpException {
        if (!(value instanceof Map)) {
            throw refused(property.name, 'an object of names and their values is expected')
        }
        List<String> given = []
        for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) value).entrySet()) {
            Object key = entry.getKey()
            if (!(key instanceof String) || ((String) key).isEmpty() || ((String) key).contains('=')) {
                throw refused(property.name, 'a name is not empty and has no =')
            }
            text(property, (String) key)
            if (!(entry.getValue() instanceof String)) {
                throw refused(property.name, "the value of ${key} is not a string")
            }
            String each = text(property, (String) entry.getValue())
            // -Dkey=value, or --name=key=value for an option of keys with values that has a long name
            given.add(property.option.startsWith('--') ? "${property.option}=${key}=${each}".toString() : "${property.option}${key}=${each}".toString())
        }
        return given
    }

    // a value of the type of a property, as the command line is given it
    private String scalar(InputSchemas.Property property, Object value) throws McpException {
        if (property.type == CommandCatalog.TYPE_INTEGER) {
            return wholeNumber(property, value)
        }
        if (property.type == CommandCatalog.TYPE_NUMBER) {
            return number(property, value)
        }
        if (property.type == CommandCatalog.TYPE_BOOLEAN) {
            if (!(value instanceof Boolean)) {
                throw refused(property.name, 'true or false is expected')
            }
            return value.toString()
        }
        String text = string(property, value)
        if (property.choices != null && !property.choices.contains(text)) {
            throw refused(property.name, "one of ${property.choices.join(', ')} is expected")
        }
        if (property.featureValued && (text.contains(':') || text.contains('#'))) {
            throw refused(property.name, 'a feature named by the coordinates of a plugin (with : or #) is refused: its feature pack is code, ' +
                    'which grails mcp does not run. Add it at the terminal')
        }
        return text
    }

    private String wholeNumber(InputSchemas.Property property, Object value) throws McpException {
        BigInteger whole = integer(value)
        if (whole == null) {
            throw refused(property.name, 'a whole number is expected')
        }
        if (property.choices != null && !property.choices.any { Object choice -> choice instanceof BigInteger && ((BigInteger) choice) == whole }) {
            throw refused(property.name, "one of ${property.choices.join(', ')} is expected")
        }
        return whole.toString()
    }

    private String number(InputSchemas.Property property, Object value) throws McpException {
        if (!(value instanceof Number) || !finite((Number) value)) {
            throw refused(property.name, 'a number is expected')
        }
        BigDecimal number = new BigDecimal(value.toString()).stripTrailingZeros()
        if (property.choices != null && !property.choices.any { Object choice -> choice instanceof BigDecimal && ((BigDecimal) choice).compareTo(number) == 0 }) {
            throw refused(property.name, "one of ${property.choices.join(', ')} is expected")
        }
        return number.toPlainString()
    }

    private String string(InputSchemas.Property property, Object value) throws McpException {
        if (!(value instanceof String)) {
            throw refused(property.name, 'a string is expected')
        }
        return text(property, (String) value)
    }

    // text no command line carries is refused: U+0000, and half of a character of two code units
    private String text(InputSchemas.Property property, String value) throws McpException {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i)
            if (c == (char) 0) {
                throw refused(property.name, 'the character U+0000 is refused, since no command line carries it')
            }
            if (Character.isHighSurrogate(c) && i + 1 < value.length() && Character.isLowSurrogate(value.charAt(i + 1))) {
                i++
                continue
            }
            if (Character.isSurrogate(c)) {
                throw refused(property.name, 'half of a character of two code units is refused, since it cannot be written as text')
            }
        }
        return value
    }

    private static BigInteger integer(Object value) {
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return BigInteger.valueOf(((Number) value).longValue())
        }
        if (value instanceof BigInteger) {
            return (BigInteger) value
        }
        if (value instanceof Number && finite((Number) value)) {
            try {
                return new BigDecimal(value.toString()).toBigIntegerExact()
            } catch (ArithmeticException ignored) {
                return null
            }
        }
        return null
    }

    private static boolean finite(Number value) {
        return !(value instanceof Double || value instanceof Float) || Double.isFinite(value.doubleValue())
    }

    private static String described(String type) {
        return type == CommandCatalog.TYPE_INTEGER ? 'whole number' : type == CommandCatalog.TYPE_NUMBER ? 'number' : 'string'
    }

    private static McpException refused(String property, CharSequence what) {
        return new McpException(McpException.INVALID_PARAMS, "${property}: ${what}".toString())
    }
}
