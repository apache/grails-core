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
package grails.gsp.taglib.compiler;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import groovy.lang.GroovySystem;
import groovy.lang.MetaMethod;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassCodeExpressionTransformer;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ConstructorNode;
import org.codehaus.groovy.ast.DynamicVariable;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.Variable;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.ast.expr.TupleExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.SourceUnit;

import org.grails.compiler.injection.GrailsASTUtils;
import org.grails.taglib.CompiledTagInvocation;
import org.grails.taglib.discovery.TagLibraryAstDiscovery;
import org.grails.taglib.index.TagLibraryIndex;

/**
 * Rewrites a call to a known tag into a direct invocation.
 *
 * <p>Writing {@code g.message(code: 'x')} reaches the tag library through {@code propertyMissing} to
 * find the namespace and {@code invokeMethod} to find the tag, which is a dynamic call site even in a
 * statically compiled class. Both the namespace and the tag name are fixed in the source, and the tag
 * library index says whether that tag exists, so the call is replaced with a call to
 * {@link CompiledTagInvocation}, an ordinary static method call.
 *
 * <p>The tag is still selected by name at runtime, through the same lookup the dynamic path uses, so
 * a tag library registered later, one that overrides another, and the order tag libraries are
 * registered in all decide the outcome exactly as they did before. Nothing is bound to a particular
 * tag library class.
 *
 * <p>A namespace the index does not know is left alone, which is what keeps a tag library registered
 * at runtime working, as is a name that something else in scope already answers to.
 *
 * @since 8.0.0
 */
public class CompiledTagCallRewriter extends ClassCodeExpressionTransformer {

    private static final ClassNode INVOCATION_TYPE = ClassHelper.make(CompiledTagInvocation.class);
    private static final String LOOKUP_ACCESSOR = "getTagLibraryLookup";
    private static final String OUTPUT_CONTEXT_ACCESSOR = "getOutputContext";
    private static final String INVOKE = "invoke";
    private static final String INVOKE_ARGUMENTS = "invokeArguments";
    private static final String INVOKE_ARGUMENTS_IN_CONTEXT = "invokeArgumentsInContext";
    private static final String GROOVY_PAGE_TYPE = "org.grails.gsp.GroovyPage";
    private static final String COMPILE_STATIC_TYPE = "groovy.transform.CompileStatic";
    private static final String GRAILS_COMPILE_STATIC_TYPE = "grails.compiler.GrailsCompileStatic";
    private static final String MARKUP_TAG_CALL = "invokeTag";
    private static final String DEFAULT_NAMESPACE = "g";

    /**
     * Names an unqualified call never reaches a tag through, however the index reads.
     *
     * <p>Two kinds. {@code body} and {@code render} the dispatch treats as its own before it ever
     * considers a tag. The rest is every name the metaclass answers to for an arbitrary receiver —
     * {@code DefaultGroovyMethods} and any extension module on the compiler's classpath. Those are
     * real methods on every object: an unqualified {@code each { }} or {@code with { }} reached one
     * directly and never went near {@code methodMissing}, so a tag library declaring a tag of the same
     * name must not capture the call. Nothing here restricts a call that names its namespace, where
     * the source has said which tag library it means.
     */
    private static final Set<String> RESERVED_NAMES = reservedNames();

    private static final String REWRITTEN_MARKER = CompiledTagCallRewriter.class.getName();

    private final SourceUnit sourceUnit;
    private final TagLibraryIndex index;
    private final ClassNode classNode;
    private final String callerNamespace;
    private final boolean page;
    private final boolean rewritingPermitted;
    private Set<String> localNames = Collections.emptySet();
    /**
     * How many closures enclose the expression being transformed. An unqualified call inside
     * one may belong to the closure's delegate, which is only known when it runs.
     */
    private int closureDepth;
    private Set<String> pageBindings = Collections.emptySet();
    private int rewritten;

