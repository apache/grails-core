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
import java.nio.charset.StandardCharsets

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.CodeVisitorSupport
import org.codehaus.groovy.ast.ConstructorNode
import org.codehaus.groovy.ast.InnerClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.syntax.Types
import org.codehaus.groovy.transform.trait.Traits

import org.apache.grails.gradle.model.cli.CommandDescriptor

/**
 * Writes the descriptor of a compiled command, {@code META-INF/grails-commands/<class name>.json},
 * into the compilation target directory, beside the registration of the command in
 * {@code META-INF/grails-cli.factories}. A build reads it to list the command with its name and
 * description without loading the class.
 *
 * <p>Only what the source says as a constant is written. The name of a command is the one its
 * class name gives unless the class, or a class it extends, declares {@code name} or
 * {@code getName()}; the description is the name unless one of them declares {@code description}
 * or {@code getDescription()}. A declaration is read when it is a property with a string constant
 * for its value, or a method that does nothing but return one. A name that is declared in another
 * way cannot be known without running the command, so that command gets no descriptor and is listed
 * from its registration alone; a description declared in another way is left out of the
 * descriptor.</p>
 *
 * <p>The execution target of the descriptor is {@code standalone} for a class that implements
 * {@code StandaloneCommand}, itself or through a class it extends or an interface, and
 * {@code application} for every other command.</p>
 *
 * @since 9.0
 */
@CompileStatic
class CommandDescriptorWriter {

    private static final ClassNode TRAIT_BRIDGE = ClassHelper.make(Traits.TraitBridge)

    private static final String TRAIT_CLASS_MEMBER = 'traitClass'

    private static final String STANDALONE_COMMAND = 'org.apache.grails.core.cli.StandaloneCommand'

    private static final Object UNKNOWN = new Object()

    /**
     * Writes the descriptor of a command class, or removes the one an earlier compilation wrote
     * when the name of the command cannot be read from the source any more.
     *
     * @param classNode the command class
     * @param compilationTargetDirectory the compilation output directory
     * @return {@code true} when a descriptor was written
     */
    static boolean write(ClassNode classNode, File compilationTargetDirectory) {
        String location = CommandDescriptor.location(classNode.name)
        if (location == null) {
            return false
        }
        File descriptor = new File(compilationTargetDirectory, location)
        // a class without a name of its own is nothing that can be typed
        if (classNode instanceof InnerClassNode && ((InnerClassNode) classNode).isAnonymous()) {
            descriptor.delete()
            return false
        }
        Object declaredName = declared(classNode, 'name')
        if (declaredName.is(UNKNOWN)) {
            descriptor.delete()
            return false
        }
        String name = declaredName == null ? CommandDescriptor.conventionName(classNode.name) : (String) declaredName
        Object declaredDescription = declared(classNode, 'description')
        String description = declaredDescription.is(UNKNOWN) ? null :
                declaredDescription == null ? name : (String) declaredDescription
        // nothing is written that the reader of a descriptor refuses: a name that is not the name of a
        // command gets no descriptor, and a description that is not one line that can be shown is left out
        String json = CommandDescriptor.describe(classNode.name, name, description,
                isStandalone(classNode) ? CommandDescriptor.TARGET_STANDALONE : CommandDescriptor.TARGET_APPLICATION)
        if (json == null) {
            descriptor.delete()
            return false
        }
        descriptor.parentFile.mkdirs()
        descriptor.setBytes(json.getBytes(StandardCharsets.US_ASCII))
        true
    }

    /**
     * Removes the descriptor of a class that is not a command, which it has when an earlier
     * compilation found it to be one.
     *
     * @param className the name of the class
     * @param compilationTargetDirectory the compilation output directory
     */
    static void remove(String className, File compilationTargetDirectory) {
        String location = CommandDescriptor.location(className)
        if (location != null) {
            File descriptor = new File(compilationTargetDirectory, location)
            if (descriptor.isFile()) {
                descriptor.delete()
            }
        }
    }

