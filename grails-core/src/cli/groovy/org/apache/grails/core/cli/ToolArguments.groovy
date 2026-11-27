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
package org.apache.grails.core.cli

import java.lang.annotation.Annotation
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.regex.Pattern

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

import grails.util.Environment
import org.apache.grails.gradle.model.cli.ToolDescriptor
import org.grails.build.parsing.CommandLine
import org.grails.build.parsing.DefaultCommandLine

/**
 * Internal: not an API of Grails, and it may change in any version. The runners of tools use it to
 * give a tool the arguments it declares, before {@code handle} runs.
 *
 * <p>The declarations are read from the class of the tool at run time, the {@link ToolOption} and
 * {@link ToolParameter} on its fields, on the fields of the classes it extends and on its class, and are
 * held to the rules the compiler holds a Groovy tool to; a Java tool, which the compiler does not see,
 * is held to them here.</p>
 *
 * <p>A tool that declares no option and no parameter is given its arguments as it always was: it is not
 * held to anything, and its command line is the one {@code CommandLineParser} makes. A tool that
 * declares one is held to its declarations: an option it does not declare, a value an option needs and
 * is not given, a value of the wrong type, an option given twice that cannot be, a required option or
 * parameter that is missing, and an argument more than its parameters take are refused before
 * {@code handle} runs, with one line ({@link #refusal}) and the exit code {@link #REFUSED}. The forms
 * taken are {@code --name value}, {@code --name=value}, {@code -x value}, {@code -x=value}, switches
 * ({@code --name}, {@code -x}), and {@code --}, after which every argument is a parameter; a negative
 * number, as {@code -5}, is a parameter too. An argument {@code -Dname=value} is a system property of
 * the tool's command line, as it is for every tool. The
 * fields of the declared options and parameters are set to what they were given, and an option that is
 * not given and declares a {@code defaultValue} is set to that; the others keep the value they start
 * with.</p>
 *
 * <p>The command line of a tool that declares is made from the same reading, so that what it says agrees
 * with the declarations: {@code getRawArguments()} is what the tool was given, {@code getRemainingArgs()}
 * are its parameters, and an option that was given has its value, or {@code true} for a switch, under
 * each of its names without the hyphens, as {@code optionValue('since')} and {@code optionValue('s')}.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class ToolArguments {

    /** The exit code of a tool whose arguments are refused, as the command line refuses a command line. */
    static final int REFUSED = 2

    // an option name of the refusal is shown as it was given when it is one of these, and as words otherwise
    private static final Pattern SHOWN = Pattern.compile('-{1,2}[A-Za-z0-9][A-Za-z0-9._-]{0,62}')

    // an argument that is a negative number is a parameter, unless an option has it as its name
    private static final Pattern NEGATIVE_NUMBER = Pattern.compile('-[0-9]+(\\.[0-9]+)?')

    private static final List<Class<?>> WHOLE_NUMBERS = [int, Integer, long, Long] as List<Class<?>>

    private final List<DeclaredOption> options = []

    private final List<DeclaredParameter> parameters = []

    private final Map<String, DeclaredOption> byName = [:]

    /**
     * Why the declarations of the tool break a rule, as a sentence that starts with the declaration;
     * {@code null} when they keep every rule.
     */
    final String broken

    private ToolArguments(Class<?> type) {
        String why
        try {
            why = read(type)
        } catch (NoClassDefFoundError | TypeNotPresentException ignored) {
            // a field of a type that is not on the class path cannot be looked at: the tool is run as one that
            // declares nothing, as a tool was run before it could declare anything
            options.clear()
            parameters.clear()
            byName.clear()
            why = null
        }
        this.broken = why
    }

    /**
     * @param type the class of a tool: of the tool an adapter adapts, for an adapter
     * @return its declarations
     */
    static ToolArguments of(Class<?> type) {
        new ToolArguments(type)
    }

    /**
     * @return whether the tool declares an option or a parameter, and is held to its declarations
     */
    boolean isDeclaring() {
        !options.isEmpty() || !parameters.isEmpty()
    }

    /**
     * @param name the name of the tool
     * @param what what is wrong with its arguments
     * @return the line a tool whose arguments are refused ends with
     */
    static String refusal(String name, String what) {
        "| Error ${name}: ${what}. grails help ${name} shows its options".toString()
    }

    /**
     * Says what is wrong with the arguments of a tool that declares them. The message is the
     * {@code <what>} of {@link #refusal}, and repeats no value that was given.
     */
    static final class Refused extends Exception {

        Refused(String message) {
            super(message)
        }
    }

    /**
     * What a tool that declares its arguments was given, held to its declarations.
     */
    static final class Bound {

        /** The command line of the tool, made from the same reading. */
        final CommandLine commandLine

        private final Map<Field, Object> values

        private Bound(CommandLine commandLine, Map<Field, Object> values) {
            this.commandLine = commandLine
            this.values = values
        }

        /**
         * Sets the fields of the declared options and parameters of the tool.
         *
         * @param tool the tool: the one an adapter adapts, for an adapter
         */
        void applyTo(Object tool) {
            for (Map.Entry<Field, Object> value : values) {
                value.key.accessible = true
                value.key.set(tool, value.value)
            }
        }
    }

    /**
     * Reads the arguments of a tool that declares them.
     *
     * @param given the name of the tool and its arguments
     * @return what it was given
     * @throws Refused when they are not what it declares
     */
    Bound bind(String[] given) throws Refused {
        Map<DeclaredOption, Object> optionValues = new LinkedHashMap<DeclaredOption, Object>()
        Map<DeclaredOption, String> typed = new LinkedHashMap<DeclaredOption, String>()
        List<String> positional = []
        Map<String, String> systemProperties = new LinkedHashMap<String, String>()
        boolean ended = false
        for (int index = 1; index < given.length; index++) {
            String argument = given[index]
            if (!ended && argument == '--') {
                ended = true
                continue
            }
            if (!ended && isSystemProperty(argument)) {
                int equals = argument.indexOf('=')
                systemProperties.put(argument.substring(2, equals), argument.substring(equals + 1))
                continue
            }
            if (ended || argument.length() < 2 || !argument.startsWith('-') || (NEGATIVE_NUMBER.matcher(argument).matches() && !byName.containsKey(argument))) {
                positional << argument
                continue
            }
            int equals = argument.indexOf('=')
            String name = equals < 0 ? argument : argument.substring(0, equals)
            DeclaredOption option = byName.get(name)
            if (option == null) {
                throw new Refused("${shown(name)} is not one of its options")
            }
            if (optionValues.containsKey(option) && !option.repeatable) {
                throw new Refused("${name} is given more than once")
            }
            if (option.isSwitch) {
                if (equals >= 0) {
                    throw new Refused("${name} takes no value")
                }
                optionValues.put(option, Boolean.TRUE)
                typed.put(option, null)
                continue
            }
            String value
            if (equals >= 0) {
                value = argument.substring(equals + 1)
            } else {
                if (index + 1 >= given.length || namesAnOption(given[index + 1])) {
                    throw new Refused("${name} needs a value")
                }
                value = given[++index]
            }
            Object converted = convert(name, value, option.valueType, option.choices)
            if (option.repeatable) {
                List<String> values = (List<String>) optionValues.get(option) ?: new ArrayList<String>()
                values << (String) converted
                optionValues.put(option, values)
            } else {
                optionValues.put(option, converted)
            }
            typed.put(option, value)
        }
        for (DeclaredOption option : options) {
            if (option.required && !optionValues.containsKey(option)) {
                throw new Refused("${option.longName} is required")
            }
        }
        Map<Field, Object> values = new LinkedHashMap<Field, Object>()
        int at = 0
        for (DeclaredParameter parameter : parameters) {
            if (parameter.many) {
                List<String> rest = new ArrayList<String>()
                while (at < positional.size()) {
                    rest << (String) convert(parameter.label, positional[at++], String, parameter.choices)
                }
                if (rest.isEmpty() && parameter.required) {
                    throw new Refused("${parameter.label} is required")
                }
                if (parameter.field != null && !rest.isEmpty()) {
                    values.put(parameter.field, rest)
                }
            } else if (at < positional.size()) {
                Object converted = convert(parameter.label, positional[at++], parameter.valueType, parameter.choices)
                if (parameter.field != null) {
                    values.put(parameter.field, converted)
                }
            } else if (parameter.required) {
                throw new Refused("${parameter.label} is required")
            }
        }
        if (at < positional.size()) {
            int most = parameters.size()
            throw new Refused(most == 0 ? "it takes no parameters, and was given ${positional.size()}".toString() :
                    "it takes at most ${most} parameter${most == 1 ? '' : 's'}, and was given ${positional.size()}".toString())
        }
        for (DeclaredOption option : options) {
            if (option.field == null) {
                continue
            }
            if (optionValues.containsKey(option)) {
                values.put(option.field, optionValues.get(option))
            } else if (option.defaultValue != null) {
                values.put(option.field, convert(option.longName, option.defaultValue, option.valueType, option.choices))
            }
        }
        new Bound(commandLine(given, typed, positional, systemProperties), values)
    }

    // the command line of a tool that declares, as the tool reads it
    private static CommandLine commandLine(String[] given, Map<DeclaredOption, String> typed, List<String> positional,
                                           Map<String, String> systemProperties) {
        DefaultCommandLine commandLine = new DefaultCommandLine()
        commandLine.rawArguments = given
        commandLine.commandName = given[0]
        for (Map.Entry<DeclaredOption, String> option : typed) {
            for (String name : option.key.names) {
                commandLine.addUndeclaredOption(name.replaceFirst('^-{1,2}', ''), option.value == null ? Boolean.TRUE : option.value)
            }
        }
        for (String parameter : positional) {
            commandLine.addRemainingArg(parameter)
        }
        // the system properties are data of the tool: the one of the environment does not change the environment of the JVM
        String environment = System.getProperty(Environment.KEY)
        try {
            for (Map.Entry<String, String> property : systemProperties) {
                commandLine.addSystemProperty(property.key, property.value)
            }
        } finally {
            if (environment == null) {
                System.clearProperty(Environment.KEY)
            } else {
                System.setProperty(Environment.KEY, environment)
            }
        }
        commandLine
    }

    private static boolean isSystemProperty(String argument) {
        argument.length() > 3 && argument.startsWith('-D') && argument.indexOf('=') > 2
    }

    // an argument that names a declared option, or ends the options, is no value of the option before it
    private boolean namesAnOption(String argument) {
        if (argument == '--') {
            return true
        }
        int equals = argument.indexOf('=')
        byName.containsKey(equals < 0 ? argument : argument.substring(0, equals))
    }

    private static Object convert(String name, String value, Class<?> type, List<String> choices) throws Refused {
        if (WHOLE_NUMBERS.contains(type)) {
            if (!ToolDescriptor.isWholeNumber(value)) {
                throw new Refused("${name} takes a whole number")
            }
            long number = Long.parseLong(value)
            if (type == int || type == Integer) {
                if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
                    throw new Refused("${name} takes a whole number from ${Integer.MIN_VALUE} to ${Integer.MAX_VALUE}")
                }
                return Integer.valueOf((int) number)
            }
            return Long.valueOf(number)
        }
        if (choices != null && !choices.contains(value)) {
            throw new Refused("${name} takes one of ${choices.join(', ')}")
        }
        if (type != null && type.isEnum()) {
            return enumValue(type, value)
        }
        value
    }

    private static Object enumValue(Class<?> type, String value) {
        for (Object constant : type.enumConstants) {
            if (((Enum) constant).name() == value) {
                return constant
            }
        }
        null
    }

    private static String shown(String name) {
        SHOWN.matcher(name).matches() ? name : 'an option it was given'
    }

    // the declarations of the class, held to the rules; why they break one, or null
    private String read(Class<?> type) {
        String className = type.simpleName
        // what the class has itself, those its containers hold and those a collector put there, in the order of the class
        List<ToolOption> classOptions = []
        List<ToolParameter> classParameters = []
        for (Annotation annotation : type.declaredAnnotations) {
            if (annotation instanceof ToolOption) {
                classOptions << (ToolOption) annotation
            } else if (annotation instanceof ToolOptions) {
                classOptions.addAll(((ToolOptions) annotation).value())
            } else if (annotation instanceof ToolParameter) {
                classParameters << (ToolParameter) annotation
            } else if (annotation instanceof ToolParameters) {
                classParameters.addAll(((ToolParameters) annotation).value())
            }
        }
        int place = 0
        for (ToolOption declared : classOptions) {
            place++
            String first = declared.names().find { String name -> ToolDescriptor.isLongOptionName(name) }
            String what = first != null ? "@ToolOption ${first} on the class ${className}" : "The ${ordinal(place)} @ToolOption on the class ${className}"
            if (!declared.arity()) {
                return "${what} gives no arity, which an option on the class has to: '0' for a switch, '1' for an option that takes a value"
            }
            String why = addOption(declared, declared.arity(), null, null, what)
            if (why != null) {
                return why
            }
        }
        Map<Integer, DeclaredParameter> byIndex = new TreeMap<Integer, DeclaredParameter>()
        place = 0
        for (ToolParameter declared : classParameters) {
            place++
            String what = "The ${ordinal(place)} @ToolParameter on the class ${className}"
            if (!declared.arity()) {
                return "${what} gives no arity, which a parameter on the class has to: '1' for one value, '1..*' for the rest"
            }
            if (declared.arity().startsWith('0') && declared.required()) {
                return "${what} has the arity ${declared.arity()}, which can be left out, and is required: set required = false"
            }
            String why = addParameter(declared, null, String, null, what, byIndex)
            if (why != null) {
                return why
            }
        }
        List<Class<?>> hierarchy = []
        for (Class<?> each = type; each != null && each != Object; each = each.superclass) {
            hierarchy.add(0, each)
        }
        for (Class<?> each : hierarchy) {
            for (Field field : each.declaredFields) {
                ToolOption option = field.getAnnotation(ToolOption)
                ToolParameter parameter = field.getAnnotation(ToolParameter)
                if (option == null && parameter == null) {
                    continue
                }
                String what = "${each.simpleName}.${field.name}"
                if (option != null && parameter != null) {
                    return "${what} is declared more than once: a field is one option or one parameter"
                }
                if (Modifier.isStatic(field.modifiers)) {
                    return "${what} is static, and a value is given to the tool: declare the option or the parameter on a field of the tool"
                }
                if (Modifier.isFinal(field.modifiers)) {
                    return "${what} is final, so it cannot be given its value"
                }
                String why = option != null ? fieldOption(option, field, "@ToolOption on ${what}") :
                        fieldParameter(parameter, field, "@ToolParameter on ${what}", byIndex)
                if (why != null) {
                    return why
                }
            }
        }
        List<Integer> indexes = new ArrayList<Integer>(byIndex.keySet())
        for (int expected = 0; expected < indexes.size(); expected++) {
            if (indexes[expected] != expected) {
                return "The parameters of ${className} have the indexes ${indexes.join(', ')}: they are counted from 0, without gaps"
            }
        }
        parameters.addAll(byIndex.values())
        String together = ToolDescriptor.declarationsRefusal(options*.declaration as List<Map>, parameters*.declaration as List<Map>)
        if (together != null) {
            return "${className} ${together}"
        }
        for (DeclaredOption option : options) {
            for (String name : option.names) {
                byName.put(name, option)
            }
        }
        null
    }

    private String fieldOption(ToolOption declared, Field field, String what) {
        if (declared.arity()) {
            return "${what} gives an arity, which on a field the type of the field says: leave it out"
        }
        Class<?> type = field.type
        if (type == boolean || type == Boolean) {
            return addOption(declared, ToolDescriptor.ARITY_SWITCH, field, type, what)
        }
        if (type == String || WHOLE_NUMBERS.contains(type) || type.isEnum()) {
            return addOption(declared, ToolDescriptor.ARITY_ONE, field, type, what)
        }
        if (isListOfStrings(field)) {
            return addOption(declared, ToolDescriptor.ARITY_ONE, field, List, what)
        }
        "${what} is on a field of the type ${field.genericType.typeName}, which no option takes: " +
                'String, boolean, Boolean, int, Integer, long, Long, an enum or List<String>'
    }

    private String fieldParameter(ToolParameter declared, Field field, String what, Map<Integer, DeclaredParameter> byIndex) {
        if (declared.arity()) {
            return "${what} gives an arity, which on a field the type of the field says: leave it out"
        }
        Class<?> type = field.type
        if (type == String || WHOLE_NUMBERS.contains(type) || type.isEnum() || isListOfStrings(field)) {
            return addParameter(declared, field, isListOfStrings(field) ? List : type, isListOfStrings(field) ? ToolDescriptor.ARITY_SOME : ToolDescriptor.ARITY_ONE,
                    what, byIndex)
        }
        "${what} is on a field of the type ${field.genericType.typeName}, which no parameter takes: String, int, Integer, long, Long, an enum or List<String>"
    }

    private String addOption(ToolOption declared, String arity, Field field, Class<?> type, String what) {
        Map<String, Object> option = new LinkedHashMap<String, Object>()
        option.put('names', declared.names().toList())
        option.put('arity', arity)
        boolean whole = type != null && WHOLE_NUMBERS.contains(type)
        option.put('type', arity == ToolDescriptor.ARITY_SWITCH ? ToolDescriptor.TYPE_BOOLEAN : whole ? ToolDescriptor.TYPE_INTEGER : ToolDescriptor.TYPE_STRING)
        if (declared.label()) {
            option.put('label', declared.label())
        } else if (arity == ToolDescriptor.ARITY_ONE && ToolDescriptor.defaultLabel(declared.names().toList()) != null) {
            option.put('label', ToolDescriptor.defaultLabel(declared.names().toList()))
        }
        option.put('required', declared.required())
        option.put('description', declared.description())
        option.put('repeatable', type == List)
        List<String> choices = type != null && type.isEnum() ? constantsOf(type) : null
        option.put('choices', choices)
        option.put('default', declared.defaultValue() ?: null)
        String why = ToolDescriptor.optionRefusal(option)
        if (why != null) {
            return "${what} ${why}"
        }
        options << new DeclaredOption(declaration: option, names: declared.names().toList(), isSwitch: arity == ToolDescriptor.ARITY_SWITCH,
                repeatable: type == List, required: declared.required(), choices: choices, defaultValue: declared.defaultValue() ?: null,
                field: field, valueType: type == List ? String : type)
        null
    }

    private String addParameter(ToolParameter declared, Field field, Class<?> type, String fieldArity, String what,
                                Map<Integer, DeclaredParameter> byIndex) {
        String arity = fieldArity ?: declared.arity()
        if (!declared.required()) {
            arity = arity == ToolDescriptor.ARITY_ONE ? ToolDescriptor.ARITY_OPTIONAL : arity == ToolDescriptor.ARITY_SOME ? ToolDescriptor.ARITY_ANY : arity
        }
        Map<String, Object> parameter = new LinkedHashMap<String, Object>()
        parameter.put('label', declared.label())
        parameter.put('arity', arity)
        parameter.put('type', type != null && WHOLE_NUMBERS.contains(type) ? ToolDescriptor.TYPE_INTEGER : ToolDescriptor.TYPE_STRING)
        parameter.put('description', declared.description())
        List<String> choices = type != null && type.isEnum() ? constantsOf(type) : null
        parameter.put('choices', choices)
        String why = ToolDescriptor.parameterRefusal(parameter)
        if (why != null) {
            return "${what} ${why}"
        }
        if (declared.index() < 0) {
            return "${what} has an index that is not a whole number from 0"
        }
        if (byIndex.containsKey(declared.index())) {
            return "${what} has the index ${declared.index()}, which another parameter has too: each parameter has an index of its own"
        }
        byIndex.put(declared.index(), new DeclaredParameter(declaration: parameter, label: declared.label(), many: arity.endsWith('*'),
                required: arity.startsWith('1'), choices: choices, field: field, valueType: type == List ? String : type))
        null
    }

    private static boolean isListOfStrings(Field field) {
        Type generic = field.genericType
        field.type == List && generic instanceof ParameterizedType && ((ParameterizedType) generic).actualTypeArguments.length == 1 &&
                ((ParameterizedType) generic).actualTypeArguments[0] == String
    }

    private static List<String> constantsOf(Class<?> type) {
        type.enumConstants.collect { Object constant -> ((Enum) constant).name() }
    }

    private static String ordinal(int place) {
        int lastTwo = place % 100
        String suffix = lastTwo in 11..13 ? 'th' : place % 10 == 1 ? 'st' : place % 10 == 2 ? 'nd' : place % 10 == 3 ? 'rd' : 'th'
        "${place}${suffix}".toString()
    }

    @PackageScope
    static final class DeclaredOption {
        Map<String, Object> declaration
        List<String> names
        boolean isSwitch
        boolean repeatable
        boolean required
        List<String> choices
        String defaultValue
        Field field
        Class<?> valueType

        String getLongName() {
            names.find { String name -> ToolDescriptor.isLongOptionName(name) }
        }
    }

    @PackageScope
    static final class DeclaredParameter {
        Map<String, Object> declaration
        String label
        boolean many
        boolean required
        List<String> choices
        Field field
        Class<?> valueType
    }
}
