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

import java.lang.reflect.Method

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.CodeVisitorSupport
import org.codehaus.groovy.ast.ConstructorNode
import org.codehaus.groovy.ast.DynamicVariable
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.InnerClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.Variable
import org.codehaus.groovy.ast.expr.ArrayExpression
import org.codehaus.groovy.ast.expr.AttributeExpression
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.BitwiseNegationExpression
import org.codehaus.groovy.ast.expr.BooleanExpression
import org.codehaus.groovy.ast.expr.CastExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.ConstructorCallExpression
import org.codehaus.groovy.ast.expr.DeclarationExpression
import org.codehaus.groovy.ast.expr.ElvisOperatorExpression
import org.codehaus.groovy.ast.expr.EmptyExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.FieldExpression
import org.codehaus.groovy.ast.expr.GStringExpression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.MapEntryExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.MethodPointerExpression
import org.codehaus.groovy.ast.expr.PostfixExpression
import org.codehaus.groovy.ast.expr.PrefixExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.RangeExpression
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression
import org.codehaus.groovy.ast.expr.TernaryExpression
import org.codehaus.groovy.ast.expr.TupleExpression
import org.codehaus.groovy.ast.expr.UnaryMinusExpression
import org.codehaus.groovy.ast.expr.UnaryPlusExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.EmptyStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.IfStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.ast.stmt.ThrowStatement
import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.codehaus.groovy.runtime.DefaultGroovyStaticMethods
import org.codehaus.groovy.syntax.Types
import org.codehaus.groovy.transform.trait.Traits

/**
 * Reads, from the source of a command and of the classes it extends, what a property of the command
 * that can be set is once an instance of the command is made. What runs then is followed in the order
 * it runs, from the class at the root down to the command: of each class its field initializers, its
 * initializer blocks and, for each of its constructors, the constructor, with the methods they call.
 *
 * <p>The property is known after a statement that sets it to a string constant: an assignment,
 * {@code name = '...'} or {@code this.name = '...'}, or a call of its setter, {@code setName('...')},
 * in that code or in a method it calls on the command, or a static method of its classes it gives the
 * command to, that is compiled with the command. Such a method is read in turn, to a bounded depth, and
 * is found as Groovy finds it: a private method of the class whose code calls it; of the methods with
 * the same parameters, the one nearest to the command; every method the call can mean otherwise. Where
 * the code can go two ways that leave the property different, a condition or two constructors, it is
 * not known.</p>
 *
 * <p>What can set the property and is not read makes it unknown: a method that is not compiled with
 * the command or has no code, one of the Groovy runtime or one the runtime looks for, an assignment
 * through {@code super}, by a name that is worked out or through a subscript, a setter of the property
 * written by hand, a trait other than those of the command contract that gives its fields values, and a
 * class that takes over how its properties are set and its methods found. Code that is not read and is
 * not given the command, such as {@code new JsonSlurper()}, cannot reach it, and leaves the property
 * as it is until the command is handed to code that is not read (a closure holds the command, and a
 * class compiled elsewhere that the command extends may have handed it out): from then on any code that
 * is not read makes the property unknown. Changes to the meta class of a command, methods an extension
 * module adds, and code that runs in another thread are not seen.</p>
 *
 * @since 9.0
 */
@PackageScope
@CompileStatic
class ConstructionReader {

    private static final Object UNKNOWN = new Object()

    // how deep calls are followed, and how many calls one property is read through
    private static final int DEEPEST_CALL = 16

    private static final int MOST_CALLS = 256

    // the traits of the command contract, whose fields are given no value when a command is made
    private static final Set<String> CONTRACT_TRAITS = [
            'org.apache.grails.core.cli.ApplicationCommand',
            'org.apache.grails.core.cli.GrailsApplicationCommand',
            'grails.codegen.model.ModelBuilder',
    ].toSet().asImmutable()

    private static final String TRAIT_HELPER = '$Trait$Helper'

    private static final String TRAIT_INIT = '$init$'

    private static final ClassNode TRAIT_BRIDGE = ClassHelper.make(Traits.TraitBridge)

    // methods of a class that decide how its properties are set and its methods are found
    private static final List<String> MOP_METHODS = ['getProperty', 'setProperty', 'invokeMethod', 'methodMissing', 'propertyMissing'].asImmutable()

    // the names of the methods the Groovy runtime gives every object: a call by such a name can run one of those
    private static final Set<String> RUNTIME_METHODS = runtimeMethods()

    private final ClassNode command

