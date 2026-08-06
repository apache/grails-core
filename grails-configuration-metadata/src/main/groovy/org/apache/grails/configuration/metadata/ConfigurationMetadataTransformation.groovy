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
package org.apache.grails.configuration.metadata

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.GenericsType
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.messages.WarningMessage
import org.codehaus.groovy.syntax.SyntaxException
import org.codehaus.groovy.syntax.Token
import org.codehaus.groovy.syntax.Types
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

import static java.lang.reflect.Modifier.PRIVATE
import static java.lang.reflect.Modifier.FINAL
import static java.lang.reflect.Modifier.STATIC

/**
 * Embeds configuration metadata in each annotated Groovy class. Aggregation is deliberately
 * deferred to the Gradle task so incremental Groovy compilation never writes shared output.
 * This transformation runs during SEMANTIC_ANALYSIS, before {@code @Delegate} composes methods.
 */
@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
class ConfigurationMetadataTransformation implements ASTTransformation {

    static final String PAYLOAD_FIELD = '__grailsConfigurationMetadata'
    private static final String CONSTRUCTOR_BINDING =
            'org.springframework.boot.context.properties.bind.ConstructorBinding'
    private static final int SYNTHETIC = 0x00001000
    private static final String CONFIGURATION_PROPERTIES = 'org.springframework.boot.context.properties.ConfigurationProperties'
    private static final String NESTED_CONFIGURATION_PROPERTY =
            'org.springframework.boot.context.properties.NestedConfigurationProperty'

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        source.AST.classes.findAll { ClassNode node ->
            !node.interface && node.getAnnotations(ClassHelper.make(CONFIGURATION_PROPERTIES))
        }.each { ClassNode node -> addPayload(node, source) }
    }

    private static void addPayload(ClassNode node, SourceUnit source) {
        FieldNode existingField = node.getDeclaredField(PAYLOAD_FIELD)
        if (existingField != null) {
            source.addError(new SyntaxException(
                    "Configuration properties classes cannot declare reserved field '${PAYLOAD_FIELD}'",
                    existingField.lineNumber, existingField.columnNumber))
            return
        }
        def annotation = node.getAnnotations(ClassHelper.make(CONFIGURATION_PROPERTIES))[0]
        Expression prefixExpression = annotation.getMember('prefix') ?: annotation.getMember('value')
        if (prefixExpression != null && !(prefixExpression instanceof ConstantExpression)) {
            return
        }
        String prefix = prefixExpression == null ? '' : String.valueOf(((ConstantExpression) prefixExpression).value)
        warnForDelegateProperties(node, source)
        Map<String, List<Map<String, Object>>> metadata = metadata(
                node, prefix, node.name, new LinkedHashSet<String>())
        String payload = toJson([
                prefix: prefix,
                sourceType: node.name,
                groups: metadata.get('groups'),
                properties: metadata.get('properties')
        ])
        FieldNode field = node.addField(PAYLOAD_FIELD, PRIVATE | STATIC | FINAL | SYNTHETIC,
                ClassHelper.STRING_TYPE, new ConstantExpression(payload))
        field.synthetic = true
    }

    private static Map<String, List<Map<String, Object>>> metadata(ClassNode node, String prefix,
                                                                   String sourceType, Set<String> visiting) {
        if (!visiting.add(node.name)) {
            return [groups: [], properties: []]
        }
        Map<String, Map<String, Object>> bindable = [:]
        node.properties.findAll { PropertyNode property -> isBindableProperty(property) }.each {
            PropertyNode propertyNode ->
            FieldNode field = propertyNode.field
            Map<String, Object> propertyMetadata = [
                    propertyName: field.name, type: typeName(field.type), classNode: field.type,
                    nested: isNestedConfigurationProperty(node, field.type, field.annotations),
                    owner: node.name]
            Expression initialExpression = field.initialExpression
            if (initialExpression instanceof ConstantExpression && !initialExpression.isNullExpression()) {
                propertyMetadata.put('defaultValue', ((ConstantExpression) initialExpression).value)
            }
            bindable.put(field.name, propertyMetadata)
        }
        collectAccessorProperties(node, bindable, new LinkedHashSet<String>())

        List<Map<String, Object>> groups = []
        List<Map<String, Object>> properties = []
        bindable.values().sort { Map<String, Object> property -> property.propertyName as String }.each {
            Map<String, Object> property ->
            String propertyName = property.propertyName as String
            String name = prefix ? "${prefix}.${propertyName}" : propertyName
            ClassNode propertyType = property.classNode as ClassNode
            if (property.nested) {
                Map<String, List<Map<String, Object>>> nested = metadata(propertyType, name, sourceType, visiting)
                if (nested.get('groups') || nested.get('properties')) {
                    groups << [name: name, type: property.type, sourceType: sourceType]
                    groups.addAll(nested.get('groups'))
                    properties.addAll(nested.get('properties'))
                } else {
                    properties << scalarProperty(name, property)
                }
            } else {
                properties << scalarProperty(name, property)
            }
        }
        visiting.remove(node.name)
        [
                groups: groups.sort { Map<String, Object> group -> group.name as String },
                properties: properties.sort { Map<String, Object> property -> property.name as String }
        ]
    }

    private static Map<String, Object> scalarProperty(String name, Map<String, Object> property) {
        Map<String, Object> entry = [name: name, type: property.type]
        if (property.containsKey('defaultValue')) {
            entry.put('defaultValue', property.get('defaultValue'))
        }
        entry
    }

    private static boolean isBindableProperty(PropertyNode property) {
        FieldNode field = property.field
        boolean constructorBound = !field.final || constructorBoundProperties(field.owner).contains(field.name)
        !field.static && constructorBound && !field.name.startsWith('$') &&
                field.name != 'metaClass' && field.name != PAYLOAD_FIELD &&
                !isDelegate(field)
    }

    private static void warnForDelegateProperties(ClassNode node, SourceUnit source) {
        node.fields.each { FieldNode field ->
            if (isDelegate(field)) {
                String warning = "Configuration properties class uses @Delegate field '${node.name}.${field.name}', " +
                        'but SEMANTIC_ANALYSIS runs before @Delegate composition. ' +
                        'Add metadata for delegated properties to additional-spring-configuration-metadata.json.'
                source.errorCollector.addWarning(WarningMessage.LIKELY_ERRORS, warning,
                        Token.newSymbol(Types.UNKNOWN, field.lineNumber, field.columnNumber), source)
            }
        }
    }

    private static boolean isDelegate(FieldNode field) {
        field.annotations.any { AnnotationNode annotation ->
            annotation.classNode.name in ['groovy.lang.Delegate', 'groovy.transform.Delegate']
        }
    }

    private static Set<String> constructorBoundProperties(ClassNode owner) {
        List constructors = owner.declaredConstructors.findAll { constructor ->
            !constructor.synthetic && !constructor.private
        }
        List candidates = constructors.findAll { constructor -> constructor.parameters.length > 0 }
        List selected = candidates.findAll { constructor ->
            constructor.annotations.any { annotation -> annotation.classNode.name == CONSTRUCTOR_BINDING }
        }
        def bindingConstructor = selected.size() == 1 ? selected[0] :
                (!constructors.any { constructor -> constructor.parameters.length == 0 } && candidates.size() == 1 ?
                        candidates[0] : null)
        bindingConstructor ? bindingConstructor.parameters*.name as Set<String> : Collections.emptySet()
    }

    private static void collectAccessorProperties(ClassNode node, Map<String, Map<String, Object>> bindable,
                                                  Set<String> visited) {
        if (node == null || node.name == Object.name || !visited.add(node.name)) {
            return
        }
        node.methods.findAll { MethodNode method ->
            method.public && !method.static && method.name.startsWith('set') && method.name.length() > 3 &&
                    !(method.name in ['setGrailsApplication', 'setMetaClass']) && method.parameters.length == 1
        }.each { MethodNode method ->
            String propertySuffix = method.name.substring(3)
            String propertyName = decapitalize(propertySuffix)
            ClassNode propertyType = method.parameters[0].type
            MethodNode getter = findGetter(node, propertySuffix, propertyType)
            List<AnnotationNode> annotations = new ArrayList<AnnotationNode>(method.annotations)
            if (getter != null) {
                annotations.addAll(getter.annotations)
            }
            mergeAccessorProperty(node, bindable, propertyName, propertyType, annotations)
        }
        node.methods.findAll { MethodNode method -> isStandardGetter(method) &&
                isNestedConfigurationProperty(node, method.returnType, method.annotations) }.each { MethodNode getter ->
            String propertySuffix = getter.name.startsWith('get') ? getter.name.substring(3) : getter.name.substring(2)
            mergeAccessorProperty(node, bindable, decapitalize(propertySuffix), getter.returnType, getter.annotations)
        }
        collectAccessorProperties(node.superClass, bindable, visited)
        node.interfaces.each { ClassNode interfaceNode -> collectAccessorProperties(interfaceNode, bindable, visited) }
    }

    private static void mergeAccessorProperty(ClassNode owner, Map<String, Map<String, Object>> bindable,
                                              String propertyName, ClassNode propertyType,
                                              List<AnnotationNode> annotations) {
        Map<String, Object> existing = bindable.get(propertyName)
        if (existing == null) {
            bindable.put(propertyName, [propertyName: propertyName, type: typeName(propertyType), classNode: propertyType,
                                        nested: isNestedConfigurationProperty(owner, propertyType, annotations),
                                        owner: owner.name])
        } else if (existing.get('owner') == owner.name && typesMatch(existing.get('classNode') as ClassNode, propertyType)) {
            existing.put('nested', (existing.get('nested') as boolean) ||
                    isNestedConfigurationProperty(owner, propertyType, annotations))
        }
    }

    private static MethodNode findGetter(ClassNode node, String propertySuffix, ClassNode propertyType) {
        String getterName = "get${propertySuffix}"
        String booleanGetterName = "is${propertySuffix}"
        node.methods.find { MethodNode method ->
            method.name in [getterName, booleanGetterName] && isStandardGetter(method) &&
                    typesMatch(method.returnType, propertyType)
        }
    }

    private static boolean isStandardGetter(MethodNode method) {
        if (!method.public || method.static || method.parameters.length != 0) {
            return false
        }
        (method.name.startsWith('get') && method.name.length() > 3 && method.returnType != ClassHelper.VOID_TYPE) ||
                (method.name.startsWith('is') && method.name.length() > 2 && method.returnType == ClassHelper.boolean_TYPE)
    }

    private static boolean typesMatch(ClassNode first, ClassNode second) {
        first.name == second.name
    }

    private static boolean isNestedConfigurationProperty(ClassNode owner, ClassNode type,
                                                          List<AnnotationNode> annotations) {
        isInnerClassOf(type, owner) || annotations.any { AnnotationNode annotation ->
            annotation.classNode.name == NESTED_CONFIGURATION_PROPERTY
        }
    }

    private static boolean isInnerClassOf(ClassNode type, ClassNode owner) {
        type.name.startsWith(owner.name + '$')
    }

    private static String typeName(ClassNode type) {
        if (type.array) {
            return "${typeName(type.componentType)}[]"
        }
        String name = type.name
        GenericsType[] genericsTypes = type.genericsTypes
        if (genericsTypes) {
            name += '<' + genericsTypes.collect { GenericsType generic -> genericTypeName(generic) }.join(',') + '>'
        }
        name
    }

    private static String genericTypeName(GenericsType generic) {
        if (generic.wildcard) {
            if (generic.lowerBound) {
                return "? super ${typeName(generic.lowerBound)}"
            }
            if (generic.upperBounds && generic.upperBounds[0].name != Object.name) {
                return "? extends ${typeName(generic.upperBounds[0])}"
            }
            return '?'
        }
        generic.placeholder ? generic.name : typeName(generic.type)
    }

    private static String decapitalize(String value) {
        value.length() > 1 && Character.isUpperCase(value.charAt(0)) && Character.isUpperCase(value.charAt(1)) ?
                value : value[0].toLowerCase(Locale.ROOT) + value.substring(1)
    }

    private static String toJson(Object value) {
        if (value == null) {
            return 'null'
        }
        if (value instanceof CharSequence || value instanceof Character) {
            return '"' + escapeJson(value.toString()) + '"'
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString()
        }
        if (value instanceof Map) {
            return '{' + ((Map<Object, Object>) value).collect { Object key, Object item ->
                '"' + escapeJson(key.toString()) + '":' + toJson(item)
            }.join(',') + '}'
        }
        if (value instanceof Iterable) {
            return '[' + ((Iterable<Object>) value).collect { Object item -> toJson(item) }.join(',') + ']'
        }
        '"' + escapeJson(value.toString()) + '"'
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length())
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index)
            switch (character) {
                case '"' as char:
                    escaped.append('\\"')
                    break
                case '\\' as char:
                    escaped.append('\\\\')
                    break
                case '\b' as char:
                    escaped.append('\\b')
                    break
                case '\f' as char:
                    escaped.append('\\f')
                    break
                case '\n' as char:
                    escaped.append('\\n')
                    break
                case '\r' as char:
                    escaped.append('\\r')
                    break
                case '\t' as char:
                    escaped.append('\\t')
                    break
                default:
                    if (character < 0x20) {
                        escaped.append(String.format('\\u%04x', (int) character))
                    } else {
                        escaped.append(character)
                    }
            }
        }
        escaped.toString()
    }
}
