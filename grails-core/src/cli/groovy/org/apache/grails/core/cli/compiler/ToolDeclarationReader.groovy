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
package org.apache.grails.core.cli.compiler

import java.lang.reflect.Modifier

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.GenericsType
import org.codehaus.groovy.ast.expr.AnnotationConstantExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.UnaryMinusExpression

import org.apache.grails.gradle.model.cli.ToolDescriptor

/**
 * Reads what a tool declares of its command line and of what it does from its source, without running
 * it: the {@code @ToolOption} and {@code @ToolParameter} on the fields of the tool and of the classes it
 * extends and on its class (where a Groovy {@code @AnnotationCollector} has put them as well), and its
 * {@code @ToolHints}. What a declaration does against a rule is a {@link Breach} that names the field, or
 * the declaration on the class, and the rule; the rules are those of {@link ToolDescriptor} and those of
 * the types of the fields.
 *
 * <p>The options are in the order they are declared: those on the class first, then those on the fields
 * of the class at the root of the tool's classes down to the tool, each in the order of its fields. The
 * parameters are in the order of their indexes.</p>
 *
 * @since 9.0
 */
@PackageScope
@CompileStatic
final class ToolDeclarationReader {

    static final String TOOL_OPTION = 'org.apache.grails.core.cli.ToolOption'

    static final String TOOL_OPTIONS = 'org.apache.grails.core.cli.ToolOptions'

    static final String TOOL_PARAMETER = 'org.apache.grails.core.cli.ToolParameter'

    static final String TOOL_PARAMETERS = 'org.apache.grails.core.cli.ToolParameters'

    static final String TOOL_HINTS = 'org.apache.grails.core.cli.ToolHints'

    // what the types of a field can be, as the rules say them
    static final String OPTION_TYPES = 'String, boolean, Boolean, int, Integer, long, Long, an enum or List<String>'

    static final String PARAMETER_TYPES = 'String, int, Integer, long, Long, an enum or List<String>'

    private static final List<String> WHOLE_NUMBERS = ['int', 'java.lang.Integer', 'long', 'java.lang.Long'].asImmutable()

    private static final List<String> SWITCHES = ['boolean', 'java.lang.Boolean'].asImmutable()

    private ToolDeclarationReader() {
    }

    /**
     * A declaration that breaks a rule: what the compiler makes an error of.
     */
    static final class Breach {

        /** Where it is: the annotation, the field, or the class. */
        final ASTNode node

        /** What is wrong, naming the field or the declaration on the class, and the rule. */
        final String message

        Breach(ASTNode node, String message) {
            this.node = node
            this.message = message
        }
    }

    /**
     * What a tool declares.
     */
    static final class Read {

        /** The options that keep the rules, in the order they are declared. */
        final List<Map<String, Object>> options = []

        /** The parameters that keep the rules, in the order of their indexes. */
        final List<Map<String, Object>> parameters = []

        /** The hints, or {@code null} when the tool declares none. */
        Map<String, Boolean> hints

        /** Whether the tool declares an option or a parameter, kept or not. */
        boolean declaring

        /** What breaks a rule. */
        final List<Breach> breaches = []

        /**
         * @return the declarations as a descriptor has them; {@code null} when something breaks a rule
         */
        ToolDescriptor.Declarations declarations(boolean readsInput, List<String> incomplete) {
            breaches.isEmpty() ? new ToolDescriptor.Declarations(options, parameters, hints, readsInput, incomplete) : null
        }
    }

