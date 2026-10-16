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
package org.grails.datastore.gorm.query.transform;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassCodeVisitorSupport;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.DynamicVariable;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.InnerClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.Variable;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.CastExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.EmptyExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.GStringExpression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.ast.expr.SwitchExpression;
import org.codehaus.groovy.ast.expr.TernaryExpression;
import org.codehaus.groovy.ast.expr.TupleExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.BreakStatement;
import org.codehaus.groovy.ast.stmt.CaseStatement;
import org.codehaus.groovy.ast.stmt.CatchStatement;
import org.codehaus.groovy.ast.stmt.ContinueStatement;
import org.codehaus.groovy.ast.stmt.DoWhileStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ForStatement;
import org.codehaus.groovy.ast.stmt.IfStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.ast.stmt.SwitchStatement;
import org.codehaus.groovy.ast.stmt.SynchronizedStatement;
import org.codehaus.groovy.ast.stmt.ThrowStatement;
import org.codehaus.groovy.ast.stmt.TryCatchStatement;
import org.codehaus.groovy.ast.stmt.WhileStatement;
import org.codehaus.groovy.ast.stmt.YieldStatement;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.messages.WarningMessage;
import org.codehaus.groovy.syntax.Token;
import org.codehaus.groovy.syntax.Types;

import org.grails.datastore.mapping.reflect.AstUtils;

/**
 * {@link ClassCodeVisitorSupport} that detects GORM HQL/Cypher query text built from a
 * {@link GStringExpression} that Groovy coerced to a plain {@code String} <em>before</em> it
 * reaches a GORM query method, e.g.:
 *
 * <pre>{@code
 * String query = "from Book where name = ${userInput}"   // coerced to String right here
 * Book.executeQuery(query)                                // -> raw, unescaped text, no binding
 * }</pre>
 *
 * <p>When a {@link groovy.lang.GString} is passed directly to a GORM query method, GORM binds
 * each interpolated value as a query parameter — safe. Once the {@code GString} has been coerced
 * to a {@code String} (an explicit {@code String}-typed local, a {@code .toString()} call, or an
 * {@code as String}/cast coercion), that information is gone: a {@code String} carries no trace
 * of ever having been a {@code GString}, so this can only be caught here, before the coercion
 * erases it — a runtime check at the query boundary is structurally blind to this case.
 *
 * <p>A {@code GString} does not have to be flattened directly at the call site to be unsafe —
 * aliasing it through one or more intermediate variables still loses the binding the moment it is
 * assigned to a {@code String}-typed variable, however many hops away that happens:
 *
 * <pre>{@code
 * def g = "from Book where name = ${userInput}"   // g: still a live GString
 * String q = g                                     // flattened HERE, not at the executeQuery call
 * Book.executeQuery(q)
 * }</pre>
 *
 * <p><strong>Query text vs. values.</strong> Only an interpolation that can carry runtime data is
 * a finding. An interpolated expression that is <em>constant text</em> cannot be influenced by
 * user input, so a {@code GString} whose interpolations are all constant text is treated exactly
 * like a plain {@code String} literal. Constant text is: a literal; a {@code static final} field
 * initialised from constant text; a ternary or Elvis expression choosing between constant text; a
 * {@code switch} expression, or any closure called on the spot, whose every result is constant
 * text; a {@code +} concatenation, {@code GString}, cast or {@code .toString()} of constant text;
 * and a local variable that only ever held constant text on every path reaching the use. This is what
 * lets query text be assembled from fixed HQL fragments chosen at runtime, which a {@code GString}
 * passed directly to GORM could not express (GORM would bind the fragment as a parameter value):
 *
 * <pre>{@code
 * String restriction = ""
 * if (params.title) {
 *     restriction = " and b.title = :title"      // constant text, chosen at runtime
 *     queryParams.title = params.title           // the value is bound, never interpolated
 * }
 * String query = "from Book b where 1 = 1 ${restriction}"   // not a finding
 * Book.executeQuery(query, queryParams)
 * }</pre>
 *
 * <p>Compound assignment with {@code +=} is tracked exactly like {@code x = x + y}: a constant
 * local stays constant when the appended text is constant, a live {@code GString} operand is
 * flattened (Groovy's {@code GString.plus} returns {@code String}), and an already-flattened one
 * stays flattened. The same holds for any {@code +} concatenation: a flattened or live
 * {@code GString} operand anywhere in it makes the whole result flattened, not merely a
 * lower-confidence concatenation.
 *
 * <p><strong>Control flow.</strong> Local tracking is flow-sensitive. The branches of an
 * {@code if}/{@code else} and the cases of a {@code switch} (including fall-through) are each
 * walked from the state before the statement and merged pessimistically afterwards: a variable
 * unsafe at the end of any path stays unsafe, and is constant text only if it is constant at the
 * end of every path. A {@code catch} block starts from the merge of every state its {@code try}
 * block passed through, since an exception may leave the block after any statement, and a
 * {@code finally} block is checked against every path into it. A loop body or closure body may
 * run any number of times, so a body that assigns a local declared outside it is re-walked from
 * the merged loop-head state until that state is stable before findings are reported: an
 * assignment late in the body is seen by a use earlier in it, which the next iteration reaches.
 * The arms of a {@code switch} expression are walked like the cases of a {@code switch}
 * statement, each {@code yield} carrying its state past the expression.
 * {@code break}, {@code continue} and, in a closure, {@code return} carry their state to the
 * exit or head they jump to, and a path ending in such a jump or in {@code throw} contributes
 * nothing to the state after the statement. A jump or an exception that leaves a {@code try}
 * statement runs its {@code finally} block first, so it carries the state that block ends with
 * when walked from every path into it. Locals are tracked per declaration, so a later local,
 * parameter or loop variable that reuses a name never inherits the state of an earlier one.
 *
 * <p>Some assignments can run at a point the walk cannot place: inside a closure or the code of
 * an anonymous inner class, which may run at any later point; inside another expression, such
 * as a ternary or Elvis branch, an {@code &&} or {@code ||} operand, a method argument or a loop
 * condition; or through multiple assignment. Where the walk cannot know what a local holds - a
 * local assigned in any of these ways, anywhere; inside a closure, a local declared outside it;
 * and inside an anonymous inner class, a local of the method enclosing it - the local holds the
 * worst that any assignment to it in the method stores. It is constant text only when every
 * assignment is, and flattened when any assignment flattens a {@code GString} into it.
 *
 * <p>This is a build-breaking error for the local-variable case above, because the detection is
 * precise: every flattening point is visible in the method being compiled. Two related patterns
 * are lower-confidence and instead reported as compile-time <em>warnings</em>, which do not fail
 * the build:
 *
 * <ul>
 *     <li><strong>Fields.</strong> A {@code String}-typed field initialized from an interpolated
 *     {@code GString}, or assigned one via {@code this.field = ...}, that later reaches a query
 *     method through {@code this.field}. Unlike locals, a field can be reassigned from a
 *     constructor, another method, or a subclass that this check never visits, so it is flagged
 *     rather than failed.</li>
 *     <li><strong>String concatenation.</strong> Query text built with {@code +} from a
 *     non-constant value and no {@code GString} involved at all, e.g.
 *     {@code "select ... " + userInput}. This is a real injection shape, but concatenation is
 *     common enough for benign, non-query purposes that a hard failure would be too blunt an
 *     instrument. Concatenating only constant text, as defined above, is not a finding.</li>
 * </ul>
 *
 * <p>A query method is recognised when it is called on a domain class, or on {@code this}, written
 * or implied, in a domain class or in a class nested in one that has no method of that name
 * itself, since Groovy sends such a call on to the enclosing class.
 *
 * <p>Both warnings share the same {@link #SUPPRESS_WARNINGS_VALUE} suppression as the error case.
 * The suppression applies to the enclosing method or class (for a nested class, also to any class
 * or method it is declared in, as with a closure), or - more narrowly - to a single
 * local variable or field declaration: {@code @SuppressWarnings("GormUnsafeQueryString") String
 * query = ...} marks that variable as reviewed for the rest of the method, whatever it is later
 * assigned, and leaves every other variable checked. A reviewed variable is also treated as
 * constant text wherever it is interpolated or concatenated, so the annotation may go either on
 * the assembled query or on the fragment it is built from.
 *
 * <p><strong>Known limitations (deliberate scope):</strong>
 * <ul>
 *     <li>Intraprocedural only — a flattened {@code String} built inside a helper method and
 *     returned to the caller is invisible to this check, and so is constant text returned from
 *     one (it is treated as data).</li>
 *     <li>Constant-text tracking follows locals, {@code static final} fields, ternaries and
 *     {@code +}/{@code +=}/{@code GString} composition only - not collections (for example
 *     fragments gathered in a {@code List} and joined), method calls, or non-final fields.</li>
 *     <li>Flow-sensitivity applies to locals only; field tracking is last-write-wins in source
 *     order. A local judged by every assignment to it (see above) is judged by the assignments
 *     that can no longer reach the use too, so a local flattened and then reset to constant text
 *     is still reported where a closure created after the reset reads it. Reachability is
 *     approximated: a path is treated as not continuing only when its block ends in
 *     {@code return}, {@code throw}, {@code break} or {@code continue}.</li>
 *     <li>Field tracking only recognizes a directly-interpolated {@code GString} initializer or
 *     {@code this.field = ...} assignment - it does not follow aliasing chains or
 *     {@code .toString()}/cast coercions the way local tracking does.</li>
 *     <li>Does not detect raw JDBC via {@code groovy.sql.Sql}, or any datastore whose query
 *     methods use names outside {@link #CANDIDATE_METHODS}.</li>
 * </ul>
 *
 * @since 8.0
 */
public class GormQuerySafetyTransformer extends ClassCodeVisitorSupport {

    /**
     * What a tracked local variable currently holds, from this check's point of view, from the
     * safest to the least safe: where two paths meet, the later of the two survives the merge.
     */
    private enum Origin {
        /** Constant text (see the class Javadoc) - can never carry runtime data, so safe anywhere. */
        CONSTANT,
        /** Not derived from an interpolated GString or unsafe concatenation - nothing to track. */
        NONE,
        /** Still a real {@link groovy.lang.GString} - safe if passed directly to a query method. */
        LIVE_GSTRING,
        /** Built via {@code +} concatenation of a non-constant value, no GString involved. */
        CONCATENATED,
        /** Already coerced to a plain {@code String} - unsafe if it reaches a query method. */
        FLATTENED;

        Origin or(Origin other) {
            return other != null && other.compareTo(this) > 0 ? other : this;
        }
    }