    public CompiledTagCallRewriter(SourceUnit sourceUnit, TagLibraryIndex index, ClassNode classNode) {
        this.sourceUnit = sourceUnit;
        this.index = index;
        this.classNode = classNode;
        this.page = isGroovyPage(classNode);
        // A page resolves a name against the model it was rendered with before it reaches a tag
        // library, and that model is not visible here, so rewriting a page's tag call can only be
        // sound where the page has given up dynamic resolution. Declaring compileStatic is that: it
        // reserves the namespace names for tag libraries. A page that has not declared it keeps
        // resolving its tags exactly as before.
        this.rewritingPermitted = !this.page || isCompileStatic(classNode);
        // An unqualified call is offered to the caller's own namespace before the default one, which is
        // what a tag library declaring a namespace does at runtime. A page and a controller have no
        // namespace of their own, so for them the two are the same.
        String declared = this.page ? DEFAULT_NAMESPACE : TagLibraryAstDiscovery.resolveNamespace(classNode);
        this.callerNamespace = declared != null ? declared : DEFAULT_NAMESPACE;
    }

    /**
     * @return how many calls were rewritten, for tests to assert against
     */
    public int getRewrittenCount() {
        return rewritten;
    }

    public void rewrite() {
        // A tag library is reached both as an artefact and as a class carrying the invoker trait, so
        // rewriting can be asked for twice. Rewriting again would be harmless, but reporting a
        // misspelled tag twice would not be.
        if (classNode.getNodeMetaData(REWRITTEN_MARKER) != null) {
            return;
        }
        classNode.putNodeMetaData(REWRITTEN_MARKER, Boolean.TRUE);
        if (page) {
            pageBindings = PageBindingCollector.collect(classNode);
        }
        for (MethodNode method : classNode.getMethods()) {
            // getMethods() reaches inherited methods, whose bodies belong to the class that declared
            // them. Rewriting one here would change a superclass through a subclass that happens to be
            // able to call tags. Trait methods are woven as declarations on this class and so remain.
            if (method.getDeclaringClass() != null && !classNode.equals(method.getDeclaringClass())) {
                continue;
            }
            if (method.getCode() != null && !method.isAbstract()) {
                rewriteBody(method.getCode(), method.getParameters());
            }
        }
        for (ConstructorNode constructor : classNode.getDeclaredConstructors()) {
            if (constructor.getCode() != null) {
                rewriteBody(constructor.getCode(), constructor.getParameters());
            }
        }
        for (FieldNode field : classNode.getFields()) {
            if (field.getDeclaringClass() != null && !classNode.equals(field.getDeclaringClass())) {
                continue;
            }
            Expression initial = field.getInitialExpression();
            if (initial != null) {
                localNames = Collections.emptySet();
                field.setInitialValueExpression(transform(initial));
            }
        }
        for (Statement statement : classNode.getObjectInitializerStatements()) {
            rewriteBody(statement, null);
        }
    }

    private void rewriteBody(Statement code, Parameter[] parameters) {
        // An unqualified call reaches a tag only when nothing nearer answers to the name, and a local
        // holding a closure answers to it. Which locals are in scope at a given point is not tracked
        // here: a name declared anywhere in the body is treated as claimed throughout it, which can
        // leave a call dispatched dynamically but never sends one to the wrong place.
        localNames = LocalNameCollector.collect(code, parameters);
        visitClassCodeContainer(code);
    }

    @Override
    protected SourceUnit getSourceUnit() {
        return sourceUnit;
    }