    /**
     * @param classNode a tool class
     * @return what it declares
     */
    static Read read(ClassNode classNode) {
        Read read = new Read()
        String className = classNode.nameWithoutPackage
        int place = 0
        for (AnnotationNode annotation : declared(classNode, TOOL_OPTION, TOOL_OPTIONS)) {
            place++
            read.declaring = true
            String named = shownName(annotation)
            String what = named != null ? "@ToolOption ${named} on the class ${className}" : "The ${ordinal(place)} @ToolOption on the class ${className}"
            Map<String, Object> option = classOption(annotation, what, read)
            keep(read, read.options, option, annotation, what, ToolDescriptor.optionRefusal(option ?: [:]))
        }
        Map<Integer, Map<String, Object>> parameters = new TreeMap<Integer, Map<String, Object>>()
        Map<Integer, String> placed = new HashMap<Integer, String>()
        place = 0
        for (AnnotationNode annotation : declared(classNode, TOOL_PARAMETER, TOOL_PARAMETERS)) {
            place++
            read.declaring = true
            String what = "The ${ordinal(place)} @ToolParameter on the class ${className}"
            Map<String, Object> parameter = classParameter(annotation, what, read)
            index(read, parameters, placed, annotation, what, parameter)
        }
        List<ClassNode> hierarchy = []
        for (ClassNode type = classNode; type != null && type != ClassHelper.OBJECT_TYPE; type = type.superClass) {
            hierarchy.add(0, type)
        }
        for (ClassNode type : hierarchy) {
            for (FieldNode field : type.fields) {
                List<AnnotationNode> options = annotations(field, TOOL_OPTION)
                List<AnnotationNode> fieldParameters = annotations(field, TOOL_PARAMETER)
                if (options.isEmpty() && fieldParameters.isEmpty()) {
                    continue
                }
                read.declaring = true
                String what = "${type.nameWithoutPackage}.${field.name}"
                if (options.size() + fieldParameters.size() > 1) {
                    read.breaches << new Breach(field, "${what} is declared more than once: a field is one option or one parameter.".toString())
                    continue
                }
                String fieldBreach = fieldBreach(field)
                if (fieldBreach != null) {
                    read.breaches << new Breach(field, "${what} ${fieldBreach}.".toString())
                    continue
                }
                if (!options.isEmpty()) {
                    String whatOption = "@ToolOption on ${what}"
                    Map<String, Object> option = fieldOption(options[0], field, whatOption, read)
                    if (option != null) {
                        keep(read, read.options, option, options[0], whatOption, ToolDescriptor.optionRefusal(option))
                    }
                } else {
                    String whatParameter = "@ToolParameter on ${what}"
                    Map<String, Object> parameter = fieldParameter(fieldParameters[0], field, whatParameter, read)
                    index(read, parameters, placed, fieldParameters[0], whatParameter, parameter)
                }
            }
        }
        List<Integer> indexes = new ArrayList<Integer>(parameters.keySet())
        for (int expected = 0; expected < indexes.size(); expected++) {
            if (indexes[expected] != expected) {
                read.breaches << new Breach(classNode, "The parameters of ${className} have the indexes ${indexes.join(', ')}: ".toString() +
                        'they are counted from 0, without gaps.')
                break
            }
        }
        read.parameters.addAll(parameters.values())
        if (read.breaches.isEmpty()) {
            String together = ToolDescriptor.declarationsRefusal((List<Map>) read.options, (List<Map>) read.parameters)
            if (together != null) {
                read.breaches << new Breach(classNode, "${className} ${together}.".toString())
            }
        }
        AnnotationNode hints = annotations(classNode, TOOL_HINTS)[0]
        if (hints != null) {
            Map<String, Boolean> declaredHints = new LinkedHashMap<String, Boolean>()
            for (String hint : ToolDescriptor.HINTS) {
                Object value = constant(hints.getMember(hint))
                if (hints.getMember(hint) == null) {
                    declaredHints.put(hint, ToolDescriptor.HINT_DEFAULTS.get(hint))
                } else if (value instanceof Boolean) {
                    declaredHints.put(hint, (Boolean) value)
                } else {
                    read.breaches << new Breach(hints, "@ToolHints on the class ${className} has a ${hint} that is not the constant true or false.".toString())
                }
            }
            read.hints = declaredHints
        }
        read
    }

    // an option or a parameter kept, or the rule it breaks
    private static void keep(Read read, List<Map<String, Object>> kept, Map<String, Object> declared, ASTNode node, String what, String refusal) {
        if (declared == null) {
            return
        }
        if (refusal != null) {
            read.breaches << new Breach(node, "${what} ${refusal}.".toString())
            return
        }
        kept << declared
    }

    private static void index(Read read, Map<Integer, Map<String, Object>> parameters, Map<Integer, String> placed, AnnotationNode annotation,
                              String what, Map<String, Object> parameter) {
        if (parameter == null) {
            return
        }
        String refusal = ToolDescriptor.parameterRefusal(parameter)
        if (refusal != null) {
            read.breaches << new Breach(annotation, "${what} ${refusal}.".toString())
            return
        }
        Object index = constant(annotation.getMember('index'))
        if (!(index instanceof Integer) || (Integer) index < 0) {
            read.breaches << new Breach(annotation, "${what} has an index that is not a whole number from 0.".toString())
            return
        }
        String before = placed.get((Integer) index)
        if (before != null) {
            read.breaches << new Breach(annotation, "${what} has the index ${index}, which ${before} has too: each parameter has an index of its own.".toString())
            return
        }
        placed.put((Integer) index, what)
        parameters.put((Integer) index, parameter)
    }