    // whether the class, or a class it extends, is marked as a command that runs without the application
    private static boolean isStandalone(ClassNode classNode) {
        ClassNode current = classNode
        while (current != null && current != ClassHelper.OBJECT_TYPE) {
            if (current.allInterfaces.any { ClassNode interfaceNode -> interfaceNode.name == STANDALONE_COMMAND }) {
                return true
            }
            current = current.superClass
        }
        false
    }

    // null: nothing declares the property, so the command contract's default applies;
    // a String: the constant the nearest declaration gives; UNKNOWN: declared, and not a constant
    private static Object declared(ClassNode classNode, String property) {
        String getter = 'get' + property.capitalize()
        ClassNode current = classNode
        while (current != null && current != ClassHelper.OBJECT_TYPE) {
            MethodNode method = current.getDeclaredMethod(getter, Parameter.EMPTY_ARRAY)
            boolean declaresMethod = method != null && !method.isStatic()
            // a method the class writes itself is what is called, also when the class has the property too
            if (declaresMethod && method.getAnnotations(TRAIT_BRIDGE).isEmpty()) {
                return returnedConstant(method)
            }
            PropertyNode propertyNode = current.getProperty(property)
            if (propertyNode != null && !propertyNode.isStatic()) {
                // a property that can be set is what it starts as unless the class sets it when an instance is made
                boolean set = !Modifier.isFinal(propertyNode.modifiers) && setsWhenMade(current, property)
                return set ? UNKNOWN : constant(propertyNode.initialExpression)
            }
            // a method a trait weaves into the class: the default when the trait is the command contract's
            if (declaresMethod) {
                return isCommandContractDefault(method) ? null : UNKNOWN
            }
            current = current.superClass
        }
        null
    }

    // whether a constructor or an initializer block of the class assigns to the property
    private static boolean setsWhenMade(ClassNode classNode, String property) {
        String setter = 'set' + property.capitalize()
        boolean[] found = [false] as boolean[]
        CodeVisitorSupport visitor = new CodeVisitorSupport() {

            @Override
            void visitBinaryExpression(BinaryExpression expression) {
                if (Types.ofType(expression.operation.type, Types.ASSIGNMENT_OPERATOR) && names(expression.leftExpression, property)) {
                    found[0] = true
                }
                super.visitBinaryExpression(expression)
            }

            @Override
            void visitMethodCallExpression(MethodCallExpression call) {
                if (call.methodAsString == setter) {
                    found[0] = true
                }
                super.visitMethodCallExpression(call)
            }
        }
        for (ConstructorNode constructor : classNode.declaredConstructors) {
            constructor.code?.visit(visitor)
        }
        for (Statement statement : classNode.objectInitializerStatements) {
            statement.visit(visitor)
        }
        found[0]
    }

    private static boolean names(Expression target, String property) {
        if (target instanceof VariableExpression) {
            return ((VariableExpression) target).name == property
        }
        if (target instanceof PropertyExpression) {
            return ((PropertyExpression) target).propertyAsString == property
        }
        false
    }

    private static boolean isCommandContractDefault(MethodNode method) {
        for (AnnotationNode annotation : method.getAnnotations(TRAIT_BRIDGE)) {
            Expression traitClass = annotation.getMember(TRAIT_CLASS_MEMBER)
            if (traitClass instanceof ClassExpression) {
                String traitName = ((ClassExpression) traitClass).type.name
                return traitName == CommandFactoriesTransformation.APPLICATION_COMMAND_CLASS.name
            }
        }
        false
    }

    private static Object returnedConstant(MethodNode method) {
        Statement code = method.code
        if (code instanceof BlockStatement) {
            List<Statement> statements = ((BlockStatement) code).statements
            if (statements.size() != 1) {
                return UNKNOWN
            }
            code = statements[0]
        }
        if (code instanceof ReturnStatement) {
            return constant(((ReturnStatement) code).expression)
        }
        if (code instanceof ExpressionStatement) {
            return constant(((ExpressionStatement) code).expression)
        }
        UNKNOWN
    }

    private static Object constant(Expression expression) {
        if (expression instanceof ConstantExpression && ((ConstantExpression) expression).value instanceof String) {
            return ((ConstantExpression) expression).value
        }
        UNKNOWN
    }
}