    /**
     * What kind of unsafe query argument was found at a candidate call site, and therefore how
     * severely (and with what message) to report it, from the least to the most severe.
     */
    private enum Finding {
        NONE,
        UNSAFE_CONCATENATION,
        FLATTENED_FIELD,
        FLATTENED_GSTRING;

        Finding or(Finding other) {
            return other.compareTo(this) > 0 ? other : this;
        }
    }

    /**
     * The {@code @SuppressWarnings} value that silences this check (both the error and the two
     * warnings below) on the enclosing method (or, for calls outside any method, the enclosing
     * class), or on a single local variable or field declaration.
     */
    public static final String SUPPRESS_WARNINGS_VALUE = "GormUnsafeQueryString";

    /**
     * Shared tail of every message: how to silence the check for a reviewed call site.
     */
    private static final String SUPPRESSION_HINT = "To suppress this check for a reviewed, safe call site, add " +
            "@SuppressWarnings(\"" + SUPPRESS_WARNINGS_VALUE + "\") to the declaration of the variable " +
            "that holds the query text, or to the enclosing method.";

    private static final Set<String> CANDIDATE_METHODS = new HashSet<>(Arrays.asList(
            "find", "findAll", "executeQuery", "executeUpdate",
            "findAllWithSql", "cypherStatic", "findPath", "findPathTo"));

    /**
     * The positional index of the query argument for each candidate method. Every candidate
     * method takes the query as its first argument except Neo4j's
     * {@code findPathTo(Class type, CharSequence query, Map params)}.
     */
    private static final Map<String, Integer> QUERY_ARGUMENT_INDEX = buildQueryArgumentIndex();

    private static Map<String, Integer> buildQueryArgumentIndex() {
        Map<String, Integer> indexes = new HashMap<>();
        for (String method : CANDIDATE_METHODS) {
            indexes.put(method, 0);
        }
        indexes.put("findPathTo", 1);
        return Collections.unmodifiableMap(indexes);
    }

    private final SourceUnit sourceUnit;
    /**
     * Local tracking state, keyed by {@link #keyOf}: the declaration a local resolves to, so a
     * later local, parameter or loop variable reusing a name is never confused with an earlier one.
     */
    private final Map<Object, ASTNode> flattenedStringVars = new HashMap<>();
    private final Map<Object, ASTNode> liveGStringVars = new HashMap<>();
    private final Map<Object, ASTNode> concatenatedStringVars = new HashMap<>();
    private final Set<Object> constantTextVars = new HashSet<>();
    private final Map<String, ASTNode> flattenedFields = new HashMap<>();
    private final Set<String> resolvingFields = new HashSet<>();
    /** What the pre-scan of the code being walked found; see {@link CodeFacts}. */
    private CodeFacts facts = new CodeFacts();
    /**
     * While {@link #settleConstants} or {@link #settleOrigins} runs, the locals assumed to hold
     * only constant text; {@code null} otherwise.
     */
    private Set<Object> assumedConstant;
    /**
     * While {@link #settleOrigins} runs, the worst each local is assumed to hold so far;
     * {@code null} otherwise.
     */
    private Map<Object, Origin> assumedOrigins;
    /**
     * For an anonymous inner class being visited, what the pre-scan finds in each method enclosing
     * it, innermost first (see {@link #enclosingFactsOf}); empty otherwise.
     */
    private List<CodeFacts> enclosingFacts = Collections.emptyList();
    /** The closures being walked, innermost first. */
    private final Deque<ClosureExpression> closures = new ArrayDeque<>();
    /** The {@code switch} expressions being walked, innermost first. */
    private final Deque<SwitchExpression> switchExpressions = new ArrayDeque<>();
    /**
     * The loops, {@code switch} statements and expressions and closure bodies being walked,
     * innermost first, that a {@code break}, {@code continue}, {@code yield} or {@code return}
     * can carry its state to.
     */
    private final Deque<JumpTarget> jumpTargets = new ArrayDeque<>();
    /**
     * One entry per {@code try} or {@code catch} block being walked: the merge of every state the
     * block has passed through so far, which is what an exception leaving it after any statement
     * can carry to a {@code catch} or {@code finally} block.
     */
    private final List<TrackingSnapshot> exceptionalStates = new ArrayList<>();
    /**
     * The {@code try} statements with a {@code finally} block whose {@code try} or {@code catch}
     * blocks are being walked, innermost first, holding the jumps out of them until the
     * {@code finally} block has run.
     */
    private final Deque<FinallyFrame> finallyFrames = new ArrayDeque<>();
    /** Above zero while a body is re-walked to settle its state; findings are reported only at zero. */
    private int silentPasses;
    private ClassNode currentClassNode;
    private MethodNode currentMethodNode;

    public GormQuerySafetyTransformer(SourceUnit sourceUnit) {
        this.sourceUnit = sourceUnit;
    }

    @Override
    protected SourceUnit getSourceUnit() {
        return this.sourceUnit;
    }

    @Override
    public void visitClass(ClassNode node) {
        try {
            this.currentClassNode = node;
            this.enclosingFacts = enclosingFactsOf(node);
            // Pre-scan field initializers so a field flattened here is already tracked no matter
            // which order the base class visits fields vs. methods in.
            for (FieldNode field : node.getFields()) {
                trackFieldInitializer(field);
            }
            super.visitClass(node);
        } finally {
            this.currentClassNode = null;
            this.enclosingFacts = Collections.emptyList();
            clearTracking();
            flattenedFields.clear();
        }
    }

    /**
     * For an anonymous inner class, what the pre-scan finds in each method enclosing it, innermost
     * first. The class's code may run at any point after it is created, so a local of an enclosing
     * method that it uses is judged by every assignment to that local there.
     */
    private List<CodeFacts> enclosingFactsOf(ClassNode node) {
        Deque<MethodNode> methods = new ArrayDeque<>();
        for (ClassNode type = node; isAnonymous(type) && type.getEnclosingMethod() != null;
                type = type.getEnclosingMethod().getDeclaringClass()) {
            methods.push(type.getEnclosingMethod());
        }
        List<CodeFacts> enclosing = new ArrayList<>();
        // Outermost first, since a method's facts depend on those of the methods enclosing it.
        for (MethodNode method : methods) {
            enclosingFacts = new ArrayList<>(enclosing);
            enclosing.add(0, scan(method.getParameters(), method.getCode()));
        }
        return enclosing;
    }

    private static boolean isAnonymous(ClassNode type) {
        return type instanceof InnerClassNode && ((InnerClassNode) type).isAnonymous();
    }

    @Override
    protected void visitConstructorOrMethod(MethodNode node, boolean isConstructor) {
        this.currentMethodNode = node;
        analyse(node.getParameters(), node.getCode());
        try {
            super.visitConstructorOrMethod(node, isConstructor);
        } finally {
            this.currentMethodNode = null;
            clearTracking();
        }
    }

    @Override
    public void visitField(FieldNode node) {
        analyse(Parameter.EMPTY_ARRAY, node.getInitialExpression());
        try {
            super.visitField(node);
        } finally {
            clearTracking();
        }
    }

    @Override
    public void visitProperty(PropertyNode node) {
        analyse(Parameter.EMPTY_ARRAY, node.getInitialExpression(), node.getGetterBlock(), node.getSetterBlock());
        try {
            super.visitProperty(node);
        } finally {
            clearTracking();
        }
    }

    @Override
    protected void visitObjectInitializerStatements(ClassNode node) {
        analyse(Parameter.EMPTY_ARRAY, node.getObjectInitializerStatements().toArray(new ASTNode[0]));
        try {
            super.visitObjectInitializerStatements(node);
        } finally {
            clearTracking();
        }
    }

    private void clearTracking() {
        flattenedStringVars.clear();
        liveGStringVars.clear();
        concatenatedStringVars.clear();
        constantTextVars.clear();
        closures.clear();
        switchExpressions.clear();
        facts = new CodeFacts();
    }

    /** Pre-scans the code about to be walked, taking {@code parameters}, with {@link #scan}. */
    private void analyse(Parameter[] parameters, ASTNode... roots) {
        facts = scan(parameters, roots);
    }

    /**
     * Pre-scans code taking {@code parameters} (see {@link CodeFacts}) and settles which locals
     * hold only constant text, and the worst each local holds, however its assignments are ordered.
     */
    private CodeFacts scan(Parameter[] parameters, ASTNode... roots) {
        CodeFacts scanned = new CodeFacts();
        scanned.owned.addAll(Arrays.asList(parameters));
        for (ASTNode root : roots) {
            if (root != null) {
                root.visit(scanned);
            }
        }
        CodeFacts previous = facts;
        facts = scanned;
        try {
            settleConstants();
            settleOrigins();
        } finally {
            facts = previous;
        }
        return scanned;
    }

    /**
     * Computes {@link CodeFacts#constant}: starting from every local declared in the code, drops
     * each one with an assignment that is not constant text, assuming the locals still in the set
     * hold constant text, until nothing more is dropped.
     */
    private void settleConstants() {
        Set<Object> constant = new HashSet<>(facts.declared);
        assumedConstant = constant;
        try {
            boolean changed = true;
            while (changed) {
                changed = false;
                for (Write write : facts.writes) {
                    if (constant.contains(write.key) && !facts.suppressed.contains(write.key) &&
                            (write.value == null || !isConstantText(write.value))) {
                        constant.remove(write.key);
                        changed = true;
                    }
                }
            }
        } finally {
            assumedConstant = null;
        }
        facts.constant.addAll(constant);
    }

    /**
     * Computes {@link CodeFacts#origins}: the worst that any assignment in the code stores in each
     * local it assigns, judging every local it reads the same way, until nothing gets worse. This
     * is what a local holds at a point the walk cannot follow it to (see
     * {@link #isJudgedByEveryAssignment}).
     */
    private void settleOrigins() {
        Map<Object, Origin> origins = new HashMap<>();
        for (Object key : facts.declared) {
            origins.put(key, facts.constant.contains(key) ? Origin.CONSTANT : Origin.NONE);
        }
        assumedConstant = facts.constant;
        assumedOrigins = origins;
        try {
            boolean changed = true;
            while (changed) {
                changed = false;
                for (Write write : facts.writes) {
                    if (facts.suppressed.contains(write.key)) {
                        continue;
                    }
                    Origin current = origins.getOrDefault(write.key, Origin.NONE);
                    Origin worst = current.or(write.value == null ? Origin.NONE : classify(write.value, write.type));
                    if (worst != current || !origins.containsKey(write.key)) {
                        origins.put(write.key, worst);
                        changed |= worst != current;
                    }
                }
            }
        } finally {
            assumedConstant = null;
            assumedOrigins = null;
        }
        facts.origins.putAll(origins);
    }

