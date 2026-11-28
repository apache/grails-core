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
 * What a tool of the application or of a plugin declares of its command line and of what it does, as
 * the catalog of the tools lists it ({@link ToolCatalogFile}): its options and parameters, its hints,
 * whether it reads what is typed, and what it lacks of that as the compiler found it. A tool that
 * declares no option and no parameter takes whatever it is given.
 *
 * <p>The catalog is written by the build, in a directory every build writes to, so what it says of a
 * tool is held to the rules the compiler holds a declaration to, which are those of
 * {@code ToolDescriptor} of {@code grails-gradle-model}: a tool whose declarations break one is not
 * used ({@link #read}). A specification holds the two to the same rules.</p>
 *
 * <p>A tool that declares an option or a parameter is held to its declarations by the class that runs
 * it, and by the command line before anything is started ({@link #refusal}): the same rules, read from
 * the same cases ({@code tool-arguments-cases.json}), except that whether a whole number fits a field of
 * the type {@code int} is known only to the class that runs the tool.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class ToolDeclarations {

    /** The member of a tool of the catalog that lists its options. */
    public static final String OPTIONS = 'options'

    /** The member of a tool of the catalog that lists its parameters, in their order. */
    public static final String PARAMETERS = 'parameters'

    /** The member of a tool of the catalog with its hints. */
    public static final String HINTS = 'hints'

    /** The member of a tool of the catalog that says that it reads what is typed. */
    public static final String READS_INPUT = 'readsInput'

    /** The member of a tool of the catalog that lists what it lacks of what describes it. */
    public static final String INCOMPLETE = 'incomplete'

    /** What a tool lacks that has no description. */
    public static final String LACKS_DESCRIPTION = 'description'

    /** What a tool lacks that reads its arguments without declaring them. */
    public static final String LACKS_ARGUMENTS = 'arguments'

    /** What a tool lacks that does not declare its hints. */
    public static final String LACKS_HINTS = 'hints'

    /** Everything a tool can lack, in the order it is written down. */
    public static final List<String> LACKING = [LACKS_DESCRIPTION, LACKS_ARGUMENTS, LACKS_HINTS].asImmutable()

    /** The hints, in their order: those of a tool of the Model Context Protocol. */
    public static final List<String> HINT_NAMES = ['readOnlyHint', 'destructiveHint', 'idempotentHint', 'openWorldHint'].asImmutable()

    /** How many options a tool declares, at most: as many as the compiler takes. */
    public static final int MAX_OPTIONS = 32

    /** How many parameters a tool declares, at most. */
    public static final int MAX_PARAMETERS = 16

    /** How many names an option has, at most. */
    public static final int MAX_OPTION_NAMES = 4

    /** How many characters the name of an option has, at most, with its hyphens. */
    public static final int MAX_OPTION_NAME = 32

    /** How many characters a label has, at most. */
    public static final int MAX_LABEL = 32

    /** How many characters the description of an option or a parameter has, at most. */
    public static final int MAX_DESCRIPTION = 512

    /** How many choices an option or a parameter has, at most. */
    public static final int MAX_CHOICES = 64

    /** How many characters a choice has, at most. */
    public static final int MAX_CHOICE = 64

    /** How many characters the default of an option has, at most. */
    public static final int MAX_DEFAULT = 128

    /** The names no option has: the command line has {@code --help} and {@code --version}, and {@code -D} is a system property of the tool. */
    public static final List<String> RESERVED = ['--help', '--version', '-D'].asImmutable()

    /** What asks the command line for the help of a tool that declares its arguments: {@code -h} only where the tool does not declare it. */
    public static final List<String> HELP = ['--help', '-h'].asImmutable()

    /** What a tool that declares nothing, and lacks nothing that is known, has. */
    static final ToolDeclarations NONE = new ToolDeclarations([], [], null, false, [])

    static final String TYPE_BOOLEAN = 'boolean'
    static final String TYPE_STRING = 'string'
    static final String TYPE_INTEGER = 'integer'

    /** The member of an option or a parameter that says that it takes its choices in any case. */
    static final String CASE_INSENSITIVE = 'caseInsensitive'

    private static final String SWITCH = '0'
    private static final String ONE = '1'
    private static final List<String> OPTION_ARITIES = [SWITCH, ONE].asImmutable()
    private static final List<String> PARAMETER_ARITIES = [ONE, '0..1', '1..*', '0..*'].asImmutable()
    private static final Pattern LONG_NAME = Pattern.compile('--[a-z][a-zA-Z0-9]*(-[a-zA-Z0-9]+)*')
    private static final Pattern SHORT_NAME = Pattern.compile('-[a-zA-Z]')
    private static final Pattern LABEL = Pattern.compile('[A-Z][A-Z0-9-]{0,31}')
    private static final Pattern CHOICE = Pattern.compile('[A-Za-z_$][A-Za-z0-9_$]*')
    private static final Pattern WHOLE_NUMBER = Pattern.compile('[-+]?[0-9]{1,19}')
    // a name of an option a refusal says as it was given; any other is said in words
    private static final Pattern SHOWN = Pattern.compile('-{1,2}[A-Za-z0-9][A-Za-z0-9._-]{0,62}')
    // an argument that is a negative number is a parameter, unless an option has it as its name
    private static final Pattern NEGATIVE_NUMBER = Pattern.compile('-[0-9]+(\\.[0-9]+)?')
    private static final List<Integer> NOT_SHOWN = [(int) Character.CONTROL, (int) Character.FORMAT, (int) Character.LINE_SEPARATOR,
                                                    (int) Character.PARAGRAPH_SEPARATOR, (int) Character.SURROGATE, (int) Character.PRIVATE_USE,
                                                    (int) Character.UNASSIGNED]

    /** The options, in the order the catalog lists them, which is the order they are declared in. */
    final List<Option> options

    /** The parameters, in their order. */
    final List<Parameter> parameters

    /** The four hints of {@link #HINT_NAMES}, in that order; {@code null} for a tool that declares none. */
    final Map<String, Boolean> hints

    /** Whether the tool reads what is typed. */
    final boolean readsInput

    /** What the tool lacks, of {@link #LACKING} and in that order. */
    final List<String> incomplete

    private final Map<String, Option> byName = new HashMap<>()

    private ToolDeclarations(List<Option> options, List<Parameter> parameters, Map<String, Boolean> hints, boolean readsInput, List<String> incomplete) {
        this.options = options.asImmutable()
        this.parameters = parameters.asImmutable()
        this.hints = hints == null ? null : Collections.unmodifiableMap(hints)
        this.readsInput = readsInput
        this.incomplete = incomplete.asImmutable()
        for (Option option : options) {
            for (String name : option.names) {
                byName.put(name, option)
            }
        }
    }

    /**
     * @return whether the tool declares an option or a parameter, and is held to its declarations
     */
    boolean isDeclaring() {
        return !options.isEmpty() || !parameters.isEmpty()
    }

    /**
     * Says why what a tool of the catalog declares cannot be used.
     */
    static final class Refused extends Exception {

        /**
         * @param message the end of a sentence that starts with the tool, which repeats nothing of the catalog
         */
        Refused(String message) {
            super(message)
        }
    }

    /**
     * Reads what a tool of the catalog declares, held to every rule a declaration of a compiled tool
     * is held to. A member that is not there declares nothing; a member that is not known is not read.
     *
     * @param tool a tool of the catalog
     * @return what it declares
     * @throws Refused when a declaration breaks a rule; the message says which, and repeats nothing of the catalog
     */
    static ToolDeclarations read(Map<String, Object> tool) throws Refused {
        Object listedOptions = tool.get(OPTIONS)
        Object listedParameters = tool.get(PARAMETERS)
        if (listedOptions != null && !(listedOptions instanceof List && ((List) listedOptions).size() <= MAX_OPTIONS)) {
            throw new Refused("its options are not a list of at most ${MAX_OPTIONS}")
        }
        if (listedParameters != null && !(listedParameters instanceof List && ((List) listedParameters).size() <= MAX_PARAMETERS)) {
            throw new Refused("its parameters are not a list of at most ${MAX_PARAMETERS}")
        }
        List<Option> options = []
        int place = 0
        for (Object each : (List) (listedOptions ?: [])) {
            place++
            if (!(each instanceof Map)) {
                throw new Refused("its option ${place} is not a JSON object")
            }
            options.add(option((Map) each, place))
        }
        List<Parameter> parameters = []
        place = 0
        for (Object each : (List) (listedParameters ?: [])) {
            place++
            if (!(each instanceof Map)) {
                throw new Refused("its parameter ${place} is not a JSON object")
            }
            parameters.add(parameter((Map) each, place))
        }
        together(options, parameters)
        return new ToolDeclarations(options, parameters, hints(tool.get(HINTS)), readsInput(tool), incomplete(tool))
    }

    private static Option option(Map declared, int place) throws Refused {
        String what = "its option ${place}"
        Object names = declared.get('names')
        if (!(names instanceof List) || ((List) names).isEmpty() || ((List) names).size() > MAX_OPTION_NAMES ||
                !((List) names).every { Object name -> name instanceof String && (isLongName((String) name) || isShortName((String) name)) && !RESERVED.contains(name) }) {
            throw new Refused("${what} has names that are not 1 to ${MAX_OPTION_NAMES} names of an option, long (two hyphens, a lower-case letter, then letters, digits " +
                    "and single hyphens, at most ${MAX_OPTION_NAME} characters) or short (a hyphen and one letter), none of them ${RESERVED.join(', ')}")
        }
        List<String> named = (List<String>) names
        if (named.toSet().size() < named.size() || !named.any { String name -> isLongName(name) } || named.count { String name -> isShortName(name) } > 1) {
            throw new Refused("${what} has a name twice, no long name, or more than one short name")
        }
        Object arity = declared.get('arity')
        if (!OPTION_ARITIES.contains(arity)) {
            throw new Refused("${what} has an arity that is neither 0, for a switch, nor 1, for an option that takes a value")
        }
        boolean isSwitch = arity == SWITCH
        Object type = declared.containsKey('type') ? declared.get('type') : (isSwitch ? TYPE_BOOLEAN : TYPE_STRING)
        if (isSwitch ? type != TYPE_BOOLEAN : !(type in [TYPE_STRING, TYPE_INTEGER])) {
            throw new Refused("${what} has a type its arity does not take: a switch is boolean, and an option that takes a value is string or integer")
        }
        String description = description(declared, what)
        List<String> choices = choices(declared, type, what)
        boolean caseInsensitive = caseInsensitive(declared, choices, what)
        Object label = declared.get('label')
        if (label != null && !(label instanceof String && LABEL.matcher((String) label).matches())) {
            throw new Refused("${what} has a label that is not an upper-case letter followed by at most ${MAX_LABEL - 1} upper-case letters, digits and hyphens")
        }
        Object required = declared.get('required')
        Object repeatable = declared.get('repeatable')
        if ((required != null && !(required instanceof Boolean)) || (repeatable != null && !(repeatable instanceof Boolean))) {
            throw new Refused("${what} does not say with true or false whether it is required or repeatable")
        }
        Object defaultValue = declared.get('default')
        if (isSwitch && (label != null || required == Boolean.TRUE || repeatable == Boolean.TRUE || defaultValue != null)) {
            throw new Refused("${what} is a switch, and has a label, is required, is repeatable or has a default")
        }
        if (defaultValue != null) {
            if (!(defaultValue instanceof String) || ((String) defaultValue).length() > MAX_DEFAULT || !isShown((String) defaultValue)) {
                throw new Refused("${what} has a default that is not one line of at most ${MAX_DEFAULT} characters that can be shown")
            }
            if (required == Boolean.TRUE || repeatable == Boolean.TRUE) {
                throw new Refused("${what} has a default and is required or repeatable")
            }
            if ((type == TYPE_INTEGER && !isWholeNumber((String) defaultValue)) || (choices != null && !choices.contains(defaultValue))) {
                throw new Refused("${what} has a default that is not a value it takes")
            }
        }
        String shownLabel = isSwitch ? null : (label != null ? (String) label : named.find { String name -> isLongName(name) }.substring(2).toUpperCase(Locale.ROOT))
        return new Option(named, isSwitch, (String) type, shownLabel, required == Boolean.TRUE, description, repeatable == Boolean.TRUE, choices,
                caseInsensitive, (String) defaultValue)
    }

    private static Parameter parameter(Map declared, int place) throws Refused {
        String what = "its parameter ${place}"
        Object label = declared.get('label')
        if (!(label instanceof String) || !LABEL.matcher((String) label).matches()) {
            throw new Refused("${what} has no label that is an upper-case letter followed by at most ${MAX_LABEL - 1} upper-case letters, digits and hyphens")
        }
        Object arity = declared.get('arity')
        if (!PARAMETER_ARITIES.contains(arity)) {
            throw new Refused("${what} has an arity that is none of ${PARAMETER_ARITIES.join(', ')}")
        }
        Object type = declared.containsKey('type') ? declared.get('type') : TYPE_STRING
        if (!(type in [TYPE_STRING, TYPE_INTEGER])) {
            throw new Refused("${what} has a type that is neither string nor integer")
        }
        String description = description(declared, what)
        List<String> choices = choices(declared, type, what)
        boolean caseInsensitive = caseInsensitive(declared, choices, what)
        Object repeatable = declared.get('repeatable')
        if (repeatable != null && repeatable != Boolean.valueOf(((String) arity).endsWith('*'))) {
            throw new Refused("${what} has a repeatable that its arity does not say")
        }
        return new Parameter((String) label, (String) arity, (String) type, description, choices, caseInsensitive)
    }

    private static String description(Map declared, String what) throws Refused {
        Object description = declared.get('description')
        if (description != null && !(description instanceof String && ((String) description).length() <= MAX_DESCRIPTION && isShown((String) description))) {
            throw new Refused("${what} has a description that is not one line of at most ${MAX_DESCRIPTION} characters that can be shown")
        }
        return (String) description
    }

    private static List<String> choices(Map declared, Object type, String what) throws Refused {
        Object choices = declared.get('choices')
        if (choices == null) {
            return null
        }
        if (type != TYPE_STRING || !(choices instanceof List) || ((List) choices).isEmpty() || ((List) choices).size() > MAX_CHOICES ||
                ((List) choices).toSet().size() < ((List) choices).size() ||
                !((List) choices).every { Object choice -> choice instanceof String && ((String) choice).length() <= MAX_CHOICE && CHOICE.matcher((String) choice).matches() }) {
            throw new Refused("${what} has choices that are not 1 to ${MAX_CHOICES} different names of at most ${MAX_CHOICE} characters, of a value that is text")
        }
        return ((List<String>) choices).asImmutable()
    }

    // whether a declaration with choices takes them in any case; only one with choices that differ in more than their case can
    private static boolean caseInsensitive(Map declared, List<String> choices, String what) throws Refused {
        Object caseInsensitive = declared.get(CASE_INSENSITIVE)
        if (caseInsensitive == null || caseInsensitive == Boolean.FALSE) {
            return false
        }
        if (!(caseInsensitive instanceof Boolean) || choices == null ||
                choices.collect { String choice -> choice.toLowerCase(Locale.ROOT) }.toSet().size() < choices.size()) {
            throw new Refused("${what} does not say with true or false whether it takes its choices in any case, or takes in any case choices " +
                    'it has none of or that differ only in case')
        }
        return true
    }

    // the rules the options and the parameters of a tool keep together
    private static void together(List<Option> options, List<Parameter> parameters) throws Refused {
        Set<String> names = new HashSet<>()
        for (Option option : options) {
            for (String name : option.names) {
                if (!names.add(name)) {
                    throw new Refused('its options have a name two of them have')
                }
            }
        }
        Set<String> labels = new HashSet<>()
        boolean optional = false
        for (int index = 0; index < parameters.size(); index++) {
            Parameter parameter = parameters.get(index)
            if (!labels.add(parameter.label)) {
                throw new Refused('its parameters have a label two of them have')
            }
            if (parameter.isMany() && index < parameters.size() - 1) {
                throw new Refused('it has a parameter that takes any number of values before its last')
            }
            if (optional && parameter.isRequired()) {
                throw new Refused('it has a parameter that has to be given after one that can be left out')
            }
            optional = optional || !parameter.isRequired()
        }
    }

    private static Map<String, Boolean> hints(Object declared) throws Refused {
        if (declared == null) {
            return null
        }
        if (!(declared instanceof Map) || !HINT_NAMES.every { String hint -> ((Map) declared).get(hint) instanceof Boolean }) {
            throw new Refused("its hints are not an object with ${HINT_NAMES.join(', ')}, each true or false")
        }
        Map<String, Boolean> hints = new LinkedHashMap<>()
        for (String hint : HINT_NAMES) {
            hints.put(hint, (Boolean) ((Map) declared).get(hint))
        }
        return hints
    }

    private static boolean readsInput(Map<String, Object> tool) throws Refused {
        Object declared = tool.get(READS_INPUT)
        if (declared != null && !(declared instanceof Boolean)) {
            throw new Refused('it does not say with true or false whether it reads what is typed')
        }
        return declared == Boolean.TRUE
    }

    private static List<String> incomplete(Map<String, Object> tool) throws Refused {
        Object listed = tool.get(INCOMPLETE)
        if (listed == null) {
            return []
        }
        if (!(listed instanceof List) || ((List) listed).size() > LACKING.size() * 2 || !LACKING.containsAll((List) listed)) {
            throw new Refused("what it says it lacks is not a list of ${LACKING.join(', ')}")
        }
        return LACKING.findAll { String lacking -> ((List) listed).contains(lacking) }
    }

    /**
     * @param name a name of an option, with its hyphens
     * @return whether it is a long name of a declared option, as {@code --since} or {@code --dataSource}
     */
    static boolean isLongName(String name) {
        return name.length() <= MAX_OPTION_NAME && LONG_NAME.matcher(name).matches()
    }

    /**
     * @param name a name of an option, with its hyphen
     * @return whether it is a short name of a declared option, as {@code -s}
     */
    static boolean isShortName(String name) {
        return SHORT_NAME.matcher(name).matches()
    }

    /**
     * @param value a value given for an option or a parameter that takes a whole number
     * @return whether it is one: an optional sign and ASCII digits, within the range of a {@code long}
     */
    static boolean isWholeNumber(String value) {
        if (!WHOLE_NUMBER.matcher(value).matches()) {
            return false
        }
        try {
            Long.parseLong(value)
            return true
        } catch (NumberFormatException ignored) {
            return false
        }
    }

    // one line that can be shown: no control or format character, no line separator, nothing unassigned
    private static boolean isShown(String text) {
        int index = 0
        while (index < text.length()) {
            int codePoint = text.codePointAt(index)
            if (Character.getType(codePoint) in NOT_SHOWN) {
                return false
            }
            index += Character.charCount(codePoint)
        }
        return true
    }

    /**
     * Says what is wrong with the arguments a tool is given, by the rules the class that runs the tool
     * holds them to: read from left to right, the first that is wrong is said; then a required option
     * that is missing, in the order they are declared; then the parameters, in their order; then the
     * arguments more than the parameters take. Whether a whole number fits a field of the type
     * {@code int} is not said: only the class that runs the tool knows the type of its field.
     *
     * @param given the arguments after the name of the tool
     * @return what is wrong, as the end of the line {@code | Error NAME: <what>. ...}, which repeats no
     * value that was given; {@code null} when nothing is, and for a tool that declares nothing
     */
    String refusal(List<String> given) {
        if (!isDeclaring()) {
            return null
        }
        Set<Option> seen = new HashSet<>()
        List<String> positional = []
        boolean ended = false
        for (int index = 0; index < given.size(); index++) {
            String argument = given.get(index)
            if (!ended && argument == '--') {
                ended = true
                continue
            }
            if (!ended && isSystemProperty(argument)) {
                continue
            }
            if (ended || argument.length() < 2 || !argument.startsWith('-') || (NEGATIVE_NUMBER.matcher(argument).matches() && !byName.containsKey(argument))) {
                positional.add(argument)
                continue
            }
            int equals = argument.indexOf('=')
            String name = equals < 0 ? argument : argument.substring(0, equals)
            Option option = byName.get(name)
            if (option == null) {
                return "${SHOWN.matcher(name).matches() ? name : 'an option it was given'} is not one of its options".toString()
            }
            if (seen.contains(option) && !option.repeatable) {
                return "${name} is given more than once".toString()
            }
            seen.add(option)
            if (option.isSwitch) {
                if (equals >= 0) {
                    return "${name} takes no value".toString()
                }
                continue
            }
            String value
            if (equals >= 0) {
                value = argument.substring(equals + 1)
            } else {
                if (index + 1 >= given.size() || namesAnOption(given.get(index + 1))) {
                    return "${name} needs a value".toString()
                }
                value = given.get(++index)
            }
            String wrong = wrongValue(name, value, option.type, option.choices, option.caseInsensitive)
            if (wrong != null) {
                return wrong
            }
        }
        for (Option option : options) {
            if (option.required && !seen.contains(option)) {
                return "${option.longName} is required".toString()
            }
        }
        int at = 0
        for (Parameter parameter : parameters) {
            if (parameter.isMany()) {
                if (at >= positional.size() && parameter.isRequired()) {
                    return "${parameter.label} is required".toString()
                }
                // each value of the rest is checked as the parameter declares it, and given to the tool as text
                while (at < positional.size()) {
                    String wrong = wrongValue(parameter.label, positional.get(at++), parameter.type, parameter.choices, parameter.caseInsensitive)
                    if (wrong != null) {
                        return wrong
                    }
                }
            } else if (at < positional.size()) {
                String wrong = wrongValue(parameter.label, positional.get(at++), parameter.type, parameter.choices, parameter.caseInsensitive)
                if (wrong != null) {
                    return wrong
                }
            } else if (parameter.isRequired()) {
                return "${parameter.label} is required".toString()
            }
        }
        if (at < positional.size()) {
            int most = parameters.size()
            return most == 0 ? "it takes no parameters, and was given ${positional.size()}".toString() :
                    "it takes at most ${most} parameter${most == 1 ? '' : 's'}, and was given ${positional.size()}".toString()
        }
        return null
    }

    /**
     * Says whether the arguments of a tool that declares them ask for its help: {@code --help}, which no
     * tool declares, or {@code -h} where the tool does not declare it, given where an option stands. After
     * {@code --}, and where it is the value of the option before it, it is no such question, as the class that
     * runs the tool reads it.
     *
     * @param given the arguments after the name of the tool
     * @return whether they ask for the help; {@code false} for a tool that declares nothing, which is given them
     */
    boolean asksForHelp(List<String> given) {
        if (!isDeclaring()) {
            return false
        }
        for (int index = 0; index < given.size(); index++) {
            String argument = given.get(index)
            if (argument == '--') {
                return false
            }
            if (isSystemProperty(argument) || argument.length() < 2 || !argument.startsWith('-') ||
                    (NEGATIVE_NUMBER.matcher(argument).matches() && !byName.containsKey(argument))) {
                continue
            }
            if (HELP.contains(argument) && !byName.containsKey(argument)) {
                return true
            }
            int equals = argument.indexOf('=')
            Option option = byName.get(equals < 0 ? argument : argument.substring(0, equals))
            // the value of an option that takes one is no option, whatever it looks like
            if (option != null && !option.isSwitch && equals < 0 && index + 1 < given.size() && !namesAnOption(given.get(index + 1))) {
                index++
            }
        }
        return false
    }

    private static boolean isSystemProperty(String argument) {
        return argument.length() > 3 && argument.startsWith('-D') && argument.indexOf('=') > 2
    }

    // an argument that names a declared option, or ends the options, is no value of the option before it
    private boolean namesAnOption(String argument) {
        if (argument == '--') {
            return true
        }
        int equals = argument.indexOf('=')
        return byName.containsKey(equals < 0 ? argument : argument.substring(0, equals))
    }

    private static String wrongValue(String name, String value, String type, List<String> choices, boolean caseInsensitive) {
        if (type == TYPE_INTEGER) {
            return isWholeNumber(value) ? null : "${name} takes a whole number".toString()
        }
        if (choices != null && !(caseInsensitive ? choices.any { String choice -> choice.equalsIgnoreCase(value) } : choices.contains(value))) {
            return "${name} takes one of ${choices.join(', ')}".toString()
        }
        return null
    }

    /**
     * @return the options as {@code help --json} describes an option of a command of the command line,
     * with every member, and in its order: by the longest name of each without its hyphens, ignoring case
     */
    List<Map<String, Object>> describedOptions() {
        List<Option> sorted = new ArrayList<>(options)
        sorted.sort { Option one, Option other ->
            String first = one.longestName.replaceFirst(/^-+/, '')
            String second = other.longestName.replaceFirst(/^-+/, '')
            int ignoringCase = String.CASE_INSENSITIVE_ORDER.compare(first, second)
            ignoringCase != 0 ? ignoringCase : one.longestName <=> other.longestName
        }
        return sorted.collect { Option option -> option.describe() }
    }

    /**
     * @return the parameters as {@code help --json} describes a parameter of a command of the command
     * line, with every member, in their order
     */
    List<Map<String, Object>> describedParameters() {
        return parameters.collect { Parameter parameter -> parameter.describe() }
    }

    /**
     * Adds the options and the parameters to the command of a tool, for its help and for completion:
     * nothing the command is given is read by them, since every argument after the name of a tool is the
     * tool's, and nothing picocli would check of them is checked. An option that has a name of an option
     * the command line gives every command ({@code --plain-output}, {@code --non-interactive}) is left out
     * of the help, where the two would be one; it is the tool's all the same.
     *
     * @param spec the command of the tool
     * @param taken the names of the options the command line gives every command
     */
    void addTo(CommandLine.Model.CommandSpec spec, Collection<String> taken) {
        for (Option option : options) {
            if (option.names.any { String name -> taken.contains(name) }) {
                continue
            }
            // one chain, as the builder of picocli is typed: a switch takes no value, and its label is never shown
            spec.addOption(CommandLine.Model.OptionSpec.builder(option.names as String[])
                    .type(option.isSwitch ? boolean : option.repeatable ? List : String)
                    .auxiliaryTypes(String)
                    .arity(option.isSwitch ? SWITCH : ONE)
                    .required(option.required)
                    .paramLabel(option.label ?: 'VALUE')
                    .description(shown(option.shownDescription()))
                    .completionCandidates(option.choices)
                    .build())
        }
        // what the command line answers itself, as for every command it has (asksForHelp)
        List<String> help = HELP.findAll { String name -> !byName.containsKey(name) && !taken.contains(name) }
        if (!help.isEmpty()) {
            spec.addOption(CommandLine.Model.OptionSpec.builder(help.reverse() as String[])
                    .usageHelp(true)
                    .type(boolean)
                    .arity(SWITCH)
                    .description('Show this help message and exit.')
                    .build())
        }
        for (Parameter parameter : parameters) {
            spec.addPositional(CommandLine.Model.PositionalParamSpec.builder()
                    .type(parameter.isMany() ? List : String)
                    .auxiliaryTypes(String)
                    .arity(parameter.arity)
                    .paramLabel(parameter.label)
                    .description(shown(parameter.shownDescription()))
                    .completionCandidates(parameter.choices)
                    .build())
        }
    }

    // text of a declaration, as the help of the command line shows it as it is: nothing in it is markup
    private static String shown(String text) {
        return text.replace('@|', '@ |')
    }

    // what the help says of the choices of an option or a parameter
    private static String possibleValues(List<String> choices, boolean caseInsensitive) {
        return choices == null ? null : "Possible values${caseInsensitive ? ', in any case' : ''}: ${choices.join(', ')}".toString()
    }

    // one sentence after another, each with a full stop
    private static String sentences(List<String> parts) {
        return parts.findAll { String part -> part }.collect { String part -> part.endsWith('.') || part.endsWith('?') || part.endsWith('!') ? part : part + '.' }
                .join(' ')
    }

    /** An option a tool declares. */
    static final class Option {

        /** Every name of the option, with its hyphens, in the order they are declared. */
        final List<String> names

        /** Whether the option takes no value. */
        final boolean isSwitch

        /** What a value of the option is: {@code boolean} for a switch, {@code string} or {@code integer}. */
        final String type

        /** The label of the value: the one declared, or the first long name in upper case; {@code null} for a switch. */
        final String label

        /** Whether the option has to be given. */
        final boolean required

        /** The description, or {@code null} for none. */
        final String description

        /** Whether the option can be given more than once, each time with a value. */
        final boolean repeatable

        /** Every value the option takes, or {@code null} where it takes any. */
        final List<String> choices

        /** Whether the option takes its choices whatever their case. */
        final boolean caseInsensitive

        /** The value the option has when it is not given, as the tool declares it, or {@code null}. */
        final String defaultValue

        private Option(List<String> names, boolean isSwitch, String type, String label, boolean required, String description, boolean repeatable,
                       List<String> choices, boolean caseInsensitive, String defaultValue) {
            this.names = new ArrayList<String>(names).asImmutable()
            this.isSwitch = isSwitch
            this.type = type
            this.label = label
            this.required = required
            this.description = description
            this.repeatable = repeatable
            this.choices = choices
            this.caseInsensitive = caseInsensitive
            this.defaultValue = defaultValue
        }

        /**
         * @return the first long name of the option, which a refusal names it by
         */
        String getLongName() {
            return names.find { String name -> isLongName(name) }
        }

        /**
         * @return the longest name of the option, the first of those of that length
         */
        String getLongestName() {
            String longest = names.get(0)
            for (String name : names) {
                if (name.length() > longest.length()) {
                    longest = name
                }
            }
            return longest
        }

        /**
         * @return the option as {@code help --json} describes an option, with every member, and
         * {@code caseInsensitive} after the choices where it takes them in any case
         */
        Map<String, Object> describe() {
            Map<String, Object> described = new LinkedHashMap<>()
            described.put('names', names)
            described.put('arity', isSwitch ? SWITCH : ONE)
            described.put('type', type)
            described.put('label', label)
            described.put('required', required)
            described.put('description', description ?: '')
            described.put('repeatable', repeatable)
            described.put('split', null)
            described.put('choices', choices)
            if (caseInsensitive) {
                described.put(CASE_INSENSITIVE, true)
            }
            described.put('default', defaultValue)
            return described
        }

        // what the help says of the option
        private String shownDescription() {
            return sentences([description, possibleValues(choices, caseInsensitive),
                              defaultValue == null ? null : "Default: ${defaultValue}".toString(), required ? 'Required' : null,
                              repeatable ? 'Can be given more than once' : null])
        }
    }

    /** A parameter a tool declares. */
    static final class Parameter {

        /** What the help calls the parameter. */
        final String label

        /** How many values it takes: {@code 1}, {@code 0..1}, {@code 1..*} or {@code 0..*}. */
        final String arity

        /** What a value of it is: {@code string} or {@code integer}. */
        final String type

        /** The description, or {@code null} for none. */
        final String description

        /** Every value it takes, or {@code null} where it takes any. */
        final List<String> choices

        /** Whether it takes its choices whatever their case. */
        final boolean caseInsensitive

        private Parameter(String label, String arity, String type, String description, List<String> choices, boolean caseInsensitive) {
            this.label = label
            this.arity = arity
            this.type = type
            this.description = description
            this.choices = choices
            this.caseInsensitive = caseInsensitive
        }

        /**
         * @return whether the parameter has to be given
         */
        boolean isRequired() {
            return arity.startsWith('1')
        }

        /**
         * @return whether the parameter takes the rest of the parameters, any number of them
         */
        boolean isMany() {
            return arity.endsWith('*')
        }

        /**
         * @return the parameter as {@code help --json} describes a parameter, with every member, and
         * {@code caseInsensitive} after the choices where it takes them in any case
         */
        Map<String, Object> describe() {
            Map<String, Object> described = new LinkedHashMap<>()
            described.put('label', label)
            described.put('arity', arity)
            described.put('type', type)
            described.put('description', description ?: '')
            described.put('repeatable', isMany())
            described.put('split', null)
            described.put('choices', choices)
            if (caseInsensitive) {
                described.put(CASE_INSENSITIVE, true)
            }
            return described
        }

        private String shownDescription() {
            return sentences([description, possibleValues(choices, caseInsensitive)])
        }
    }
}
