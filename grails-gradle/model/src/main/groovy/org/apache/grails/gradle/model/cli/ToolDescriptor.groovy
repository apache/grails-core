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
 *       source is in a deprecated folder;</li>
 *   <li>{@code options}, {@code parameters}, {@code hints}, {@code readsInput} and {@code incomplete},
 *       optionally: what the tool declares of its command line and of what it does, and what it lacks
 *       of that ({@link #MEMBER_OPTIONS} and the members after it).</li>
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
     * The member that lists the options a tool declares, each as {@code help --json} of the
     * {@code grails} command line describes an option.
     */
    public static final String MEMBER_OPTIONS = 'options'

    /**
     * The member that lists the parameters a tool declares, in their order, each as {@code help --json}
     * of the {@code grails} command line describes a parameter.
     */
    public static final String MEMBER_PARAMETERS = 'parameters'

    /**
     * The member with the hints a tool declares of what it does: the four booleans {@code readOnlyHint},
     * {@code destructiveHint}, {@code idempotentHint} and {@code openWorldHint}. A tool that declares
     * none has no such member.
     */
    public static final String MEMBER_HINTS = 'hints'

    /** The member that says that a tool reads what is typed: {@code true}, and there only for a tool that does. */
    public static final String MEMBER_READS_INPUT = 'readsInput'

    /**
     * The member that lists what a tool lacks of what describes it, as the compiler found it:
     * {@link #INCOMPLETE_DESCRIPTION}, {@link #INCOMPLETE_ARGUMENTS} and {@link #INCOMPLETE_HINTS}.
     */
    public static final String MEMBER_INCOMPLETE = 'incomplete'

    /** What a tool lacks that has no description. */
    public static final String INCOMPLETE_DESCRIPTION = 'description'

    /** What a tool lacks that reads its arguments without declaring them. */
    public static final String INCOMPLETE_ARGUMENTS = 'arguments'

    /** What a tool lacks that does not declare its hints. */
    public static final String INCOMPLETE_HINTS = 'hints'

    /** Everything a tool can lack of what describes it, in the order it is written down. */
    public static final List<String> INCOMPLETE = [INCOMPLETE_DESCRIPTION, INCOMPLETE_ARGUMENTS, INCOMPLETE_HINTS].asImmutable()

    /**
     * The {@code type} of an option that takes no value, a switch. The member {@code type} of an
     * option or a parameter says what each of its values is; for one that can be given more than
     * once, what each value given is.
     */
    public static final String TYPE_BOOLEAN = 'boolean'

    /** The {@code type} of a value that is text: a name, a path, or one of the {@code choices}. */
    public static final String TYPE_STRING = 'string'

    /** The {@code type} of a value that is a whole number. */
    public static final String TYPE_INTEGER = 'integer'

    /** The {@code type} of a value that is a number with a fraction. */
    public static final String TYPE_NUMBER = 'number'

    /** The {@code type} of an option whose values are keys with values, each given as {@code key=value}. */
    public static final String TYPE_MAP = 'map'

    /** Every {@code type} an option or a parameter can have, as {@code help --json} of the command line has them. */
    public static final List<String> TYPES = [TYPE_BOOLEAN, TYPE_STRING, TYPE_INTEGER, TYPE_NUMBER, TYPE_MAP].asImmutable()

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

    /**
     * The most bytes a descriptor has. The compiler refuses a tool whose declarations need more, with an
     * error that says so: a description is written in ASCII, and each of its characters outside ASCII
     * takes six bytes ({@code é}).
     */
    public static final int MAX_BYTES = 16 * 1024

    /** How deep the arrays and objects of a descriptor go at most: the names of an option are four deep. */
    public static final int MAX_DEPTH = 4

    /** The most options a tool declares. */
    public static final int MAX_OPTIONS = 32

    /** The most parameters a tool declares. */
    public static final int MAX_PARAMETERS = 16

    /** The most names one option has. */
    public static final int MAX_OPTION_NAMES = 4

    /** The most characters the name of an option has, with its hyphens. */
    public static final int MAX_OPTION_NAME_LENGTH = 32

    /** The most characters a label has. */
    public static final int MAX_LABEL_LENGTH = 32

    /** The most characters the description of an option or a parameter has. */
    public static final int MAX_DECLARATION_DESCRIPTION_LENGTH = 512

    /** The most choices an option or a parameter has. */
    public static final int MAX_CHOICES = 64

    /** The most characters a choice has. */
    public static final int MAX_CHOICE_LENGTH = 64

    /** The most characters the default value of an option has. */
    public static final int MAX_DEFAULT_LENGTH = 128

    /** The arity of an option that takes no value, a switch. */
    public static final String ARITY_SWITCH = '0'

    /** The arity of an option that takes one value, and of a parameter that has to be given. */
    public static final String ARITY_ONE = '1'

    /** The arity of a parameter that can be left out. */
    public static final String ARITY_OPTIONAL = '0..1'

    /** The arity of the last parameter when it takes one value or more. */
    public static final String ARITY_SOME = '1..*'

    /** The arity of the last parameter when it takes any number of values. */
    public static final String ARITY_ANY = '0..*'

    /** The arities of an option. */
    public static final List<String> OPTION_ARITIES = [ARITY_SWITCH, ARITY_ONE].asImmutable()

    /** The arities of a parameter. */
    public static final List<String> PARAMETER_ARITIES = [ARITY_ONE, ARITY_OPTIONAL, ARITY_SOME, ARITY_ANY].asImmutable()

    /**
     * The names no option takes: the command line has {@code --help} and {@code --version} itself, and
     * an argument that starts with {@code -D} and has an {@code =} is a system property of the tool.
     */
    public static final List<String> RESERVED_OPTION_NAMES = ['--help', '--version', '-D'].asImmutable()

    /**
     * The hints of {@link #MEMBER_HINTS}, in their order, which are those of a tool of the Model Context
     * Protocol and of {@code @McpTool.McpAnnotations} of Spring AI.
     */
    public static final List<String> HINTS = ['readOnlyHint', 'destructiveHint', 'idempotentHint', 'openWorldHint'].asImmutable()

    /** What each hint is where a tool does not say otherwise: the most cautious. */
    public static final Map<String, Boolean> HINT_DEFAULTS = Collections.unmodifiableMap(
            [readOnlyHint: false, destructiveHint: true, idempotentHint: false, openWorldHint: true] as LinkedHashMap<String, Boolean>)

    /** The members of an option, in the order they are written. */
    public static final List<String> OPTION_MEMBERS = ['names', 'arity', 'type', 'label', 'required', 'description', 'repeatable', 'choices',
                                                       'default'].asImmutable()

    /** The members of a parameter, in the order they are written. */
    public static final List<String> PARAMETER_MEMBERS = ['label', 'arity', 'type', 'description', 'repeatable', 'choices'].asImmutable()

    /** The most characters the name of a tool has. */
    public static final int MAX_NAME_LENGTH = 64

    /** The most characters the description of a tool has. */
    public static final int MAX_DESCRIPTION_LENGTH = 512

    /** The most characters the name of a tool class has. */
    public static final int MAX_CLASS_NAME_LENGTH = 255

    private static final Pattern COMMAND_NAME = Pattern.compile('[a-z][a-z0-9]*(-[a-z0-9]+)*')

    private static final Pattern CLASS_NAME = Pattern.compile('[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*')

    private static final Pattern LONG_OPTION = Pattern.compile('--[a-z][a-zA-Z0-9]*(-[a-zA-Z0-9]+)*')

    private static final Pattern SHORT_OPTION = Pattern.compile('-[a-zA-Z]')

    private static final Pattern LABEL = Pattern.compile('[A-Z][A-Z0-9-]{0,31}')

    private static final Pattern CHOICE = Pattern.compile('[A-Za-z_$][A-Za-z0-9_$]*')

    private static final Pattern WHOLE_NUMBER = Pattern.compile('[-+]?[0-9]{1,19}')

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
     * @param name a name of an option, with its hyphens
     * @return whether it is a long name: two hyphens, a lower-case letter, then letters, digits and
     * single hyphens, at most {@link #MAX_OPTION_NAME_LENGTH} characters, as {@code --since} or the
     * {@code --dataSource} of a released tool
     */
    static boolean isLongOptionName(String name) {
        name != null && name.length() <= MAX_OPTION_NAME_LENGTH && LONG_OPTION.matcher(name).matches()
    }

    /**
     * @param name a name of an option, with its hyphen
     * @return whether it is a short name: a hyphen and one letter, as {@code -s}
     */
    static boolean isShortOptionName(String name) {
        name != null && SHORT_OPTION.matcher(name).matches()
    }

    /**
     * @param label the label of a value
     * @return whether it is an upper-case letter followed by at most 31 upper-case letters, digits and
     * hyphens, as {@code DATE} or {@code DOMAIN-CLASS}
     */
    static boolean isLabel(String label) {
        label != null && LABEL.matcher(label).matches()
    }

    /**
     * @param value a value given for an option or a parameter of the type {@link #TYPE_INTEGER}
     * @return whether it is a whole number, as {@link Long#parseLong} reads one: an optional sign and
     * ASCII digits, within the range of a {@code long}
     */
    static boolean isWholeNumber(String value) {
        if (value == null || !WHOLE_NUMBER.matcher(value).matches()) {
            return false
        }
        try {
            Long.parseLong(value)
            return true
        } catch (NumberFormatException ignored) {
            return false
        }
    }

    /**
     * The label a value of an option has when its declaration gives none: its first long name without
     * the hyphens, in upper case, as {@code DATASOURCE} for {@code --dataSource}.
     *
     * @param names the names of the option
     * @return the label, or {@code null} when the option has no long name
     */
    static String defaultLabel(List<String> names) {
        String first = names?.find { String name -> isLongOptionName(name) }
        first == null ? null : first.substring(2).toUpperCase(Locale.ROOT)
    }

    /**
     * Says which rule an option breaks, as {@code help --json} of the command line describes an option
     * and a descriptor has it.
     *
     * @param option the members of the option
     * @return the end of a sentence that starts with the option, which repeats nothing of it but a
     * name or a label that is as the rules have it; {@code null} when it keeps every rule
     */
    static String optionRefusal(Map option) {
        Object names = option.get('names')
        if (!(names instanceof List) || ((List) names).isEmpty() || !((List) names).every { Object name -> name instanceof String }) {
            return 'has no names'
        }
        List<String> named = (List<String>) names
        if (named.size() > MAX_OPTION_NAMES) {
            return "has more than ${MAX_OPTION_NAMES} names".toString()
        }
        for (String name : named) {
            if (RESERVED_OPTION_NAMES.contains(name)) {
                return "takes the name ${name}, which the command line keeps for itself".toString()
            }
            if (!isLongOptionName(name) && !isShortOptionName(name)) {
                return 'has a name that is neither a long name (two hyphens, a lower-case letter, then letters, digits and single hyphens, ' +
                        "at most ${MAX_OPTION_NAME_LENGTH} characters, as --since) nor a short name (a hyphen and one letter, as -s)"
            }
        }
        if (named.toSet().size() < named.size()) {
            return 'has a name twice'
        }
        if (!named.any { String name -> isLongOptionName(name) }) {
            return 'has no long name, as --since'
        }
        if (named.count { String name -> isShortOptionName(name) } > 1) {
            return 'has more than one short name'
        }
        Object arity = option.get('arity')
        if (!OPTION_ARITIES.contains(arity)) {
            return 'has an arity that is neither 0, for a switch, nor 1, for an option that takes a value'
        }
        boolean isSwitch = arity == ARITY_SWITCH
        Object type = option.containsKey('type') ? option.get('type') : (isSwitch ? TYPE_BOOLEAN : TYPE_STRING)
        if (isSwitch ? type != TYPE_BOOLEAN : !(type in [TYPE_STRING, TYPE_INTEGER])) {
            return 'has a type its arity does not take: a switch is boolean, and an option that takes a value is string or integer'
        }
        String common = commonRefusal(option, type)
        if (common != null) {
            return common
        }
        Object label = option.get('label')
        if (label != null && !(label instanceof String && isLabel((String) label))) {
            return "has a label that is not an upper-case letter followed by at most ${MAX_LABEL_LENGTH - 1} upper-case letters, digits and hyphens, as DATE".toString()
        }
        Object required = option.get('required')
        if (required != null && !(required instanceof Boolean)) {
            return 'has a required that is neither true nor false'
        }
        Object repeatable = option.get('repeatable')
        if (repeatable != null && !(repeatable instanceof Boolean)) {
            return 'has a repeatable that is neither true nor false'
        }
        Object defaultValue = option.get('default')
        if (isSwitch) {
            if (label != null) {
                return 'is a switch and has a label: a switch takes no value'
            }
            if (required == Boolean.TRUE) {
                return 'is a switch and is required: a switch is given or not'
            }
            if (repeatable == Boolean.TRUE) {
                return 'is a switch and is repeatable: a switch is given once or not'
            }
            if (defaultValue != null) {
                return 'is a switch and has a default: a switch is false unless it is given'
            }
            return null
        }
        if (defaultValue == null) {
            return null
        }
        if (!(defaultValue instanceof String) || ((String) defaultValue).length() > MAX_DEFAULT_LENGTH || !isDescription((String) defaultValue)) {
            return "has a default that is not one line of at most ${MAX_DEFAULT_LENGTH} characters that can be shown".toString()
        }
        if (required == Boolean.TRUE) {
            return 'is required and has a default, which it never takes'
        }
        if (repeatable == Boolean.TRUE) {
            return 'is repeatable and has a default: an option given more than once has the values it is given'
        }
        if (type == TYPE_INTEGER && !isWholeNumber((String) defaultValue)) {
            return 'has a default that is not a whole number'
        }
        Object choices = option.get('choices')
        if (choices != null && !((List) choices).contains(defaultValue)) {
            return 'has a default that is not one of its choices'
        }
        null
    }

    /**
     * Says which rule a parameter breaks, as {@code help --json} of the command line describes a
     * parameter and a descriptor has it: in the same form as an option, with no {@code required}, since
     * its arity says whether it can be left out.
     *
     * @param parameter the members of the parameter
     * @return the end of a sentence that starts with the parameter, which repeats nothing of it but a
     * label that is as the rules have it; {@code null} when it keeps every rule
     */
    static String parameterRefusal(Map parameter) {
        Object label = parameter.get('label')
        if (!(label instanceof String) || !isLabel((String) label)) {
            return "has no label that is an upper-case letter followed by at most ${MAX_LABEL_LENGTH - 1} upper-case letters, digits and hyphens, as OUTPUT".toString()
        }
        Object arity = parameter.get('arity')
        if (!PARAMETER_ARITIES.contains(arity)) {
            return "has an arity that is none of ${PARAMETER_ARITIES.join(', ')}".toString()
        }
        Object type = parameter.containsKey('type') ? parameter.get('type') : TYPE_STRING
        if (!(type in [TYPE_STRING, TYPE_INTEGER])) {
            return 'has a type that is neither string nor integer'
        }
        String common = commonRefusal(parameter, type)
        if (common != null) {
            return common
        }
        Object repeatable = parameter.get('repeatable')
        if (repeatable != null && repeatable != Boolean.valueOf(((String) arity).endsWith('*'))) {
            return 'has a repeatable that its arity does not say'
        }
        null
    }

    // what the rules of an option that takes a value and of a parameter have in common
    private static String commonRefusal(Map declared, Object type) {
        Object description = declared.get('description')
        if (description != null && !(description instanceof String && ((String) description).length() <= MAX_DECLARATION_DESCRIPTION_LENGTH &&
                isDescription((String) description))) {
            return "has a description that is not one line of at most ${MAX_DECLARATION_DESCRIPTION_LENGTH} characters that can be shown".toString()
        }
        Object choices = declared.get('choices')
        if (choices == null) {
            return null
        }
        if (type != TYPE_STRING) {
            return 'has choices and takes no text'
        }
        if (!(choices instanceof List) || ((List) choices).isEmpty() || ((List) choices).size() > MAX_CHOICES ||
                ((List) choices).toSet().size() < ((List) choices).size() ||
                !((List) choices).every { Object choice -> choice instanceof String && ((String) choice).length() <= MAX_CHOICE_LENGTH && CHOICE.matcher((String) choice).matches() }) {
            return "has choices that are not 1 to ${MAX_CHOICES} different names of at most ${MAX_CHOICE_LENGTH} characters, " +
                    'each an ASCII letter, _ or $ followed by ASCII letters, digits, _ and $'
        }
        null
    }

    /**
     * Says which rule the options and the parameters of a tool break together, each of them keeping
     * the rules of its own ({@link #optionRefusal}, {@link #parameterRefusal}).
     *
     * @param options the options of the tool
     * @param parameters the parameters of the tool, in their order
     * @return the end of a sentence that starts with the tool, which repeats nothing but a name or a
     * label that is as the rules have it; {@code null} when they keep every rule
     */
    static String declarationsRefusal(List<Map> options, List<Map> parameters) {
        if (options.size() > MAX_OPTIONS) {
            return "declares more than ${MAX_OPTIONS} options".toString()
        }
        if (parameters.size() > MAX_PARAMETERS) {
            return "declares more than ${MAX_PARAMETERS} parameters".toString()
        }
        Set<String> names = new HashSet<String>()
        for (Map option : options) {
            for (Object name : (List) option.get('names')) {
                if (!names.add((String) name)) {
                    return "gives two options the name ${name}".toString()
                }
            }
        }
        Set<String> labels = new HashSet<String>()
        boolean optional = false
        for (int index = 0; index < parameters.size(); index++) {
            Map parameter = parameters[index]
            String arity = (String) parameter.get('arity')
            if (!labels.add((String) parameter.get('label'))) {
                return "gives two parameters the label ${parameter.get('label')}".toString()
            }
            if (arity.endsWith('*') && index < parameters.size() - 1) {
                return "has a parameter that takes any number of values, ${parameter.get('label')}, before its last".toString()
            }
            if (optional && arity.startsWith('1')) {
                return "has a parameter that has to be given, ${parameter.get('label')}, after one that can be left out".toString()
            }
            optional = optional || arity.startsWith('0')
        }
        null
    }

    /**
     * Writes an option with its members in the order of {@link #OPTION_MEMBERS}, and only those that say
     * something: {@code required} and {@code repeatable} when they are {@code true}, {@code description}
     * when it is not empty, {@code choices} when there are some, {@code label} and {@code default} when
     * there is one, and the {@code type} its arity gives when the option has none.
     *
     * @param option an option that keeps the rules ({@link #optionRefusal})
     * @return the option as a descriptor has it
     */
    static Map<String, Object> normalOption(Map option) {
        Map<String, Object> normal = new LinkedHashMap<String, Object>()
        String arity = (String) option.get('arity')
        normal.put('names', new ArrayList<String>((List<String>) option.get('names')).asImmutable())
        normal.put('arity', arity)
        normal.put('type', option.containsKey('type') ? option.get('type') : (arity == ARITY_SWITCH ? TYPE_BOOLEAN : TYPE_STRING))
        putSaying(normal, 'label', option.get('label'))
        putSaying(normal, 'required', option.get('required'))
        putSaying(normal, 'description', option.get('description'))
        putSaying(normal, 'repeatable', option.get('repeatable'))
        putSaying(normal, 'choices', option.get('choices'))
        putSaying(normal, 'default', option.get('default'))
        normal.asImmutable()
    }

    /**
     * Writes a parameter with its members in the order of {@link #PARAMETER_MEMBERS}, and only those that
     * say something, as {@link #normalOption} does: {@code repeatable} is {@code true} for the arities
     * {@link #ARITY_SOME} and {@link #ARITY_ANY}.
     *
     * @param parameter a parameter that keeps the rules ({@link #parameterRefusal})
     * @return the parameter as a descriptor has it
     */
    static Map<String, Object> normalParameter(Map parameter) {
        Map<String, Object> normal = new LinkedHashMap<String, Object>()
        String arity = (String) parameter.get('arity')
        normal.put('label', parameter.get('label'))
        normal.put('arity', arity)
        normal.put('type', parameter.containsKey('type') ? parameter.get('type') : TYPE_STRING)
        putSaying(normal, 'description', parameter.get('description'))
        putSaying(normal, 'repeatable', arity.endsWith('*') ? Boolean.TRUE : null)
        putSaying(normal, 'choices', parameter.get('choices'))
        normal.asImmutable()
    }

    private static void putSaying(Map<String, Object> normal, String member, Object value) {
        if (value == null || value == Boolean.FALSE || (value instanceof String && ((String) value).isEmpty()) ||
                (value instanceof List && ((List) value).isEmpty())) {
            return
        }
        normal.put(member, value instanceof List ? new ArrayList<Object>((List) value).asImmutable() : value)
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
     * and repeats nothing of the descriptor, unless it is one of a fixed set of words, or the name of
     * an option or the label of a parameter that is as the rules have it.
     */
    static final class Refused extends Exception {

        Refused(String message) {
            super(message)
        }
    }

    /**
     * What a tool declares of its command line and of what it does, and what it lacks of that: the
     * members {@link #MEMBER_OPTIONS} to {@link #MEMBER_INCOMPLETE} of its descriptor. Nothing of it can
     * be changed.
     */
    static final class Declarations {

        /** What a tool that declares nothing has, and lacks nothing the compiler found. */
        static final Declarations NONE = new Declarations(null, null, null, false, null)

        /** The options, each as {@link ToolDescriptor#normalOption} writes one; never {@code null}. */
        final List<Map<String, Object>> options

        /** The parameters, in their order, each as {@link ToolDescriptor#normalParameter} writes one; never {@code null}. */
        final List<Map<String, Object>> parameters

        /** The four hints of {@link ToolDescriptor#HINTS}, in that order; {@code null} for a tool that declares none. */
        final Map<String, Boolean> hints

        /** Whether the tool reads what is typed. */
        final boolean readsInput

        /** What the tool lacks, of {@link ToolDescriptor#INCOMPLETE} and in that order; never {@code null}. */
        final List<String> incomplete

        /**
         * @param options the options; {@code null} for none
         * @param parameters the parameters, in their order; {@code null} for none
         * @param hints the hints, each of {@link ToolDescriptor#HINTS} that is missing being its default;
         * {@code null} for none
         * @param readsInput whether the tool reads what is typed
         * @param incomplete what the tool lacks; {@code null} for nothing
         * @throws IllegalArgumentException when the options and the parameters break a rule
         * ({@link ToolDescriptor#declarationsRefusalOf})
         */
        Declarations(List<? extends Map> options, List<? extends Map> parameters, Map<String, Boolean> hints, boolean readsInput,
                     List<String> incomplete) {
            String refusal = declarationsRefusalOf((List<Map>) (options ?: []), (List<Map>) (parameters ?: []))
            if (refusal != null) {
                throw new IllegalArgumentException("The declarations ${refusal}")
            }
            List<Map<String, Object>> normalOptions = []
            for (Map option : (options ?: [])) {
                normalOptions << normalOption(option)
            }
            List<Map<String, Object>> normalParameters = []
            for (Map parameter : (parameters ?: [])) {
                normalParameters << normalParameter(parameter)
            }
            this.options = normalOptions.asImmutable()
            this.parameters = normalParameters.asImmutable()
            if (hints == null) {
                this.hints = null
            } else {
                Map<String, Boolean> all = new LinkedHashMap<String, Boolean>()
                for (String hint : HINTS) {
                    all.put(hint, hints.containsKey(hint) ? hints.get(hint) : HINT_DEFAULTS.get(hint))
                }
                this.hints = Collections.unmodifiableMap(all)
            }
            this.readsInput = readsInput
            this.incomplete = INCOMPLETE.findAll { String lacking -> incomplete != null && incomplete.contains(lacking) }.asImmutable()
        }

        /**
         * @return whether the tool declares an option or a parameter, and is held to its declarations
         */
        boolean isDeclaring() {
            !options.isEmpty() || !parameters.isEmpty()
        }
    }

    /**
     * Says which rule the options and the parameters of a tool break, each on its own or together.
     *
     * @param options the options of the tool, as they are written
     * @param parameters the parameters of the tool, in their order, as they are written
     * @return the end of a sentence that starts with the tool or its declarations, which repeats nothing
     * but a name or a label that is as the rules have it; {@code null} when they keep every rule
     */
    static String declarationsRefusalOf(List<Map> options, List<Map> parameters) {
        int place = 0
        for (Object option : (List) options) {
            place++
            String why = option instanceof Map ? optionRefusal((Map) option) : 'is not a JSON object'
            if (why != null) {
                return "have an option, the ${ordinal(place)}, that ${why}".toString()
            }
        }
        place = 0
        for (Object parameter : (List) parameters) {
            place++
            String why = parameter instanceof Map ? parameterRefusal((Map) parameter) : 'is not a JSON object'
            if (why != null) {
                return "have a parameter, the ${ordinal(place)}, that ${why}".toString()
            }
        }
        String why = declarationsRefusal(options, parameters)
        why == null ? null : "say that the tool ${why}".toString()
    }

    private static String ordinal(int place) {
        int lastTwo = place % 100
        String suffix = lastTwo in 11..13 ? 'th' : place % 10 == 1 ? 'st' : place % 10 == 2 ? 'nd' : place % 10 == 3 ? 'rd' : 'th'
        "${place}${suffix}".toString()
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

        /** What the tool declares of its command line and of what it does, and what it lacks of that; never {@code null}. */
        final Declarations declarations

        Described(String name, String description) {
            this(name, description, TARGET_APPLICATION, true, Collections.<String> emptyList())
        }

        Described(String name, String description, String target, boolean startsApplication, List<String> deprecated) {
            this(name, description, target, startsApplication, deprecated, Declarations.NONE)
        }

        Described(String name, String description, String target, boolean startsApplication, List<String> deprecated, Declarations declarations) {
            this.name = name
            this.description = description
            this.target = target
            this.startsApplication = target == TARGET_APPLICATION && startsApplication
            this.deprecated = Collections.unmodifiableList(inOrder(deprecated))
            this.declarations = declarations == null ? Declarations.NONE : declarations
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
                deprecated == null ? Collections.<String> emptyList() : (List<String>) deprecated, declarations(descriptor))
    }

    // what the tool declares, held to every rule a compiled tool is held to: a descriptor written by hand is read as one the compiler wrote
    private static Declarations declarations(Map descriptor) throws Refused {
        Object options = descriptor.get(MEMBER_OPTIONS)
        Object parameters = descriptor.get(MEMBER_PARAMETERS)
        if (options != null && !(options instanceof List)) {
            throw new Refused("has ${MEMBER_OPTIONS} that are not a list")
        }
        if (parameters != null && !(parameters instanceof List)) {
            throw new Refused("has ${MEMBER_PARAMETERS} that are not a list")
        }
        String why = declarationsRefusalOf((List<Map>) (options ?: []), (List<Map>) (parameters ?: []))
        if (why != null) {
            throw new Refused("has declarations that ${why}")
        }
        Object hints = descriptor.get(MEMBER_HINTS)
        if (hints != null && !(hints instanceof Map && HINTS.every { String hint -> ((Map) hints).get(hint) instanceof Boolean })) {
            throw new Refused("has ${MEMBER_HINTS} that are not an object with ${HINTS.join(', ')}, each true or false")
        }
        Object readsInput = descriptor.get(MEMBER_READS_INPUT)
        if (readsInput != null && !(readsInput instanceof Boolean)) {
            throw new Refused("has a ${MEMBER_READS_INPUT} that is neither true nor false")
        }
        Object incomplete = descriptor.get(MEMBER_INCOMPLETE)
        if (incomplete != null && !(incomplete instanceof List && INCOMPLETE.containsAll((List) incomplete))) {
            throw new Refused("has an ${MEMBER_INCOMPLETE} that is not a list of ${INCOMPLETE.join(', ')}")
        }
        new Declarations((List<Map>) options, (List<Map>) parameters, (Map<String, Boolean>) hints, readsInput == Boolean.TRUE,
                (List<String>) incomplete)
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
        describe(className, name, description, target, startsApplication, deprecated, Declarations.NONE)
    }

    /**
     * Writes the descriptor of a tool with what it declares, when {@link #read} would take it: as
     * {@link #describe(String, String, String, String, boolean, List)} does, and with no descriptor that
     * has more than {@link #MAX_BYTES} bytes.
     *
     * @param className the name of the tool class
     * @param name what is typed for the tool
     * @param description the description of the tool, or {@code null} when it is not known
     * @param target the execution target of the tool, one of {@link #RUN_TARGETS}
     * @param startsApplication whether the application is started around the tool
     * @param deprecated what is deprecated about how the tool is written; {@code null} is nothing
     * @param declarations what the tool declares, and what it lacks
     * @return the JSON document, or {@code null} when the tool cannot be described
     */
    static String describe(String className, String name, String description, String target, boolean startsApplication, List<String> deprecated,
                           Declarations declarations) {
        if (!isClassName(className) || !isCommandName(name) || !RUN_TARGETS.contains(target) ||
                (deprecated != null && !DEPRECATIONS.containsAll(deprecated))) {
            return null
        }
        String json = toJson(className, name, description != null && isDescription(description) ? description : null, target, startsApplication,
                deprecated, declarations)
        json.length() > MAX_BYTES ? null : json
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
        toJson(className, name, description, target, startsApplication, deprecated, Declarations.NONE)
    }

    /**
     * Writes a descriptor of format 1 with what the tool declares, whatever {@link #read} makes of it:
     * the members of {@link Declarations} after the others, each only where it says something, so that
     * a tool that declares nothing and lacks nothing has the descriptor it had without them.
     *
     * @param className the name of the tool class
     * @param name what is typed for the tool
     * @param description the description of the tool, or {@code null} when it is not known
     * @param target the execution target of the tool
     * @param startsApplication whether the application is started around the tool, which is written
     * for the target {@link #TARGET_APPLICATION} and for no other
     * @param deprecated what is deprecated about how the tool is written; {@code null} and an empty
     * list are not written
     * @param declarations what the tool declares, and what it lacks
     * @return the JSON document, in ASCII, with a line end
     */
    static String toJson(String className, String name, String description, String target, boolean startsApplication, List<String> deprecated,
                         Declarations declarations) {
        String plain = plainJson(className, name, description, target, startsApplication, deprecated)
        Declarations declared = declarations == null ? Declarations.NONE : declarations
        StringBuilder json = new StringBuilder(plain.substring(0, plain.length() - 2))
        if (!declared.options.isEmpty()) {
            json.append(',"').append(MEMBER_OPTIONS).append('":').append(StrictJson.write(declared.options))
        }
        if (!declared.parameters.isEmpty()) {
            json.append(',"').append(MEMBER_PARAMETERS).append('":').append(StrictJson.write(declared.parameters))
        }
        if (declared.hints != null) {
            json.append(',"').append(MEMBER_HINTS).append('":').append(StrictJson.write(declared.hints))
        }
        if (declared.readsInput) {
            json.append(',"').append(MEMBER_READS_INPUT).append('":true')
        }
        if (!declared.incomplete.isEmpty()) {
            json.append(',"').append(MEMBER_INCOMPLETE).append('":').append(StrictJson.write(declared.incomplete))
        }
        json.append('}\n')
        json.toString()
    }

    private static String plainJson(String className, String name, String description, String target, boolean startsApplication, List<String> deprecated) {
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