    // the annotations of a type on a node, also those a container holds and those a collector put there
    private static List<AnnotationNode> declared(ClassNode classNode, String type, String container) {
        List<AnnotationNode> found = []
        for (AnnotationNode annotation : classNode.annotations) {
            String name = annotation.classNode.name
            if (name == type) {
                found << annotation
            } else if (name == container) {
                Expression value = annotation.getMember('value')
                List<Expression> held = value instanceof ListExpression ? ((ListExpression) value).expressions : (value == null ? [] : [value])
                for (Expression each : held) {
                    if (each instanceof AnnotationConstantExpression) {
                        found << (AnnotationNode) ((AnnotationConstantExpression) each).value
                    }
                }
            }
        }
        found
    }

    private static List<AnnotationNode> annotations(AnnotatedNode node, String type) {
        node.annotations.findAll { AnnotationNode annotation -> annotation.classNode.name == type }
    }

    // why a field cannot be given a value, or null
    private static String fieldBreach(FieldNode field) {
        if (Modifier.isStatic(field.modifiers)) {
            return 'is static, and a value is given to the tool: declare the option or the parameter on a field of the tool'
        }
        if (Modifier.isFinal(field.modifiers)) {
            return 'is final, so it cannot be given its value'
        }
        null
    }

    private static Map<String, Object> classOption(AnnotationNode annotation, String what, Read read) {
        Map<String, Object> option = common(annotation, what, read, ['names', 'description', 'required', 'label', 'defaultValue', 'arity', 'type', 'choices', 'caseInsensitive'])
        if (option == null) {
            return null
        }
        String arity = (String) option.remove('arity')
        if (!arity) {
            read.breaches << new Breach(annotation, "${what} gives no arity, which an option on the class has to: '0' for a switch, '1' for an option that takes a value.".toString())
            return null
        }
        option.put('arity', arity)
        String type = (String) option.remove('type')
        option.put('type', type ?: (arity == ToolDescriptor.ARITY_SWITCH ? ToolDescriptor.TYPE_BOOLEAN : ToolDescriptor.TYPE_STRING))
        valueSet(option, (List<String>) option.remove('choices'), (Boolean) option.remove('caseInsensitive'))
        valueMembers(option)
        option
    }

    private static Map<String, Object> fieldOption(AnnotationNode annotation, FieldNode field, String what, Read read) {
        Map<String, Object> option = common(annotation, what, read, ['names', 'description', 'required', 'label', 'defaultValue', 'arity', 'type', 'choices', 'caseInsensitive'])
        if (option == null) {
            return null
        }
        if (option.remove('arity')) {
            read.breaches << new Breach(annotation, "${what} gives an arity, which on a field the type of the field says: leave it out.".toString())
            return null
        }
        ClassNode type = field.type
        List<String> choices = fieldChoices(annotation, field, option, what, read)
        if (choices == null) {
            return null
        }
        if (SWITCHES.contains(type.name)) {
            option.put('arity', ToolDescriptor.ARITY_SWITCH)
            option.put('type', ToolDescriptor.TYPE_BOOLEAN)
        } else if (type.name == 'java.lang.String' || WHOLE_NUMBERS.contains(type.name) || type.isEnum()) {
            option.put('arity', ToolDescriptor.ARITY_ONE)
            option.put('type', WHOLE_NUMBERS.contains(type.name) ? ToolDescriptor.TYPE_INTEGER : ToolDescriptor.TYPE_STRING)
            if (type.isEnum()) {
                option.put('choices', constantsOf(type))
            }
            if (option.get('defaultValue') == null) {
                option.put('defaultValue', initialValue(field))
            }
        } else if (isListOfStrings(type)) {
            option.put('arity', ToolDescriptor.ARITY_ONE)
            option.put('type', ToolDescriptor.TYPE_STRING)
            option.put('repeatable', Boolean.TRUE)
        } else {
            read.breaches << new Breach(field, "${what} is on a field of the type ${typeName(type)}, which no option takes: ${OPTION_TYPES}.".toString())
            return null
        }
        valueSet(option, choices, (Boolean) option.remove('caseInsensitive'))
        valueMembers(option)
        option
    }

