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
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.InnerClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.transform.trait.Traits

import org.apache.grails.gradle.model.cli.ToolDescriptor

/**
 * Writes the descriptor of a compiled tool, {@code META-INF/grails-tools/<class name>.json},
 * into the compilation target directory, beside the registration of the tool in
 * {@code META-INF/grails-cli.factories}. A build reads it to list the tool with its name,
 * description, target and whether it starts the application, without loading the class.
 *
 * <p>Only what the source says as a constant is written. The name of a tool is the one its class
 * name gives ({@code CreateReportTool} and {@code CreateReportCommand} are {@code create-report})
 * unless the class, or a class it extends, declares {@code name} or {@code getName()}; the
 * description is the name unless one of them declares {@code description} or
 * {@code getDescription()}. The declaration nearest to the tool is read, as it is the one that is
 * called: a method that does nothing but return a string constant, or a property. A property that is
 * {@code final} is the value it is declared with unless a constructor of its class sets it, which
 * Groovy allows. A property that can be set, or is set so, is what it is once an instance of the tool
 * is made: the field initializers, initializer blocks and constructors of the tool and of the classes
 * it extends are followed in the order they run, with the methods of those classes they call on the
 * tool, and the property is known when the last statement that sets it sets it to a string constant
 * on every way the code can go, and nothing that is not read and runs after it can set it. A name
 * that is declared or set in another way cannot be known without running the tool, so that tool gets
 * no descriptor and is listed from its registration alone, by the name its class name gives, which the
 * registry of tools finds it by; a description declared or set in another way is left out of the
 * descriptor.</p>
 *
 * <p>The execution target of the descriptor is {@code standalone} for a class that implements
 * {@code StandaloneTool}, itself or through a class it extends or an interface, and
 * {@code application} for every other tool. A tool of the target {@code application} starts the
 * application unless the property {@code startsApplication} is {@code false} once an instance of the
 * tool is made, which is read as the name is: from {@code isStartsApplication()} where the class, or
 * the class nearest to it that it extends, writes one that returns a constant; from the property
 * where one of them declares it, with its initializer and what the constructors set; and otherwise
 * from the property of {@code ApplicationTool}, which is {@code true} unless the code of the tool sets
 * it, and is then what the constructors set. A value that cannot be read this way gives the tool no
 * descriptor, and is an error of the compilation ({@link ToolRules}); so is a {@code getStartsApplication()}
 * written by hand, which is not what the property is read with.</p>
 *
 * <p>What is deprecated about how the tool is written goes into the descriptor as it is given.</p>
 *
 * @since 9.0
 */
@CompileStatic
class ToolDescriptorWriter {

    private static final ClassNode TRAIT_BRIDGE = ClassHelper.make(Traits.TraitBridge)

    private static final String STARTS_APPLICATION = 'startsApplication'

    private static final String IS_STARTS_APPLICATION = 'isStartsApplication'

    private static final String GET_STARTS_APPLICATION = 'getStartsApplication'

    private static final Object UNKNOWN = new Object()

    /**
     * Writes the descriptor of a tool class with nothing deprecated about it (see
     * {@link #write(ClassNode, File, List)}).
     *
     * @param classNode the tool class
     * @param compilationTargetDirectory the compilation output directory
     * @return {@code true} when a descriptor was written
     */
    static boolean write(ClassNode classNode, File compilationTargetDirectory) {
        write(classNode, compilationTargetDirectory, Collections.<String> emptyList())
    }

    /**
     * Writes the descriptor of a tool class, or removes the one an earlier compilation wrote when the
     * name of the tool, or whether it starts the application, cannot be read from the source any more.
     *
     * @param classNode the tool class
     * @param compilationTargetDirectory the compilation output directory
     * @param deprecated what is deprecated about how the tool is written, of {@link ToolDescriptor#DEPRECATIONS}
     * @return {@code true} when a descriptor was written
     */
    static boolean write(ClassNode classNode, File compilationTargetDirectory, List<String> deprecated) {
        String location = ToolDescriptor.location(classNode.name)
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
        String name = declaredName == null ? ToolDescriptor.conventionName(classNode.name) : (String) declaredName
        Object declaredDescription = declared(classNode, 'description')
        String description = declaredDescription.is(UNKNOWN) ? null :
                declaredDescription == null ? name : (String) declaredDescription
        String target = target(classNode)
        Boolean startsApplication = target == ToolDescriptor.TARGET_APPLICATION ? startsApplication(classNode) : Boolean.FALSE
        if (startsApplication == null) {
            descriptor.delete()
            return false
        }
        // nothing is written that the reader of a descriptor refuses: a name that is not the name of a
        // command gets no descriptor, and a description that is not one line that can be shown is left out
        String json = ToolDescriptor.describe(classNode.name, name, description, target, startsApplication, deprecated)
        if (json == null) {
            descriptor.delete()
            return false
        }
        descriptor.parentFile.mkdirs()
        descriptor.setBytes(json.getBytes(StandardCharsets.US_ASCII))
        true
    }