    private final ClassNode declaring

    private final String property

    private final String setter

    // the command and the classes it extends, nearest first, without Object
    private final List<ClassNode> hierarchy

    private final boolean setterWritten

    private final boolean shadowed

    private final Deque<MethodNode> calling = new ArrayDeque<MethodNode>()

    private int calls

    private ConstructionReader(ClassNode command, ClassNode declaring, String property) {
        this.command = command
        this.declaring = declaring
        this.property = property
        this.setter = 'set' + property.capitalize()
        List<ClassNode> types = []
        for (ClassNode type = command; type != null && type != ClassHelper.OBJECT_TYPE; type = type.superClass) {
            types << type
        }
        this.hierarchy = types.asImmutable()
        List<ClassNode> below = types.subList(0, Math.max(0, types.findIndexOf { ClassNode type -> type.name == declaring.name }) + 1)
        // a setter written by hand may make of a value something else
        this.setterWritten = below.any { ClassNode type -> type.getDeclaredMethods(setter).any { MethodNode method -> !method.isStatic() } }
        // with a field of that name in a class below the one that declares the property, what the runtime sets by the name is not certain
        this.shadowed = below.any { ClassNode type -> type.name != declaring.name && type.getDeclaredField(property) != null }
    }

    /**
     * @param command the command class
     * @param declaring the class, the command or one it extends, that declares the property
     * @param property the name of the property
     * @return the string constant the property is once an instance of the command is made, or
     * {@code null} when that cannot be known from the source
     */
    static String read(ClassNode command, ClassNode declaring, String property) {
        new ConstructionReader(command, declaring, property).read()
    }

    private String read() {
        if (hierarchy.any { ClassNode type -> MOP_METHODS.any { String name -> !type.getDeclaredMethods(name).isEmpty() } }) {
            return null
        }
        State state = new State(UNKNOWN, false)
        for (ClassNode type : hierarchy.reverse()) {
            // the constructor of a class compiled elsewhere is not read: it may set the property, and hand the command out
            state = type.isPrimaryClassNode() ? made(type, state) : new State(UNKNOWN, true)
            if (state == null) {
                return null
            }
        }
        state.value instanceof String ? (String) state.value : null
    }

    // what the property is after the class's part of making the command, whichever constructor runs
    private State made(ClassNode type, State before) {
        List<ConstructorNode> constructors = type.declaredConstructors
        if (constructors.isEmpty()) {
            return construct(type, null, before.copy())
        }
        State made = null
        for (ConstructorNode constructor : constructors) {
            State end = construct(type, constructor.code, before.copy())
            made = end == null ? made : State.join(made, end)
        }
        made
    }

    // the field initializers, the initializer blocks and the constructor of the class, in the order they run
    private State construct(ClassNode type, Statement body, State state) {
        Frame frame = new Frame(type)
        State current = state
        for (FieldNode field : type.fields) {
            Expression initial = field.initialValueExpression
            if (field.isStatic() || initial == null) {
                continue
            }
            boolean carries = eval(initial, current, frame)
            if (field.name == property && type.name == declaring.name) {
                // the value the property is declared with goes into its field, whatever its setter is
                current.value = stringConstant(initial) ?: UNKNOWN
            }
            if (carries) {
                current.escaped = true
            }
        }
        for (Statement statement : type.objectInitializerStatements) {
            current = run(statement, current, frame)
            if (current == null) {
                return State.join(null, frame.returned)
            }
        }
        State end = body == null ? current : run(body, current, frame)
        State.join(end, frame.returned)
    }

    // the state after the statement when it completes, or null when it does not; what a return leaves goes to the frame
    private State run(Statement statement, State state, Frame frame) {
        if (statement == null || statement instanceof EmptyStatement) {
            return state
        }
        if (statement instanceof BlockStatement) {
            State current = state
            for (Statement inner : ((BlockStatement) statement).statements) {
                current = run(inner, current, frame)
                if (current == null) {
                    return null
                }
            }
            return current
        }
        if (statement instanceof ExpressionStatement) {
            eval(((ExpressionStatement) statement).expression, state, frame)
            return state
        }
        if (statement instanceof ReturnStatement) {
            eval(((ReturnStatement) statement).expression, state, frame)
            frame.returned = State.join(frame.returned, state)
            return null
        }
        if (statement instanceof ThrowStatement) {
            // the command is not made when this runs
            eval(((ThrowStatement) statement).expression, state, frame)
            return null
        }
        if (statement instanceof IfStatement) {
            IfStatement choice = (IfStatement) statement
            truth(choice.booleanExpression, state, frame)
            return State.join(run(choice.ifBlock, state.copy(), frame), run(choice.elseBlock, state.copy(), frame))
        }
        // a loop, a switch, a try, and the rest are not followed
        State unknown = new State(UNKNOWN, true)
        if (returns(statement)) {
            frame.returned = State.join(frame.returned, unknown)
        }
        unknown
    }

