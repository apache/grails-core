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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassCodeVisitorSupport;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.CastExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.GStringExpression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.IfStatement;
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
 *     instrument. Concatenating a {@code GString} with anything else (e.g.
 *     {@code "...${x}..." + " order by title"}) is a different matter - {@code GString.plus}
 *     returns a plain {@code String}, so this flattens the interpolation immediately and is
 *     reported as the build-breaking error above, not this warning.</li>
 * </ul>
 *
 * <p>Both warnings share the same {@link #SUPPRESS_WARNINGS_VALUE} suppression as the error case.
 *
 * <p><strong>Known limitations (deliberate scope):</strong>
 * <ul>
 *     <li>Intraprocedural only — a flattened {@code String} built inside a helper method and
 *     returned to the caller is invisible to this check.</li>
 *     <li>Reassignment tracking for locals is branch-sensitive across a single {@code if}/{@code
 *     else} (a variable unsafe after either branch stays unsafe after the statement), but not
 *     across loops, {@code switch}, or {@code try}/{@code catch} - and is last-write-wins for
 *     fields, which are not branch-sensitive at all.</li>
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
     * What a tracked local variable currently holds, from this check's point of view.
     */
    private enum Origin {
        /** Not derived from an interpolated GString or unsafe concatenation - nothing to track. */
        NONE,
        /** Still a real {@link groovy.lang.GString} - safe if passed directly to a query method. */
        LIVE_GSTRING,
        /** Already coerced to a plain {@code String} - unsafe if it reaches a query method. */
        FLATTENED,
        /** Built via {@code +} concatenation of a non-constant value, no GString involved. */
        CONCATENATED
    }

    /**
     * What kind of unsafe query argument was found at a candidate call site, and therefore how
     * severely (and with what message) to report it.
     */
    private enum Finding {
        NONE,
        FLATTENED_GSTRING,
        FLATTENED_FIELD,
        UNSAFE_CONCATENATION
    }

    /**
     * The {@code @SuppressWarnings} value that silences this check (both the error and the two
     * warnings below) on the enclosing method (or, for calls outside any method, the enclosing
     * class).
     */
    public static final String SUPPRESS_WARNINGS_VALUE = "GormUnsafeQueryString";

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
    private final Map<String, ASTNode> flattenedStringVars = new HashMap<>();
    private final Map<String, ASTNode> liveGStringVars = new HashMap<>();
    private final Map<String, ASTNode> concatenatedStringVars = new HashMap<>();
    private final Map<String, ASTNode> flattenedFields = new HashMap<>();
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
            // Pre-scan field initializers so a field flattened here is already tracked no matter
            // which order the base class visits fields vs. methods in.
            for (FieldNode field : node.getFields()) {
                trackFieldInitializer(field);
            }
            super.visitClass(node);
        } finally {
            this.currentClassNode = null;
            clearTracking();
            flattenedFields.clear();
        }
    }

    @Override
    public void visitMethod(MethodNode node) {
        this.currentMethodNode = node;
        try {
            super.visitMethod(node);
        } finally {
            this.currentMethodNode = null;
            clearTracking();
        }
    }

    private void clearTracking() {
        flattenedStringVars.clear();
        liveGStringVars.clear();
        concatenatedStringVars.clear();
    }

    @Override
    public void visitDeclarationExpression(DeclarationExpression expression) {
        // getVariableExpression() is null for multiple-assignment declarations, e.g. def (a, b) = [...]
        VariableExpression variableExpression = expression.isMultipleAssignmentDeclaration() ?
                null : expression.getVariableExpression();
        if (variableExpression != null) {
            track(variableExpression.getName(), expression.getRightExpression(), variableExpression.getType(), expression);
        }
        super.visitDeclarationExpression(expression);
    }

    @Override
    public void visitBinaryExpression(BinaryExpression expression) {
        if (expression.getOperation().getType() == Types.ASSIGN) {
            Expression left = expression.getLeftExpression();
            if (left instanceof VariableExpression) {
                VariableExpression leftVariable = (VariableExpression) left;
                track(leftVariable.getName(), expression.getRightExpression(), leftVariable.getType(), expression);
            }
            else if (isThisFieldReference(left)) {
                trackField(fieldNameOf(left), expression.getRightExpression(), expression);
            }
        }
        super.visitBinaryExpression(expression);
    }

    /**
     * Visits an {@code if}/{@code else} branch-sensitively: each branch is walked from the same
     * starting state, and the two resulting states are merged pessimistically afterwards - a
     * variable unsafe at the end of either branch stays unsafe after the statement, since we
     * don't know at compile time which branch will actually run. Without this, whichever branch
     * happens to be visited last would silently win, e.g. a variable flattened only in the
     * {@code if} branch would be forgotten if the {@code else} branch reassigns it safely.
     */
    @Override
    public void visitIfElse(IfStatement ifElse) {
        ifElse.getBooleanExpression().visit(this);

        TrackingSnapshot beforeBranches = snapshot();

        ifElse.getIfBlock().visit(this);
        TrackingSnapshot afterIf = snapshot();

        restore(beforeBranches);
        ifElse.getElseBlock().visit(this);
        TrackingSnapshot afterElse = snapshot();

        restore(mergePessimistically(afterIf, afterElse));
    }

    private TrackingSnapshot snapshot() {
        return new TrackingSnapshot(flattenedStringVars, liveGStringVars, concatenatedStringVars);
    }

    private void restore(TrackingSnapshot state) {
        flattenedStringVars.clear();
        flattenedStringVars.putAll(state.flattened);
        liveGStringVars.clear();
        liveGStringVars.putAll(state.live);
        concatenatedStringVars.clear();
        concatenatedStringVars.putAll(state.concatenated);
    }

    private TrackingSnapshot mergePessimistically(TrackingSnapshot a, TrackingSnapshot b) {
        Set<String> names = new HashSet<>();
        names.addAll(a.flattened.keySet());
        names.addAll(a.live.keySet());
        names.addAll(a.concatenated.keySet());
        names.addAll(b.flattened.keySet());
        names.addAll(b.live.keySet());
        names.addAll(b.concatenated.keySet());

        Map<String, ASTNode> mergedFlattened = new HashMap<>();
        Map<String, ASTNode> mergedLive = new HashMap<>();
        Map<String, ASTNode> mergedConcatenated = new HashMap<>();

        for (String name : names) {
            // Unsafe states win pessimistically: if either branch leaves this variable unsafe,
            // that state survives past the if/else regardless of which branch actually runs.
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
        return new TrackingSnapshot(mergedFlattened, mergedLive, mergedConcatenated);
    }

    private static final class TrackingSnapshot {

        final Map<String, ASTNode> flattened;
        final Map<String, ASTNode> live;
        final Map<String, ASTNode> concatenated;

        TrackingSnapshot(Map<String, ASTNode> flattened, Map<String, ASTNode> live, Map<String, ASTNode> concatenated) {
            this.flattened = new HashMap<>(flattened);
            this.live = new HashMap<>(live);
            this.concatenated = new HashMap<>(concatenated);
        }
    }

    /**
     * Records what {@code variableName} now holds after being assigned {@code rightExpression},
     * resolving through any variable aliasing so a {@code GString} tracked several assignments
     * earlier is still recognised as unsafe once it (or an alias of it) reaches a
     * {@code String}-typed variable.
     */
    private void track(String variableName, Expression rightExpression, ClassNode declaredType, ASTNode locationNode) {
        Origin origin = classify(rightExpression, declaredType);
        // Any reassignment first clears prior tracking under all three categories - last write
        // wins for what follows, then the switch below re-establishes tracking if still unsafe.
        flattenedStringVars.remove(variableName);
        liveGStringVars.remove(variableName);
        concatenatedStringVars.remove(variableName);
        switch (origin) {
            case FLATTENED:
                flattenedStringVars.put(variableName, locationNode);
                break;
            case LIVE_GSTRING:
                liveGStringVars.put(variableName, locationNode);
                break;
            case CONCATENATED:
                concatenatedStringVars.put(variableName, locationNode);
                break;
            case NONE:
            default:
                break;
        }
    }

    /**
     * Determines what {@code expression} evaluates to, from this check's point of view, resolving
     * one level of variable reference against the current tracking state so aliasing chains
     * (however many hops long) are followed correctly - each hop was itself already classified
     * when its own assignment was visited.
     */
    private Origin classify(Expression expression, ClassNode declaredType) {
        if (expression instanceof VariableExpression) {
            String name = ((VariableExpression) expression).getName();
            if (flattenedStringVars.containsKey(name)) {
                return Origin.FLATTENED; // already a plain String - stays unsafe regardless of declaredType
            }
            if (concatenatedStringVars.containsKey(name)) {
                return Origin.CONCATENATED; // already a plain String - stays unsafe regardless of declaredType
            }
            if (liveGStringVars.containsKey(name)) {
                return ClassHelper.STRING_TYPE.equals(declaredType) ? Origin.FLATTENED : Origin.LIVE_GSTRING;
            }
            return Origin.NONE;
        }
        if (isInterpolatedGString(expression)) {
            return ClassHelper.STRING_TYPE.equals(declaredType) ? Origin.FLATTENED : Origin.LIVE_GSTRING;
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
     * Classifies a {@code +} concatenation. One built from a {@link GStringExpression} anywhere in
     * the tree is {@link Origin#FLATTENED}, not merely {@link Origin#CONCATENATED} - concatenating
     * a GString with anything else immediately converts it to a plain {@code String} at runtime
     * (Groovy's {@code GString.plus} returns {@code String}), the same irreversible coercion a
     * {@code .toString()} call causes. A concatenation with no GString at all, but at least one
     * non-constant operand, is the lower-confidence {@link Origin#CONCATENATED} case.
     */
    private Origin classifyConcatenation(Expression expression) {
        if (!isConcatenation(expression)) {
            return Origin.NONE;
        }
        if (containsGString(expression)) {
            return Origin.FLATTENED;
        }
        if (hasNonConstantOperand(expression)) {
            return Origin.CONCATENATED;
        }
        return Origin.NONE;
    }

    /**
     * True when {@code expression} is itself a live GString, or a variable reference already
     * tracked as a live GString or an already-flattened String - i.e. anything a cast or
     * {@code .toString()} applied on top of would still be unsafe to hand to a query method.
     */
    private boolean isUnsafeSource(Expression expression) {
        if (isInterpolatedGString(expression)) {
            return true;
        }
        if (expression instanceof VariableExpression) {
            String name = ((VariableExpression) expression).getName();
            return flattenedStringVars.containsKey(name) || liveGStringVars.containsKey(name);
        }
        return false;
    }

    private boolean isInterpolatedGString(Expression expression) {
        return expression instanceof GStringExpression && !((GStringExpression) expression).getValues().isEmpty();
    }

    private boolean isConcatenation(Expression expression) {
        return expression instanceof BinaryExpression &&
                ((BinaryExpression) expression).getOperation().getType() == Types.PLUS;
    }

    private boolean hasNonConstantOperand(Expression expression) {
        if (expression instanceof ConstantExpression) {
            return false;
        }
        if (isConcatenation(expression)) {
            BinaryExpression binary = (BinaryExpression) expression;
            return hasNonConstantOperand(binary.getLeftExpression()) || hasNonConstantOperand(binary.getRightExpression());
        }
        return true;
    }

    private boolean containsGString(Expression expression) {
        if (expression instanceof GStringExpression) {
            return true;
        }
        if (expression instanceof BinaryExpression) {
            BinaryExpression binary = (BinaryExpression) expression;
            return containsGString(binary.getLeftExpression()) || containsGString(binary.getRightExpression());
        }
        return false;
    }

    /**
     * Seeds {@link #flattenedFields} from a field's own initializer, e.g.
     * {@code String query = "...${x}..."} declared directly on the class. Unlike local tracking,
     * this only recognises a bare interpolated GString initializer - not a {@code .toString()} or
     * cast coercion - to keep the (already lower-confidence) field check simple.
     */
    private void trackFieldInitializer(FieldNode field) {
        Expression initial = field.getInitialValueExpression();
        if (initial != null && isInterpolatedGString(initial) && ClassHelper.STRING_TYPE.equals(field.getType())) {
            flattenedFields.put(field.getName(), field);
        }
    }

    /**
     * Records a {@code this.field = ...} assignment. A safe reassignment clears prior tracking
     * for that field - last-write-wins, with no branch-sensitivity (see class Javadoc).
     */
    private void trackField(String fieldName, Expression rightExpression, ASTNode locationNode) {
        ClassNode fieldType = fieldDeclaredType(fieldName);
        if (isInterpolatedGString(rightExpression) && ClassHelper.STRING_TYPE.equals(fieldType)) {
            flattenedFields.put(fieldName, locationNode);
        }
        else {
            flattenedFields.remove(fieldName);
        }
    }

    private ClassNode fieldDeclaredType(String fieldName) {
        if (currentClassNode == null) {
            return null;
        }
        FieldNode field = currentClassNode.getField(fieldName);
        return field != null ? field.getType() : null;
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
                isGormReceiver(call.getObjectExpression()) && !isSuppressed()) {
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

    private boolean isGormReceiver(Expression objectExpression) {
        if (objectExpression instanceof ClassExpression) {
            return AstUtils.isDomainClass(((ClassExpression) objectExpression).getType());
        }
        if (objectExpression instanceof VariableExpression && ((VariableExpression) objectExpression).isThisExpression()) {
            return currentClassNode != null && AstUtils.isDomainClass(currentClassNode);
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
        Expression argument = args.get(index);

        if (argument instanceof VariableExpression) {
            String name = ((VariableExpression) argument).getName();
            if (flattenedStringVars.containsKey(name)) {
                return Finding.FLATTENED_GSTRING;
            }
            if (concatenatedStringVars.containsKey(name)) {
                return Finding.UNSAFE_CONCATENATION;
            }
            return Finding.NONE;
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

    private boolean isSuppressed() {
        if (currentMethodNode != null && isSuppressedNode(currentMethodNode)) {
            return true;
        }
        return currentClassNode != null && isSuppressedNode(currentClassNode);
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
                "risk. Keep the value as a GString when calling '" + methodName + "' (GORM turns " +
                "GString interpolations into bound query parameters automatically), or pass " +
                "named/positional parameters explicitly. To suppress this check for a reviewed, " +
                "safe call site, add @SuppressWarnings(\"" + SUPPRESS_WARNINGS_VALUE + "\") to the " +
                "enclosing method.";
        sourceUnit.getErrorCollector().addErrorAndContinue(message, node, sourceUnit);
    }

    private void reportFlattenedFieldQuery(ASTNode node, String methodName) {
        String message = "[GORM] The query string passed to '" + methodName + "' was built from a " +
                "field that was assigned a GString-interpolated value coerced to a plain String, " +
                "so any interpolated values may be embedded as raw, unescaped text - this is a " +
                "query injection risk if that field can be influenced by user input. This is a " +
                "warning rather than a build failure because field assignments outside this method " +
                "(other methods, constructors, subclasses) aren't visible to this check. Prefer " +
                "keeping the value as a GString or passing named/positional parameters; to " +
                "suppress, add @SuppressWarnings(\"" + SUPPRESS_WARNINGS_VALUE + "\") to the " +
                "enclosing method.";
        reportWarning(node, message);
    }

    private void reportUnsafeConcatenation(ASTNode node, String methodName) {
        String message = "[GORM] The query string passed to '" + methodName + "' is built using " +
                "'+' string concatenation with a non-constant value, so no automatic parameter " +
                "binding is possible - this is a query injection risk if that value can be " +
                "influenced by user input. Prefer a GString (GORM binds interpolated values " +
                "automatically) or named/positional parameters. To suppress, add " +
                "@SuppressWarnings(\"" + SUPPRESS_WARNINGS_VALUE + "\") to the enclosing method.";
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