    /**
     * The key a local's tracking state is held under: the declaration the compiler resolved the
     * reference to (the declaring {@link VariableExpression}, a {@link Parameter}, or a field), or
     * the name for a dynamic variable or a reference the compiler did not resolve.
     */
    private static Object keyOf(VariableExpression variable) {
        Variable accessed = variable.getAccessedVariable();
        if (accessed == null || accessed instanceof DynamicVariable) {
            return variable.getName();
        }
        return accessed;
    }

    @Override
    public void visitDeclarationExpression(DeclarationExpression expression) {
        // A declaration that carries the suppression has been reviewed: the variable stays
        // unchecked for the rest of the method, whatever it is later assigned (see track).
        if (expression.isMultipleAssignmentDeclaration()) {
            forgetTargets(expression.getTupleExpression());
        }
        else {
            VariableExpression variableExpression = expression.getVariableExpression();
            track(keyOf(variableExpression), expression.getRightExpression(), variableExpression.getType(), expression);
        }
        super.visitDeclarationExpression(expression);
    }

    @Override
    public void visitBinaryExpression(BinaryExpression expression) {
        int operation = expression.getOperation().getType();
        Expression left = expression.getLeftExpression();
        if (operation == Types.ASSIGN) {
            if (left instanceof VariableExpression) {
                VariableExpression leftVariable = (VariableExpression) left;
                track(keyOf(leftVariable), expression.getRightExpression(), leftVariable.getType(), expression);
            }
            else if (isThisFieldReference(left)) {
                trackField(fieldNameOf(left), expression.getRightExpression(), expression);
            }
            else if (left instanceof TupleExpression) {
                forgetTargets((TupleExpression) left);
            }
        }
        else if (operation == Types.PLUS_EQUAL && left instanceof VariableExpression) {
            // x += y is x = x + y: classify the equivalent concatenation so constant text stays
            // constant, a live GString operand flattens, and a flattened one stays flattened.
            VariableExpression leftVariable = (VariableExpression) left;
            track(keyOf(leftVariable), plusEqualAsConcatenation(expression), leftVariable.getType(), expression);
        }
        else if (Types.ofType(operation, Types.ASSIGNMENT_OPERATOR) && left instanceof VariableExpression) {
            // Any other compound assignment is not string building this check understands, so
            // the variable can no longer be relied on as constant text.
            constantTextVars.remove(keyOf((VariableExpression) left));
        }
        super.visitBinaryExpression(expression);
    }

    /**
     * Forgets what the targets of a multiple assignment held. The pre-scan pairs each target with
     * the value it is assigned (see {@link #elementAssigned}) and counts the assignment as one the
     * walk cannot place, so a target is judged by every assignment to it from here on.
     */
    private void forgetTargets(TupleExpression targets) {
        for (Expression target : targets.getExpressions()) {
            if (target instanceof VariableExpression) {
                untrack(keyOf((VariableExpression) target));
            }
        }
    }

    /**
     * The value a multiple assignment from {@code value} gives the variable at {@code index}: the
     * element of a list literal in that position, or {@code null} past its end. From anything other
     * than a list literal the value is unknown, so {@code null} is returned instead, for data.
     */
    private static Expression elementAssigned(Expression value, int index) {
        if (!(value instanceof ListExpression)) {
            return null;
        }
        List<Expression> elements = ((ListExpression) value).getExpressions();
        return index < elements.size() ? elements.get(index) : ConstantExpression.NULL;
    }

    private static BinaryExpression plusEqualAsConcatenation(BinaryExpression expression) {
        Token plus = Token.newSymbol(Types.PLUS, expression.getLineNumber(), expression.getColumnNumber());
        return new BinaryExpression(expression.getLeftExpression(), plus, expression.getRightExpression());
    }

    /**
     * Visits an {@code if}/{@code else} flow-sensitively: each branch is walked from the state
     * before the statement, and the states at the end of the branches that can complete normally
     * are merged pessimistically afterwards (see {@link #merge}). Without this, whichever branch
     * happens to be visited last would silently win, e.g. a variable flattened only in the
     * {@code if} branch would be forgotten if the {@code else} branch reassigns it safely.
     */
    @Override
    public void visitIfElse(IfStatement ifElse) {
        visitStatement(ifElse);
        ifElse.getBooleanExpression().visit(this);
        TrackingSnapshot before = snapshot();
        TrackingSnapshot afterIf = walkFrom(before, ifElse.getIfBlock());
        TrackingSnapshot afterElse = walkFrom(before, ifElse.getElseBlock());
        restore(merge(normalExit(ifElse.getIfBlock(), afterIf), normalExit(ifElse.getElseBlock(), afterElse)),
                merge(afterIf, afterElse));
    }

    /**
     * Visits a {@code switch}: every case is walked from the state before the statement merged
     * with the state the preceding case falls through with, and the state afterwards is merged
     * from every path that leaves the statement - the last case or the default completing
     * normally (which, with no default, includes no case matching) and every {@code break}.
     */
    @Override
    public void visitSwitch(SwitchStatement statement) {
        visitStatement(statement);
        statement.getExpression().visit(this);
        TrackingSnapshot before = snapshot();
        JumpTarget target = new JumpTarget(statement, JumpTarget.Kind.SWITCH);
        jumpTargets.push(target);
        try {
            TrackingSnapshot fallThrough = null;
            for (CaseStatement caseStatement : statement.getCaseStatements()) {
                restore(merge(before, fallThrough));
                visitStatement(caseStatement);
                caseStatement.getExpression().visit(this);
                caseStatement.getCode().visit(this);
                fallThrough = normalExit(caseStatement.getCode(), snapshot());
            }
            Statement defaultStatement = statement.getDefaultStatement();
            TrackingSnapshot end = walkFrom(merge(before, fallThrough), defaultStatement);
            restore(merge(normalExit(defaultStatement, end), target.exit), end);
        } finally {
            jumpTargets.pop();
        }
    }

    /**
     * Visits a {@code switch} expression like a {@code switch} statement (see {@link #visitSwitch}).
     * Every arm ends with a {@code yield}, which leaves the expression the way a {@code break}
     * leaves the statement, so the state afterwards is merged from every {@code yield}.
     */
    @Override
    public void visitSwitchExpression(SwitchExpression expression) {
        expression.getExpression().visit(this);
        TrackingSnapshot before = snapshot();
        JumpTarget target = new JumpTarget(null, JumpTarget.Kind.SWITCH_EXPRESSION);
        jumpTargets.push(target);
        switchExpressions.push(expression);
        try {
            TrackingSnapshot fallThrough = null;
            for (CaseStatement caseStatement : expression.getCaseStatements()) {
                restore(merge(before, fallThrough));
                visitStatement(caseStatement);
                caseStatement.getExpression().visit(this);
                caseStatement.getCode().visit(this);
                fallThrough = normalExit(caseStatement.getCode(), snapshot());
            }
            Statement defaultStatement = expression.getDefaultStatement();
            TrackingSnapshot end = walkFrom(merge(before, fallThrough), defaultStatement);
            restore(merge(normalExit(defaultStatement, end), target.exit), end);
        } finally {
            switchExpressions.pop();
            jumpTargets.pop();
        }
    }

    /**
     * Visits a {@code try} statement. An exception can leave the {@code try} block after any
     * statement, so each {@code catch} block starts from the merge of every state the block
     * passed through (accumulated by {@link #visitStatement}). The {@code finally} block runs
     * after any of those states, any state a {@code catch} block passed through, or any state a
     * jump out of the statement leaves with, and is checked against all of them. Only a
     * {@code try} or {@code catch} block that completes normally continues past the statement,
     * so the state afterwards is derived from those paths alone. Every other way out - a jump,
     * which lands only once the {@code finally} block has run, or an exception - leaves with the
     * state the {@code finally} block ends with when walked from every path into it.
     */
    @Override
    public void visitTryCatchFinally(TryCatchStatement statement) {
        visitStatement(statement);
        Statement finallyStatement = statement.getFinallyStatement();
        FinallyFrame frame = finallyStatement.isEmpty() ? null : new FinallyFrame(jumpTargets);
        if (frame != null) {
            finallyFrames.push(frame);
        }
        TrackingSnapshot normalExits;
        TrackingSnapshot intoFinally;
        try {
            TrackingSnapshot inTry = accumulateStates(() -> {
                for (Statement resource : statement.getResourceStatements()) {
                    resource.visit(this);
                }
                statement.getTryStatement().visit(this);
            });
            normalExits = normalExit(statement.getTryStatement(), snapshot());
            intoFinally = inTry;
            for (CatchStatement catchStatement : statement.getCatchStatements()) {
                restore(inTry);
                intoFinally = merge(intoFinally, accumulateStates(() -> catchStatement.visit(this)));
                normalExits = merge(normalExits, normalExit(catchStatement.getCode(), snapshot()));
            }
        } finally {
            if (frame != null) {
                finallyFrames.pop();
            }
        }
        if (frame == null) {
            restore(normalExits, intoFinally);
            return;
        }
        intoFinally = merge(intoFinally, frame.jumpStates);
        TrackingSnapshot afterFinally = null;
        if (normalExits != null) {
            silentPasses++;
            try {
                afterFinally = walkFrom(normalExits, finallyStatement);
            } finally {
                silentPasses--;
            }
        }
        // In a silent pass the findings of this walk are dropped, so it is needed only for the
        // state it ends with, when a jump or an enclosing handler takes it. Skipping it otherwise
        // keeps nested finally blocks from being walked an exponential number of times.
        if (silentPasses == 0 || afterFinally == null || !frame.jumps.isEmpty() || !exceptionalStates.isEmpty()) {
            TrackingSnapshot abruptExit = walkFrom(intoFinally, finallyStatement);
            addExceptionalState(abruptExit);
            for (Jump jump : frame.jumps) {
                jump(jump.target, jump.toHead, abruptExit);
            }
        }
        restore(afterFinally, snapshot());
    }

    /**
     * Runs {@code walk} with a fresh entry on {@link #exceptionalStates} and returns the merge of
     * every state the walked code passed through, from the state before it to the one it leaves.
     */
    private TrackingSnapshot accumulateStates(Runnable walk) {
        int index = exceptionalStates.size();
        exceptionalStates.add(snapshot());
        TrackingSnapshot accumulated;
        try {
            walk.run();
        } finally {
            accumulated = exceptionalStates.remove(index);
        }
        return merge(accumulated, snapshot());
    }

    /**
     * Records the state before every statement walked inside a {@code try} or {@code catch}
     * block, for the handlers an exception thrown by that statement would reach.
     */
    @Override
    protected void visitStatement(Statement statement) {
        if (!exceptionalStates.isEmpty()) {
            addExceptionalState(snapshot());
        }
    }

