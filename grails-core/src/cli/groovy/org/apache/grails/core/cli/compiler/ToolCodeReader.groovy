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

import groovy.transform.CompileStatic
import groovy.transform.Generated
import groovy.transform.PackageScope
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.CodeVisitorSupport
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.Variable
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.transform.trait.Traits

/**
 * Looks at the code of a tool class itself, its methods, constructors and initializers with the
 * closures in them, for what a check of the compiler warns of: a tool that reads its arguments, and
 * a tool that reads what is typed. These are heuristics: they see the names the code uses, not what
 * the code does, and only warn.
 *
 * @since 9.0
 */
@PackageScope
@CompileStatic
final class ToolCodeReader {

    // what the code of a tool reads its arguments by: properties of the tool and of its command line, and their getters
    private static final Set<String> ARGUMENT_PROPERTIES = ['args', 'commandLine', 'remainingArgs'].toSet().asImmutable()

    private static final Set<String> ARGUMENT_METHODS = ['getArgs', 'getCommandLine', 'getRemainingArgs', 'optionValue', 'hasOption'].toSet().asImmutable()

    private static final ClassNode GENERATED = ClassHelper.make(Generated)

    private static final ClassNode TRAIT_BRIDGE = ClassHelper.make(Traits.TraitBridge)

    private static final String SYSTEM = 'java.lang.System'

    private ToolCodeReader() {
    }

    /**
     * @param classNode a tool class
     * @return whether its own code reads its arguments: {@code args}, {@code getArgs()},
     * {@code executionContext.commandLine}, {@code commandLine.remainingArgs}, {@code optionValue(...)}
     * or {@code hasOption(...)}
     */
    static boolean readsArguments(ClassNode classNode) {
        boolean[] found = [false] as boolean[]
        visit(classNode, new CodeVisitorSupport() {

            @Override
            void visitVariableExpression(VariableExpression expression) {
                Variable accessed = expression.accessedVariable
                // a parameter or a local variable of that name is not the property of the tool
                if (ARGUMENT_PROPERTIES.contains(expression.name) && !(accessed instanceof Parameter) && !(accessed instanceof VariableExpression)) {
                    found[0] = true
                }
            }

            @Override
            void visitPropertyExpression(PropertyExpression expression) {
                found[0] = found[0] || ARGUMENT_PROPERTIES.contains(expression.propertyAsString)
                super.visitPropertyExpression(expression)
            }

            @Override
            void visitMethodCallExpression(MethodCallExpression call) {
                found[0] = found[0] || ARGUMENT_METHODS.contains(call.methodAsString)
                super.visitMethodCallExpression(call)
            }
        })
        found[0]
    }

    /**
     * @param classNode a tool class
     * @return whether its own code reads what is typed: {@code System.in} or {@code System.console()}
     */
    static boolean readsTyped(ClassNode classNode) {
        boolean[] found = [false] as boolean[]
        visit(classNode, new CodeVisitorSupport() {

            @Override
            void visitPropertyExpression(PropertyExpression expression) {
                found[0] = found[0] || (isSystem(expression.objectExpression) && expression.propertyAsString == 'in')
                super.visitPropertyExpression(expression)
            }

            @Override
            void visitMethodCallExpression(MethodCallExpression call) {
                found[0] = found[0] || (isSystem(call.objectExpression) && call.methodAsString == 'console')
                super.visitMethodCallExpression(call)
            }

            @Override
            void visitStaticMethodCallExpression(StaticMethodCallExpression call) {
                found[0] = found[0] || (call.ownerType.name == SYSTEM && call.method == 'console')
                super.visitStaticMethodCallExpression(call)
            }
        })
        found[0]
    }

    /**
     * @param classNode a tool class
     * @param property the name of a property
     * @return whether the code of the class, or of a class it extends that is compiled with it, names the
     * property, its getter or its setter: what sets a property by a name that is worked out does not name it
     */
    static boolean names(ClassNode classNode, String property) {
        Set<String> accessors = ['get', 'set', 'is'].collect { String prefix -> prefix + property.capitalize() }.toSet()
        boolean[] found = [false] as boolean[]
        CodeVisitorSupport visitor = new CodeVisitorSupport() {

            @Override
            void visitVariableExpression(VariableExpression expression) {
                found[0] = found[0] || expression.name == property
            }

            @Override
            void visitPropertyExpression(PropertyExpression expression) {
                found[0] = found[0] || expression.propertyAsString == property
                super.visitPropertyExpression(expression)
            }

            @Override
            void visitMethodCallExpression(MethodCallExpression call) {
                found[0] = found[0] || accessors.contains(call.methodAsString)
                super.visitMethodCallExpression(call)
            }
        }
        for (ClassNode type = classNode; type != null && type.isPrimaryClassNode(); type = type.superClass) {
            visit(type, visitor)
        }
        found[0]
    }

    private static boolean isSystem(Expression expression) {
        expression instanceof ClassExpression && expression.type.name == SYSTEM
    }

    // the code the class writes itself: not what a trait or the compiler adds
    private static void visit(ClassNode classNode, CodeVisitorSupport visitor) {
        for (FieldNode field : classNode.fields) {
            field.initialValueExpression?.visit(visitor)
        }
        for (Statement statement : classNode.objectInitializerStatements) {
            statement.visit(visitor)
        }
        List<MethodNode> code = new ArrayList<MethodNode>(classNode.declaredConstructors)
        code.addAll(classNode.methods)
        for (MethodNode method : code) {
            if (method.getAnnotations(GENERATED).isEmpty() && method.getAnnotations(TRAIT_BRIDGE).isEmpty()) {
                method.code?.visit(visitor)
            }
        }
    }
}
