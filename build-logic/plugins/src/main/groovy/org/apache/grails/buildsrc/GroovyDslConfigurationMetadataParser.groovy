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
package org.apache.grails.buildsrc

import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.TernaryExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.IfStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.syntax.Types

import java.nio.charset.StandardCharsets

/** Extracts configuration metadata from Groovy DSL source without evaluating source code. */
final class GroovyDslConfigurationMetadataParser {

    private GroovyDslConfigurationMetadataParser() {
    }

    static List<Map<String, Object>> parse(Collection<File> files, Map<String, String> rootPrefixes) {
        List<Map<String, Object>> properties = []
        files.findAll { File file -> file.isFile() }.sort { File file -> file.absolutePath }.each { File file ->
            parseFile(file, rootPrefixes, properties)
        }
        mergeBranches(properties)
    }

    /**
     * Reconciles multiple entries for the same property name before they reach the stricter
     * cross-source conflict check. Mutually exclusive branches (if/else) commonly assign a
     * property differently per branch, e.g. a different literal type or null in one branch, or
     * override an unconditional default only under some environments; neither is an authoring
     * conflict. Two *unconditional* assignments to the same name disagreeing is still a real
     * conflict, regardless of how many conditional entries for that name sit between them.
     */
    private static List<Map<String, Object>> mergeBranches(List<Map<String, Object>> properties) {
        properties.groupBy { Map<String, Object> property -> property.name as String }
                .collect { String name, List<Map<String, Object>> entries -> mergeGroup(name, entries) }
                .sort { Map<String, Object> property -> property.name as String }
    }

    private static Map<String, Object> mergeGroup(String name, List<Map<String, Object>> entries) {
        List<Map<String, Object>> unconditional = (entries.findAll { Map<String, Object> entry ->
            !(entry.conditional as boolean)
        }.collect { Map<String, Object> entry -> stripConditional(entry) } as Set).toList()
        if (unconditional.size() > 1) {
            throw new IllegalArgumentException("Conflicting DSL properties metadata for '${name}'")
        }
        Map<String, Object> merged = [name: name]
        Set<String> types = (entries*.type.findAll { String type -> type != null } as Set)
        if (types.size() == 1) {
            merged.type = types.first()
        }
        if (unconditional && unconditional[0].containsKey('defaultValue')) {
            merged.defaultValue = unconditional[0].defaultValue
        }
        merged
    }

    private static Map<String, Object> stripConditional(Map<String, Object> entry) {
        Map<String, Object> stripped = new LinkedHashMap<>(entry)
        stripped.remove('conditional')
        stripped
    }

    private static void parseFile(File file, Map<String, String> rootPrefixes,
                                  List<Map<String, Object>> properties) {
        ModuleNode module = parseSource(file)
        module.statementBlock.statements.each { Statement statement ->
            MethodCallExpression call = methodCall(statement)
            String root = call?.methodAsString
            ClosureExpression closure = call == null ? null : closureArgument(call)
            String prefix = root == null ? null : rootPrefixes[root]
            if (prefix != null && closure != null) {
                parseStatements(closure.code, prefix, false, properties)
            }
        }
    }

    private static ModuleNode parseSource(File file) {
        try {
            SourceUnit source = SourceUnit.create(file.absolutePath, file.getText(StandardCharsets.UTF_8.name()))
            source.parse()
            source.completePhase()
            source.nextPhase()
            source.convert()
            source.errorCollector.failIfErrors()
            source.AST
        } catch (CompilationFailedException exception) {
            throw new IllegalArgumentException("Failed to parse Groovy DSL source '${file.absolutePath}'", exception)
        }
    }