    /**
     * Adds {@code state} to every entry of {@link #exceptionalStates}: an exception may leave the
     * code being walked with it.
     */
    private void addExceptionalState(TrackingSnapshot state) {
        for (int i = 0; i < exceptionalStates.size(); i++) {
            exceptionalStates.set(i, merge(exceptionalStates.get(i), state));
        }
    }

    @Override
    public void visitForLoop(ForStatement statement) {
        visitStatement(statement);
        statement.getCollectionExpression().visit(this);
        visitRepeatedly(new JumpTarget(statement, JumpTarget.Kind.LOOP), statement.getLoopBlock(), false);
    }

    @Override
    public void visitWhileLoop(WhileStatement statement) {
        visitStatement(statement);
        statement.getBooleanExpression().visit(this);
        visitRepeatedly(new JumpTarget(statement, JumpTarget.Kind.LOOP), statement.getLoopBlock(), false);
    }

    @Override
    public void visitDoWhileLoop(DoWhileStatement statement) {
        visitStatement(statement);
        visitRepeatedly(new JumpTarget(statement, JumpTarget.Kind.LOOP), statement.getLoopBlock(), true);
        statement.getBooleanExpression().visit(this);
    }

    /**
     * A closure body may run any number of times after the closure is defined, so it is walked
     * like a loop body, with the state at the point of definition. Because it may also run at any
     * later point, after the locals it shares with the enclosing code have been reassigned, those
     * locals are judged by every assignment to them inside it (see
     * {@link #isJudgedByEveryAssignment}).
     */
    @Override
    public void visitClosureExpression(ClosureExpression expression) {
        if (expression.isParameterSpecified()) {
            for (Parameter parameter : expression.getParameters()) {
                visitAnnotations(parameter);
                if (parameter.hasInitialExpression()) {
                    parameter.getInitialExpression().visit(this);
                }
            }
        }
        closures.push(expression);
        try {
            visitRepeatedly(new JumpTarget(null, JumpTarget.Kind.CLOSURE), expression.getCode(), false);
        } finally {
            closures.pop();
        }
    }

    /**
     * Walks a loop or closure {@code body}, which may run any number of times. When the body
     * assigns a local declared outside it, it is first re-walked silently from the merge of the
     * states that reach its head - the state before it, the state at its end and at every
     * {@code continue} (or, for a closure, every {@code return}) - until that merged state stops
     * changing, so a variable assigned data late in the body is unsafe at
     * every use the next iteration reaches, including the uses before the assignment. This
     * terminates because {@link #merge} only ever moves a variable towards a less safe category.
     * A body that assigns no such local cannot change the state at its head, so one walk is
     * enough. Only a walk outside any silent pass reports findings, so a nested loop already
     * walked silently from its settled head is not walked again. The state afterwards is the
     * loop-head state merged with every {@code break}, or for a body that runs at least once the
     * states leaving its last iteration.
     */
    private void visitRepeatedly(JumpTarget target, Statement body, boolean runsAtLeastOnce) {
        TrackingSnapshot head = snapshot();
        if (facts.bodiesAssigningOuterLocals.contains(body)) {
            silentPasses++;
            try {
                TrackingSnapshot next = iterate(target, head, body);
                while (!next.sameStateAs(head)) {
                    head = next;
                    next = iterate(target, head, body);
                }
            } finally {
                silentPasses--;
            }
            // The last silent walk started from the settled head; repeat it only to report.
            if (silentPasses == 0) {
                iterate(target, head, body);
            }
        }
        else {
            iterate(target, head, body);
        }
        TrackingSnapshot exit = runsAtLeastOnce ?
                merge(merge(normalExit(body, target.end), target.head), target.exit) :
                merge(head, target.exit);
        restore(exit, target.end);
    }

    /**
     * Walks {@code body} once from {@code head}, with {@code target} collecting the jumps inside
     * it, and returns the state at the head of the next run.
     */
    private TrackingSnapshot iterate(JumpTarget target, TrackingSnapshot head, Statement body) {
        target.reset();
        jumpTargets.push(target);
        try {
            target.end = walkFrom(head, body);
        } finally {
            jumpTargets.pop();
        }
        TrackingSnapshot next = merge(head, target.head);
        // A closure's return is collected as a jump to its head; any other way out of its body
        // (or a loop body that falls off its end) reaches the head too.
        return target.kind == JumpTarget.Kind.CLOSURE ?
                merge(next, target.end) : merge(next, normalExit(body, target.end));
    }

    @Override
    public void visitBreakStatement(BreakStatement statement) {
        super.visitBreakStatement(statement);
        JumpTarget target = jumpTarget(statement.getLabel(), false);
        if (target != null) {
            jump(target, false, snapshot());
        }
    }

    @Override
    public void visitContinueStatement(ContinueStatement statement) {
        super.visitContinueStatement(statement);
        JumpTarget target = jumpTarget(statement.getLabel(), true);
        if (target != null) {
            jump(target, true, snapshot());
        }
    }

    @Override
    public void visitYieldStatement(YieldStatement statement) {
        super.visitYieldStatement(statement);
        // A yield leaves the innermost switch expression; it cannot cross a closure boundary.
        for (JumpTarget target : jumpTargets) {
            if (target.kind == JumpTarget.Kind.CLOSURE) {
                return;
            }
            if (target.kind == JumpTarget.Kind.SWITCH_EXPRESSION) {
                jump(target, false, snapshot());
                return;
            }
        }
    }

    @Override
    public void visitReturnStatement(ReturnStatement statement) {
        super.visitReturnStatement(statement);
        // Returning from a closure ends one run of its body; the next run starts from its head.
        JumpTarget closure = null;
        for (JumpTarget target : jumpTargets) {
            if (target.kind == JumpTarget.Kind.CLOSURE) {
                closure = target;
                break;
            }
        }
        if (closure != null) {
            jump(closure, true, snapshot());
        }
        else if (!finallyFrames.isEmpty()) {
            // A return from the method lands nowhere this check follows, but the finally blocks
            // it leaves run first, from the state it returns with.
            FinallyFrame frame = finallyFrames.peek();
            frame.jumpStates = merge(frame.jumpStates, snapshot());
        }
    }

    /**
     * Carries {@code state} to the head or the exit of {@code target}. A jump out of a
     * {@code try} statement with a {@code finally} block lands only once that block has run, so
     * it is held by the statement's {@link FinallyFrame} instead, and carried on from the state
     * the {@code finally} block ends with (see {@link #visitTryCatchFinally}).
     */
    private void jump(JumpTarget target, boolean toHead, TrackingSnapshot state) {
        FinallyFrame frame = finallyFrames.peek();
        if (frame != null && frame.outerTargets.contains(target)) {
            frame.jumps.add(new Jump(target, toHead));
            frame.jumpStates = merge(frame.jumpStates, state);
        }
        else if (toHead) {
            target.head = merge(target.head, state);
        }
        else {
            target.exit = merge(target.exit, state);
        }
    }

    /**
     * The innermost enclosing loop (or, for a {@code break}, {@code switch}) that a jump leaves,
     * by label when it has one. A jump cannot cross a closure or {@code switch} expression boundary.
     */
    private JumpTarget jumpTarget(String label, boolean loopsOnly) {
        for (JumpTarget target : jumpTargets) {
            if (target.kind == JumpTarget.Kind.CLOSURE || target.kind == JumpTarget.Kind.SWITCH_EXPRESSION) {
                return null;
            }
            boolean kindMatches = target.kind == JumpTarget.Kind.LOOP || !loopsOnly;
            if (kindMatches && (label == null || target.labels.contains(label))) {
                return target;
            }
        }
        return null;
    }

    private TrackingSnapshot walkFrom(TrackingSnapshot start, Statement statement) {
        restore(start);
        statement.visit(this);
        return snapshot();
    }

    /**
     * {@code end} when {@code statement} can complete normally, else {@code null}: the state at
     * the end of a block whose last statement is a jump went where the jump went instead.
     */
    private static TrackingSnapshot normalExit(Statement statement, TrackingSnapshot end) {
        return completesAbruptly(statement) ? null : end;
    }

    private static boolean completesAbruptly(Statement statement) {
        if (statement instanceof BreakStatement || statement instanceof ContinueStatement ||
                statement instanceof ReturnStatement || statement instanceof ThrowStatement ||
                statement instanceof YieldStatement) {
            return true;
        }
        if (statement instanceof BlockStatement) {
            List<Statement> statements = ((BlockStatement) statement).getStatements();
            return !statements.isEmpty() && completesAbruptly(statements.get(statements.size() - 1));
        }
        if (statement instanceof IfStatement) {
            IfStatement ifElse = (IfStatement) statement;
            return completesAbruptly(ifElse.getIfBlock()) && completesAbruptly(ifElse.getElseBlock());
        }
        return false;
    }

    private TrackingSnapshot snapshot() {
        return new TrackingSnapshot(flattenedStringVars, liveGStringVars, concatenatedStringVars, constantTextVars);
    }

    private void restore(TrackingSnapshot state) {
        flattenedStringVars.clear();
        flattenedStringVars.putAll(state.flattened);
        liveGStringVars.clear();
        liveGStringVars.putAll(state.live);
        concatenatedStringVars.clear();
        concatenatedStringVars.putAll(state.concatenated);
        constantTextVars.clear();
        constantTextVars.addAll(state.constant);
    }

    /**
     * Restores {@code state}, or {@code ifUnreachable} when no path reaches this point (every
     * path jumped away), in which case what follows is dead code and the state is immaterial.
     */
    private void restore(TrackingSnapshot state, TrackingSnapshot ifUnreachable) {
        restore(state != null ? state : ifUnreachable);
    }

    /**
     * Merges the states at the end of two paths pessimistically, since we don't know at compile
     * time which will actually run: a variable unsafe at the end of either stays unsafe, and one
     * is constant text only if it is constant at the end of both. {@code null} stands for a path
     * that does not exist and merges to the other.
     */
    private static TrackingSnapshot merge(TrackingSnapshot a, TrackingSnapshot b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        Set<Object> names = new HashSet<>();
        names.addAll(a.flattened.keySet());
        names.addAll(a.live.keySet());
        names.addAll(a.concatenated.keySet());
        names.addAll(b.flattened.keySet());
        names.addAll(b.live.keySet());
        names.addAll(b.concatenated.keySet());

        Map<Object, ASTNode> mergedFlattened = new HashMap<>();
        Map<Object, ASTNode> mergedLive = new HashMap<>();
        Map<Object, ASTNode> mergedConcatenated = new HashMap<>();

        for (Object name : names) {
            // Unsafe states win pessimistically: if either path leaves this variable unsafe,
            // that state survives the merge regardless of which path actually runs.
            if (a.flattened.containsKey(name) || b.flattened.containsKey(name)) {
                mergedFlattened.put(name, a.flattened.containsKey(name) ? a.flattened.get(name) : b.flattened.get(name));
            }
            else if (a.concatenated.containsKey(name) || b.concatenated.containsKey(name)) {
                mergedConcatenated.put(name, a.concatenated.containsKey(name) ? a.concatenated.get(name) : b.concatenated.get(name));
            }
            else if (a.live.containsKey(name) || b.live.containsKey(name)) {
                mergedLive.put(name, a.live.containsKey(name) ? a.live.get(name) : b.live.get(name));
            }
        }

        // Constant text is the one state that must hold on both paths to survive the merge.
        Set<Object> mergedConstant = new HashSet<>(a.constant);
        mergedConstant.retainAll(b.constant);

        return new TrackingSnapshot(mergedFlattened, mergedLive, mergedConcatenated, mergedConstant);
    }

