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
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.InnerClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.DeclarationExpression
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
 * or {@code getDescription()}. The declaration nearest to the command is read, as it is the one
 * that is called: a method that does nothing but return a string constant, or a property. A
 * property that can be set is what the class nearest to the command that sets it when an instance
 * is made sets it to, since the constructors and initializer blocks of a class run after those of
 * the class it extends; that is read when every statement of the class that sets it does nothing
 * but assign or pass the same string constant, and one of them always runs: in an initializer
 * block, or in every constructor. A property that no class sets is the string constant it is
 * declared with. A name that is declared or set in another way cannot be known without running the
 * command, so that command gets no descriptor and is listed from its registration alone; a
 * description declared or set in another way is left out of the descriptor.</p>
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

    private static final Object NOT_SET = new Object()

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
        // the classes from the command up to the one that declares the property, each of which can set it
        List<ClassNode> chain = []
        ClassNode current = classNode
        while (current != null && current != ClassHelper.OBJECT_TYPE) {
            chain << current
            MethodNode method = current.getDeclaredMethod(getter, Parameter.EMPTY_ARRAY)
            boolean declaresMethod = method != null && !method.isStatic()
            // a method the class writes itself is what is called, also when the class has the property too
            if (declaresMethod && method.getAnnotations(TRAIT_BRIDGE).isEmpty()) {
                return returnedConstant(method)
            }
            PropertyNode propertyNode = current.getProperty(property)
            if (propertyNode != null && !propertyNode.isStatic()) {
                Object initial = constant(propertyNode.initialExpression)
                return Modifier.isFinal(propertyNode.modifiers) ? initial : whenMade(chain, property, initial)
            }
            // a method a trait weaves into the class: the default when the trait is the command contract's
            if (declaresMethod) {
                return isCommandContractDefault(method) ? null : UNKNOWN
            }
            current = current.superClass
        }
        null
    }

    // what a property that can be set is once an instance is made: the constructors and initializers of a
    // class run after those of the class it extends, so the class nearest to the command that sets it
    // decides, and the property is what it starts as when no class sets it
    private static Object whenMade(List<ClassNode> chain, String property, Object initial) {
        for (ClassNode made : chain) {
            Object set = setWhenMade(made, property, chain)
            if (!set.is(NOT_SET)) {
                return set
            }
        }
        initial
    }

    // NOT_SET: no constructor or initializer block of the class assigns to the property; a String: every
    // assignment is a statement of its own that sets the property to that constant, and one of them always
    // runs; UNKNOWN: the class sets the property in another way
    private static Object setWhenMade(ClassNode classNode, String property, List<ClassNode> chain) {
        List<Statement> constructors = classNode.declaredConstructors.collect { ConstructorNode constructor -> constructor.code }
        List<Statement> initializers = classNode.objectInitializerStatements
        // in a class with a field of its own of that name, what is assigned by the name goes into that field
        boolean byName = classNode.is(chain.last()) || classNode.getDeclaredField(property) == null
        int assignments = assignments(constructors + initializers, property, byName)
        if (assignments == 0) {
            return NOT_SET
        }
        String setter = 'set' + property.capitalize()
        // a setter written by hand may make of a value something else
        if (chain.any { ClassNode type -> type.getDeclaredMethods(setter).any { MethodNode method -> !method.isStatic() } }) {
            return UNKNOWN
        }
        Set<String> values = new HashSet<>()
        int read = 0
        boolean always = false
        for (Statement initializer : initializers) {
            Settings settings = settings(initializer, property, byName)
            read += settings.count
            values.addAll(settings.values)
            always = always || settings.always
        }
        boolean everyConstructor = !constructors.isEmpty()
        for (Statement constructor : constructors) {
            Settings settings = settings(constructor, property, byName)
            read += settings.count
            values.addAll(settings.values)
            everyConstructor = everyConstructor && settings.always
        }
        read == assignments && values.size() == 1 && (always || everyConstructor) ? values.first() : UNKNOWN
    }

    // the statements of a constructor or an initializer block that set the property to a string constant and do
    // nothing else, and whether one of them always runs, which it does when no statement before it can skip it
    private static Settings settings(Statement code, String property, boolean byName) {
        Settings settings = new Settings()
        boolean reached = true
        for (Statement statement : statements(code)) {
            String value = statement instanceof ExpressionStatement ?
                    constantSet(((ExpressionStatement) statement).expression, property, byName) : null
            if (value != null) {
                settings.count++
                settings.values << value
                settings.always = settings.always || reached
            }
            reached = reached && statement instanceof ExpressionStatement
        }
        settings
    }

    // the statements of the code in the order they run, those of a block in it included: an initializer
    // block is a block in a block
    private static List<Statement> statements(Statement code) {
        if (code instanceof BlockStatement) {
            return ((BlockStatement) code).statements.collectMany { Statement statement -> statements(statement) }
        }
        code == null ? [] : [code]
    }

    // the string constant that name = '...', this.name = '...', setName('...') or this.setName('...')
    // sets the property to, and null for every other expression
    private static String constantSet(Expression expression, String property, boolean byName) {
        if (expression instanceof BinaryExpression && !(expression instanceof DeclarationExpression)) {
            BinaryExpression assignment = (BinaryExpression) expression
            if (byName && assignment.operation.type == Types.ASSIGN && isProperty(assignment.leftExpression, property)) {
                return stringConstant(assignment.rightExpression)
            }
            return null
        }
        if (expression instanceof MethodCallExpression) {
            MethodCallExpression call = (MethodCallExpression) expression
            if (call.methodAsString == 'set' + property.capitalize() && !call.safe && !call.spreadSafe &&
                    (call.implicitThis || isThis(call.objectExpression)) && call.arguments instanceof ArgumentListExpression) {
                List<Expression> arguments = ((ArgumentListExpression) call.arguments).expressions
                return arguments.size() == 1 ? stringConstant(arguments[0]) : null
            }
        }
        null
    }

    // the property itself, not a parameter or a local variable of that name, nor one of another object
    private static boolean isProperty(Expression target, String property) {
        if (target instanceof VariableExpression) {
            VariableExpression variable = (VariableExpression) target
            return variable.name == property &&
                    (variable.accessedVariable instanceof FieldNode || variable.accessedVariable instanceof PropertyNode)
        }
        if (target instanceof PropertyExpression) {
            PropertyExpression access = (PropertyExpression) target
            return access.propertyAsString == property && !access.safe && !access.spreadSafe && isThis(access.objectExpression)
        }
        false
    }

    private static boolean isThis(Expression expression) {
        expression instanceof VariableExpression && ((VariableExpression) expression).isThisExpression()
    }

    private static String stringConstant(Expression expression) {
        Object value = constant(expression)
        value.is(UNKNOWN) ? null : (String) value
    }

    // how many times the code assigns to something of the property's name, or calls a method that sets one
    private static int assignments(List<Statement> code, String property, boolean byName) {
        String setter = 'set' + property.capitalize()
        int[] found = [0] as int[]
        CodeVisitorSupport visitor = new CodeVisitorSupport() {

            @Override
            void visitBinaryExpression(BinaryExpression expression) {
                if (Types.ofType(expression.operation.type, Types.ASSIGNMENT_OPERATOR) && names(expression.leftExpression, property, byName)) {
                    found[0]++
                }
                super.visitBinaryExpression(expression)
            }

            @Override
            void visitMethodCallExpression(MethodCallExpression call) {
                if (call.methodAsString == setter) {
                    found[0]++
                }
                super.visitMethodCallExpression(call)
            }
        }
        for (Statement statement : code) {
            statement?.visit(visitor)
        }
        found[0]
    }

    private static boolean names(Expression target, String property, boolean byName) {
        if (target instanceof VariableExpression) {
            return byName && ((VariableExpression) target).name == property
        }
        if (target instanceof PropertyExpression) {
            PropertyExpression access = (PropertyExpression) target
            return access.propertyAsString == property && (byName || !isThis(access.objectExpression))
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

    // what the statements of one constructor or initializer block set the property to
    private static final class Settings {

        int count

        final List<String> values = []

        boolean always
    }
}