    private static void parseStatements(Statement statement, String prefix, boolean conditional,
                                        List<Map<String, Object>> properties) {
        if (statement instanceof BlockStatement) {
            statement.statements.each { Statement child -> parseStatements(child, prefix, conditional, properties) }
        } else if (statement instanceof IfStatement) {
            parseStatements(statement.ifBlock, prefix, true, properties)
            parseStatements(statement.elseBlock, prefix, true, properties)
        } else if (statement instanceof ExpressionStatement) {
            Expression expression = statement.expression
            if (expression instanceof BinaryExpression && expression.operation.type == Types.ASSIGN) {
                addAssignment(expression, prefix, conditional, properties)
            } else if (expression instanceof MethodCallExpression) {
                ClosureExpression closure = closureArgument(expression)
                String nestedName = expression.methodAsString
                if (closure != null && nestedName != null) {
                    parseStatements(closure.code, "${prefix}.${nestedName}", conditional, properties)
                }
            }
        }
    }

    private static void addAssignment(BinaryExpression assignment, String prefix, boolean conditional,
                                      List<Map<String, Object>> properties) {
        List<String> segments = leftHandPath(assignment.leftExpression)
        if (segments == null) {
            return
        }
        String name = "${prefix}.${segments.join('.')}"
        Inference inference = infer(assignment.rightExpression)
        Map<String, Object> property = [name: name, conditional: conditional]
        if (inference.type != null) {
            property.type = inference.type
        }
        if (!conditional && inference.literal) {
            property.defaultValue = inference.value
        }
        properties << property
    }

    private static List<String> leftHandPath(Expression expression) {
        if (expression instanceof VariableExpression) {
            return [expression.name]
        }
        if (expression instanceof PropertyExpression && expression.property instanceof ConstantExpression &&
                expression.property.value instanceof String) {
            List<String> owner = leftHandPath(expression.objectExpression)
            return owner == null ? null : owner + expression.property.value
        }
        null
    }

    private static MethodCallExpression methodCall(Statement statement) {
        statement instanceof ExpressionStatement && statement.expression instanceof MethodCallExpression ?
                statement.expression as MethodCallExpression : null
    }

    private static ClosureExpression closureArgument(MethodCallExpression call) {
        call.arguments instanceof ArgumentListExpression ?
                (call.arguments.expressions.find { Expression expression -> expression instanceof ClosureExpression } as ClosureExpression) : null
    }

    private static Inference infer(Expression expression) {
        if (expression instanceof ConstantExpression) {
            return new Inference(type: expression.value?.class?.name, literal: true, value: expression.value)
        }
        if (expression instanceof ListExpression) {
            return listInference(expression)
        }
        if (expression instanceof MapExpression) {
            return mapInference(expression)
        }
        if (expression instanceof TernaryExpression) {
            return sharedType(infer(expression.trueExpression), infer(expression.falseExpression))
        }
        if (expression instanceof MethodCallExpression && isSystemCall(expression) &&
                expression.methodAsString in ['getProperty', 'getenv']) {
            return new Inference(type: 'java.lang.String')
        }
        new Inference()
    }

    private static boolean isSystemCall(MethodCallExpression expression) {
        Expression receiver = expression.objectExpression
        receiver instanceof ClassExpression && receiver.type.name == 'java.lang.System' ||
                receiver instanceof VariableExpression && receiver.name == 'System'
    }

    private static Inference sharedType(Inference left, Inference right) {
        left.type != null && left.type == right.type ? new Inference(type: left.type) : new Inference()
    }

    private static Inference listInference(ListExpression expression) {
        List<Object> values = []
        for (Expression element : expression.expressions) {
            Inference inference = infer(element)
            if (!inference.literal) {
                return new Inference(type: 'java.util.List')
            }
            values << inference.value
        }
        new Inference(type: 'java.util.List', literal: true, value: values)
    }

    private static Inference mapInference(MapExpression expression) {
        Map<String, Object> values = new LinkedHashMap<>()
        for (def entry : expression.mapEntryExpressions) {
            Inference key = infer(entry.keyExpression)
            Inference value = infer(entry.valueExpression)
            if (!key.literal || !(key.value instanceof String) || !value.literal) {
                return new Inference(type: 'java.util.Map')
            }
            values[key.value] = value.value
        }
        new Inference(type: 'java.util.Map', literal: true, value: values)
    }

    private static final class Inference {
        String type
        boolean literal
        Object value
    }
}