    private static final class TrackingSnapshot {

        final Map<Object, ASTNode> flattened;
        final Map<Object, ASTNode> live;
        final Map<Object, ASTNode> concatenated;
        final Set<Object> constant;

        TrackingSnapshot(Map<Object, ASTNode> flattened, Map<Object, ASTNode> live, Map<Object, ASTNode> concatenated,
                Set<Object> constant) {
            this.flattened = new HashMap<>(flattened);
            this.live = new HashMap<>(live);
            this.concatenated = new HashMap<>(concatenated);
            this.constant = new HashSet<>(constant);
        }

        /**
         * Whether every variable is in the same category as in {@code other}. The location nodes
         * are not compared: they only record where a category was first established.
         */
        boolean sameStateAs(TrackingSnapshot other) {
            return flattened.keySet().equals(other.flattened.keySet()) &&
                    live.keySet().equals(other.live.keySet()) &&
                    concatenated.keySet().equals(other.concatenated.keySet()) &&
                    constant.equals(other.constant);
        }
    }

    /**
     * A loop, {@code switch} statement or expression, or closure body being walked, with the
     * states that jumps inside it carry to its exit ({@code break}, or {@code yield} from a
     * {@code switch} expression) and to its head ({@code continue}, or {@code return} from a
     * closure), and the state at the end of its most recent walk.
     */
    private static final class JumpTarget {

        enum Kind {
            LOOP, SWITCH, SWITCH_EXPRESSION, CLOSURE
        }

        final Kind kind;
        final List<String> labels;
        TrackingSnapshot exit;
        TrackingSnapshot head;
        TrackingSnapshot end;

        JumpTarget(Statement statement, Kind kind) {
            this.kind = kind;
            List<String> statementLabels = statement != null ? statement.getStatementLabels() : null;
            this.labels = statementLabels != null ? statementLabels : Collections.emptyList();
        }

        void reset() {
            exit = null;
            head = null;
            end = null;
        }
    }

    /**
     * A {@code try} statement with a {@code finally} block whose {@code try} or {@code catch}
     * blocks are being walked, with the jumps out of it that land only once the {@code finally}
     * block has run.
     */
    private static final class FinallyFrame {

        /** The jump targets enclosing the statement: a jump from inside it to one of these leaves it. */
        final Set<JumpTarget> outerTargets;
        /** The jumps out of the statement, each to the head or the exit of one of {@link #outerTargets}. */
        final List<Jump> jumps = new ArrayList<>();
        /** The merge of the states the jumps and returns out of the statement leave with. */
        TrackingSnapshot jumpStates;

        FinallyFrame(Collection<JumpTarget> outerTargets) {
            this.outerTargets = new HashSet<>(outerTargets);
        }
    }

    /** A jump held by a {@link FinallyFrame}: to the head of its target, or to its exit. */
    private static final class Jump {

        final JumpTarget target;
        final boolean toHead;

        Jump(JumpTarget target, boolean toHead) {
            this.target = target;
            this.toHead = toHead;
        }
    }

    /**
     * An assignment to a local found by the pre-scan: the value assigned, or {@code null} for an
     * assignment that is not string building this check understands, and the type the local is
     * declared with.
     */
    private static final class Write {

        final Object key;
        final Expression value;
        final ClassNode type;

        Write(Object key, Expression value, ClassNode type) {
            this.key = key;
            this.value = value;
            this.type = type;
        }
    }

    /**
     * What a pre-scan of the code about to be walked finds: the facts about its locals that do not
     * depend on the order its statements run in.
     */
    private final class CodeFacts extends CodeVisitorSupport {

        /** Every local declared in the code. */
        final Set<Object> declared = new HashSet<>();
        /**
         * Every local and parameter the code declares, those of its closures and anonymous inner
         * classes included.
         */
        final Set<Object> owned = new HashSet<>();
        /** Every assignment to a local, declarations with a value included. */
        final List<Write> writes = new ArrayList<>();
        /** The locals whose declaration carries the suppression. */
        final Set<Object> suppressed = new HashSet<>();
        /**
         * The locals assigned where the walk cannot place the assignment in order: inside another
         * expression (a ternary or Elvis branch, an {@code &&} or {@code ||} operand, a method
         * argument, a loop condition), by multiple assignment, or inside a closure or an anonymous
         * inner class that does not declare them, which may run at any later point.
         */
        final Set<Object> unordered = new HashSet<>();
        /** The locals and parameters each closure declares. */
        final Map<ClosureExpression, Set<Object>> closureLocals = new IdentityHashMap<>();
        /** The innermost closure declaring each local declared inside one. */
        final Map<Object, ClosureExpression> declaringClosures = new HashMap<>();
        /** The innermost {@code switch} expression declaring each local declared inside one. */
        final Map<Object, SwitchExpression> declaringSwitchExpressions = new HashMap<>();
        /**
         * The locals that hold only constant text however their assignments are ordered, filled
         * in by {@link #settleConstants} once the scan is complete.
         */
        final Set<Object> constant = new HashSet<>();
        /**
         * The worst each local or parameter holds however its assignments are ordered, filled in
         * by {@link #settleOrigins} once the scan is complete.
         */
        final Map<Object, Origin> origins = new HashMap<>();

        /** The loop and closure bodies that assign a local declared outside them. */
        final Set<Statement> bodiesAssigningOuterLocals = Collections.newSetFromMap(new IdentityHashMap<>());

        private final Set<Expression> statementExpressions = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Deque<Set<Object>> closureScopes = new ArrayDeque<>();
        /**
         * The closure, anonymous inner class creation or {@code switch} expression owning each of
         * {@link #closureScopes}.
         */
        private final Deque<Expression> scopeOwners = new ArrayDeque<>();
        /** The loop and closure bodies being scanned, innermost first. */
        private final Deque<BodyScope> openBodies = new ArrayDeque<>();

        @Override
        public void visitExpressionStatement(ExpressionStatement statement) {
            statementExpressions.add(statement.getExpression());
            super.visitExpressionStatement(statement);
        }

        @Override
        public void visitClosureExpression(ClosureExpression expression) {
            Set<Object> locals = new HashSet<>();
            if (expression.getParameters() != null) {
                locals.addAll(Arrays.asList(expression.getParameters()));
            }
            closureLocals.put(expression, locals);
            owned.addAll(locals);
            openScope(expression, locals);
            try {
                scanBody(expression.getCode(), locals, () -> super.visitClosureExpression(expression));
            } finally {
                closeScope();
            }
        }

        /**
         * The value of a {@code switch} expression is classified where it is assigned, before its
         * arms are walked, so the locals its arms declare or assign are judged as a closure's are.
         */
        @Override
        public void visitSwitchExpression(SwitchExpression expression) {
            openScope(expression, new HashSet<>());
            try {
                super.visitSwitchExpression(expression);
            } finally {
                closeScope();
            }
        }

        private void openScope(Expression owner, Set<Object> locals) {
            scopeOwners.push(owner);
            closureScopes.push(locals);
        }

        private void closeScope() {
            scopeOwners.pop();
            closureScopes.pop();
        }

        /**
         * The class is checked on its own, as a separate class, but its code may run at any point
         * after it is created, like a closure's, and assign locals declared outside it.
         */
        @Override
        public void visitConstructorCallExpression(ConstructorCallExpression call) {
            super.visitConstructorCallExpression(call);
            if (!call.isUsingAnonymousInnerClass()) {
                return;
            }
            ClassNode type = call.getType();
            Set<Object> locals = new HashSet<>();
            for (MethodNode method : type.getMethods()) {
                locals.addAll(Arrays.asList(method.getParameters()));
            }
            owned.addAll(locals);
            openScope(call, locals);
            try {
                for (FieldNode field : type.getFields()) {
                    Expression initial = field.getInitialValueExpression();
                    if (initial != null) {
                        initial.visit(this);
                    }
                }
                for (Statement statement : type.getObjectInitializerStatements()) {
                    statement.visit(this);
                }
                for (MethodNode method : type.getMethods()) {
                    if (method.getCode() != null) {
                        method.getCode().visit(this);
                    }
                }
            } finally {
                closeScope();
            }
        }

        @Override
        public void visitForLoop(ForStatement statement) {
            declareLocal(statement.getVariable());
            // The loop itself assigns its variable on every iteration, so it counts as the body's own.
            scanBody(statement.getLoopBlock(), Collections.singleton(statement.getVariable()),
                    () -> super.visitForLoop(statement));
        }

        @Override
        public void visitWhileLoop(WhileStatement statement) {
            scanBody(statement.getLoopBlock(), Collections.emptySet(), () -> super.visitWhileLoop(statement));
        }

        @Override
        public void visitDoWhileLoop(DoWhileStatement statement) {
            scanBody(statement.getLoopBlock(), Collections.emptySet(), () -> super.visitDoWhileLoop(statement));
        }

        @Override
        public void visitCatchStatement(CatchStatement statement) {
            declareLocal(statement.getVariable());
            super.visitCatchStatement(statement);
        }

        private void scanBody(Statement body, Set<?> ownVariables, Runnable scan) {
            BodyScope scope = new BodyScope(body);
            scope.locals.addAll(ownVariables);
            openBodies.push(scope);
            try {
                scan.run();
            } finally {
                openBodies.pop();
            }
        }

        @Override
        public void visitDeclarationExpression(DeclarationExpression expression) {
            if (expression.isMultipleAssignmentDeclaration()) {
                List<Expression> elements = expression.getTupleExpression().getExpressions();
                for (int i = 0; i < elements.size(); i++) {
                    if (elements.get(i) instanceof VariableExpression) {
                        VariableExpression element = (VariableExpression) elements.get(i);
                        Object key = keyOf(element);
                        declareLocal(key);
                        declared.add(key);
                        if (isSuppressedNode(expression)) {
                            suppressed.add(key);
                        }
                        assign(key, elementAssigned(expression.getRightExpression(), i), element.getType(), false);
                    }
                }
            }
            else {
                Object key = keyOf(expression.getVariableExpression());
                declareLocal(key);
                declared.add(key);
                if (isSuppressedNode(expression)) {
                    suppressed.add(key);
                }
                Expression value = expression.getRightExpression();
                // A local declared without a value holds null until it is assigned.
                if (!(value instanceof EmptyExpression)) {
                    assign(key, value, expression.getVariableExpression().getType(),
                            statementExpressions.contains(expression));
                }
                else if (!statementExpressions.contains(expression)) {
                    unordered.add(key);
                }
            }
            expression.getRightExpression().visit(this);
        }

