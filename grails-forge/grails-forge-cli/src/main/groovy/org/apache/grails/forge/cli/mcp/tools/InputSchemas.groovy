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

import java.util.regex.Matcher
import java.util.regex.Pattern

import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.CommandCatalog

/**
 * The input schema of a tool of {@code grails mcp}, made from the options and parameters
 * {@code help --json} lists for its command, and how the value of each of its properties becomes
 * arguments of the command line. A schema is JSON Schema 2020-12 without {@code $schema}: an object
 * whose properties are named with 1 to 64 letters, digits and hyphens, that takes no other property,
 * and that lists the properties it requires.
 *
 * <ul>
 * <li>An option is named by its first name that starts with {@code --}, without the hyphens. An option
 * with only a short name is offered only as {@code -D}, {@code system-properties}, and {@code -P},
 * {@code project-properties}.</li>
 * <li>A switch is a boolean. An option that takes a value is a string, an integer or a number as its
 * {@code type} says, with its {@code choices} as an enum and its {@code default} as the default, and
 * one that can be given more than once is an array of those. An option of keys with values, as
 * {@code -D}, is an object of strings.</li>
 * <li>A parameter is named after its label, in lower case, with every run of other characters a
 * hyphen, and with {@code -argument} after it when an option has the name. One that takes one value is
 * a string, an integer or a number; one that takes more is an array.</li>
 * <li>A tool that declares neither options nor parameters takes {@code arguments}, an array of
 * strings, each given to it as the one argument it is.</li>
 * </ul>
 *
 * <p>The relations between options are not in the schema, since clients flatten a schema whose root has
 * {@code anyOf} or {@code oneOf}: they are in the {@code _meta} of the tool, and the command line
 * refuses a breach of one.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class InputSchemas {

    /** The property of a tool that declares no options and no parameters: the arguments it is given, as they are. */
    public static final String ARGUMENTS = 'arguments'

    /** The description of {@link #ARGUMENTS}. */
    public static final String ARGUMENTS_DESCRIPTION = 'The arguments of the tool, each given to it as the one argument it is.'

    /** The property of the option {@code -D}, the system properties of the build. */
    public static final String SYSTEM_PROPERTIES = 'system-properties'

    /** The property of the option {@code -P}, the project properties of the build. */
    public static final String PROJECT_PROPERTIES = 'project-properties'

    /** What a parameter whose name an option has is named with after its name. */
    public static final String PARAMETER_SUFFIX = '-argument'

    /** What the name of a property of a schema is made of. */
    public static final Pattern PROPERTY_NAME = Pattern.compile('[A-Za-z0-9-]{1,64}')

    /** The options with only a short name that are offered, and the properties they are offered as. */
    public static final Map<String, String> SHORT_ONLY = Collections.unmodifiableMap(new LinkedHashMap<String, String>(['-D': SYSTEM_PROPERTIES, '-P': PROJECT_PROPERTIES]))

    private static final Pattern ARITY = Pattern.compile('(\\d+)(?:\\.\\.(\\d+|\\*))?')
    private static final int UNBOUNDED = -1

    /** One property of an input schema, and how its value reaches the command line. */
    @CompileStatic
    static final class Property {

        /** How a value of a property is given to the command line. */
        enum Kind {

            /** {@code true} gives the option; {@code false} gives nothing. */
            SWITCH,

            /** One value, given as {@code --name=value}. */
            VALUE,

            /** Values, each given as {@code --name=value}. */
            VALUES,

            /** Keys with values, each given as {@code -Dkey=value}, or {@code --name=key=value} for an option with a long name. */
            ENTRIES,

            /** One value, given after the options as the one argument it is. */
            PARAMETER,

            /** Values, given after the options in their order, each as the one argument it is. */
            PARAMETERS,

            /** The arguments of a tool that declares none, given after its name as they are. */
            ARGUMENTS
        }

        /** The name of the property. */
        final String name

        /** How its value is given to the command line. */
        final Kind kind

        /** The name of the option the value is given with, as {@code --port} or {@code -D}; {@code null} for a parameter. */
        final String option

        /** The type of the value, or of each value: {@code string}, {@code integer}, {@code number} or {@code boolean}. */
        final String type

        /** The values it can have, or {@code null} for any of its type. */
        final List<Object> choices

        /** Whether a call has to give it. */
        final boolean required

        /** The fewest values of an array. */
        final int minItems

        /** The most values of an array, or {@code -1} for no limit. */
        final int maxItems

        /** Whether its values name features, which refuse the coordinates of a plugin. */
        final boolean featureValued

        /** Whether its values are passed as they are, also where they begin with {@code -}. */
        final boolean passedAsIs

        /** Its schema. */
        final Map<String, Object> schema

        Property(String name, Kind kind, String option, String type, List<Object> choices, boolean required, int minItems, int maxItems, boolean featureValued,
                 boolean passedAsIs, Map<String, Object> schema) {
            this.name = name
            this.kind = kind
            this.option = option
            this.type = type
            this.choices = choices == null ? null : Collections.unmodifiableList(new ArrayList<Object>(choices))
            this.required = required
            this.minItems = minItems
            this.maxItems = maxItems
            this.featureValued = featureValued
            this.passedAsIs = passedAsIs
            this.schema = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(schema))
        }

        @Override
        String toString() {
            return name
        }
    }

    /** An input schema, its properties in the order their values are given, and what could not be offered. */
    @CompileStatic
    static final class Schema {

        /** The schema, as JSON. */
        final Map<String, Object> json

        /** The properties, options first and then parameters, in the order {@code help --json} lists them. */
        final List<Property> properties

        /** One line for each option or parameter that is not offered because it cannot be. */
        final List<String> diagnostics

        Schema(List<Property> properties, List<String> diagnostics) {
            this.properties = Collections.unmodifiableList(new ArrayList<Property>(properties))
            this.diagnostics = Collections.unmodifiableList(new ArrayList<String>(diagnostics))
            Map<String, Object> json = new LinkedHashMap<>()
            json.put('type', 'object')
            Map<String, Object> described = new LinkedHashMap<>()
            for (Property property : properties) {
                described.put(property.name, property.schema)
            }
            json.put('properties', described)
            List<String> required = properties.findAll { Property property -> property.required }.collect { Property property -> property.name }
            if (!required.isEmpty()) {
                json.put('required', required)
            }
            json.put('additionalProperties', false)
            this.json = json
        }

        /**
         * @param name the name of a property
         * @return the property, or {@code null} when the schema has none of the name
         */
        Property property(String name) {
            return properties.find { Property property -> property.name == name }
        }
    }

    private InputSchemas() {
    }

    /**
     * @return the schema of a tool that takes nothing
     */
    static Schema none() {
        return new Schema(Collections.<Property>emptyList(), Collections.<String>emptyList())
    }

    /**
     * @return the schema of a tool that declares neither options nor parameters: {@code arguments}, an
     * array of strings, each given to the tool as the one argument it is, also where it begins with {@code -}
     */
    static Schema freeForm() {
        Map<String, Object> items = new LinkedHashMap<>()
        items.put('type', 'string')
        Map<String, Object> schema = new LinkedHashMap<>()
        schema.put('type', 'array')
        schema.put('items', items)
        schema.put('description', ARGUMENTS_DESCRIPTION)
        return new Schema([new Property(ARGUMENTS, Property.Kind.ARGUMENTS, null, CommandCatalog.TYPE_STRING, null, false, 0, UNBOUNDED, false, true, schema)],
                Collections.<String>emptyList())
    }

    /**
     * Makes the schema of a command or of a tool that declares its options and parameters.
     *
     * @param command the name of the command or tool, for the diagnostics
     * @param options the options {@code help --json} lists for it
     * @param parameters the parameters {@code help --json} lists for it
     * @param leftOut the names of options that are not offered: those in front of a command, and those its policy leaves out or gives on every call
     * @param featureValued the properties whose values name features
     * @param passedAsIs the parameters whose values are passed as they are
     * @return the schema
     */
    static Schema of(String command, List<Map<String, Object>> options, List<Map<String, Object>> parameters, Collection<String> leftOut,
                     Collection<String> featureValued, Collection<String> passedAsIs) {
        List<Property> properties = []
        List<String> diagnostics = []
        Set<String> names = new HashSet<>()
        for (Map<String, Object> option : options) {
            List<String> optionNames = strings(option.get('names'))
            if (optionNames.isEmpty() || optionNames.any { String name -> leftOut.contains(name) }) {
                continue
            }
            String shown = optionNames.join(', ')
            String longName = optionNames.find { String name -> name.startsWith('--') && name.length() > 2 }
            String given = longName ?: optionNames.find { String name -> SHORT_ONLY.containsKey(name) }
            if (given == null) {
                diagnostics.add("The option ${shown} of ${command} is not offered: an option with only a short name is offered only as -D or -P".toString())
                continue
            }
            String name = longName != null ? longName.substring(2) : SHORT_ONLY.get(given)
            if (!PROPERTY_NAME.matcher(name).matches()) {
                diagnostics.add("The option ${shown} of ${command} is not offered: ${name} is not 1 to 64 letters, digits and hyphens".toString())
                continue
            }
            if (!names.add(name)) {
                diagnostics.add("The option ${shown} of ${command} is not offered: another option of it is offered as ${name}".toString())
                continue
            }
            StringBuilder refused = new StringBuilder()
            Property property = ofOption(name, given, option, featureValued.contains(name), refused)
            if (property == null) {
                names.remove(name)
                diagnostics.add("The option ${shown} of ${command} is not offered: ${refused}".toString())
                continue
            }
            properties.add(property)
        }
        Set<String> optionNames = new HashSet<>(names)
        for (int index = 0; index < parameters.size(); index++) {
            Map<String, Object> parameter = parameters.get(index)
            String label = parameter.get('label') instanceof String ? (String) parameter.get('label') : ''
            String name = parameterName(label, index, optionNames, names)
            if (!PROPERTY_NAME.matcher(name).matches()) {
                diagnostics.add("The parameter ${label} of ${command} is not offered: ${name} is not 1 to 64 letters, digits and hyphens".toString())
                continue
            }
            names.add(name)
            StringBuilder refused = new StringBuilder()
            Property property = ofParameter(name, parameter, featureValued.contains(name), passedAsIs.contains(name), refused)
            if (property == null) {
                diagnostics.add("The parameter ${label} of ${command} is not offered: ${refused}".toString())
                continue
            }
            properties.add(property)
        }
        return new Schema(properties, diagnostics)
    }

    /**
     * @param label the label of a parameter, as {@code CONTROLLER-NAME} or {@code PROPERTY:TYPE}
     * @return the name of its property: the label in lower case, with every run of characters other than
     * letters and digits a hyphen, and none at either end, as {@code controller-name} and {@code property-type}
     */
    static String parameterName(String label) {
        return label.toLowerCase(Locale.ROOT).replaceAll('[^a-z0-9]+', '-').replaceAll('^-+|-+$', '')
    }

    private static String parameterName(String label, int index, Set<String> optionNames, Set<String> taken) {
        String name = parameterName(label)
        if (name.isEmpty()) {
            name = "argument-${index + 1}".toString()
        }
        if (optionNames.contains(name)) {
            name += PARAMETER_SUFFIX
        }
        if (taken.contains(name)) {
            name += "-${index + 1}"
        }
        return name
    }

    private static Property ofOption(String name, String given, Map<String, Object> option, boolean featureValued, StringBuilder refused) {
        int[] arity = arityOf(option.get('arity'))
        String type = option.get('type') instanceof String ? (String) option.get('type') : null
        if (arity == null || !(type in CommandCatalog.TYPES)) {
            refused.append("its arity ${option.get('arity')} and type ${type} are not ones a schema has")
            return null
        }
        Map<String, Object> schema = new LinkedHashMap<>()
        Property.Kind kind
        String valueType = type
        List<Object> choices = null
        if (type == CommandCatalog.TYPE_MAP) {
            kind = Property.Kind.ENTRIES
            schema.put('type', 'object')
            schema.put('additionalProperties', [type: 'string'])
            // a key is not empty, and has no =, which would make it another key
            schema.put('propertyNames', [pattern: '^[^=]+$'])
            valueType = CommandCatalog.TYPE_STRING
        } else if (arity[1] == 0) {
            kind = Property.Kind.SWITCH
            schema.put('type', 'boolean')
            valueType = CommandCatalog.TYPE_BOOLEAN
        } else if (arity[1] == 1) {
            choices = choicesOf(option.get('choices'), type)
            Map<String, Object> value = valueSchema(type, choices)
            if (option.get('repeatable') == Boolean.TRUE) {
                kind = Property.Kind.VALUES
                schema.put('type', 'array')
                schema.put('items', value)
            } else {
                kind = Property.Kind.VALUE
                schema.putAll(value)
                Object defaultValue = converted(option.get('default'), type)
                if (defaultValue != null && (choices == null || choices.contains(defaultValue))) {
                    schema.put('default', defaultValue)
                }
            }
        } else {
            refused.append("it takes ${option.get('arity')} values at a time, and a value is given as --name=value")
            return null
        }
        describe(schema, option.get('description'))
        return new Property(name, kind, given, valueType, choices, option.get('required') == Boolean.TRUE, 0, UNBOUNDED, featureValued, false, schema)
    }

    private static Property ofParameter(String name, Map<String, Object> parameter, boolean featureValued, boolean passedAsIs, StringBuilder refused) {
        int[] arity = arityOf(parameter.get('arity'))
        String type = parameter.get('type') instanceof String ? (String) parameter.get('type') : CommandCatalog.TYPE_STRING
        if (arity == null || arity[1] == 0 || !(type in [CommandCatalog.TYPE_STRING, CommandCatalog.TYPE_INTEGER, CommandCatalog.TYPE_NUMBER])) {
            refused.append("its arity ${parameter.get('arity')} and type ${type} are not ones a parameter is offered with")
            return null
        }
        List<Object> choices = choicesOf(parameter.get('choices'), type)
        Map<String, Object> value = valueSchema(type, choices)
        Map<String, Object> schema = new LinkedHashMap<>()
        Property.Kind kind
        if (arity[1] == 1 && parameter.get('repeatable') != Boolean.TRUE) {
            kind = Property.Kind.PARAMETER
            schema.putAll(value)
        } else {
            kind = Property.Kind.PARAMETERS
            schema.put('type', 'array')
            schema.put('items', value)
            if (arity[0] > 0) {
                schema.put('minItems', arity[0])
            }
            if (arity[1] != UNBOUNDED) {
                schema.put('maxItems', arity[1])
            }
        }
        describe(schema, parameter.get('description'))
        return new Property(name, kind, null, type, choices, arity[0] > 0, arity[0], arity[1], featureValued, passedAsIs, schema)
    }

    private static Map<String, Object> valueSchema(String type, List<Object> choices) {
        Map<String, Object> value = new LinkedHashMap<>()
        value.put('type', type)
        if (choices != null) {
            value.put('enum', choices)
        }
        return value
    }

    private static void describe(Map<String, Object> schema, Object description) {
        if (description instanceof String && !((String) description).isEmpty()) {
            schema.put('description', description)
        }
    }

    // the values an option or a parameter takes, of its type; none where one of them is not of it
    private static List<Object> choicesOf(Object listed, String type) {
        if (!(listed instanceof List) || ((List) listed).isEmpty()) {
            return null
        }
        List<Object> choices = []
        for (Object each : (List) listed) {
            Object value = converted(each, type)
            if (value == null) {
                return null
            }
            choices.add(value)
        }
        return choices
    }

    // a value help --json writes as text, as the type it is; null where it is not one
    private static Object converted(Object value, String type) {
        if (!(value instanceof String)) {
            return null
        }
        String text = (String) value
        try {
            switch (type) {
                case CommandCatalog.TYPE_INTEGER:
                    return new BigInteger(text)
                case CommandCatalog.TYPE_NUMBER:
                    return new BigDecimal(text)
                case CommandCatalog.TYPE_BOOLEAN:
                    return text == 'true' ? Boolean.TRUE : text == 'false' ? Boolean.FALSE : null
                default:
                    return text
            }
        } catch (NumberFormatException ignored) {
            return null
        }
    }

    // the arity of picocli as the fewest and the most values, -1 for no most; null for none it can be
    private static int[] arityOf(Object arity) {
        if (!(arity instanceof String)) {
            return null
        }
        Matcher matcher = ARITY.matcher((String) arity)
        if (!matcher.matches()) {
            return null
        }
        int min = Integer.parseInt(matcher.group(1))
        String most = matcher.group(2)
        int max = most == null ? min : most == '*' ? UNBOUNDED : Integer.parseInt(most)
        return max != UNBOUNDED && max < min ? null : [min, max] as int[]
    }

    private static List<String> strings(Object value) {
        return value instanceof List ? ((List<Object>) value).findAll { Object each -> each instanceof String }.collect { Object each -> (String) each } : Collections.<String>emptyList()
    }
}