    @Override
    public Expression transform(Expression expression) {
        if (expression instanceof ClosureExpression closure) {
            // ClassCodeExpressionTransformer deliberately does not descend into closures, and documents
            // this override as the way to reach them. Without it a tag call written in a tag body, in a
            // withFormat block, or in anything else taking a closure is never resolved - which is most
            // of the tag calls in a real tag library.
            //
            // A call written with its namespace still says which tag library it means, so it is
            // resolved here as anywhere else. One written without a namespace is not: a closure is
            // given a delegate when it runs, and a name the delegate answers to is that delegate's,
            // not a tag. request.withFormat { form multipartForm { } } is the case that proves it -
            // form there is a format in a DSL, and rewriting it into g:form sends the call somewhere
            // the author never wrote.
            closureDepth++;
            try {
                closure.visit(this);
            }
            finally {
                closureDepth--;
            }
            return closure;
        }
        if (expression instanceof MethodCallExpression call) {
            Expression rewrite = rewriteTagCall(call);
            if (rewrite != null) {
                rewritten++;
                return rewrite;
            }
            validateMarkupTagCall(call);
        }
        return super.transform(expression);
    }

    private Expression rewriteTagCall(MethodCallExpression call) {
        if (!(call.getMethod() instanceof ConstantExpression methodName) ||
                methodName.getValue() == null) {
            return null;
        }
        String tagName = methodName.getValue().toString();
        String namespace = namespaceOf(call.getObjectExpression());
        if (namespace != null) {
            // A namespace the build declared as filled in at runtime is left alone entirely: that
            // declaration is how an application says its tags are decided while it runs, whether by a
            // tag library registered then or by metaprogramming, and binding a call now would settle
            // what it asked to keep open.
            if (index.isDynamicNamespace(namespace)) {
                return null;
            }
            if (!index.hasNamespace(namespace) || isShadowed(call.getObjectExpression(), namespace) ||
                    pageBindings.contains(namespace)) {
                return null;
            }
            if (!index.isKnown(namespace, tagName)) {
                // Only where the name means a tag library for certain. In a page that has not given up
                // dynamic resolution the receiver may just as well be the model it was rendered with,
                // and reporting there would reject a call this release deliberately still allows.
                if (this.rewritingPermitted) {
                    reportUnknownTag(namespace, tagName, call);
                }
                return null;
            }
        }
        else {
            namespace = unqualifiedNamespaceOf(call, tagName);
            if (namespace == null) {
                return null;
            }
        }
        if (!this.rewritingPermitted) {
            return null;
        }
        Expression invocation = invocation(namespace, tagName, call.getArguments());
        if (invocation != null) {
            // Kept where the tag was written, so a stack trace and any later diagnostic still point at
            // the line the author wrote rather than at the start of the file.
            setSourcePosition(invocation, call);
        }
        return invocation;
    }

    /**
     * The namespace an unqualified call such as {@code message(code: 'x')} resolves in.
     *
     * <p>Only a name nothing else answers to reaches a tag at all: a real method of the class, an
     * inherited one, a field, a property or a local wins, and whether such a member exists is what
     * decides the call. Where nothing claims the name, dispatch offers it to the caller's own
     * namespace and then to the default one, which is the order reproduced here.
     *
     * @return the namespace to invoke in, or {@code null} when the call is not resolvably a tag
     */
    private String unqualifiedNamespaceOf(MethodCallExpression call, String tagName) {
        if (page) {
            // A page resolves an unqualified name against its binding before it reaches a tag, and what
            // a page's binding holds - the model it was rendered with - is not visible here. A call
            // written with its namespace says which tag library it means and is rewritten; one without
            // is left to resolve as it did.
            return null;
        }
        if (!index.rewritesUnqualifiedCalls()) {
            // A bare name is only a tag when nothing nearer answers to it, and what answers to it is
            // not fully knowable here: a method Groovy gives every object, a delegate the enclosing
            // closure is handed at runtime, an overload the tag library also declares. Each of those
            // is excluded below as far as the source shows it, but the compiler cannot see all of
            // them, so this is off unless the build asks for it.
            return null;
        }
        if (!call.isImplicitThis() || RESERVED_NAMES.contains(tagName)) {
            return null;
        }
        if (closureDepth > 0) {
            // Inside a closure the name may be answered by whatever delegate the closure is given.
            return null;
        }
        if (declaresMember(tagName) || localNames.contains(tagName)) {
            return null;
        }
        if (index.isDynamicNamespace(callerNamespace) || index.isDynamicNamespace(DEFAULT_NAMESPACE)) {
            // The namespaces an unqualified call could reach were declared as decided at runtime.
            return null;
        }
        if (index.isKnown(callerNamespace, tagName)) {
            return callerNamespace;
        }
        if (index.isKnown(DEFAULT_NAMESPACE, tagName)) {
            return DEFAULT_NAMESPACE;
        }
        // Not a tag this build knows about. It is not reported: an unqualified name in a controller is
        // as likely to be a dynamic finder, an injected service method or anything else contributed at
        // runtime as it is a misspelled tag.
        return null;
    }