        @Override
        public void visitBinaryExpression(BinaryExpression expression) {
            int operation = expression.getOperation().getType();
            if (Types.ofType(operation, Types.ASSIGNMENT_OPERATOR)) {
                Expression left = expression.getLeftExpression();
                if (left instanceof VariableExpression) {
                    Expression value = operation == Types.ASSIGN ? expression.getRightExpression() :
                            operation == Types.PLUS_EQUAL ? plusEqualAsConcatenation(expression) : null;
                    assign(keyOf((VariableExpression) left), value, left.getType(), statementExpressions.contains(expression));
                }
                else if (left instanceof TupleExpression && operation == Types.ASSIGN) {
                    List<Expression> elements = ((TupleExpression) left).getExpressions();
                    for (int i = 0; i < elements.size(); i++) {
                        if (elements.get(i) instanceof VariableExpression) {
                            assign(keyOf((VariableExpression) elements.get(i)),
                                    elementAssigned(expression.getRightExpression(), i), elements.get(i).getType(), false);
                        }
                    }
                }
            }
            super.visitBinaryExpression(expression);
        }

        private void declareLocal(Object key) {
            owned.add(key);
            // A switch expression runs in place, so its locals are also those of the scope around it.
            Iterator<Expression> owners = scopeOwners.iterator();
            for (Set<Object> closureScope : closureScopes) {
                closureScope.add(key);
                if (!(owners.next() instanceof SwitchExpression)) {
                    break;
                }
            }
            Expression owner = scopeOwners.peek();
            if (owner instanceof ClosureExpression) {
                declaringClosures.put(key, (ClosureExpression) owner);
            }
            else if (owner instanceof SwitchExpression) {
                declaringSwitchExpressions.put(key, (SwitchExpression) owner);
            }
            for (BodyScope scope : openBodies) {
                scope.locals.add(key);
            }
        }

        private void assign(Object key, Expression value, ClassNode type, boolean inOrder) {
            writes.add(new Write(key, value, type));
            Set<Object> closureScope = closureScopes.peek();
            if (!inOrder || closureScope != null && !closureScope.contains(key)) {
                unordered.add(key);
            }
            for (BodyScope scope : openBodies) {
                if (!scope.locals.contains(key)) {
                    bodiesAssigningOuterLocals.add(scope.body);
                }
            }
        }
    }

    /** A loop or closure body being scanned, with the locals and parameters it declares. */
    private static final class BodyScope {

        final Statement body;
        final Set<Object> locals = new HashSet<>();

        BodyScope(Statement body) {
            this.body = body;
        }
    }

    /**
     * Records what the local under {@code key} now holds after being assigned {@code rightExpression},
     * resolving through any variable aliasing so a {@code GString} tracked several assignments
     * earlier is still recognised as unsafe once it (or an alias of it) reaches a
     * {@code String}-typed variable. A variable whose declaration carries the suppression is
     * never tracked, whatever it is assigned.
     */
    private void track(Object key, Expression rightExpression, ClassNode declaredType, ASTNode locationNode) {
        // Classify against the state *before* this assignment, so self-references such as
        // q = q + "..." see what q held until now.
        Origin origin = classify(rightExpression, declaredType);
        // Any reassignment first clears prior tracking under every category - last write wins
        // for what follows, then the switch below re-establishes tracking if still relevant.
        untrack(key);
        if (facts.suppressed.contains(key)) {
            return;
        }
        switch (origin) {
            case FLATTENED:
                flattenedStringVars.put(key, locationNode);
                break;
            case LIVE_GSTRING:
                liveGStringVars.put(key, locationNode);
                break;
            case CONCATENATED:
                concatenatedStringVars.put(key, locationNode);
                break;
            case CONSTANT:
                constantTextVars.add(key);
                break;
            case NONE:
            default:
                break;
        }
    }

    private void untrack(Object key) {
        flattenedStringVars.remove(key);
        liveGStringVars.remove(key);
        concatenatedStringVars.remove(key);
        constantTextVars.remove(key);
    }

    /**
     * Determines what {@code expression} evaluates to, from this check's point of view, resolving
     * one level of variable reference against the current tracking state so aliasing chains
     * (however many hops long) are followed correctly - each hop was itself already classified
     * when its own assignment was visited.
     */
    private Origin classify(Expression expression, ClassNode declaredType) {
        if (expression instanceof VariableExpression) {
            Origin origin = originOf((VariableExpression) expression);
            // A flattened or concatenated String stays unsafe regardless of declaredType.
            return origin == Origin.LIVE_GSTRING && ClassHelper.STRING_TYPE.equals(declaredType) ? Origin.FLATTENED : origin;
        }
        if (isConstantText(expression)) {
            return Origin.CONSTANT;
        }
        if (isDataInterpolatedGString(expression)) {
            return ClassHelper.STRING_TYPE.equals(declaredType) ? Origin.FLATTENED : Origin.LIVE_GSTRING;
        }
        if (expression instanceof TernaryExpression) {
            // Covers the Elvis operator too: either branch may be the value assigned.
            TernaryExpression ternary = (TernaryExpression) expression;
            return classify(ternary.getTrueExpression(), declaredType).or(classify(ternary.getFalseExpression(), declaredType));
        }
        List<Expression> results = resultsOf(expression);
        if (results != null) {
            Origin origin = Origin.CONSTANT;
            for (Expression result : results) {
                origin = origin.or(classify(result, declaredType));
            }
            return origin;
        }
        if (expression instanceof CastExpression) {
            CastExpression cast = (CastExpression) expression;
            if (ClassHelper.STRING_TYPE.equals(cast.getType()) && isUnsafeSource(cast.getExpression())) {
                return Origin.FLATTENED;
            }
            return Origin.NONE;
        }
        if (expression instanceof MethodCallExpression) {
            MethodCallExpression call = (MethodCallExpression) expression;
            if ("toString".equals(call.getMethodAsString()) && isUnsafeSource(call.getObjectExpression())) {
                return Origin.FLATTENED; // .toString() always yields a String, regardless of declaredType
            }
            return Origin.NONE;
        }
        return classifyConcatenation(expression);
    }

    /**
     * Classifies a {@code +} concatenation. One with a flattened or live {@code GString} source
     * anywhere in the tree is {@link Origin#FLATTENED}, not merely {@link Origin#CONCATENATED} -
     * concatenating a GString with anything else immediately converts it to a plain {@code String}
     * at runtime (Groovy's {@code GString.plus} returns {@code String}), the same irreversible
     * coercion a {@code .toString()} call causes, and a flattened operand stays flattened. A
     * concatenation with no such source, but at least one operand that is not constant text, is
     * the lower-confidence {@link Origin#CONCATENATED} case.
     */
    private Origin classifyConcatenation(Expression expression) {
        if (!isConcatenation(expression)) {
            return Origin.NONE;
        }
        if (containsUnsafeSource(expression)) {
            return Origin.FLATTENED;
        }
        if (hasNonConstantOperand(expression)) {
            return Origin.CONCATENATED;
        }
        return Origin.NONE;
    }

