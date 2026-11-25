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
import org.codehaus.groovy.ast.InnerClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.stmt.Statement
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
 * or {@code getDescription()}. The declaration nearest to the command is read, as it is the one
 * that is called: a method that does nothing but return a string constant, or a property. A
 * property that is {@code final} is the value it is declared with unless a constructor of its class
 * sets it, which Groovy allows. A property that can be set, or is set so, is what it is once an
 * instance of the command is made: the field
 * initializers, initializer blocks and constructors of the command and of the classes it extends
 * are followed in the order they run, with the methods of those classes they call on the command,
 * and the property is known when the last statement that sets it sets it to a string constant on
 * every way the code can go, and nothing that is not read and runs after it can set it. A name that
 * is declared or set in another way cannot be known without running the command, so that command
 * gets no descriptor and is listed from its registration alone, by the name its class name gives,
 * which the registry of commands finds it by; a description declared or set in another way is left
 * out of the descriptor.</p>
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
                // nothing but a constructor of its class sets a final property over the value it is declared with
                if (Modifier.isFinal(propertyNode.modifiers) && !ConstructionReader.setsInConstructor(current, property)) {
                    return constant(propertyNode.initialExpression)
                }
                // a property that is set is what the code that runs when the command is made leaves it as
                String made = ConstructionReader.read(classNode, current, property)
                return made == null ? UNKNOWN : made
            }
            // a method a trait weaves into the class: the default when the trait is the command contract's
            if (declaresMethod) {
                return isCommandContractDefault(method) ? null : UNKNOWN
            }
            current = current.superClass
        }
        null
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