    private static Map<String, Object> classParameter(AnnotationNode annotation, String what, Read read) {
        Map<String, Object> parameter = common(annotation, what, read, ['label', 'description', 'required', 'arity', 'type', 'choices', 'caseInsensitive'])
        if (parameter == null) {
            return null
        }
        String arity = (String) parameter.remove('arity')
        boolean required = parameter.remove('required') != Boolean.FALSE
        if (!arity) {
            read.breaches << new Breach(annotation, "${what} gives no arity, which a parameter on the class has to: '1' for one value, '1..*' for the rest.".toString())
            return null
        }
        if (arity.startsWith('0') && required) {
            read.breaches << new Breach(annotation, "${what} has the arity ${arity}, which can be left out, and is required: set required = false.".toString())
            return null
        }
        parameter.put('arity', required ? arity : (arity == ToolDescriptor.ARITY_ONE ? ToolDescriptor.ARITY_OPTIONAL :
                arity == ToolDescriptor.ARITY_SOME ? ToolDescriptor.ARITY_ANY : arity))
        String type = (String) parameter.remove('type')
        parameter.put('type', type ?: ToolDescriptor.TYPE_STRING)
        valueSet(parameter, (List<String>) parameter.remove('choices'), (Boolean) parameter.remove('caseInsensitive'))
        parameter
    }

    private static Map<String, Object> fieldParameter(AnnotationNode annotation, FieldNode field, String what, Read read) {
        Map<String, Object> parameter = common(annotation, what, read, ['label', 'description', 'required', 'arity', 'type', 'choices', 'caseInsensitive'])
        if (parameter == null) {
            return null
        }
        boolean required = parameter.remove('required') != Boolean.FALSE
        if (parameter.remove('arity')) {
            read.breaches << new Breach(annotation, "${what} gives an arity, which on a field the type of the field says: leave it out.".toString())
            return null
        }
        ClassNode type = field.type
        List<String> choices = fieldChoices(annotation, field, parameter, what, read)
        if (choices == null) {
            return null
        }
        if (type.name == 'java.lang.String' || WHOLE_NUMBERS.contains(type.name) || type.isEnum()) {
            parameter.put('arity', required ? ToolDescriptor.ARITY_ONE : ToolDescriptor.ARITY_OPTIONAL)
            parameter.put('type', WHOLE_NUMBERS.contains(type.name) ? ToolDescriptor.TYPE_INTEGER : ToolDescriptor.TYPE_STRING)
            if (type.isEnum()) {
                parameter.put('choices', constantsOf(type))
            }
        } else if (isListOfStrings(type)) {
            parameter.put('arity', required ? ToolDescriptor.ARITY_SOME : ToolDescriptor.ARITY_ANY)
            parameter.put('type', ToolDescriptor.TYPE_STRING)
        } else {
            read.breaches << new Breach(field, "${what} is on a field of the type ${typeName(type)}, which no parameter takes: ${PARAMETER_TYPES}.".toString())
            return null
        }
        valueSet(parameter, choices, (Boolean) parameter.remove('caseInsensitive'))
        parameter
    }

    // The choices a declaration on a field gives itself: on a field of the type String alone, since the type of any other
    // field says what it takes, the constants of an enum included; the type is the field's. An empty list for none, and
    // null when the declaration breaks one of these rules.
    private static List<String> fieldChoices(AnnotationNode annotation, FieldNode field, Map<String, Object> declared, String what, Read read) {
        if (declared.remove('type')) {
            read.breaches << new Breach(annotation, "${what} gives a type, which on a field the type of the field says: leave it out.".toString())
            return null
        }
        List<String> choices = (List<String>) declared.remove('choices') ?: []
        if (!choices.isEmpty() && field.type.name != 'java.lang.String') {
            read.breaches << new Breach(annotation, "${what} gives choices, which only a field of the type String takes: the type of any other field says what it takes, and an enum has its constants for choices.".toString())
            return null
        }
        if (declared.get('caseInsensitive') == Boolean.TRUE && choices.isEmpty()) {
            read.breaches << new Breach(annotation, "${what} is case-insensitive without choices of its own: only the choices it gives are taken in any case.".toString())
            return null
        }
        choices
    }

    // the choices a declaration gives, and whether they are taken in any case, as a descriptor has them
    private static void valueSet(Map<String, Object> declared, List<String> choices, Boolean caseInsensitive) {
        if (choices) {
            declared.put('choices', choices)
        }
        if (caseInsensitive) {
            declared.put(ToolDescriptor.MEMBER_CASE_INSENSITIVE, Boolean.TRUE)
        }
    }