    /**
     * Builds the invocation, passing the attributes and body directly where the source says what they
     * are and forwarding the arguments as written where it does not.
     */
    private Expression invocation(String namespace, String tagName, Expression arguments) {
        if (!(arguments instanceof TupleExpression tuple)) {
            return null;
        }
        ArgumentListExpression invocationArgs = new ArgumentListExpression();
        invocationArgs.addExpression(new MethodCallExpression(new VariableExpression("this"),
                LOOKUP_ACCESSOR, MethodCallExpression.NO_ARGUMENTS));
        invocationArgs.addExpression(new ConstantExpression(namespace));
        invocationArgs.addExpression(new ConstantExpression(tagName));

        Expression[] attrsAndBody = attributesAndBody(tuple);
        if (attrsAndBody != null) {
            invocationArgs.addExpression(attrsAndBody[0]);
            invocationArgs.addExpression(attrsAndBody[1]);
            if (page) {
                invocationArgs.addExpression(outputContext());
            }
            return new StaticMethodCallExpression(INVOCATION_TYPE, INVOKE, invocationArgs);
        }

        // The shape is only known once the arguments have been evaluated - a map held in a variable, a
        // single value the tag reads under its own name, and so on - so they are forwarded as written
        // and sorted out by the same rules the dynamic path applies.
        //
        // Only where those rules can actually sort them out. The invocation understands no arguments,
        // one argument, and two whose first is a Map; anything else it reduces to a call with no
        // attributes and no body, silently dropping what was written. A name can be both a tag and an
        // ordinary overload - a tag foo(Map) beside a helper foo(String, String) - and such a call
        // used to reach the overload. Forwarding it here would run the tag with nothing instead, so a
        // shape this cannot account for is left to be dispatched as it was.
        if (!forwardableShape(tuple)) {
            return null;
        }
        if (page) {
            invocationArgs.addExpression(outputContext());
        }
        for (Expression argument : tuple.getExpressions()) {
            invocationArgs.addExpression(transform(argument));
        }
        return new StaticMethodCallExpression(INVOCATION_TYPE,
                page ? INVOKE_ARGUMENTS_IN_CONTEXT : INVOKE_ARGUMENTS, invocationArgs);
    }

    /**
     * @param tuple the arguments as written
     * @return whether forwarding them reaches the same tag the dynamic path would have reached
     */
    private static boolean forwardableShape(TupleExpression tuple) {
        List<Expression> args = tuple.getExpressions();
        switch (args.size()) {
            case 0:
            case 1:
                // Every one-argument shape is accounted for: a Map is the attributes, a Closure or a
                // CharSequence is the body, anything else is a value read under the tag's own name.
                return true;
            case 2:
                // Two arguments are attributes and a body only when the first really is a Map. Where
                // that is not evident here it is not evident to the invocation either.
                return args.get(0) instanceof MapExpression;
            default:
                return false;
        }
    }

    private Expression outputContext() {
        return new MethodCallExpression(new VariableExpression("this"), OUTPUT_CONTEXT_ACCESSOR,
                MethodCallExpression.NO_ARGUMENTS);
    }