    /**
     * Removes the descriptor of a class that is not a tool, which it has when an earlier
     * compilation found it to be one.
     *
     * @param className the name of the class
     * @param compilationTargetDirectory the compilation output directory
     */
    static void remove(String className, File compilationTargetDirectory) {
        String location = ToolDescriptor.location(className)
        if (location != null) {
            File descriptor = new File(compilationTargetDirectory, location)
            if (descriptor.isFile()) {
                descriptor.delete()
            }
        }
    }

    // the kind of the tool: what the class, or a class it extends, is marked as
    private static String target(ClassNode classNode) {
        ToolRules.isStandaloneTool(classNode) ? ToolDescriptor.TARGET_STANDALONE : ToolDescriptor.TARGET_APPLICATION
    }

    /**
     * @param classNode a tool class of the target {@code application}
     * @return whether the application is started around the tool, which is what the property
     * {@code startsApplication} is once an instance of the tool is made, or {@code null} when that
     * cannot be read from the source
     */
    static Boolean startsApplication(ClassNode classNode) {
        ClassNode current = classNode
        while (current != null && current != ClassHelper.OBJECT_TYPE) {
            // the property is read with isStartsApplication: one written by hand is what is called
            MethodNode reader = ownMethod(current, IS_STARTS_APPLICATION)
            if (reader != null) {
                Object returned = returnedConstant(reader, Boolean)
                return returned.is(UNKNOWN) ? null : (Boolean) returned
            }
            // and getStartsApplication written by hand is not, so what it says is not what the tool does
            if (ownMethod(current, GET_STARTS_APPLICATION) != null) {
                return null
            }
            PropertyNode propertyNode = current.getProperty(STARTS_APPLICATION)
            if (propertyNode != null && !propertyNode.isStatic()) {
                if (Modifier.isFinal(propertyNode.modifiers) && !ConstructionReader.setsInConstructor(current, STARTS_APPLICATION)) {
                    Object declared = constant(propertyNode.initialExpression, Boolean)
                    return declared.is(UNKNOWN) ? null : (Boolean) declared
                }
                return ConstructionReader.readBoolean(classNode, current, STARTS_APPLICATION)
            }
            MethodNode bridge = current.getDeclaredMethod(IS_STARTS_APPLICATION, Parameter.EMPTY_ARRAY)
            if (bridge != null && !bridge.isStatic()) {
                // the property of ApplicationTool, in a class compiled with the tool: true unless the code of the tool sets it
                if (ConstructionReader.bridgedTrait(bridge) != ToolRules.APPLICATION_TOOL || !current.isPrimaryClassNode()) {
                    return null
                }
                return ConstructionReader.writes(classNode, STARTS_APPLICATION) ?
                        ConstructionReader.readTraitBoolean(classNode, current, STARTS_APPLICATION, ToolRules.APPLICATION_TOOL, true) :
                        Boolean.TRUE
            }
            current = current.superClass
        }
        Boolean.TRUE
    }

    // a method without parameters the class writes itself, and not a trait for it
    private static MethodNode ownMethod(ClassNode classNode, String name) {
        MethodNode method = classNode.getDeclaredMethod(name, Parameter.EMPTY_ARRAY)
        method != null && !method.isStatic() && method.getAnnotations(TRAIT_BRIDGE).isEmpty() ? method : null
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
                return returnedConstant(method, String)
            }
            PropertyNode propertyNode = current.getProperty(property)
            if (propertyNode != null && !propertyNode.isStatic()) {
                // nothing but a constructor of its class sets a final property over the value it is declared with
                if (Modifier.isFinal(propertyNode.modifiers) && !ConstructionReader.setsInConstructor(current, property)) {
                    return constant(propertyNode.initialExpression, String)
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
        ConstructionReader.bridgedTrait(method) == CommandFactoriesTransformation.GRAILS_TOOL_CLASS.name
    }

    // the constant of the kind the method does nothing but return, or UNKNOWN
    private static Object returnedConstant(MethodNode method, Class<?> kind) {
        Statement code = method.code
        if (code instanceof BlockStatement) {
            List<Statement> statements = ((BlockStatement) code).statements
            if (statements.size() != 1) {
                return UNKNOWN
            }
            code = statements[0]
        }
        if (code instanceof ReturnStatement) {
            return constant(((ReturnStatement) code).expression, kind)
        }
        if (code instanceof ExpressionStatement) {
            return constant(((ExpressionStatement) code).expression, kind)
        }
        UNKNOWN
    }

    private static Object constant(Expression expression, Class<?> kind) {
        if (expression instanceof ConstantExpression && kind.isInstance(((ConstantExpression) expression).value)) {
            return ((ConstantExpression) expression).value
        }
        UNKNOWN
    }
}