    // the members of an annotation as constants, or null when one is not a constant
    private static Map<String, Object> common(AnnotationNode annotation, String what, Read read, List<String> members) {
        Map<String, Object> declared = new LinkedHashMap<String, Object>()
        for (String member : members) {
            Expression expression = annotation.getMember(member)
            if (expression == null) {
                continue
            }
            Object value = member == 'names' || member == 'choices' ? names(expression) : constant(expression)
            if (value == null) {
                read.breaches << new Breach(annotation, "${what} has a ${member} that is not a constant: it is read without running the tool.".toString())
                return null
            }
            declared.put(member, value)
        }
        declared
    }

    // what a value an option takes says beside its names: its label, and what it is when it is not given
    private static void valueMembers(Map<String, Object> option) {
        String defaultValue = (String) option.remove('defaultValue')
        if (option.get('label') == '') {
            option.remove('label')
        }
        if (option.get('description') == '') {
            option.remove('description')
        }
        if (option.get('arity') == ToolDescriptor.ARITY_ONE) {
            if (!option.containsKey('label')) {
                String label = ToolDescriptor.defaultLabel((List<String>) option.get('names'))
                if (label != null) {
                    option.put('label', label)
                }
            }
            if (defaultValue) {
                option.put('default', defaultValue)
            }
        } else if (defaultValue) {
            option.put('default', defaultValue)
        }
    }

    // the names of an option: a list of constants, or one
    private static List<String> names(Expression expression) {
        List<Expression> each = expression instanceof ListExpression ? ((ListExpression) expression).expressions : [expression]
        List<String> names = []
        for (Expression name : each) {
            Object value = constant(name)
            if (!(value instanceof String)) {
                return null
            }
            names << (String) value
        }
        names
    }

    private static Object constant(Expression expression) {
        if (expression instanceof ConstantExpression) {
            return ((ConstantExpression) expression).value
        }
        null
    }

    // the value a field of an option starts with, as the option's default, where it is a constant of its type
    private static String initialValue(FieldNode field) {
        Expression initial = field.hasInitialExpression() ? field.initialExpression : null
        if (initial instanceof ConstantExpression) {
            Object value = ((ConstantExpression) initial).value
            return value instanceof String || value instanceof Integer || value instanceof Long ? value.toString() : null
        }
        if (initial instanceof UnaryMinusExpression && ((UnaryMinusExpression) initial).expression instanceof ConstantExpression) {
            Object value = ((ConstantExpression) ((UnaryMinusExpression) initial).expression).value
            return value instanceof Integer || value instanceof Long ? "-${value}".toString() : null
        }
        if (initial instanceof PropertyExpression && field.type.isEnum()) {
            PropertyExpression property = (PropertyExpression) initial
            if (property.objectExpression instanceof ClassExpression && property.objectExpression.type.name == field.type.name &&
                    constantsOf(field.type).contains(property.propertyAsString)) {
                return property.propertyAsString
            }
        }
        null
    }

    // the constants of an enum, in their order
    static List<String> constantsOf(ClassNode enumType) {
        enumType.fields.findAll { FieldNode field -> field.isEnum() && field.type.name == enumType.name }
                .collect { FieldNode field -> field.name }
    }

    // the name of a type as Java names a generic type: java.util.List<java.lang.Integer>
    private static String typeName(ClassNode type) {
        GenericsType[] generics = type.genericsTypes
        if (generics == null || generics.length == 0) {
            return type.name
        }
        type.name + '<' + generics.collect { GenericsType generic -> generic.isWildcard() ? '?' : generic.type.name }.join(', ') + '>'
    }

    private static boolean isListOfStrings(ClassNode type) {
        if (type.name != 'java.util.List') {
            return false
        }
        GenericsType[] generics = type.genericsTypes
        generics != null && generics.length == 1 && !generics[0].isWildcard() && !generics[0].isPlaceholder() &&
                generics[0].type.name == 'java.lang.String'
    }

    // the first name of an option on the class, where it can be shown
    private static String shownName(AnnotationNode annotation) {
        Expression expression = annotation.getMember('names')
        List<String> names = expression == null ? null : names(expression)
        String first = names?.find { String name -> ToolDescriptor.isLongOptionName(name) }
        first
    }

    private static String ordinal(int place) {
        int lastTwo = place % 100
        String suffix = lastTwo in 11..13 ? 'th' : place % 10 == 1 ? 'st' : place % 10 == 2 ? 'nd' : place % 10 == 3 ? 'rd' : 'th'
        "${place}${suffix}".toString()
    }
}