    // evaluates the expression, and says whether its value can be, or hold, the command
    private boolean eval(Expression expression, State state, Frame frame) {
        if (expression == null || expression instanceof ConstantExpression || expression instanceof ClassExpression ||
                expression instanceof EmptyExpression || expression instanceof FieldExpression) {
            return false
        }
        if (expression instanceof VariableExpression) {
            return variable((VariableExpression) expression, state, frame)
        }
        if (expression instanceof DeclarationExpression) {
            if (eval(((DeclarationExpression) expression).rightExpression, state, frame)) {
                state.escaped = true
            }
            return false
        }
        if (expression instanceof BinaryExpression) {
            return binary((BinaryExpression) expression, state, frame)
        }
        if (expression instanceof PropertyExpression) {
            return read((PropertyExpression) expression, state, frame)
        }
        if (expression instanceof MethodCallExpression) {
            return call((MethodCallExpression) expression, state, frame)
        }
        if (expression instanceof StaticMethodCallExpression) {
            return staticCall((StaticMethodCallExpression) expression, state, frame)
        }
        if (expression instanceof ConstructorCallExpression) {
            return instantiate((ConstructorCallExpression) expression, state, frame)
        }
        if (expression instanceof ClosureExpression) {
            // a closure holds the command it is written in
            return true
        }
        if (expression instanceof MethodPointerExpression) {
            eval(((MethodPointerExpression) expression).expression, state, frame)
            return true
        }
        if (expression instanceof TernaryExpression) {
            return choose((TernaryExpression) expression, state, frame)
        }
        if (expression instanceof BooleanExpression) {
            truth(((BooleanExpression) expression).expression, state, frame)
            return false
        }
        if (expression instanceof CastExpression) {
            boolean carries = eval(((CastExpression) expression).expression, state, frame)
            external(state, carries)
            return carries
        }
        if (expression instanceof GStringExpression) {
            return all(((GStringExpression) expression).values, state, frame)
        }
        if (expression instanceof ListExpression || expression instanceof TupleExpression) {
            List<Expression> elements = expression instanceof ListExpression ?
                    ((ListExpression) expression).expressions : ((TupleExpression) expression).expressions
            return all(elements, state, frame)
        }
        if (expression instanceof ArrayExpression) {
            ArrayExpression array = (ArrayExpression) expression
            boolean carries = all(array.expressions ?: [], state, frame)
            all(array.sizeExpression ?: [], state, frame)
            return carries
        }
        if (expression instanceof MapExpression) {
            boolean carries = false
            for (MapEntryExpression entry : ((MapExpression) expression).mapEntryExpressions) {
                boolean key = eval(entry.keyExpression, state, frame)
                carries = eval(entry.valueExpression, state, frame) || carries
                // a key that is not a constant is asked for its hash
                if (!(entry.keyExpression instanceof ConstantExpression)) {
                    external(state, key)
                }
                carries = carries || key
            }
            return carries
        }
        if (expression instanceof RangeExpression) {
            RangeExpression range = (RangeExpression) expression
            boolean carries = eval(range.from, state, frame) | eval(range.to, state, frame)
            if (!(range.from instanceof ConstantExpression && range.to instanceof ConstantExpression)) {
                external(state, carries)
            }
            return false
        }
        if (expression instanceof UnaryMinusExpression || expression instanceof UnaryPlusExpression ||
                expression instanceof BitwiseNegationExpression) {
            // an operator is a method of the value
            external(state, eval(operand(expression), state, frame))
            return false
        }
        if (expression instanceof PostfixExpression || expression instanceof PrefixExpression) {
            // i++ on a local variable; on a property it reads and writes it, which is not followed
            Expression target = operand(expression)
            if (isLocal(target, frame)) {
                external(state, false)
                return false
            }
        }
        unknown(state)
    }