    /**
     * @return the attributes and body to pass, or {@code null} when the source does not say what they
     *         are and the arguments have to be forwarded instead
     */
    private Expression[] attributesAndBody(TupleExpression tuple) {
        List<Expression> args = tuple.getExpressions();
        Expression noAttributes = new MapExpression();
        Expression noBody = new ConstantExpression(null);
        switch (args.size()) {
            case 0:
                return new Expression[] { noAttributes, noBody };
            case 1:
                if (args.get(0) instanceof MapExpression) {
                    return new Expression[] { transform(args.get(0)), noBody };
                }
                if (args.get(0) instanceof ClosureExpression) {
                    return new Expression[] { noAttributes, transform(args.get(0)) };
                }
                return null;
            case 2:
                if (args.get(0) instanceof MapExpression && args.get(1) instanceof ClosureExpression) {
                    return new Expression[] { transform(args.get(0)), transform(args.get(1)) };
                }
                return null;
            default:
                return null;
        }
    }

    /**
     * Checks a tag written as markup, which a page compiles into a call naming the tag and namespace
     * directly. Such a call is already an ordinary method call and needs no rewriting, but the names in
     * it are worth the same check as the ones written in an expression.
     */
    private void validateMarkupTagCall(MethodCallExpression call) {
        if (!page || !MARKUP_TAG_CALL.equals(call.getMethodAsString()) ||
                !(call.getArguments() instanceof TupleExpression tuple) ||
                tuple.getExpressions().size() < 2) {
            return;
        }
        if (!(tuple.getExpression(0) instanceof ConstantExpression tagName) ||
                !(tuple.getExpression(1) instanceof ConstantExpression namespace) ||
                tagName.getValue() == null || namespace.getValue() == null) {
            return;
        }
        String namespaceName = namespace.getValue().toString();
        String tag = tagName.getValue().toString();
        if (index.isDynamicNamespace(namespaceName)) {
            return;
        }
        if (index.hasNamespace(namespaceName) && !index.isKnown(namespaceName, tag)) {
            reportUnknownTag(namespaceName, tag, call);
        }
    }

    /**
     * Reports a tag that no compiled tag library declares.
     *
     * <p>Silent unless the build declared its tag libraries complete. A namespace holding some
     * compiled tag libraries is not the same as one holding all of them: a plugin built before
     * descriptors existed contributes tags to {@code g} without one, and a tag library registered
     * while an application runs contributes more. Reporting by default would mean warning about calls
     * that are perfectly correct - this framework calls one such tag itself - so a build says when it
     * knows better.
     */
    private void reportUnknownTag(String namespace, String tagName, Expression call) {
        if (!index.isStrict() || index.isDynamicNamespace(namespace)) {
            return;
        }
        if (!index.declaresNamespace(namespace)) {
            // A namespace this project does not declare is filled in by tag libraries from elsewhere,
            // and how many of them carry descriptors is not knowable here. Reporting a tag missing
            // from such a namespace would fail a build over a plugin's perfectly good tag - which is
            // what made strict checking unusable for g, the namespace it would matter most for.
            return;
        }
        if (!index.isNamespaceComplete(namespace)) {
            // Something contributing to this namespace could not be described. A tag missing from it
            // is as likely to be one of those as a misspelling, and reporting it would fail a build
            // over code that is correct.
            return;
        }
        String message = "No such tag [" + tagName + "] in namespace [" + namespace + "]. Known tags: " +
                String.join(", ", index.getTagNames(namespace));
        // Collected rather than fatal, so that every misspelling in a file is reported at once instead
        // of one per build.
        GrailsASTUtils.error(sourceUnit, call, message, false);
    }

    /**
     * @return the namespace a call is made through, or {@code null} when the receiver is not a plain
     *         name that could be one
     */
    private static String namespaceOf(Expression objectExpression) {
        if (objectExpression instanceof VariableExpression variable) {
            return variable.isThisExpression() || variable.isSuperExpression() ? null : variable.getName();
        }
        if (objectExpression instanceof PropertyExpression property &&
                property.getObjectExpression() instanceof VariableExpression receiver &&
                receiver.isThisExpression()) {
            return property.getPropertyAsString();
        }
        return null;
    }