    /**
     * True when {@code expression} is itself a GString interpolating data, or a variable reference
     * already tracked as a live GString or an already-flattened String - i.e. anything a cast or
     * {@code .toString()} applied on top of would still be unsafe to hand to a query method.
     */
    private boolean isUnsafeSource(Expression expression) {
        if (isDataInterpolatedGString(expression)) {
            return true;
        }
        if (expression instanceof VariableExpression) {
            Origin origin = originOf((VariableExpression) expression);
            return origin == Origin.FLATTENED || origin == Origin.LIVE_GSTRING;
        }
        if (expression instanceof TernaryExpression) {
            TernaryExpression ternary = (TernaryExpression) expression;
            return isUnsafeSource(ternary.getTrueExpression()) || isUnsafeSource(ternary.getFalseExpression());
        }
        List<Expression> results = resultsOf(expression);
        if (results != null) {
            for (Expression result : results) {
                if (isUnsafeSource(result)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The expressions whose value {@code expression} takes when it is a closure literal called on
     * the spot, as in {@code { ... }()}, or a {@code switch} expression, or {@code null} for any
     * other expression.
     */
    private static List<Expression> resultsOf(Expression expression) {
        ClosureExpression called = calledClosure(expression);
        if (called != null) {
            return resultsOf(called);
        }
        if (expression instanceof SwitchExpression) {
            return resultsOf((SwitchExpression) expression);
        }
        return null;
    }

    /**
     * The closure literal {@code expression} calls on the spot, as in {@code { ... }()}, or
     * {@code null}.
     */
    private static ClosureExpression calledClosure(Expression expression) {
        if (expression instanceof MethodCallExpression) {
            MethodCallExpression call = (MethodCallExpression) expression;
            if ("call".equals(call.getMethodAsString()) && call.getObjectExpression() instanceof ClosureExpression) {
                return (ClosureExpression) call.getObjectExpression();
            }
        }
        return null;
    }

    /**
     * The expressions a closure can return: those of its {@code return} statements, its own and
     * not those of closures nested in it, and the last expression of each path through its body.
     */
    private static List<Expression> resultsOf(ClosureExpression closure) {
        List<Expression> results = new ArrayList<>();
        closure.getCode().visit(new CodeVisitorSupport() {
            @Override
            public void visitReturnStatement(ReturnStatement statement) {
                results.add(statement.getExpression());
                super.visitReturnStatement(statement);
            }

            @Override
            public void visitClosureExpression(ClosureExpression nested) {
                // A nested closure's returns end that closure, not this one.
            }
        });
        addTailResults(closure.getCode(), results);
        return results;
    }

    /**
     * The expressions a {@code switch} expression can take: those of its own {@code yield}
     * statements, not those of {@code switch} expressions nested in it.
     */
    private static List<Expression> resultsOf(SwitchExpression switchExpression) {
        List<Expression> results = new ArrayList<>();
        CodeVisitorSupport yields = new CodeVisitorSupport() {
            @Override
            public void visitYieldStatement(YieldStatement statement) {
                results.add(statement.getExpression());
            }

            @Override
            public void visitSwitchExpression(SwitchExpression nested) {
                // A nested switch expression's yields give that expression its value, not this one.
            }

            @Override
            public void visitClosureExpression(ClosureExpression nested) {
                // A yield cannot leave a closure.
            }
        };
        for (CaseStatement caseStatement : switchExpression.getCaseStatements()) {
            caseStatement.getCode().visit(yields);
        }
        switchExpression.getDefaultStatement().visit(yields);
        return results;
    }

    private static void addTailResults(Statement statement, List<Expression> results) {
        if (statement instanceof ExpressionStatement) {
            results.add(((ExpressionStatement) statement).getExpression());
        }
        else if (statement instanceof BlockStatement) {
            List<Statement> statements = ((BlockStatement) statement).getStatements();
            int last = statements.size() - 1;
            if (last > 0 && statements.get(last) instanceof BreakStatement) {
                last--;
            }
            if (last >= 0) {
                addTailResults(statements.get(last), results);
            }
        }
        else if (statement instanceof IfStatement) {
            addTailResults(((IfStatement) statement).getIfBlock(), results);
            addTailResults(((IfStatement) statement).getElseBlock(), results);
        }
        else if (statement instanceof SwitchStatement) {
            for (CaseStatement caseStatement : ((SwitchStatement) statement).getCaseStatements()) {
                addTailResults(caseStatement.getCode(), results);
            }
            addTailResults(((SwitchStatement) statement).getDefaultStatement(), results);
        }
        else if (statement instanceof TryCatchStatement) {
            addTailResults(((TryCatchStatement) statement).getTryStatement(), results);
            for (CatchStatement catchStatement : ((TryCatchStatement) statement).getCatchStatements()) {
                addTailResults(catchStatement.getCode(), results);
            }
        }
        else if (statement instanceof SynchronizedStatement) {
            addTailResults(((SynchronizedStatement) statement).getCode(), results);
        }
    }

    /**
     * A {@code GString} with at least one interpolation that is not constant text - the only kind
     * of {@code GString} that can carry runtime data into a query.
     */
    private boolean isDataInterpolatedGString(Expression expression) {
        return expression instanceof GStringExpression &&
                !allConstantText(((GStringExpression) expression).getValues());
    }

    private boolean allConstantText(List<Expression> expressions) {
        for (Expression expression : expressions) {
            if (!isConstantText(expression)) {
                return false;
            }
        }
        return true;
    }

    /**
     * True when {@code expression} is constant text as defined in the class Javadoc: something that
     * is fully determined at compile time (or chosen at runtime from such things) and therefore
     * cannot carry user input into a query. A variable whose declaration carries the suppression
     * counts as reviewed, trusted text.
     */
    private boolean isConstantText(Expression expression) {
        if (expression instanceof ConstantExpression) {
            return true;
        }
        if (expression instanceof GStringExpression) {
            return allConstantText(((GStringExpression) expression).getValues());
        }
        if (expression instanceof VariableExpression) {
            return isConstantTextVariable((VariableExpression) expression);
        }
        if (expression instanceof PropertyExpression) {
            return isConstantTextField(staticFieldOf((PropertyExpression) expression));
        }
        if (expression instanceof TernaryExpression) {
            // Covers the Elvis operator too, whose "true" expression is its left operand.
            TernaryExpression ternary = (TernaryExpression) expression;
            return isConstantText(ternary.getTrueExpression()) && isConstantText(ternary.getFalseExpression());
        }
        List<Expression> results = resultsOf(expression);
        if (results != null) {
            for (Expression result : results) {
                if (!isConstantText(result)) {
                    return false;
                }
            }
            return true;
        }
        if (isConcatenation(expression)) {
            BinaryExpression binary = (BinaryExpression) expression;
            return isConstantText(binary.getLeftExpression()) && isConstantText(binary.getRightExpression());
        }
        if (expression instanceof CastExpression) {
            return isConstantText(((CastExpression) expression).getExpression());
        }
        if (expression instanceof MethodCallExpression) {
            MethodCallExpression call = (MethodCallExpression) expression;
            return "toString".equals(call.getMethodAsString()) && isConstantText(call.getObjectExpression());
        }
        return false;
    }

    /**
     * What the local, parameter or field {@code variable} refers to holds at this point. A local
     * the walk cannot follow here (see {@link #isJudgedByEveryAssignment}) holds the worst that any
     * assignment to it stores.
     */
    private Origin originOf(VariableExpression variable) {
        if (isConstantTextVariable(variable)) {
            return Origin.CONSTANT;
        }
        Object key = keyOf(variable);
        Origin origin = flattenedStringVars.containsKey(key) ? Origin.FLATTENED :
                concatenatedStringVars.containsKey(key) ? Origin.CONCATENATED :
                liveGStringVars.containsKey(key) ? Origin.LIVE_GSTRING : Origin.NONE;
        CodeFacts enclosing = enclosingFactsOwning(key);
        if (enclosing != null) {
            return origin.or(enclosing.origins.get(key));
        }
        if (assumedOrigins != null) {
            return origin.or(assumedOrigins.get(key));
        }
        return isJudgedByEveryAssignment(key) ? origin.or(facts.origins.get(key)) : origin;
    }

    /**
     * Whether the walk cannot know what the local under {@code key} holds at this point, so it is
     * judged by every assignment to it in the code: one assigned where the walk cannot place the
     * assignment (see {@link CodeFacts#unordered}), one declared outside the closure being walked,
     * which may run at any later point, and one declared inside a closure or {@code switch}
     * expression that is not being walked, whose state is only tracked while it is.
     */
    private boolean isJudgedByEveryAssignment(Object key) {
        if (facts.unordered.contains(key) || isSharedWithEnclosingClosure(key)) {
            return true;
        }
        SwitchExpression declaringSwitch = facts.declaringSwitchExpressions.get(key);
        if (declaringSwitch != null && !switchExpressions.contains(declaringSwitch)) {
            return true;
        }
        ClosureExpression declaring = facts.declaringClosures.get(key);
        return declaring != null && !closures.contains(declaring);
    }

    /**
     * For a local or parameter of a method enclosing the anonymous inner class being visited,
     * what the pre-scan found in that method; {@code null} for anything else.
     */
    private CodeFacts enclosingFactsOwning(Object key) {
        if (enclosingFacts.isEmpty() || facts.owned.contains(key)) {
            return null;
        }
        for (CodeFacts enclosing : enclosingFacts) {
            if (enclosing.owned.contains(key)) {
                return enclosing;
            }
        }
        return null;
    }

    /**
     * Resolves a bare name through what the compiler bound it to, so a {@code static final}
     * constant is recognised as such while a parameter or local that merely shadows one is not.
     * A local the walk cannot follow here (see {@link #isJudgedByEveryAssignment}), or one of a
     * method enclosing the anonymous inner class being visited, is constant text only if every
     * assignment to it is.
     */
    private boolean isConstantTextVariable(VariableExpression variable) {
        if (variable.isThisExpression() || variable.isSuperExpression()) {
            return false;
        }
        Variable accessed = variable.getAccessedVariable();
        if (accessed instanceof FieldNode) {
            return isConstantTextField((FieldNode) accessed);
        }
        if (accessed instanceof PropertyNode) {
            return isConstantTextField(((PropertyNode) accessed).getField());
        }
        if (accessed instanceof DynamicVariable) {
            return false;
        }
        Object key = keyOf(variable);
        if (facts.suppressed.contains(key)) {
            return true;
        }
        CodeFacts enclosing = enclosingFactsOwning(key);
        if (enclosing != null) {
            // A local whose declaration carries the suppression is always in the constant set.
            return enclosing.constant.contains(key);
        }
        if (assumedConstant != null) {
            return assumedConstant.contains(key);
        }
        if (isJudgedByEveryAssignment(key)) {
            return facts.constant.contains(key);
        }
        return constantTextVars.contains(key);
    }

    /**
     * Whether the local under {@code key} is declared outside the closure being walked, which
     * may run at any later point, after the local has been reassigned.
     */
    private boolean isSharedWithEnclosingClosure(Object key) {
        ClosureExpression closure = closures.peek();
        if (closure == null) {
            return false;
        }
        Set<Object> ownLocals = facts.closureLocals.get(closure);
        return ownLocals == null || !ownLocals.contains(key);
    }

    /**
     * A {@code static final} field whose initializer is itself constant text. Instance fields and
     * non-final statics can be assigned from anywhere this check does not see, so they never
     * qualify; a field whose initializer refers back to itself (directly or through another
     * field) is not constant either.
     */
    private boolean isConstantTextField(FieldNode field) {
        if (field == null || !field.isStatic() || !field.isFinal() || field.getInitialValueExpression() == null) {
            return false;
        }
        ClassNode owner = field.getOwner();
        String key = (owner != null ? owner.getName() : "") + "." + field.getName();
        if (!resolvingFields.add(key)) {
            return false;
        }
        try {
            return isConstantText(field.getInitialValueExpression());
        } finally {
            resolvingFields.remove(key);
        }
    }

    /**
     * The field a {@code this.NAME} or {@code SomeClass.NAME} property reference denotes, or
     * {@code null} when the receiver is anything else (an instance, a method result, ...).
     */
    private FieldNode staticFieldOf(PropertyExpression property) {
        String name = property.getPropertyAsString();
        if (name == null) {
            return null;
        }
        Expression object = property.getObjectExpression();
        if (object instanceof ClassExpression) {
            return object.getType().getField(name);
        }
        if (isThisFieldReference(property) && currentClassNode != null) {
            return currentClassNode.getField(name);
        }
        return null;
    }

    private boolean isConcatenation(Expression expression) {
        return expression instanceof BinaryExpression &&
                ((BinaryExpression) expression).getOperation().getType() == Types.PLUS;
    }

    private boolean hasNonConstantOperand(Expression expression) {
        if (isConcatenation(expression)) {
            BinaryExpression binary = (BinaryExpression) expression;
            return hasNonConstantOperand(binary.getLeftExpression()) || hasNonConstantOperand(binary.getRightExpression());
        }
        return !isConstantText(expression);
    }

    private boolean containsUnsafeSource(Expression expression) {
        if (isConcatenation(expression)) {
            BinaryExpression binary = (BinaryExpression) expression;
            return containsUnsafeSource(binary.getLeftExpression()) || containsUnsafeSource(binary.getRightExpression());
        }
        return isUnsafeSource(expression);
    }

    /**
     * Seeds {@link #flattenedFields} from a field's own initializer, e.g.
     * {@code String query = "...${x}..."} declared directly on the class. Unlike local tracking,
     * this only recognises a bare interpolated GString initializer - not a {@code .toString()} or
     * cast coercion - to keep the (already lower-confidence) field check simple. A field whose
     * declaration carries the suppression is never tracked.
     */
    private void trackFieldInitializer(FieldNode field) {
        Expression initial = field.getInitialValueExpression();
        if (initial != null && isFlatteningFieldAssignment(field, initial)) {
            flattenedFields.put(field.getName(), field);
        }
    }

    /**
     * Records a {@code this.field = ...} assignment. A safe reassignment clears prior tracking
     * for that field - last-write-wins, with no branch-sensitivity (see class Javadoc).
     */
    private void trackField(String fieldName, Expression rightExpression, ASTNode locationNode) {
        FieldNode field = currentClassNode != null ? currentClassNode.getField(fieldName) : null;
        if (isFlatteningFieldAssignment(field, rightExpression)) {
            flattenedFields.put(fieldName, locationNode);
        }
        else {
            flattenedFields.remove(fieldName);
        }
    }

    private boolean isFlatteningFieldAssignment(FieldNode field, Expression value) {
        return field != null && !isSuppressedNode(field) &&
                ClassHelper.STRING_TYPE.equals(field.getType()) && isDataInterpolatedGString(value);
    }

    private boolean isThisFieldReference(Expression expression) {
        if (!(expression instanceof PropertyExpression)) {
            return false;
        }
        PropertyExpression property = (PropertyExpression) expression;
        return property.getObjectExpression() instanceof VariableExpression &&
                ((VariableExpression) property.getObjectExpression()).isThisExpression() &&
                property.getPropertyAsString() != null;
    }

    private String fieldNameOf(Expression expression) {
        return ((PropertyExpression) expression).getPropertyAsString();
    }

    @Override
    public void visitMethodCallExpression(MethodCallExpression call) {
        String methodName = call.getMethodAsString();
        if (methodName != null && CANDIDATE_METHODS.contains(methodName) &&
                isGormReceiver(call.getObjectExpression(), methodName) && !isSuppressed()) {
            report(findUnsafeArgument(methodName, call.getArguments()), call, methodName);
        }
        super.visitMethodCallExpression(call);
    }

    @Override
    public void visitStaticMethodCallExpression(StaticMethodCallExpression call) {
        String methodName = call.getMethod();
        if (CANDIDATE_METHODS.contains(methodName) &&
                AstUtils.isDomainClass(call.getOwnerType()) && !isSuppressed()) {
            report(findUnsafeArgument(methodName, call.getArguments()), call, methodName);
        }
        super.visitStaticMethodCallExpression(call);
    }

    private boolean isGormReceiver(Expression objectExpression, String methodName) {
        if (objectExpression instanceof ClassExpression) {
            return AstUtils.isDomainClass(((ClassExpression) objectExpression).getType());
        }
        if (objectExpression instanceof VariableExpression && ((VariableExpression) objectExpression).isThisExpression()) {
            return reachesDomainClass(currentClassNode, methodName);
        }
        return false;
    }

    /**
     * Whether a call on {@code this}, written or implied, in {@code type} reaches a domain class:
     * {@code type} itself, or a class enclosing it, which is where Groovy sends a call that an
     * inner class has no method for.
     */
    private static boolean reachesDomainClass(ClassNode type, String methodName) {
        for (ClassNode receiver = type; receiver != null; receiver = receiver.getOuterClass()) {
            if (AstUtils.isDomainClass(receiver)) {
                return true;
            }
            if (hasMethodNamed(receiver, methodName)) {
                return false;
            }
        }
        return false;
    }

    private static boolean hasMethodNamed(ClassNode type, String methodName) {
        for (ClassNode declaring = type; declaring != null; declaring = declaring.getSuperClass()) {
            if (!declaring.getMethods(methodName).isEmpty()) {
                return true;
            }
        }
        for (ClassNode declaring : type.getAllInterfaces()) {
            if (!declaring.getMethods(methodName).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private Finding findUnsafeArgument(String methodName, Expression arguments) {
        if (!(arguments instanceof ArgumentListExpression)) {
            return Finding.NONE;
        }
        List<Expression> args = ((ArgumentListExpression) arguments).getExpressions();
        Integer index = QUERY_ARGUMENT_INDEX.get(methodName);
        if (index == null || args.size() <= index) {
            return Finding.NONE;
        }
        return findingFor(args.get(index));
    }

    /**
     * What is unsafe about {@code argument} as the query text, if anything. Either branch of a
     * ternary or Elvis expression, and anything a closure called on the spot returns, may be the
     * query, so the most severe of their findings is the finding.
     */
    private Finding findingFor(Expression argument) {
        if (argument instanceof VariableExpression) {
            Origin origin = originOf((VariableExpression) argument);
            if (origin == Origin.FLATTENED) {
                return Finding.FLATTENED_GSTRING;
            }
            if (origin == Origin.CONCATENATED) {
                return Finding.UNSAFE_CONCATENATION;
            }
            return Finding.NONE;
        }
        if (argument instanceof TernaryExpression) {
            TernaryExpression ternary = (TernaryExpression) argument;
            return findingFor(ternary.getTrueExpression()).or(findingFor(ternary.getFalseExpression()));
        }
        List<Expression> results = resultsOf(argument);
        if (results != null) {
            Finding finding = Finding.NONE;
            for (Expression result : results) {
                finding = finding.or(findingFor(result));
            }
            return finding;
        }
        if (isThisFieldReference(argument) && flattenedFields.containsKey(fieldNameOf(argument))) {
            return Finding.FLATTENED_FIELD;
        }
        if (argument instanceof CastExpression) {
            CastExpression cast = (CastExpression) argument;
            if (ClassHelper.STRING_TYPE.equals(cast.getType()) && isUnsafeSource(cast.getExpression())) {
                return Finding.FLATTENED_GSTRING;
            }
        }
        if (argument instanceof MethodCallExpression) {
            MethodCallExpression flatteningCall = (MethodCallExpression) argument;
            if ("toString".equals(flatteningCall.getMethodAsString()) && isUnsafeSource(flatteningCall.getObjectExpression())) {
                return Finding.FLATTENED_GSTRING;
            }
        }
        Origin concatOrigin = classifyConcatenation(argument);
        if (concatOrigin == Origin.FLATTENED) {
            return Finding.FLATTENED_GSTRING;
        }
        if (concatOrigin == Origin.CONCATENATED) {
            return Finding.UNSAFE_CONCATENATION;
        }
        return Finding.NONE;
    }

    private void report(Finding finding, ASTNode node, String methodName) {
        if (silentPasses > 0) {
            return;
        }
        switch (finding) {
            case FLATTENED_GSTRING:
                reportUnsafeQuery(node, methodName);
                break;
            case FLATTENED_FIELD:
                reportFlattenedFieldQuery(node, methodName);
                break;
            case UNSAFE_CONCATENATION:
                reportUnsafeConcatenation(node, methodName);
                break;
            case NONE:
            default:
                break;
        }
    }

    /**
     * Whether the code being walked carries the suppression: on its method, its class, or, for an
     * inner class, a class or method it is declared in.
     */
    private boolean isSuppressed() {
        if (currentMethodNode != null && isSuppressedNode(currentMethodNode)) {
            return true;
        }
        for (ClassNode type = currentClassNode; type != null; type = type.getOuterClass()) {
            MethodNode enclosingMethod = type.getEnclosingMethod();
            if (isSuppressedNode(type) || enclosingMethod != null && isSuppressedNode(enclosingMethod)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSuppressedNode(AnnotatedNode node) {
        for (AnnotationNode annotation : node.getAnnotations(ClassHelper.make(SuppressWarnings.class))) {
            Expression value = annotation.getMember("value");
            if (containsSuppressionValue(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsSuppressionValue(Expression value) {
        if (value instanceof ConstantExpression) {
            return SUPPRESS_WARNINGS_VALUE.equals(((ConstantExpression) value).getValue());
        }
        if (value instanceof ListExpression) {
            for (Expression element : ((ListExpression) value).getExpressions()) {
                if (containsSuppressionValue(element)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void reportUnsafeQuery(ASTNode node, String methodName) {
        String message = "[GORM] The query string passed to '" + methodName + "' was built from a " +
                "GString that Groovy already coerced to a plain String, so any interpolated " +
                "values are now embedded as raw, unescaped text - this is a query injection " +
                "risk. If the interpolated expressions are values, keep the query a GString when " +
                "calling '" + methodName + "' (GORM turns GString interpolations into bound query " +
                "parameters automatically), or pass named/positional parameters explicitly. If " +
                "they are query text instead - HQL fragments such as restrictions or an order-by " +
                "clause - a GString is the wrong tool, because GORM would bind the fragment as a " +
                "parameter value: build the text from constant fragments (string literals, static " +
                "final constants, and locals that only ever hold such text, which this check " +
                "recognises as safe) using '+' or '+=', and bind the values through " +
                "the params argument. " + SUPPRESSION_HINT;
        sourceUnit.getErrorCollector().addErrorAndContinue(message, node, sourceUnit);
    }

    private void reportFlattenedFieldQuery(ASTNode node, String methodName) {
        String message = "[GORM] The query string passed to '" + methodName + "' was built from a " +
                "field that was assigned a GString-interpolated value coerced to a plain String, " +
                "so any interpolated values may be embedded as raw, unescaped text - this is a " +
                "query injection risk if that field can be influenced by user input. This is a " +
                "warning rather than a build failure because field assignments outside this method " +
                "(other methods, constructors, subclasses) aren't visible to this check. Prefer " +
                "keeping the value as a GString or passing named/positional parameters. " +
                SUPPRESSION_HINT;
        reportWarning(node, message);
    }

    private void reportUnsafeConcatenation(ASTNode node, String methodName) {
        String message = "[GORM] The query string passed to '" + methodName + "' is built using " +
                "'+' string concatenation with a non-constant value, so no automatic parameter " +
                "binding is possible - this is a query injection risk if that value can be " +
                "influenced by user input. Prefer a GString (GORM binds interpolated values " +
                "automatically) or named/positional parameters for values; query text should be " +
                "built only from constant fragments, which this check does not flag. " +
                SUPPRESSION_HINT;
        reportWarning(node, message);
    }

    /**
     * Emits a compile-time warning (not a build failure) located at {@code node}.
     * {@link org.codehaus.groovy.control.ErrorCollector#addWarning} predates {@link ASTNode} and
     * still takes the older {@link org.codehaus.groovy.syntax.CSTNode} for location, so a minimal
     * {@link Token} carrying just the line/column is built to satisfy it.
     */
    private void reportWarning(ASTNode node, String message) {
        Token location = new Token(Types.UNKNOWN, "", node.getLineNumber(), node.getColumnNumber());
        sourceUnit.getErrorCollector().addWarning(WarningMessage.LIKELY_ERRORS, message, location, sourceUnit);
    }
}