    private static Expression operand(Expression expression) {
        if (expression instanceof UnaryMinusExpression) {
            return ((UnaryMinusExpression) expression).expression
        }
        if (expression instanceof UnaryPlusExpression) {
            return ((UnaryPlusExpression) expression).expression
        }
        if (expression instanceof BitwiseNegationExpression) {
            return ((BitwiseNegationExpression) expression).expression
        }
        expression instanceof PostfixExpression ? ((PostfixExpression) expression).expression : ((PrefixExpression) expression).expression
    }

    private boolean variable(VariableExpression variable, State state, Frame frame) {
        if (variable.isThisExpression() || variable.isSuperExpression() || isAlias(variable, frame)) {
            return true
        }
        Variable accessed = variable.accessedVariable
        if (accessed instanceof FieldNode || accessed instanceof PropertyNode || accessed instanceof DynamicVariable) {
            if (accessed.isStatic()) {
                return false
            }
            return readProperty(variable.name, frame.owner.getDeclaredField(variable.name) != null, state, frame)
        }
        // a local variable or a parameter
        false
    }

    private boolean read(PropertyExpression access, State state, Frame frame) {
        Expression object = access.objectExpression
        String name = access.propertyAsString
        if (name != null && !access.isSpreadSafe() && (isThis(object) || isAlias(object, frame))) {
            if (access instanceof AttributeExpression) {
                return false
            }
            return readProperty(name, isThis(object) && frame.owner.getDeclaredField(name) != null, state, frame)
        }
        // a property of another object, which its getter gives
        external(state, eval(object, state, frame))
        false
    }

    // a property of the command read: from its own field in the class that declares it, or through its getter
    private boolean readProperty(String name, boolean ownField, State state, Frame frame) {
        if (ownField) {
            return false
        }
        String suffix = name.capitalize()
        boolean read = false
        boolean carries = false
        for (String getter : ['get' + suffix, 'is' + suffix]) {
            if (!candidates(getter, 0, command, frame.owner).isEmpty()) {
                carries = callOnCommand(getter, [], false, false, state, frame) || carries
                read = true
            }
        }
        if (!read && !hasMember(name)) {
            // a property the runtime looks for when it runs
            unknown(state)
        }
        carries
    }

    private boolean binary(BinaryExpression expression, State state, Frame frame) {
        int operation = expression.operation.type
        if (operation == Types.ASSIGN) {
            assign(expression.leftExpression, expression.rightExpression, state, frame)
            return false
        }
        if (Types.ofType(operation, Types.ASSIGNMENT_OPERATOR)) {
            // name += '-x' reads the target, works the value out with a method of the value, and writes it
            Expression target = expression.leftExpression
            boolean carries = eval(target, state, frame) | eval(expression.rightExpression, state, frame)
            external(state, carries)
            if (target instanceof VariableExpression ||
                    (target instanceof PropertyExpression && isCommand(((PropertyExpression) target).objectExpression, frame))) {
                write(target, null, false, state, frame)
            } else {
                // an element, or a property of another object
                external(state, carries || isCommand(subscripted(target), frame))
            }
            return false
        }
        if (operation == Types.LEFT_SQUARE_BRACKET && isCommand(expression.leftExpression, frame)) {
            return callOnCommand('getAt', [expression.rightExpression], false, false, state, frame)
        }
        if (operation == Types.KEYWORD_INSTANCEOF || operation == Types.COMPARE_NOT_INSTANCEOF) {
            eval(expression.leftExpression, state, frame)
            return false
        }
        if (operation == Types.LOGICAL_AND || operation == Types.LOGICAL_OR) {
            truth(expression.leftExpression, state, frame)
            State skipped = state.copy()
            truth(expression.rightExpression, state, frame)
            state.take(State.join(skipped, state))
            return false
        }
        // an operator is a method of the value on its left
        external(state, eval(expression.leftExpression, state, frame) | eval(expression.rightExpression, state, frame))
        false
    }

    private void assign(Expression target, Expression value, State state, Frame frame) {
        if (target instanceof BinaryExpression && ((BinaryExpression) target).operation.type == Types.LEFT_SQUARE_BRACKET) {
            BinaryExpression subscript = (BinaryExpression) target
            if (isCommand(subscript.leftExpression, frame)) {
                callOnCommand('putAt', [subscript.rightExpression, value], false, false, state, frame)
                return
            }
            boolean carries = eval(subscript.leftExpression, state, frame) | eval(subscript.rightExpression, state, frame) |
                    eval(value, state, frame)
            external(state, carries)
            return
        }
        if (target instanceof PropertyExpression && !isCommand(((PropertyExpression) target).objectExpression, frame)) {
            // a property of another object, which its setter sets; through super, a property of the command not followed
            boolean receiver = eval(((PropertyExpression) target).objectExpression, state, frame)
            external(state, eval(value, state, frame) || receiver)
            return
        }
        write(target, value, eval(value, state, frame), state, frame)
    }

