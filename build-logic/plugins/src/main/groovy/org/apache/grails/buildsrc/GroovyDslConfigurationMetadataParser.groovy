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
import org.codehaus.groovy.ast.expr.DeclarationExpression
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

    /**
     * DSL sources are registered explicitly, so a source that is missing, or a root name that no source
     * uses, means the registration went stale and would otherwise silently drop the metadata it stood for.
     */
    static List<Map<String, Object>> parse(Collection<File> files, Map<String, String> rootPrefixes) {
        List<Map<String, Object>> properties = []
        Set<String> matchedRoots = []
        files.sort { File file -> file.absolutePath }.each { File file ->
            if (!file.isFile()) {
                throw new IllegalArgumentException("Groovy DSL configuration source '${file.absolutePath}' does not exist")
            }
            parseTopLevel(parseSource(file).statementBlock, rootPrefixes, false, properties, matchedRoots,
                    new LinkedHashSet<String>())
        }
        List<String> unmatchedRoots = (rootPrefixes.keySet() - matchedRoots).sort()
        if (unmatchedRoots) {
            throw new IllegalArgumentException(
                    "No Groovy DSL configuration source declares the registered root(s) ${unmatchedRoots}")
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
        // a null literal says nothing about the type, but a value that cannot be inferred may be of any type
        if (types.size() == 1 && !entries.any { Map<String, Object> entry -> entry.dynamic as boolean }) {
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
        stripped.remove('dynamic')
        stripped
    }

    /**
     * The top level of a ConfigSlurper script may open a root section, assign a dotted path that starts
     * with a root, or wrap either of those in an if statement or an environments block. A local variable
     * that shares its name with a root hides the root, so it neither yields settings nor declares the root.
     */
    private static void parseTopLevel(Statement statement, Map<String, String> rootPrefixes, boolean conditional,
                                      List<Map<String, Object>> properties, Set<String> matchedRoots,
                                      Set<String> locals) {
        if (statement instanceof BlockStatement) {
            Set<String> scopedLocals = new LinkedHashSet<>(locals)
            statement.statements.each { Statement child ->
                parseTopLevel(child, rootPrefixes, conditional, properties, matchedRoots, scopedLocals)
            }
        } else if (statement instanceof IfStatement) {
            parseTopLevel(statement.ifBlock, rootPrefixes, true, properties, matchedRoots, locals)
            parseTopLevel(statement.elseBlock, rootPrefixes, true, properties, matchedRoots, locals)
        } else if (statement instanceof ExpressionStatement) {
            Expression expression = statement.expression
            if (expression instanceof DeclarationExpression) {
                locals.addAll(declaredNames(expression))
            } else if (isAssignment(expression)) {
                List<String> segments = leftHandPath((expression as BinaryExpression).leftExpression)
                String prefix = segments == null || segments.size() < 2 || segments[0] in locals ?
                        null : rootPrefixes[segments[0]]
                if (prefix != null) {
                    matchedRoots << segments[0]
                    addProperty("${prefix}.${segments.tail().join('.')}",
                            (expression as BinaryExpression).rightExpression, conditional, properties)
                }
            } else if (isSectionCall(expression)) {
                MethodCallExpression call = expression as MethodCallExpression
                ClosureExpression closure = closureArgument(call)
                String root = call.methodAsString
                if (root == 'environments') {
                    environmentClosures(closure.code).each { ClosureExpression environment ->
                        parseTopLevel(environment.code, rootPrefixes, true, properties, matchedRoots, locals)
                    }
                } else if (rootPrefixes[root] != null && !(root in locals)) {
                    matchedRoots << root
                    parseStatements(closure.code, rootPrefixes[root], conditional, properties, locals)
                }
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
                                        List<Map<String, Object>> properties, Set<String> locals) {
        if (statement instanceof BlockStatement) {
            Set<String> scopedLocals = new LinkedHashSet<>(locals)
            statement.statements.each { Statement child ->
                parseStatements(child, prefix, conditional, properties, scopedLocals)
            }
        } else if (statement instanceof IfStatement) {
            parseStatements(statement.ifBlock, prefix, true, properties, locals)
            parseStatements(statement.elseBlock, prefix, true, properties, locals)
        } else if (statement instanceof ExpressionStatement) {
            Expression expression = statement.expression
            if (expression instanceof DeclarationExpression) {
                locals.addAll(declaredNames(expression))
            } else if (isAssignment(expression)) {
                List<String> segments = leftHandPath((expression as BinaryExpression).leftExpression)
                if (segments != null && !(segments[0] in locals)) {
                    addProperty("${prefix}.${segments.join('.')}",
                            (expression as BinaryExpression).rightExpression, conditional, properties)
                }
            } else if (isSectionCall(expression)) {
                MethodCallExpression call = expression as MethodCallExpression
                ClosureExpression closure = closureArgument(call)
                if (call.methodAsString == 'environments') {
                    environmentClosures(closure.code).each { ClosureExpression environment ->
                        parseStatements(environment.code, prefix, true, properties, locals)
                    }
                } else if (!(call.methodAsString in locals)) {
                    parseStatements(closure.code, "${prefix}.${call.methodAsString}", conditional, properties, locals)
                }
            }
        }
    }

    /** A local variable declaration is also a binary assignment expression, but never a setting. */
    private static boolean isAssignment(Expression expression) {
        expression instanceof BinaryExpression && !(expression instanceof DeclarationExpression) &&
                expression.operation.type == Types.ASSIGN
    }

    /**
     * Only a call on the script itself opens a configuration section: {@code items.each { }} or
     * {@code value.with { }} take a closure as well, but their receiver is ordinary code.
     */
    private static boolean isSectionCall(Expression expression) {
        expression instanceof MethodCallExpression && expression.implicitThis &&
                expression.methodAsString != null && closureArgument(expression) != null
    }

    private static List<String> declaredNames(DeclarationExpression declaration) {
        declaration.multipleAssignmentDeclaration ?
                declaration.tupleExpression.expressions.findAll { Expression variable ->
                    variable instanceof VariableExpression
                }.collect { Expression variable -> (variable as VariableExpression).name } :
                [declaration.variableExpression.name]
    }

    /**
     * A ConfigSlurper {@code environments { production { ... } } } block picks exactly one named
     * environment closure at runtime; unlike an ordinary nested section, its name is not part of
     * the property path. Each environment closure is parsed under the unchanged prefix, marked
     * conditional the same way an if/else branch is. An environment may itself be declared inside
     * an if statement, and only a call on the script itself names one, as for any other section.
     */
    private static List<ClosureExpression> environmentClosures(Statement statement) {
        if (statement instanceof BlockStatement) {
            return statement.statements.collectMany { Statement child -> environmentClosures(child) }
        }
        if (statement instanceof IfStatement) {
            return environmentClosures(statement.ifBlock) + environmentClosures(statement.elseBlock)
        }
        statement instanceof ExpressionStatement && isSectionCall(statement.expression) ?
                [closureArgument(statement.expression as MethodCallExpression)] : []
    }

    private static void addProperty(String name, Expression value, boolean conditional,
                                    List<Map<String, Object>> properties) {
        Inference inference = infer(value)
        Map<String, Object> property = [name: name, conditional: conditional]
        if (inference.type != null) {
            property.type = inference.type
        } else if (!inference.literal) {
            property.dynamic = true
        }
        // a null default is no default: the key is omitted, as it is for every other setting without one
        if (!conditional && inference.literal && inference.value != null) {
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
        // System.getenv() without a name returns the whole environment as a map
        if (expression instanceof MethodCallExpression && isSystemCall(expression) &&
                expression.methodAsString in ['getProperty', 'getenv'] && hasArguments(expression)) {
            return new Inference(type: 'java.lang.String')
        }
        new Inference()
    }

    private static boolean hasArguments(MethodCallExpression expression) {
        expression.arguments instanceof ArgumentListExpression && !expression.arguments.expressions.isEmpty()
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