    /**
     * Whether something in scope has already claimed the name, in which case it is that thing rather
     * than a tag library namespace.
     *
     * <p>A namespace is not declared anywhere: it is reached because nothing else answers to the name.
     * A local variable, a parameter or a field called {@code g} does answer to it, and rewriting such a
     * call would silently send it to a tag library instead of the object the author wrote.
     */
    private boolean isShadowed(Expression objectExpression, String namespace) {
        if (objectExpression instanceof VariableExpression variable) {
            Variable accessed = variable.getAccessedVariable();
            // A name that resolves to something - a local, a parameter, a field, a property - is that
            // thing. Only a name nothing has claimed is left to mean a namespace.
            if (accessed != null && !(accessed instanceof DynamicVariable)) {
                return true;
            }
        }
        // Reached as this.g, or as a bare name resolved dynamically: a field or property of that name
        // anywhere in the hierarchy is the member, not a namespace.
        return declaresProperty(namespace);
    }

    /**
     * Whether the class, or anything it inherits from, reads a property of this name. A method of the
     * same name does not count: {@code g.link()} reads {@code g} as a property whatever methods exist.
     */
    private boolean declaresProperty(String name) {
        return classNode.getField(name) != null ||
                classNode.getProperty(name) != null ||
                hasGetter(name);
    }

    /**
     * Whether the class, or anything it inherits from, already answers to a name at all. An
     * unqualified call reaches a tag only when nothing else does, so here a method counts too.
     */
    private boolean declaresMember(String name) {
        return declaresProperty(name) || !classNode.getMethods(name).isEmpty();
    }

    /**
     * Whether a getter answers to the name. Groovy reads a property from {@code getX()} and, when the
     * return type is boolean, from {@code isX()} as well, so both forms claim the name.
     */
    private boolean hasGetter(String namespace) {
        String capitalised = Character.toUpperCase(namespace.charAt(0)) + namespace.substring(1);
        if (!classNode.getMethods("get" + capitalised).isEmpty()) {
            return true;
        }
        for (MethodNode candidate : classNode.getMethods("is" + capitalised)) {
            ClassNode returnType = candidate.getReturnType();
            if (returnType != null && (ClassHelper.isPrimitiveBoolean(returnType) ||
                    ClassHelper.isWrapperBoolean(returnType))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Collects the names an unqualified call must never be rewritten into a tag invocation for.
     *
     * <p>The metaclass of {@code Object} answers for every {@code DefaultGroovyMethods} method that
     * applies to any receiver, and for every extension module registered on the classpath compiling
     * this source, so asking it is the same question the runtime would have asked. Erring towards
     * reserving a name costs an optimisation; failing to reserve one silently sends a call somewhere
     * the author did not write.
     */
    private static Set<String> reservedNames() {
        Set<String> names = new HashSet<>();
        names.add("body");
        names.add("render");
        for (MetaMethod method : GroovySystem.getMetaClassRegistry().getMetaClass(Object.class).getMetaMethods()) {
            names.add(method.getName());
        }
        return Set.copyOf(names);
    }

    /**
     * Whether this class is a compiled GSP. Matched by name rather than by type so that rewriting tag
     * calls in a page needs no dependency on the page runtime.
     */
    private static boolean isGroovyPage(ClassNode classNode) {
        for (ClassNode current = classNode.getSuperClass(); current != null;
                current = current.getSuperClass()) {
            if (GROOVY_PAGE_TYPE.equals(current.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the class gave up dynamic resolution. A page compiled with {@code compileStatic="true"}
     * carries the annotation, which is what makes the namespace names mean tag libraries there.
     */
    private static boolean isCompileStatic(ClassNode classNode) {
        for (AnnotationNode annotation : classNode.getAnnotations()) {
            String name = annotation.getClassNode().getName();
            if (COMPILE_STATIC_TYPE.equals(name) || GRAILS_COMPILE_STATIC_TYPE.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