    // the target, a variable or a property of the command, set to the value; a value of null is one worked out
    private void write(Expression target, Expression value, boolean carries, State state, Frame frame) {
        if (target instanceof VariableExpression) {
            VariableExpression variable = (VariableExpression) target
            Variable accessed = variable.accessedVariable
            if (accessed instanceof FieldNode || accessed instanceof PropertyNode || accessed instanceof DynamicVariable) {
                if (accessed.isStatic()) {
                    escapeIf(state, carries)
                } else {
                    writeProperty(variable.name, value, carries, frame.owner.getDeclaredField(variable.name) != null, state, frame)
                }
                return
            }
            // a local variable or a parameter, which no longer holds the command if it did
            frame.aliases.remove(variable.name)
            escapeIf(state, carries)
            return
        }
        if (target instanceof PropertyExpression && isCommand(((PropertyExpression) target).objectExpression, frame)) {
            PropertyExpression access = (PropertyExpression) target
            String name = access.propertyAsString
            boolean onThis = isThis(access.objectExpression)
            if (name == null || access.isSafe() || access.isSpreadSafe()) {
                // a property whose name is worked out
                unknown(state)
                return
            }
            if (access instanceof AttributeExpression) {
                // the field itself, written without its setter
                if (name != property) {
                    escapeIf(state, carries)
                } else if (onThis && frame.owner.name == declaring.name) {
                    set(state, value, carries)
                } else {
                    unknown(state)
                }
                return
            }
            writeProperty(name, value, carries, onThis && frame.owner.getDeclaredField(name) != null, state, frame)
            return
        }
        unknown(state)
    }

    // a property of the command written: into the field of the class whose code it is, or through the runtime and a setter
    private void writeProperty(String name, Expression value, boolean carries, boolean ownField, State state, Frame frame) {
        if (ownField) {
            // the field of the class itself, which is the property only in the class that declares it
            if (name == property && frame.owner.name == declaring.name) {
                set(state, value, carries)
            } else {
                escapeIf(state, carries)
            }
            return
        }
        if (name == property) {
            if (shadowed) {
                unknown(state)
            } else {
                set(state, value, carries)
            }
            return
        }
        // another property, through its setter: the one the compiler writes sets its field and does nothing else
        escapeIf(state, carries)
        String other = 'set' + name.capitalize()
        if (candidates(other, 1, command, frame.owner).isEmpty() && generatedAccessor(other, 1)) {
            return
        }
        // the value is worked out already
        callOnCommand(other, [value instanceof ConstantExpression ? value : EmptyExpression.INSTANCE], false, false, state, frame)
    }

    // the property set to the value through its setter or its field: the string constant the value is, if it is one
    private void set(State state, Expression value, boolean carries) {
        escapeIf(state, carries)
        if (setterWritten) {
            unknown(state)
            return
        }
        state.value = value == null ? UNKNOWN : stringConstant(value) ?: UNKNOWN
    }

    private boolean call(MethodCallExpression call, State state, Frame frame) {
        Expression receiver = call.objectExpression
        String name = call.methodAsString
        // a call written without a receiver has this as its receiver
        boolean onCommand = isThis(receiver) || isAlias(receiver, frame)
        boolean onSuper = receiver instanceof VariableExpression && ((VariableExpression) receiver).isSuperExpression()
        List<Expression> arguments = arguments(call.arguments)
        if (name != null && !call.isSpreadSafe() && arguments != null) {
            if (onCommand || onSuper) {
                return callOnCommand(name, arguments, onSuper, false, state, frame)
            }
            if (receiver instanceof ClassExpression && isTraitInit(receiver.type, name)) {
                traitInit(receiver.type, name, state)
                return false
            }
            if (receiver instanceof ClassExpression && inHierarchy(receiver.type)) {
                return callOnCommand(name, arguments, false, true, state, frame)
            }
        }
        // a method of another object, or one the runtime looks for
        external(state, eval(receiver, state, frame) | eval(call.arguments, state, frame))
        false
    }

    private boolean staticCall(StaticMethodCallExpression call, State state, Frame frame) {
        ClassNode owner = call.ownerType
        List<Expression> arguments = arguments(call.arguments)
        if (isTraitInit(owner, call.method)) {
            traitInit(owner, call.method, state)
            return false
        }
        if (arguments != null && inHierarchy(owner)) {
            return callOnCommand(call.method, arguments, false, true, state, frame)
        }
        external(state, eval(call.arguments, state, frame))
        false
    }

