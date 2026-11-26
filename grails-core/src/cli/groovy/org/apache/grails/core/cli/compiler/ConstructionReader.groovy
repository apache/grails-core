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
import groovy.transform.Generated
import groovy.transform.PackageScope
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotationNode
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
 * <p>The property is known after a statement that sets it to a constant of its kind, a string for
 * {@link #read} and {@code true} or {@code false} for {@link #readBoolean}: an assignment,
 * {@code name = '...'} or {@code this.name = '...'}, or a call of its setter, {@code setName('...')},
 * in that code or in a method it calls on the command, or a static method of its classes it gives the
 * command to, that is compiled with the command. Such a method is read in turn, to a bounded depth, and
 * is found as Groovy finds it: a private method of the class whose code calls it; of the methods with
 * the same parameters, the one nearest to the command; every method the call can mean otherwise. Where
 * the code can go two ways that leave the property different, a condition or two constructors, it is
 * not known. Each way is read with what holds the command on that way: a parameter of a method that was
 * given the command and is given another value on one way holds the command on the other ways still,
 * and where the ways meet again it is not known what it holds, so what is done through it from there
 * makes the property unknown.</p>
 *
 * <p>The code with which a trait compiled with the command gives its fields their values when the
 * command is made is read as the rest; that of a trait compiled elsewhere, other than those of the
 * command contract, is not. Such a trait is read from its class instead: one that has neither the
 * property nor a getter or a setter of it, as a helper trait of a module's standalone tools has not,
 * leaves the property as it is.</p>
 *
 * <p>What can set the property and is not read makes it unknown: a method that is not compiled with
 * the command or has no code, one of the Groovy runtime or one the runtime looks for, an assignment
 * through {@code super}, by a name that is worked out or through a subscript, a setter of the property
 * written by hand, a trait compiled elsewhere that has the property, its getter or its setter, and a
 * class that takes over how its properties are set and its methods found. Code that is not read and is
 * not given the command, such as {@code new JsonSlurper()}, cannot reach it, and leaves the property
 * as it is until the command is handed to code that is not read (a closure holds the command, and a
 * class compiled elsewhere that the command extends may have handed it out): from then on any code that
 * is not read makes the property unknown. Changes to the meta class of a command, methods an extension
 * module adds, and code that runs in another thread are not seen.</p>
 *
 * <p>A property of a trait of the command contract ({@link #readTraitBoolean}) gets the value its trait
 * gives it when the trait initializes the class that implements it, which is after the field
 * initializers and the initializer blocks of that class: a constant a statement sets before then is
 * undone, and makes the property unknown, as what the code says is not what the command is. The
 * getters and the setters that trait adds to the class read and write its field and nothing else.</p>
 *
 * @since 9.0
 */
@PackageScope
@CompileStatic
class ConstructionReader {

    private static final Object UNKNOWN = new Object()

    // the trait of the command contract whose accessors read and write a field and do nothing else
    private static final String APPLICATION_TOOL = 'org.apache.grails.core.cli.ApplicationTool'

    // how deep calls are followed, and how many calls one property is read through
    private static final int DEEPEST_CALL = 16

    private static final int MOST_CALLS = 256

    // the traits of the command contract, whose fields are given no value when a command is made
    private static final Set<String> CONTRACT_TRAITS = [
            'org.apache.grails.core.cli.GrailsTool',
            'org.apache.grails.core.cli.ApplicationTool',
            'org.apache.grails.core.cli.StandaloneTool',
            'org.apache.grails.core.cli.ToolSupport',
            'org.apache.grails.core.cli.ApplicationCommand',
            'org.apache.grails.core.cli.GrailsApplicationCommand',
            'grails.codegen.model.ModelBuilder',
    ].toSet().asImmutable()

    private static final String TRAIT_HELPER = '$Trait$Helper'

    private static final String TRAIT_INIT = '$init$'

    private static final ClassNode TRAIT_BRIDGE = ClassHelper.make(Traits.TraitBridge)

    private static final String TRAIT_CLASS_MEMBER = 'traitClass'

    private static final ClassNode GENERATED = ClassHelper.make(Generated)

    // methods of a class that decide how its properties are set and its methods are found
    private static final List<String> MOP_METHODS = ['getProperty', 'setProperty', 'invokeMethod', 'methodMissing', 'propertyMissing'].asImmutable()

    // the names of the methods the Groovy runtime gives every object: a call by such a name can run one of those
    private static final Set<String> RUNTIME_METHODS = runtimeMethods()

    private final ClassNode command

    private final ClassNode declaring

    private final String property

    private final String setter

    // the class of the constants that make the property known
    private final Class<?> kind

    // the trait of the command contract that declares the property, or null for a property a class declares
    private final String trait

    // the value that trait gives the property
    private final Object traitValue

    // the command and the classes it extends, nearest first, without Object
    private final List<ClassNode> hierarchy

    private final boolean setterWritten

    private final boolean shadowed

    private final Deque<MethodNode> calling = new ArrayDeque<MethodNode>()

    private int calls

    private ConstructionReader(ClassNode command, ClassNode declaring, String property, Class<?> kind, String trait, Object traitValue) {
        this.command = command
        this.declaring = declaring
        this.property = property
        this.setter = 'set' + property.capitalize()
        this.kind = kind
        this.trait = trait
        this.traitValue = traitValue
        List<ClassNode> types = []
        for (ClassNode type = command; type != null && type != ClassHelper.OBJECT_TYPE; type = type.superClass) {
            types << type
        }
        this.hierarchy = types.asImmutable()
        List<ClassNode> below = types.subList(0, Math.max(0, types.findIndexOf { ClassNode type -> type.name == declaring.name }) + 1)
        // a setter written by hand may make of a value something else; the one a trait of the contract adds sets its field
        this.setterWritten = below.any { ClassNode type ->
            type.getDeclaredMethods(setter).any { MethodNode method -> !method.isStatic() && !isContractAccessor(method) }
        }
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
        (String) new ConstructionReader(command, declaring, property, String, null, null).read()
    }

    /**
     * @param command the command class
     * @param declaring the class, the command or one it extends, that declares the property
     * @param property the name of a property of the type {@code boolean}
     * @return the constant {@code true} or {@code false} the property is once an instance of the
     * command is made, or {@code null} when that cannot be known from the source
     */
    static Boolean readBoolean(ClassNode command, ClassNode declaring, String property) {
        (Boolean) new ConstructionReader(command, declaring, property, Boolean, null, null).read()
    }

    /**
     * @param command the command class
     * @param declaring the class, the command or one it extends, that implements the trait
     * @param property the name of a property of the type {@code boolean} the trait declares
     * @param trait the name of the trait, one of the command contract
     * @param value the value the trait gives the property
     * @return the constant {@code true} or {@code false} the property is once an instance of the
     * command is made, or {@code null} when that cannot be known from the source
     */
    static Boolean readTraitBoolean(ClassNode command, ClassNode declaring, String property, String trait, boolean value) {
        (Boolean) new ConstructionReader(command, declaring, property, Boolean, trait, value).read()
    }

    /**
     * @param command the command class
     * @param property the name of a property
     * @return whether code of the command, of a class it extends or of a trait it has, that is compiled
     * with it, assigns the property, calls its setter or its own {@code setProperty}, or sets a property
     * or calls a method whose name is worked out; the getters, setters and other methods a compiler
     * writes are not looked at
     */
    static boolean writes(ClassNode command, String property) {
        String setterName = 'set' + property.capitalize()
        boolean[] found = [false] as boolean[]
        CodeVisitorSupport targets = new CodeVisitorSupport() {

            @Override
            void visitVariableExpression(VariableExpression expression) {
                found[0] = found[0] || expression.name == property
            }

            @Override
            void visitPropertyExpression(PropertyExpression expression) {
                String name = expression.propertyAsString
                found[0] = found[0] || name == null || name == property
            }

            @Override
            void visitAttributeExpression(AttributeExpression expression) {
                String name = expression.propertyAsString
                found[0] = found[0] || name == null || name == property
            }
        }
        CodeVisitorSupport writing = new CodeVisitorSupport() {

            @Override
            void visitBinaryExpression(BinaryExpression expression) {
                if (Types.ofType(expression.operation.type, Types.ASSIGNMENT_OPERATOR)) {
                    expression.leftExpression.visit(targets)
                }
                super.visitBinaryExpression(expression)
            }

            @Override
            void visitMethodCallExpression(MethodCallExpression call) {
                String name = call.methodAsString
                Expression receiver = call.objectExpression
                // the instance is this in a class, and the first parameter of a method of a trait
                boolean onSelf = receiver instanceof VariableExpression &&
                        (((VariableExpression) receiver).isThisExpression() || ((VariableExpression) receiver).name == '$self')
                found[0] = found[0] || name == null || name == setterName || (onSelf && name == 'setProperty')
                super.visitMethodCallExpression(call)
            }
        }
        for (ClassNode type : written(command)) {
            for (FieldNode field : type.fields) {
                field.initialValueExpression?.visit(writing)
            }
            for (Statement statement : type.objectInitializerStatements) {
                statement.visit(writing)
            }
            List<MethodNode> code = new ArrayList<MethodNode>(type.declaredConstructors)
            code.addAll(type.methods)
            for (MethodNode method : code) {
                if (method.getAnnotations(GENERATED).isEmpty() && method.getAnnotations(TRAIT_BRIDGE).isEmpty()) {
                    method.code?.visit(writing)
                }
            }
        }
        found[0]
    }

    /**
     * @param method a method of a class
     * @return the name of the trait the method is added to the class for, or {@code null} for a method
     * no trait adds
     */
    static String bridgedTrait(MethodNode method) {
        for (AnnotationNode annotation : method.getAnnotations(TRAIT_BRIDGE)) {
            Expression traitClass = annotation.getMember(TRAIT_CLASS_MEMBER)
            if (traitClass instanceof ClassExpression) {
                return ((ClassExpression) traitClass).type.name
            }
        }
        null
    }

    // the classes compiled with the command whose code can set a property of it: the command, the classes it
    // extends, the traits they have, and the classes declared in any of them
    private static Set<ClassNode> written(ClassNode command) {
        Set<ClassNode> types = new LinkedHashSet<ClassNode>()
        Deque<ClassNode> pending = new ArrayDeque<ClassNode>()
        for (ClassNode type = command; type != null && type != ClassHelper.OBJECT_TYPE; type = type.superClass) {
            pending << type
            pending.addAll(type.allInterfaces.findAll { ClassNode trait -> Traits.isTrait(trait) })
        }
        while (!pending.isEmpty()) {
            ClassNode type = pending.poll()
            if (type.isPrimaryClassNode() && types.add(type)) {
                pending.addAll(type.innerClasses.toList())
            }
        }
        types
    }

    // a getter or a setter the trait whose accessors do nothing else adds to a class
    private static boolean isContractAccessor(MethodNode method) {
        if (bridgedTrait(method) != APPLICATION_TOOL) {
            return false
        }
        int count = method.parameters.length
        ((method.name.startsWith('get') || method.name.startsWith('is')) && count == 0) || (method.name.startsWith('set') && count == 1)
    }

    /**
     * @param declaring the class that declares the property
     * @param property the name of a property that is final
     * @return whether a constructor of the class has a statement that sets the property, or a property
     * of the command whose name is worked out, which is the only code that can set a final property
     * over the value it is declared with
     */
    static boolean setsInConstructor(ClassNode declaring, String property) {
        boolean[] found = [false] as boolean[]
        CodeVisitorSupport targets = new CodeVisitorSupport() {

            @Override
            void visitVariableExpression(VariableExpression expression) {
                found[0] = found[0] || expression.name == property
            }

            @Override
            void visitPropertyExpression(PropertyExpression expression) {
                String name = expression.propertyAsString
                found[0] = found[0] || name == null || name == property
                super.visitPropertyExpression(expression)
            }

            @Override
            void visitAttributeExpression(AttributeExpression expression) {
                String name = expression.propertyAsString
                found[0] = found[0] || name == null || name == property
                super.visitAttributeExpression(expression)
            }
        }
        CodeVisitorSupport assignments = new CodeVisitorSupport() {

            @Override
            void visitBinaryExpression(BinaryExpression expression) {
                if (Types.ofType(expression.operation.type, Types.ASSIGNMENT_OPERATOR)) {
                    expression.leftExpression.visit(targets)
                }
                super.visitBinaryExpression(expression)
            }

            @Override
            void visitPostfixExpression(PostfixExpression expression) {
                expression.expression.visit(targets)
                super.visitPostfixExpression(expression)
            }

            @Override
            void visitPrefixExpression(PrefixExpression expression) {
                expression.expression.visit(targets)
                super.visitPrefixExpression(expression)
            }
        }
        for (ConstructorNode constructor : declaring.declaredConstructors) {
            constructor.code?.visit(assignments)
        }
        for (Statement statement : declaring.objectInitializerStatements) {
            statement.visit(assignments)
        }
        found[0]
    }

    private Object read() {
        if (hierarchy.any { ClassNode type -> MOP_METHODS.any { String name -> !type.getDeclaredMethods(name).isEmpty() } }) {
            return null
        }
        State state = new State(initialValue(), false)
        for (ClassNode type : hierarchy.reverse()) {
            // the constructor of a class compiled elsewhere is not read: it may set the property, and hand the command out
            state = type.isPrimaryClassNode() ? made(type, state) : new State(UNKNOWN, true)
            if (state == null) {
                return null
            }
        }
        kind.isInstance(state.value) ? state.value : null
    }

    // what the property is when the command has been allocated and nothing has run: the value of a field of the
    // type boolean that the class declaring the property has for it, which is false
    private Object initialValue() {
        FieldNode field = trait == null ? declaring.getDeclaredField(property) : null
        kind == Boolean && field != null && !field.isStatic() && field.type == ClassHelper.boolean_TYPE ? Boolean.FALSE : UNKNOWN
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
            if (trait == null && field.name == property && type.name == declaring.name) {
                // the value the property is declared with goes into its field, whatever its setter is
                current.value = known(initial)
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
            // each way starts with the parameters that hold the command where the ways part
            Set<String> held = frame.aliases
            frame.aliases = new HashSet<String>(held)
            State first = run(choice.ifBlock, state.copy(), frame)
            Set<String> heldAfterFirst = frame.aliases
            frame.aliases = new HashSet<String>(held)
            State second = run(choice.elseBlock, state.copy(), frame)
            return either(first, heldAfterFirst, second, frame)
        }
        // a loop, a switch, a try, and the rest are not followed
        forget(statement, frame)
        State unknown = new State(UNKNOWN, true)
        if (returns(statement)) {
            frame.returned = State.join(frame.returned, unknown)
        }
        unknown
    }

    // Either of two ways the code can go, of which the second is the one the frame has the parameters of. A way that
    // does not complete leaves nothing. A parameter that holds the command after one way and not after the other may
    // hold it: it is not followed from here, and the command is with code that is not read.
    private static State either(State first, Set<String> heldAfterFirst, State second, Frame frame) {
        State joined = State.join(first, second)
        if (first == null) {
            return joined
        }
        if (second == null) {
            frame.aliases = heldAfterFirst
            return joined
        }
        if (heldAfterFirst != frame.aliases) {
            frame.aliases.retainAll(heldAfterFirst)
            joined.escaped = true
        }
        joined
    }

    // code that is not followed may give a parameter that holds the command another value: one it assigns is not
    // followed after it. Says whether there was one.
    private static boolean forget(ASTNode code, Frame frame) {
        if (frame.aliases.isEmpty()) {
            return false
        }
        Set<String> assigned = new HashSet<String>()
        CodeVisitorSupport targets = new CodeVisitorSupport() {

            @Override
            void visitVariableExpression(VariableExpression expression) {
                assigned << expression.name
            }
        }
        code.visit(new CodeVisitorSupport() {

            @Override
            void visitBinaryExpression(BinaryExpression expression) {
                if (Types.ofType(expression.operation.type, Types.ASSIGNMENT_OPERATOR)) {
                    expression.leftExpression.visit(targets)
                }
                super.visitBinaryExpression(expression)
            }

            @Override
            void visitPostfixExpression(PostfixExpression expression) {
                expression.expression.visit(targets)
                super.visitPostfixExpression(expression)
            }

            @Override
            void visitPrefixExpression(PrefixExpression expression) {
                expression.expression.visit(targets)
                super.visitPrefixExpression(expression)
            }
        })
        frame.aliases.removeAll(assigned)
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
            // a closure holds the command it is written in, and can set what it sees whenever it is called
            if (forget(expression, frame)) {
                state.escaped = true
            }
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
        forget(expression, frame)
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
            // the right side runs or does not
            State skipped = state.copy()
            Set<String> held = new HashSet<String>(frame.aliases)
            truth(expression.rightExpression, state, frame)
            state.take(either(skipped, held, state, frame))
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
        // several variables at once, or something that is not followed
        Set<String> named = new HashSet<String>()
        target.visit(new CodeVisitorSupport() {

            @Override
            void visitVariableExpression(VariableExpression expression) {
                named << expression.name
            }
        })
        frame.aliases.removeAll(named)
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

    // the property set to the value through its setter or its field: the constant the value is, if it is one of its kind
    private void set(State state, Expression value, boolean carries) {
        escapeIf(state, carries)
        if (setterWritten) {
            unknown(state)
            return
        }
        state.value = value == null ? UNKNOWN : known(value)
    }

    private boolean call(MethodCallExpression call, State state, Frame frame) {
        Expression receiver = call.objectExpression
        String name = call.methodAsString
        // a call written without a receiver has this as its receiver
        boolean onCommand = isCommand(receiver, frame)
        boolean onSuper = receiver instanceof VariableExpression && ((VariableExpression) receiver).isSuperExpression()
        List<Expression> arguments = arguments(call.arguments)
        if (name != null && !call.isSpreadSafe() && arguments != null) {
            if (onCommand || onSuper) {
                return callOnCommand(name, arguments, onSuper, false, state, frame)
            }
            if (receiver instanceof ClassExpression && isTraitInit(receiver.type, name)) {
                traitInit(receiver.type, name, state, frame)
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
            traitInit(owner, call.method, state, frame)
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

    // the traits of the command contract give their fields no value the property is read for; a trait compiled with
    // the command gives its fields values with code that is read as any other; a trait compiled elsewhere that has
    // nothing of the property leaves it as it is; any other trait may set the property, and keep the command. The
    // trait of a trait's property gives it its value, and undoes what was set before.
    private void traitInit(ClassNode helper, String method, State state, Frame frame) {
        String initialized = helper.name.substring(0, helper.name.length() - TRAIT_HELPER.length())
        if (trait != null && frame.owner.name == declaring.name && initialized == trait && method == TRAIT_INIT) {
            // a constant the code set before is undone: what it says is not what the command is made with
            state.value = kind.isInstance(state.value) ? UNKNOWN : traitValue
            return
        }
        if (CONTRACT_TRAITS.contains(initialized) || (method == TRAIT_INIT && (followed(helper, state) || hasNothingOf(helper, initialized, frame)))) {
            return
        }
        unknown(state)
    }

    // whether a trait compiled elsewhere, which is not read, has nothing of the property: neither the property nor a
    // getter or a setter of it, which are what the class it is compiled into gets of a property of a trait
    private boolean hasNothingOf(ClassNode helper, String traitName, Frame frame) {
        if (helper.isPrimaryClassNode()) {
            return false
        }
        ClassNode traitNode = frame.owner.allInterfaces.find { ClassNode type -> type.name == traitName }
        if (traitNode == null || !Traits.isTrait(traitNode)) {
            return false
        }
        String capitalized = property.capitalize()
        traitNode.getProperty(property) == null && traitNode.getDeclaredField(property) == null &&
                ['get', 'is', 'set'].every { String prefix -> traitNode.getDeclaredMethods(prefix + capitalized).isEmpty() }
    }

    // reads what a trait compiled with the command runs to give the fields of the instance their values, which it is
    // given the command to do; says whether it was read
    private boolean followed(ClassNode helper, State state) {
        if (!helper.isPrimaryClassNode()) {
            return false
        }
        List<MethodNode> inits = helper.getDeclaredMethods(TRAIT_INIT)
        if (inits.size() != 1 || inits[0].parameters.length != 1 || inits[0].code == null) {
            return false
        }
        MethodNode init = inits[0]
        if (isEmpty(init.code)) {
            return true
        }
        if (calling.contains(init) || calling.size() >= DEEPEST_CALL || calls + 1 > MOST_CALLS) {
            return false
        }
        calls++
        state.take(invoke(init, [(Expression) VariableExpression.THIS_EXPRESSION], [true], state.copy()) ?: new State(UNKNOWN, true))
        true
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
            Set<String> held = new HashSet<String>(frame.aliases)
            carries = eval(choice.falseExpression, state, frame) || carries
            state.take(either(skipped, held, state, frame))
            return carries
        }
        truth(choice.booleanExpression.expression, state, frame)
        Set<String> held = frame.aliases
        frame.aliases = new HashSet<String>(held)
        State first = state.copy()
        boolean carries = eval(choice.trueExpression, first, frame)
        Set<String> heldAfterFirst = frame.aliases
        frame.aliases = new HashSet<String>(held)
        State second = state.copy()
        carries = eval(choice.falseExpression, second, frame) || carries
        state.take(either(first, heldAfterFirst, second, frame))
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
        if (candidates.every { MethodNode method -> isContractAccessor(method) }) {
            // a getter or a setter a trait of the contract adds, which reads or writes its field and nothing else
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

    // this, a parameter that holds the command, or one of them cast to a type the command is, which leaves it as it is
    private boolean isCommand(Expression expression, Frame frame) {
        if (expression instanceof CastExpression && isOwnType(((CastExpression) expression).type)) {
            return isCommand(((CastExpression) expression).expression, frame)
        }
        isThis(expression) || isAlias(expression, frame)
    }

    private boolean isOwnType(ClassNode type) {
        hierarchy.any { ClassNode member -> member.name == type.name || member.allInterfaces.any { ClassNode face -> face.name == type.name } }
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

    // the constant the expression is, when it is one of the kind of the property; otherwise unknown
    private Object known(Expression expression) {
        expression instanceof ConstantExpression && kind.isInstance(((ConstantExpression) expression).value) ?
                ((ConstantExpression) expression).value : UNKNOWN
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

        // the parameters that hold the command at the point of the code that is read, which each way the code can go has its own of
        Set<String> aliases = new HashSet<String>()

        State returned

        Frame(ClassNode owner) {
            this.owner = owner
        }
    }
}