    // what a class that implements a trait runs, with the command, to give the fields of the trait their values
    private static boolean isTraitInit(ClassNode helper, String method) {
        method.startsWith(TRAIT_INIT) && helper.name.endsWith(TRAIT_HELPER)
    }

    // the traits of the command contract give their fields no value, and nor does a trait compiled with the command that
    // gives none; any other trait may set the property, and keep the command
    private static void traitInit(ClassNode helper, String method, State state) {
        String trait = helper.name.substring(0, helper.name.length() - TRAIT_HELPER.length())
        boolean empty = helper.isPrimaryClassNode() && helper.getDeclaredMethods(method).every { MethodNode init -> isEmpty(init.code) }
        if (!CONTRACT_TRAITS.contains(trait) && !empty) {
            unknown(state)
        }
    }

    private boolean instantiate(ConstructorCallExpression call, State state, Frame frame) {
        boolean carries = eval(call.arguments, state, frame)
        if (call.isSuperCall()) {
            // what the class extends does is read with that class
            escapeIf(state, carries)
            return false
        }
        if (call.isThisCall()) {
            // another constructor of the class is not followed
            unknown(state)
            return false
        }
        // an instance of an inner class holds the command it is made in
        ClassNode type = call.type
        boolean inner = call.isUsingAnonymousInnerClass() || (type instanceof InnerClassNode && !type.isStaticClass() && inHierarchy(type.outerClass))
        external(state, carries || inner)
        false
    }

    private boolean choose(TernaryExpression choice, State state, Frame frame) {
        if (choice instanceof ElvisOperatorExpression) {
            boolean carries = eval(choice.trueExpression, state, frame)
            external(state, carries)
            State skipped = state.copy()
            carries = eval(choice.falseExpression, state, frame) || carries
            state.take(State.join(skipped, state))
            return carries
        }
        truth(choice.booleanExpression.expression, state, frame)
        State first = state.copy()
        boolean carries = eval(choice.trueExpression, first, frame)
        State second = state.copy()
        carries = eval(choice.falseExpression, second, frame) || carries
        state.take(State.join(first, second))
        carries
    }

    // a condition: the value, and what makes it true or false, which is a method of the value
    private void truth(Expression condition, State state, Frame frame) {
        external(state, eval(condition, state, frame))
    }

    // a call of a method on the command, or of a static method of its classes, from the code of a class: the setter of
    // the property, a method of a class compiled with the command, which is read, or anything else, which is not
    private boolean callOnCommand(String name, List<Expression> arguments, boolean viaSuper, boolean onClass, State state, Frame frame) {
        List<Boolean> self = []
        boolean handed = false
        for (Expression argument : arguments) {
            boolean carries = eval(argument, state, frame)
            boolean isSelf = isCommand(argument, frame)
            self << isSelf
            handed = handed || (carries && !isSelf)
        }
        if (name == setter && !onClass) {
            if (arguments.size() == 1 && !viaSuper) {
                set(state, arguments[0], handed || self.any())
            } else {
                unknown(state)
            }
            return false
        }
        if (RUNTIME_METHODS.contains(name)) {
            return unknown(state)
        }
        ClassNode from = viaSuper ? frame.owner.superClass : command
        List<MethodNode> candidates = candidates(name, arguments.size(), from, frame.owner)
                .findAll { MethodNode method -> !onClass || method.isStatic() }
        if (candidates.isEmpty()) {
            // the getter or the setter the compiler writes for a property, which reads or writes its field and nothing else
            if (onClass || !generatedAccessor(name, arguments.size())) {
                unknown(state)
            }
            escapeIf(state, handed || self.any())
            return false
        }
        if (candidates.any { MethodNode method -> !readable(method) || calling.contains(method) } ||
                calling.size() >= DEEPEST_CALL || calls + candidates.size() > MOST_CALLS) {
            return unknown(state)
        }
        calls += candidates.size()
        // a value that holds the command, passed on, is not followed
        escapeIf(state, handed)
        State end = null
        for (MethodNode method : candidates) {
            end = State.join(end, invoke(method, arguments, self, state.copy()) ?: new State(UNKNOWN, true))
        }
        state.take(end)
        candidates.any { MethodNode method -> mayReturnCommand(method) }
    }

    // the state after the method returns, or null when it cannot return
    private State invoke(MethodNode method, List<Expression> arguments, List<Boolean> self, State state) {
        Frame frame = new Frame(method.declaringClass)
        Parameter[] parameters = method.parameters
        boolean exact = arguments.size() == parameters.length && !(parameters.length > 0 && parameters[parameters.length - 1].type.isArray())
        for (int i = 0; i < arguments.size(); i++) {
            if (self[i]) {
                // the command, passed as itself to a parameter, is the command in the method; otherwise it is not followed
                if (exact) {
                    frame.aliases << parameters[i].name
                } else {
                    state.escaped = true
                }
            }
        }
        if (arguments.size() < parameters.length &&
                parameters.any { Parameter parameter -> parameter.hasInitialExpression() && !(parameter.initialExpression instanceof ConstantExpression) }) {
            unknown(state)
        }
        calling.push(method)
        try {
            return State.join(run(method.code, state, frame), frame.returned)
        } finally {
            calling.pop()
        }
    }

    // the methods a call by the name with that many arguments can run, from the class the search starts at
    private List<MethodNode> candidates(String name, int count, ClassNode from, ClassNode caller) {
        int start = from == null ? -1 : hierarchy.findIndexOf { ClassNode type -> type.name == from.name }
        if (start < 0) {
            return []
        }
        List<MethodNode> found = []
        for (ClassNode type : hierarchy.subList(start, hierarchy.size())) {
            for (MethodNode method : type.getDeclaredMethods(name)) {
                // a private method is called from the class that declares it only
                if (accepts(method, count) && (!method.isPrivate() || type.name == caller.name)) {
                    found << method
                }
            }
        }
        // parameters with a default value make methods that override one with fewer parameters
        if (found.any { MethodNode method -> method.parameters.any { Parameter parameter -> parameter.hasInitialExpression() } }) {
            return found
        }
        // an instance method is overridden by the one nearer to the command with the same parameters
        Set<String> signatures = new HashSet<String>()
        found.findAll { MethodNode method -> method.isStatic() || method.isPrivate() || signatures.add(signature(method)) }
    }

    private static boolean accepts(MethodNode method, int count) {
        Parameter[] parameters = method.parameters
        int required = parameters.count { Parameter parameter -> !parameter.hasInitialExpression() } as int
        boolean varargs = parameters.length > 0 && parameters[parameters.length - 1].type.isArray()
        (count >= required && count <= parameters.length) || (varargs && count >= parameters.length - 1)
    }

    private static String signature(MethodNode method) {
        method.parameters.collect { Parameter parameter -> parameter.type.name }.join(',')
    }

    // a method whose code is here to be read
    private static boolean readable(MethodNode method) {
        method.code != null && !method.isAbstract() && method.declaringClass.isPrimaryClassNode() &&
                method.getAnnotations(TRAIT_BRIDGE).isEmpty()
    }

    // whether the method can return the command, or something that holds it
    private static boolean mayReturnCommand(MethodNode method) {
        ClassNode type = method.returnType
        !(ClassHelper.isPrimitiveVoid(type) || ClassHelper.isPrimitiveType(type) || ClassHelper.isStringType(type) ||
                ClassHelper.isPrimitiveType(ClassHelper.getUnwrapper(type)))
    }

    // a getter with no argument or a setter with one, of a property a class compiled with the command declares; of an
    // annotated property, a transformation may write it to do more
    private boolean generatedAccessor(String name, int count) {
        String suffix = null
        if (count == 0 && name.startsWith('get') && name.length() > 3) {
            suffix = name.substring(3)
        } else if (count == 0 && name.startsWith('is') && name.length() > 2) {
            suffix = name.substring(2)
        } else if (count == 1 && name.startsWith('set') && name.length() > 3) {
            suffix = name.substring(3)
        }
        if (suffix == null) {
            return false
        }
        // getURL is the accessor of URL, getName of name
        Set<String> names = [suffix, suffix.uncapitalize()].toSet()
        hierarchy.any { ClassNode type ->
            type.isPrimaryClassNode() && names.any { String candidate ->
                PropertyNode node = type.getProperty(candidate)
                node != null && !node.isStatic() && node.annotations.isEmpty() && node.field.annotations.isEmpty()
            }
        }
    }

    // a property or a field of that name in the command or a class it extends
    private boolean hasMember(String name) {
        hierarchy.any { ClassNode type -> type.getProperty(name) != null || type.getDeclaredField(name) != null }
    }

    private boolean inHierarchy(ClassNode type) {
        type != null && hierarchy.any { ClassNode member -> member.name == type.name }
    }

    // what a subscript is applied to, a[i], or what has the property, a.b
    private static Expression subscripted(Expression target) {
        if (target instanceof BinaryExpression) {
            return ((BinaryExpression) target).leftExpression
        }
        target instanceof PropertyExpression ? ((PropertyExpression) target).objectExpression : target
    }

    private boolean isLocal(Expression expression, Frame frame) {
        expression instanceof VariableExpression && !isCommand(expression, frame) &&
                !(((VariableExpression) expression).accessedVariable instanceof FieldNode ||
                        ((VariableExpression) expression).accessedVariable instanceof PropertyNode ||
                        ((VariableExpression) expression).accessedVariable instanceof DynamicVariable) &&
                !((VariableExpression) expression).isSuperExpression()
    }

    private boolean isCommand(Expression expression, Frame frame) {
        isThis(expression) || isAlias(expression, frame)
    }

    private static boolean isThis(Expression expression) {
        expression instanceof VariableExpression && ((VariableExpression) expression).isThisExpression()
    }

    // a parameter of the method that was given the command itself
    private static boolean isAlias(Expression expression, Frame frame) {
        expression instanceof VariableExpression && ((VariableExpression) expression).accessedVariable instanceof Parameter &&
                frame.aliases.contains(((VariableExpression) expression).name)
    }

    private boolean all(List<Expression> expressions, State state, Frame frame) {
        boolean carries = false
        for (Expression expression : expressions) {
            carries = eval(expression, state, frame) || carries
        }
        carries
    }

    // the arguments of a call, or null when they are not a plain list
    private static List<Expression> arguments(Expression arguments) {
        arguments instanceof TupleExpression ? ((TupleExpression) arguments).expressions : null
    }

    // code that is not read runs: given the command, it may set the property and keep the command; not given it, it can
    // reach the command only when the command was handed out before
    private static void external(State state, boolean given) {
        if (given) {
            state.value = UNKNOWN
            state.escaped = true
        } else if (state.escaped) {
            state.value = UNKNOWN
        }
    }

    private static void escapeIf(State state, boolean carries) {
        if (carries) {
            state.escaped = true
        }
    }

    private static boolean unknown(State state) {
        state.value = UNKNOWN
        state.escaped = true
        false
    }

    private static String stringConstant(Expression expression) {
        expression instanceof ConstantExpression && ((ConstantExpression) expression).value instanceof String ?
                (String) ((ConstantExpression) expression).value : null
    }

    private static boolean isEmpty(Statement code) {
        code == null || code instanceof EmptyStatement || (code instanceof BlockStatement && ((BlockStatement) code).statements.every { Statement statement -> isEmpty(statement) })
    }

    // whether the statement has a return of its own, not one of a closure in it
    private static boolean returns(Statement statement) {
        boolean[] found = [false] as boolean[]
        statement.visit(new CodeVisitorSupport() {

            @Override
            void visitReturnStatement(ReturnStatement returnStatement) {
                found[0] = true
            }

            @Override
            void visitClosureExpression(ClosureExpression expression) {
            }
        })
        found[0]
    }

    private static Set<String> runtimeMethods() {
        Set<String> names = new HashSet<String>()
        List<Class> classes = [GroovyObject, DefaultGroovyStaticMethods] + (DefaultGroovyMethods.DGM_LIKE_CLASSES as List<Class>) +
                (DefaultGroovyMethods.ADDITIONAL_CLASSES as List<Class>)
        for (Class type : classes) {
            for (Method method : type.methods) {
                names << method.name
            }
        }
        names.asImmutable()
    }

    // what the property is at a point of the code, and whether the command was handed to code that is not read
    private static final class State {

        Object value

        boolean escaped

        State(Object value, boolean escaped) {
            this.value = value
            this.escaped = escaped
        }

        State copy() {
            new State(value, escaped)
        }

        void take(State other) {
            value = other.value
            escaped = other.escaped
        }

        // either of two ways the code can go
        static State join(State first, State second) {
            if (first == null) {
                return second?.copy()
            }
            if (second == null) {
                return first.copy()
            }
            new State(first.value == second.value ? first.value : UNKNOWN, first.escaped || second.escaped)
        }
    }

    // the code of a class being read: the class, the parameters that hold the command, what its returns leave
    private static final class Frame {

        final ClassNode owner

        final Set<String> aliases = new HashSet<String>()

        State returned

        Frame(ClassNode owner) {
            this.owner = owner
        }
    }
}
