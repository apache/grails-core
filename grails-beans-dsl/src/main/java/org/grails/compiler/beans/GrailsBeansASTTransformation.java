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
package org.grails.compiler.beans;

import java.beans.Introspector;
import java.io.File;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.SourceVersion;

import groovy.transform.CompilationUnitAware;
import groovy.transform.CompileStatic;
import groovy.transform.TypeChecked;
import org.apache.groovy.util.BeanUtils;
import org.codehaus.groovy.GroovyBugError;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.ast.expr.MapEntryExpression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.EmptyStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.syntax.SyntaxException;
import org.codehaus.groovy.syntax.Types;
import org.codehaus.groovy.transform.ASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.codehaus.groovy.transform.StaticTypesTransformation;
import org.codehaus.groovy.transform.sc.StaticCompileTransformation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScans;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.context.annotation.Scope;

/**
 * Rewrites the {@code beans} closure DSL on a {@link grails.compiler.beans.GrailsBeans}-annotated
 * class into real {@code @Bean} factory methods, at compile time.
 *
 * <p>Recognises three kinds of top-level statement inside the {@code beans} closure:
 * <ul>
 * <li>{@code bean(["name", ] Type) { ... }}, optionally chained with any combination of
 * {@code .conditionalOnMissingBean(...)} (positional types, named annotation attributes, or bare),
 * {@code .conditionalOnMissingBeanName(...)} (backs off by this bean's own name, set
 * automatically), {@code .primary()}, {@code .lazy()},
 * {@code .scope("name")}, {@code .staticMethod()} (a static factory method, for
 * {@code BeanFactoryPostProcessor}/{@code BeanPostProcessor} beans), and (repeatably)
 * {@code .annotate(AnnotationType[, attr: value, ...])}. Synthesises a public method, returning
 * the declared type, annotated {@code @org.springframework.context.annotation.Bean("name")} plus
 * whichever qualifiers were chained. Its parameters are always the DSL closure's own, annotations
 * included. Its body is the closure's, except where that body is empty - or the closure is omitted
 * altogether - in which case a {@code new Type(...)} call over those same parameters is synthesised
 * instead, leaving the compiler to select the constructor from their types exactly as it would for a
 * body written out by hand. The generated method's name is an implementation detail: it matches the
 * bean name when that is a usable Java identifier not already taken by an existing or generated
 * member, and falls back to a synthesized {@code <type>$N} name otherwise (a non-identifier name
 * like {@code "my-service"}, a reserved keyword, or a collision - a bean named {@code toString}
 * never overrides {@code Object.toString()}) - Spring resolves the bean by its {@code @Bean("name")}
 * value either way, never by the method name. One bean name may be declared by several
 * {@code bean(...)} statements when every declaration carries its own discriminating condition
 * (see {@link #validateSharedBeanNames}).</li>
 * <li>{@code field(["name", ] Type)}, optionally chained with {@code .value(...)} (config
 * injection: key + default, a bare key, or a verbatim placeholder/SpEL string) and/or (repeatably)
 * {@code .annotate(AnnotationType[, attr: value, ...])}. Declares a private field on the
 * generated class, for state shared across bean methods.</li>
 * <li>{@code method(["name", ] Type) { ... }}, chainable with {@code .annotate(...)} only
 * ({@code .value(...)} is field-specific).
 * Declares a private helper method on the generated class, for logic shared across bean methods,
 * lifted from the DSL closure the same way {@code bean(...)} is.</li>
 * </ul>
 *
 * <p>Fields and helper methods declared this way are ordinary private members of the generated
 * class - {@code bean(...)} closures reference them the same way a hand-written {@code @Bean}
 * method would reference a sibling field or method on its {@code @Configuration} class. The
 * {@code beans} property itself is removed so no closure survives into the compiled class.
 *
 * <p>When the annotated class extends {@code grails.plugins.Plugin}, the generated members land
 * on a new sibling class instead of on the plugin class itself - named by swapping a
 * {@code *GrailsPlugin} suffix for {@code AutoConfiguration}, or appending
 * {@code AutoConfiguration} otherwise, and placed in the plugin's own package unless
 * {@code @GrailsBeans(autoConfigurationName = ...)} names another. A {@code Plugin} subclass is
 * instantiated by {@code DefaultGrailsPlugin} via plain reflection, never as a Spring bean, so
 * it cannot carry {@code @Bean} methods or a meaningful {@code @AutoConfiguration} annotation
 * of its own.
 * {@code @AutoConfiguration} and every annotation that gates or configures it - the
 * {@code @Conditional*} family, {@code @Import}/{@code @ImportAutoConfiguration}/
 * {@code @ImportResource}, {@code @ComponentScan}, {@code @EnableConfigurationProperties},
 * {@code @PropertySource}/{@code @PropertySources}, and
 * {@code @AutoConfigureOrder}/{@code Before}/{@code After} - found on the plugin class are moved
 * onto the generated sibling, since that is the only place any of them has any effect; annotations
 * outside that set can be named explicitly via {@code @GrailsBeans(moveAnnotations = ...)}. This lets a
 * plugin author keep bean definitions in the familiar {@code *GrailsPlugin.groovy} file while
 * everything else about the plugin class - {@code doWithApplicationContext}, {@code onChange},
 * {@code watchedResources}, etc. - continues to work exactly as it does today.
 *
 * <p>{@code @CompileStatic}/{@code @GrailsCompileStatic} on the plugin class is propagated to the
 * generated sibling. Since the sibling is created after Groovy schedules local annotation
 * transforms, this transformation invokes Groovy's static-compilation transform directly after
 * generating the sibling's members. This is the same approach used by other Grails AST transforms
 * that generate code after local transform discovery.
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class GrailsBeansASTTransformation implements ASTTransformation, CompilationUnitAware {

    /**
     * Class-node metadata carrying the compilation's output directory, seeded by the global Grails
     * transform. It resolves the directory for Groovy-Eclipse, where the compiler configuration
     * either has none or has one relative to the Eclipse project, and that resolution lives in
     * grails-core - which this module cannot depend on.
     */
    public static final String RESOLVED_TARGET_DIRECTORY_METADATA =
            GrailsBeansASTTransformation.class.getName() + ".resolvedTargetDirectory";

    private static final String BEANS_PROPERTY = "beans";
    private static final String BEAN_CALL = "bean";
    private static final String FIELD_CALL = "field";
    private static final String METHOD_CALL = "method";
    private static final Set<String> ROOT_STATEMENT_CALL_NAMES = Set.of(BEAN_CALL, FIELD_CALL, METHOD_CALL);
    private static final String CONDITIONAL_ON_MISSING_BEAN_CALL = "conditionalOnMissingBean";
    private static final String CONDITIONAL_ON_MISSING_BEAN_NAME_CALL = "conditionalOnMissingBeanName";
    private static final String PRIMARY_CALL = "primary";
    private static final String LAZY_CALL = "lazy";
    private static final String SCOPE_CALL = "scope";
    private static final String STATIC_METHOD_CALL = "staticMethod";
    private static final String ANNOTATE_CALL = "annotate";
    private static final String VALUE_CALL = "value";
    private static final Set<String> BEAN_QUALIFIER_CALL_NAMES = Set.of(
            CONDITIONAL_ON_MISSING_BEAN_CALL, CONDITIONAL_ON_MISSING_BEAN_NAME_CALL,
            PRIMARY_CALL, LAZY_CALL, SCOPE_CALL, STATIC_METHOD_CALL, ANNOTATE_CALL);
    // field(...) and method(...) declare plain class members, not beans - bean-specific
    // qualifiers don't apply; .value(...) (@Value config injection) is field-only.
    private static final Set<String> FIELD_QUALIFIER_CALL_NAMES = Set.of(ANNOTATE_CALL, VALUE_CALL);
    private static final Set<String> METHOD_QUALIFIER_CALL_NAMES = Set.of(ANNOTATE_CALL);
    private static final Set<String> ALL_QUALIFIER_CALL_NAMES = Set.of(
            CONDITIONAL_ON_MISSING_BEAN_CALL, CONDITIONAL_ON_MISSING_BEAN_NAME_CALL,
            PRIMARY_CALL, LAZY_CALL, SCOPE_CALL, STATIC_METHOD_CALL, ANNOTATE_CALL, VALUE_CALL);
    private static final String PLUGIN_SUPERCLASS_NAME = "grails.plugins.Plugin";
    private static final String GRAILS_PLUGIN_SUFFIX = "GrailsPlugin";
    private static final String AUTO_CONFIGURATION_SUFFIX = "AutoConfiguration";
    private static final String AUTO_CONFIGURATION_NAME_MEMBER = "autoConfigurationName";
    private static final String MOVE_ANNOTATIONS_MEMBER = "moveAnnotations";

    private CompilationUnit compilationUnit;

    @Override
    public void setCompilationUnit(CompilationUnit compilationUnit) {
        this.compilationUnit = compilationUnit;
    }

    @Override
    public void visit(ASTNode[] nodes, SourceUnit source) {
        AnnotationNode grailsBeansAnnotation = (AnnotationNode) nodes[0];
        ClassNode classNode = (ClassNode) nodes[1];
        PropertyNode beansProperty = classNode.getProperty(BEANS_PROPERTY);
        if (beansProperty == null) {
            addError(classNode, source, "@GrailsBeans requires a 'beans' property initialised to a closure");
            return;
        }

        Expression initialExpression = beansProperty.getInitialExpression();
        if (!(initialExpression instanceof ClosureExpression)) {
            addError(beansProperty, source, "'beans' must be initialised to a closure, e.g. beans = { ... }");
            return;
        }

        // An empty block is a no-op, not an error - an empty @Configuration class is legal in Spring
        // and an empty resources.groovy is legal in Grails, so having nothing to declare should not
        // fail the build. Returning before the sibling is created matters: generating it would leave
        // a bean-less class holding the @AutoConfiguration and @Conditional* annotations moved off
        // the plugin, which is worse than doing nothing. Only the DSL scaffolding is stripped.
        List<Statement> statements = beanStatements((ClosureExpression) initialExpression);
        if (statements.isEmpty()) {
            removeBeansProperty(classNode, beansProperty);
            return;
        }

        boolean isPlugin = extendsGrailsPlugin(classNode);
        if (!isPlugin) {
            for (String pluginOnlyMember : new String[] { AUTO_CONFIGURATION_NAME_MEMBER, MOVE_ANNOTATIONS_MEMBER }) {
                if (grailsBeansAnnotation.getMember(pluginOnlyMember) != null) {
                    addError(grailsBeansAnnotation, source, pluginOnlyMember + " has no effect here: it only applies " +
                            "when @GrailsBeans is applied to a grails.plugins.Plugin subclass, where the compiled beans " +
                            "land on a generated sibling class rather than on " + classNode.getNameWithoutPackage() + " itself");
                }
            }
        }

        ClassNode beanMethodHost = isPlugin ?
                createAutoConfigurationSibling(classNode, grailsBeansAnnotation, source) : classNode;

        Set<String> usedNames = existingMemberNames(beanMethodHost);
        validateSharedBeanNames(statements, source);
        // Two passes: field(...)/method(...) declare explicit member names, so they are processed
        // first (along with anything malformed, so every statement is still processed exactly
        // once) and bean(...) statements second. A bean's derived method name then adapts to every
        // explicitly-named member wherever it appears in the block - reordering equivalent DSL
        // statements must never change validity.
        for (Statement statement : statements) {
            if (!isBeanRootedStatement(statement)) {
                processStatement(beanMethodHost, statement, source, usedNames);
            }
        }
        for (Statement statement : statements) {
            if (isBeanRootedStatement(statement)) {
                processStatement(beanMethodHost, statement, source, usedNames);
            }
        }

        if (beanMethodHost != classNode) {
            applyStaticCompilation(classNode, beanMethodHost, source);
        }

        removeBeansProperty(classNode, beansProperty);
    }

    private void removeBeansProperty(ClassNode classNode, PropertyNode beansProperty) {
        classNode.getProperties().remove(beansProperty);
        // removeField, not getFields().remove: the latter leaves ClassNode's own fieldIndex entry
        // behind, so another member still referring to 'beans' type-checks and compiles to a
        // getfield against a field that is never emitted, failing with NoSuchFieldError at runtime.
        classNode.removeField(BEANS_PROPERTY);
    }

    private boolean extendsGrailsPlugin(ClassNode classNode) {
        for (ClassNode current = classNode.getSuperClass(); current != null; current = current.getSuperClass()) {
            if (PLUGIN_SUPERCLASS_NAME.equals(current.getName())) {
                return true;
            }
        }
        return false;
    }

    // Annotations that only make sense on whatever class Spring Boot actually evaluates as an
    // auto-configuration - meaningless on a Plugin subclass, which is instantiated by
    // DefaultGrailsPlugin via plain reflection and never processed by Spring as a bean. Matching
    // is transitive through meta-annotations (see belongsOnSibling), so this list only needs the
    // "root" annotations - a composed annotation built on top of any of these (e.g. a custom
    // @ConditionalOnFeature meta-annotated with Spring Boot's own @ConditionalOnProperty, or a
    // custom @EnableSomething meta-annotated with @Import) is found automatically.
    private static final Set<String> SIBLING_ONLY_ANNOTATION_NAMES = Set.of(
            AutoConfiguration.class.getName(), AutoConfigureOrder.class.getName(),
            AutoConfigureBefore.class.getName(), AutoConfigureAfter.class.getName(),
            Import.class.getName(), ImportAutoConfiguration.class.getName(), ImportResource.class.getName(),
            ComponentScan.class.getName(), ComponentScans.class.getName(),
            EnableConfigurationProperties.class.getName(),
            PropertySource.class.getName(), PropertySources.class.getName(),
            Conditional.class.getName());

    private ClassNode createAutoConfigurationSibling(ClassNode pluginClass, AnnotationNode grailsBeansAnnotation, SourceUnit source) {
        List<AnnotationNode> autoConfigurationAnnotations = pluginClass.getAnnotations(ClassHelper.make(AutoConfiguration.class));
        if (autoConfigurationAnnotations.isEmpty()) {
            addError(pluginClass, source, "A Plugin class using @GrailsBeans must also be annotated " +
                    "@AutoConfiguration (even with no before=/after=) - otherwise the generated " +
                    defaultSiblingSimpleName(pluginClass) +
                    " class would never be processed by Spring Boot");
        }

        String siblingName = siblingName(pluginClass, grailsBeansAnnotation, source);
        ClassNode sibling = new ClassNode(siblingName, Modifier.PUBLIC, ClassHelper.OBJECT_TYPE);
        // Without a position, anything Groovy later reports against a generated node - a sibling
        // name clash, a typo in .annotate(...) - is reported at line -1, column -1.
        sibling.setSourcePosition(pluginClass);
        source.getAST().addClass(sibling);

        // Matching annotations move entirely rather than being merely copied - they have no effect
        // where the author wrote them (see SIBLING_ONLY_ANNOTATION_NAMES).
        Set<String> moveAnnotationNames = parseMoveAnnotations(grailsBeansAnnotation, source);
        List<AnnotationNode> siblingAnnotations = new ArrayList<>();
        for (AnnotationNode annotation : pluginClass.getAnnotations()) {
            if (belongsOnSibling(annotation.getClassNode(), moveAnnotationNames)) {
                siblingAnnotations.add(annotation);
            }
        }
        sibling.addAnnotations(siblingAnnotations);
        pluginClass.getAnnotations().removeAll(siblingAnnotations);

        // The name is settled here and nowhere else, so this is where it is registered.
        AutoConfigurationImportsWriter.register(
                siblingName, targetDirectory(pluginClass, source), source, compilationUnit);

        return sibling;
    }

    /**
     * The compilation's output directory: the one the global transform resolved if it ran, which is
     * the only one that is right under Groovy-Eclipse, and the compiler's own otherwise.
     */
    private static File targetDirectory(ClassNode classNode, SourceUnit source) {
        Object resolved = classNode == null ? null : classNode.getNodeMetaData(RESOLVED_TARGET_DIRECTORY_METADATA);
        if (resolved instanceof File) {
            return (File) resolved;
        }
        CompilerConfiguration configuration = source == null ? null : source.getConfiguration();
        return configuration == null ? null : configuration.getTargetDirectory();
    }

    private Set<String> parseMoveAnnotations(AnnotationNode grailsBeansAnnotation, SourceUnit source) {
        Expression member = grailsBeansAnnotation.getMember(MOVE_ANNOTATIONS_MEMBER);
        if (member == null) {
            return Set.of();
        }
        List<Expression> entries = member instanceof ListExpression ?
                ((ListExpression) member).getExpressions() : List.of(member);
        Set<String> names = new HashSet<>();
        for (Expression entry : entries) {
            if (!(entry instanceof ClassExpression)) {
                addError(entry, source, "moveAnnotations entries must be annotation class literals, " +
                        "e.g. @GrailsBeans(moveAnnotations = [ComponentScan])");
                continue;
            }
            ClassNode annotationType = ((ClassExpression) entry).getType();
            if (!annotationType.isAnnotationDefinition()) {
                addError(entry, source, "\"" + annotationType.getName() + "\" is not an annotation type");
                continue;
            }
            names.add(annotationType.getName());
        }
        return names;
    }

    // A *GrailsPlugin name swaps that suffix for AutoConfiguration (I18nGrailsPlugin ->
    // I18nAutoConfiguration - the name the hand-written class it replaces would have had);
    // anything else appends AutoConfiguration.
    private String defaultSiblingSimpleName(ClassNode pluginClass) {
        String simpleName = pluginClass.getNameWithoutPackage();
        if (simpleName.endsWith(GRAILS_PLUGIN_SUFFIX) && simpleName.length() > GRAILS_PLUGIN_SUFFIX.length()) {
            return simpleName.substring(0, simpleName.length() - GRAILS_PLUGIN_SUFFIX.length()) + AUTO_CONFIGURATION_SUFFIX;
        }
        return simpleName + AUTO_CONFIGURATION_SUFFIX;
    }

    // The name of the generated sibling, qualified. A bare identifier names it in the plugin's own
    // package, which is where it lands by default; a name that is already qualified is taken as
    // written, so a conversion can keep the package of the class it replaces as well as its simple
    // name - the two together being what an exclude= or a before=/after= from another module names.
    private String siblingName(ClassNode pluginClass, AnnotationNode grailsBeansAnnotation, SourceUnit source) {
        String packageName = pluginClass.getPackageName();
        String defaultName = qualify(packageName, defaultSiblingSimpleName(pluginClass));
        Expression nameArg = grailsBeansAnnotation.getMember(AUTO_CONFIGURATION_NAME_MEMBER);
        if (nameArg == null) {
            return defaultName;
        }
        Object nameValue = nameArg instanceof ConstantExpression ? ((ConstantExpression) nameArg).getValue() : null;
        if (!(nameValue instanceof String)) {
            addError(nameArg, source, "@GrailsBeans(autoConfigurationName = ...) requires a String literal");
            return defaultName;
        }
        String name = (String) nameValue;
        if (name.isBlank()) {
            addError(nameArg, source, "@GrailsBeans(autoConfigurationName = \"" + name + "\") must not be " +
                    "blank - omit the attribute entirely to use the default " + defaultName + " instead");
            return defaultName;
        }
        if (!isValidQualifiedName(name)) {
            addError(nameArg, source, "@GrailsBeans(autoConfigurationName = \"" + name + "\") is not a valid " +
                    "name: it becomes the generated sibling's class name, so it must be a valid Java " +
                    "identifier, or a qualified name whose every part is one");
            return defaultName;
        }
        return name.indexOf('.') < 0 ? qualify(packageName, name) : name;
    }

    private static String qualify(String packageName, String simpleName) {
        return (packageName == null || packageName.isEmpty()) ? simpleName : packageName + "." + simpleName;
    }

    private boolean belongsOnSibling(ClassNode annotationType, Set<String> moveAnnotationNames) {
        return belongsOnSibling(annotationType, moveAnnotationNames, new HashSet<>());
    }

    // Recurses through meta-annotations rather than checking only one level, since Spring's own
    // composed-annotation convention is arbitrarily deep - e.g. a project-specific
    // @ConditionalOnFeature is typically meta-annotated with an existing @ConditionalOnXxx (itself
    // meta-annotated @Conditional), not with @Conditional directly. `visited` guards against cycles
    // and, since every annotation type transitively reaches common JDK meta-annotations
    // (@Retention, @Target, @Documented) from multiple paths, avoids redundant re-exploration.
    private boolean belongsOnSibling(ClassNode annotationType, Set<String> moveAnnotationNames, Set<String> visited) {
        if (!visited.add(annotationType.getName())) {
            return false;
        }
        if (SIBLING_ONLY_ANNOTATION_NAMES.contains(annotationType.getName()) ||
                moveAnnotationNames.contains(annotationType.getName())) {
            return true;
        }
        for (AnnotationNode metaAnnotation : annotationType.getAnnotations()) {
            if (belongsOnSibling(metaAnnotation.getClassNode(), moveAnnotationNames, visited)) {
                return true;
            }
        }
        return false;
    }

    // The bean bodies are lifted onto a class the normal transform pipeline no longer visits, so
    // whichever of these the author put on the plugin has to be re-applied here or the bodies are
    // silently left unchecked. @GrailsCompileStatic and @GrailsTypeChecked need no handling of their
    // own: @AnnotationCollector has already expanded them by canonicalization.
    private void applyStaticCompilation(ClassNode pluginClass, ClassNode sibling, SourceUnit source) {
        if (compilationUnit == null) {
            return;
        }
        if (applyStaticTypesTransformation(pluginClass, sibling, source,
                CompileStatic.class, new StaticCompileTransformation())) {
            return;
        }
        applyStaticTypesTransformation(pluginClass, sibling, source,
                TypeChecked.class, new StaticTypesTransformation());
    }

    private boolean applyStaticTypesTransformation(ClassNode pluginClass, ClassNode sibling, SourceUnit source,
            Class<? extends java.lang.annotation.Annotation> annotationType, StaticTypesTransformation transformation) {
        List<AnnotationNode> annotations = pluginClass.getAnnotations(ClassHelper.make(annotationType));
        if (annotations.isEmpty()) {
            return false;
        }

        AnnotationNode sourceAnnotation = annotations.get(0);
        AnnotationNode siblingAnnotation = new AnnotationNode(ClassHelper.make(annotationType));
        sourceAnnotation.getMembers().forEach(siblingAnnotation::setMember);
        siblingAnnotation.setSourcePosition(sourceAnnotation);
        sibling.addAnnotation(siblingAnnotation);

        transformation.setCompilationUnit(compilationUnit);
        transformation.visit(new ASTNode[] { siblingAnnotation, sibling }, source);
        return true;
    }

    private List<Statement> beanStatements(ClosureExpression dsl) {
        Statement code = dsl.getCode();
        if (code instanceof BlockStatement) {
            return ((BlockStatement) code).getStatements();
        }
        List<Statement> single = new ArrayList<>();
        single.add(code);
        return single;
    }

    // Generated names must not collide with anything the host class already has: its own fields
    // and methods (in the standalone form the host is a real user-written class), every method
    // inherited through its full type graph - superclasses and interfaces alike, since a bean
    // named 'toString' or after an interface's default method must synthesize, not override -
    // and the GroovyObject methods Groovy itself adds at class generation.
    private Set<String> existingMemberNames(ClassNode host) {
        Set<String> names = new HashSet<>();
        for (FieldNode field : host.getFields()) {
            names.add(field.getName());
        }
        Set<String> visited = new HashSet<>();
        collectMethodNames(host, names, visited);
        collectMethodNames(ClassHelper.GROOVY_OBJECT_TYPE, names, visited);
        return names;
    }

    private void collectMethodNames(ClassNode type, Set<String> names, Set<String> visited) {
        if (type == null || !visited.add(type.getName())) {
            return;
        }
        for (MethodNode method : type.getMethods()) {
            names.add(method.getName());
        }
        // A Groovy property's accessors are synthesized by the Verifier at class generation, AFTER
        // this transform runs, so they are not in getMethods() yet - reserve the names they will
        // occupy, or a same-named bean method would displace the real accessor.
        for (PropertyNode property : type.getProperties()) {
            String capitalized = BeanUtils.capitalize(property.getName());
            names.add("get" + capitalized);
            names.add("set" + capitalized);
            if (ClassHelper.boolean_TYPE.equals(property.getType()) ||
                    ClassHelper.Boolean_TYPE.equals(property.getType())) {
                names.add("is" + capitalized);
            }
        }
        collectMethodNames(type.getSuperClass(), names, visited);
        for (ClassNode implemented : type.getInterfaces()) {
            collectMethodNames(implemented, names, visited);
        }
    }

    // Silent classification counterpart of processStatement's qualifier-chain walk: descends to
    // the root call without reporting anything, so malformed statements are classified (not
    // validated) here and still produce their usual errors when actually processed.
    private boolean isBeanRootedStatement(Statement statement) {
        if (!(statement instanceof ExpressionStatement) ||
                !(((ExpressionStatement) statement).getExpression() instanceof MethodCallExpression)) {
            return false;
        }
        MethodCallExpression call = (MethodCallExpression) ((ExpressionStatement) statement).getExpression();
        while (!ROOT_STATEMENT_CALL_NAMES.contains(call.getMethodAsString()) &&
                call.getObjectExpression() instanceof MethodCallExpression) {
            call = (MethodCallExpression) call.getObjectExpression();
        }
        return BEAN_CALL.equals(call.getMethodAsString());
    }

    // A Spring bean name may be declared by more than one bean(...) statement - the standard
    // autoconfiguration pattern for mutually exclusive variants of one bean, e.g. Grails' two
    // "grailsUrlConverter" beans selected by @ConditionalOnProperty - but only when every
    // statement sharing the name carries a condition of its own that could discriminate between
    // them at runtime. Without one, the duplicates can never all take effect (Spring keeps the
    // first definition from a configuration class and silently skips the rest), so the likeliest
    // explanation is a copy-paste accident - rejected at compile time instead. The shared-name
    // back-off (.conditionalOnMissingBeanName(), or .conditionalOnMissingBean() with no
    // arguments) does not count: it is identical on every duplicate by construction, so it can
    // never tell them apart.
    private void validateSharedBeanNames(List<Statement> statements, SourceUnit source) {
        Map<String, List<BeanNameUse>> usesByName = new LinkedHashMap<>();
        for (Statement statement : statements) {
            if (!isBeanRootedStatement(statement)) {
                continue;
            }
            BeanNameUse use = parseBeanNameUse(
                    (MethodCallExpression) ((ExpressionStatement) statement).getExpression());
            if (use != null) {
                usesByName.computeIfAbsent(use.beanName, key -> new ArrayList<>()).add(use);
            }
        }
        for (List<BeanNameUse> uses : usesByName.values()) {
            if (uses.size() < 2) {
                continue;
            }
            for (BeanNameUse use : uses) {
                if (!use.conditioned) {
                    addError(use.baseCall, source, "\"" + use.beanName + "\" is already used as the Spring " +
                            "bean name of another bean(...) statement - declaring it more than once is only " +
                            "allowed when every declaration with the name carries its own discriminating " +
                            "condition (e.g. .annotate(ConditionalOnProperty, ...)), so that at most one of " +
                            "them registers at runtime");
                }
            }
        }
    }

    private static final class BeanNameUse {
        private final String beanName;
        private final MethodCallExpression baseCall;
        private final boolean conditioned;

        BeanNameUse(String beanName, MethodCallExpression baseCall, boolean conditioned) {
            this.beanName = beanName;
            this.baseCall = baseCall;
            this.conditioned = conditioned;
        }
    }

    // Silent classification counterpart of processBeanStatement's parsing, in the same spirit as
    // isBeanRootedStatement: extracts the bean name and whether the statement carries a
    // discriminating condition, returning null for anything malformed - a malformed statement
    // still produces its usual errors when actually processed.
    private BeanNameUse parseBeanNameUse(MethodCallExpression outerCall) {
        List<MethodCallExpression> qualifierCalls = new ArrayList<>();
        MethodCallExpression baseCall = outerCall;
        while (!ROOT_STATEMENT_CALL_NAMES.contains(baseCall.getMethodAsString())) {
            if (!(baseCall.getObjectExpression() instanceof MethodCallExpression)) {
                return null;
            }
            qualifierCalls.add(baseCall);
            baseCall = (MethodCallExpression) baseCall.getObjectExpression();
        }
        if (!BEAN_CALL.equals(baseCall.getMethodAsString())) {
            return null;
        }

        List<Expression> args = withoutTrailingClosure(flatten(baseCall.getArguments()), baseCall, outerCall);
        if (args.isEmpty() || args.size() > 2 || !(args.get(args.size() - 1) instanceof ClassExpression)) {
            return null;
        }
        String name;
        if (args.size() == 1) {
            name = decapitalize(((ClassExpression) args.get(0)).getType().getNameWithoutPackage());
        }
        else {
            Object nameValue = args.get(0) instanceof ConstantExpression ?
                    ((ConstantExpression) args.get(0)).getValue() : null;
            if (!(nameValue instanceof String)) {
                return null;
            }
            name = (String) nameValue;
        }
        return new BeanNameUse(name, baseCall, hasDiscriminatingCondition(qualifierCalls, outerCall));
    }

    private boolean hasDiscriminatingCondition(List<MethodCallExpression> qualifierCalls, MethodCallExpression outerCall) {
        for (MethodCallExpression qualifierCall : qualifierCalls) {
            String qualifierName = qualifierCall.getMethodAsString();
            List<Expression> args = withoutTrailingClosure(flatten(qualifierCall.getArguments()), qualifierCall, outerCall);
            if (CONDITIONAL_ON_MISSING_BEAN_CALL.equals(qualifierName) && discriminatesByType(args)) {
                return true;
            }
            if (ANNOTATE_CALL.equals(qualifierName)) {
                for (Expression arg : args) {
                    if (arg instanceof ClassExpression && isConditionalAnnotation(((ClassExpression) arg).getType())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // @ConditionalOnMissingBean attributes that say nothing about a *type*. Where duplicates share
    // one bean name - the only situation validateSharedBeanNames runs in - a name: or search: is
    // identical on each of them and so can no more tell them apart than the bare form can.
    private static final Set<String> NON_DISCRIMINATING_MISSING_BEAN_ATTRIBUTES = Set.of("name", "search");

    private boolean discriminatesByType(List<Expression> args) {
        boolean sawSomething = false;
        for (Expression arg : args) {
            if (arg instanceof ClassExpression) {
                return true;
            }
            if (!(arg instanceof MapExpression)) {
                // an argument shape this method does not model - stay lenient rather than reject
                return true;
            }
            for (MapEntryExpression entry : ((MapExpression) arg).getMapEntryExpressions()) {
                Object key = entry.getKeyExpression() instanceof ConstantExpression ?
                        ((ConstantExpression) entry.getKeyExpression()).getValue() : null;
                if (!(key instanceof String) || !NON_DISCRIMINATING_MISSING_BEAN_ATTRIBUTES.contains(key)) {
                    return true;
                }
                sawSomething = true;
            }
        }
        return !sawSomething && !args.isEmpty();
    }

    private List<Expression> withoutTrailingClosure(List<Expression> args, MethodCallExpression call,
            MethodCallExpression outerCall) {
        if (call == outerCall && !args.isEmpty() && args.get(args.size() - 1) instanceof ClosureExpression) {
            return args.subList(0, args.size() - 1);
        }
        return args;
    }

    private boolean isConditionalAnnotation(ClassNode annotationType) {
        return isConditionalAnnotation(annotationType, new HashSet<>());
    }

    // The same transitive meta-annotation walk belongsOnSibling does, against @Conditional alone:
    // every @ConditionalOnXxx - Spring Boot's own and arbitrarily-deeply composed custom ones -
    // eventually reaches @Conditional through its meta-annotations.
    private boolean isConditionalAnnotation(ClassNode annotationType, Set<String> visited) {
        if (!visited.add(annotationType.getName())) {
            return false;
        }
        if (Conditional.class.getName().equals(annotationType.getName())) {
            return true;
        }
        for (AnnotationNode metaAnnotation : annotationType.getAnnotations()) {
            if (isConditionalAnnotation(metaAnnotation.getClassNode(), visited)) {
                return true;
            }
        }
        return false;
    }

    private void processStatement(ClassNode classNode, Statement statement, SourceUnit source, Set<String> usedNames) {
        if (!(statement instanceof ExpressionStatement) ||
                !(((ExpressionStatement) statement).getExpression() instanceof MethodCallExpression)) {
            addError(statement, source, "Each 'beans' statement must be a bean(...), field(...), or method(...) call");
            return;
        }

        MethodCallExpression outerCall = (MethodCallExpression) ((ExpressionStatement) statement).getExpression();

        // Walk from the outermost (last-written) call back to the bean(...)/field(...)/method(...)
        // call at the root, collecting any chained qualifiers along the way.
        List<MethodCallExpression> qualifierCalls = new ArrayList<>();
        MethodCallExpression baseCall = outerCall;
        while (!ROOT_STATEMENT_CALL_NAMES.contains(baseCall.getMethodAsString())) {
            if (!ALL_QUALIFIER_CALL_NAMES.contains(baseCall.getMethodAsString()) ||
                    !(baseCall.getObjectExpression() instanceof MethodCallExpression)) {
                addError(statement, source, "Expected bean([\"name\", ] Type) { ... }, field([\"name\", ] Type), " +
                        "or method([\"name\", ] Type) { ... }, optionally chained with qualifiers");
                return;
            }
            qualifierCalls.add(0, baseCall);
            baseCall = (MethodCallExpression) baseCall.getObjectExpression();
        }

        // The walk above stops at the first root call name it meets, so without this a root
        // statement chained onto another - field("suffix", String).bean("greeter", String) { } -
        // parses as one statement and the left-hand declaration is silently dropped.
        if (!baseCall.isImplicitThis() && baseCall.getObjectExpression() instanceof MethodCallExpression) {
            addError(statement, source, baseCall.getMethodAsString() + "(...) cannot be chained onto " +
                    ((MethodCallExpression) baseCall.getObjectExpression()).getMethodAsString() +
                    "(...) - each bean(...), field(...) and method(...) declaration is its own statement");
            return;
        }

        String rootName = baseCall.getMethodAsString();

        // The body closure belongs to the outermost call, after every qualifier. Written the other
        // way round it sits on the root call instead, where the [name, ] Type parsing below finds it
        // as an extra argument and reports something unrelated - that the name and type are the wrong
        // way around, or that a type is missing.
        List<Expression> rootArgs = flatten(baseCall.getArguments());
        if (!qualifierCalls.isEmpty() && !rootArgs.isEmpty() &&
                rootArgs.get(rootArgs.size() - 1) instanceof ClosureExpression) {
            String firstQualifier = qualifierCalls.get(0).getMethodAsString();
            addError(statement, source, "the body closure comes last, after every chained qualifier - " +
                    "write " + rootName + "(...)." + firstQualifier + "(...) { ... } rather than " +
                    rootName + "(...) { ... }." + firstQualifier + "(...)");
            return;
        }
        boolean isBean = BEAN_CALL.equals(rootName);
        Set<String> allowedQualifiers = isBean ? BEAN_QUALIFIER_CALL_NAMES :
                FIELD_CALL.equals(rootName) ? FIELD_QUALIFIER_CALL_NAMES : METHOD_QUALIFIER_CALL_NAMES;
        for (MethodCallExpression qualifierCall : qualifierCalls) {
            if (!allowedQualifiers.contains(qualifierCall.getMethodAsString())) {
                addError(qualifierCall, source, "." + qualifierCall.getMethodAsString() + "(...) cannot be " +
                        "chained onto " + rootName + "(...)");
                return;
            }
        }

        // .annotate(...) is repeatable (once per distinct annotation type, enforced when the
        // annotation is actually attached below); every other qualifier is single-use.
        Set<String> seenQualifiers = new HashSet<>();
        for (MethodCallExpression qualifierCall : qualifierCalls) {
            String qualifierName = qualifierCall.getMethodAsString();
            if (!ANNOTATE_CALL.equals(qualifierName) && !seenQualifiers.add(qualifierName)) {
                addError(qualifierCall, source, "." + qualifierName + "(...) may only be chained once per " +
                        rootName + "(...)");
                return;
            }
        }

        if (isBean) {
            processBeanStatement(classNode, outerCall, baseCall, qualifierCalls, source, usedNames);
        }
        else if (FIELD_CALL.equals(rootName)) {
            processFieldStatement(classNode, baseCall, qualifierCalls, source, usedNames);
        }
        else {
            processMethodStatement(classNode, outerCall, baseCall, qualifierCalls, source, usedNames);
        }
    }

    private boolean registerName(String name, ASTNode location, SourceUnit source, Set<String> usedNames, String errorSuffix) {
        if (!usedNames.add(name)) {
            addError(location, source, "\"" + name + "\" " + errorSuffix);
            return false;
        }
        return true;
    }

    private void processBeanStatement(ClassNode classNode, MethodCallExpression outerCall, MethodCallExpression baseCall,
            List<MethodCallExpression> qualifierCalls, SourceUnit source, Set<String> usedNames) {
        // The factory closure is optional: bean(Type) with no body declares a bean that is just its
        // own no-argument construction, which is by far the most common shape and reads as noise
        // when spelled out as bean(Type) { new Type() }.
        List<Expression> closureCallArgs = flatten(outerCall.getArguments());
        ClosureExpression factory = !closureCallArgs.isEmpty() &&
                closureCallArgs.get(closureCallArgs.size() - 1) instanceof ClosureExpression ?
                (ClosureExpression) closureCallArgs.get(closureCallArgs.size() - 1) : null;

        // When bean(...) is itself the outermost call (no qualifiers chained), it carries the
        // trailing closure as its own last argument - exclude it before validating the [name, ] Type
        // shape, since it was already validated above.
        List<Expression> baseArgs = flatten(baseCall.getArguments());
        if (factory != null && baseCall == outerCall && !baseArgs.isEmpty()) {
            baseArgs = baseArgs.subList(0, baseArgs.size() - 1);
        }

        TypeAndName typeAndName = parseNameAndType(baseArgs, baseCall, source, BEAN_CALL, false);
        if (typeAndName == null) {
            return;
        }

        ClassNode beanType = typeAndName.type.getType();
        // A closure whose body is empty declares construction too, from its own parameters: the
        // parameters say what is injected, and the generated body is the constructor call the author
        // would otherwise have written out. bean(Type) { } with no parameters is bean(Type).
        boolean constructsDeclaredType = factory == null || isEmpty(factory.getCode());
        if (constructsDeclaredType && (beanType.isInterface() || Modifier.isAbstract(beanType.getModifiers()))) {
            addError(baseCall, source, "bean(" + beanType.getNameWithoutPackage() + ") with no factory closure body " +
                    "constructs the declared type, which cannot be done for an interface or abstract class - " +
                    "give it a body: bean(" + beanType.getNameWithoutPackage() + ") { new SomeImplementation() }");
            return;
        }

        // The method name is an implementation detail - Spring resolves the bean by its
        // @Bean("name") value, never by the factory method's name - so a bean name that isn't a
        // usable Java identifier, or is already taken by an existing member, synthesizes instead
        // of erroring.
        String javaMethodName = isValidJavaIdentifier(typeAndName.name) && !usedNames.contains(typeAndName.name) ?
                typeAndName.name :
                syntheticBeanMethodName(typeAndName.type.getType(), usedNames);
        usedNames.add(javaMethodName);

        Parameter[] beanParameters = factory == null || factory.getParameters() == null ?
                Parameter.EMPTY_ARRAY : factory.getParameters();
        Statement beanBody = constructsDeclaredType ?
                synthesizedConstruction(beanType, beanParameters, baseCall) :
                factory.getCode();

        MethodNode beanMethod = new MethodNode(
                javaMethodName,
                Modifier.PUBLIC,
                beanType,
                beanParameters,
                ClassNode.EMPTY_ARRAY,
                beanBody);
        beanMethod.setSourcePosition(baseCall);
        beanMethod.addAnnotation(withPosition(beanAnnotation(typeAndName.name), baseCall));

        for (MethodCallExpression qualifierCall : qualifierCalls) {
            List<Expression> qualifierArgs = flatten(qualifierCall.getArguments());
            if (factory != null && qualifierCall == outerCall) {
                // only the outermost call in the chain can carry the trailing factory closure
                qualifierArgs = qualifierArgs.subList(0, qualifierArgs.size() - 1);
            }
            if (!applyQualifier(beanMethod, typeAndName.name, qualifierCall, qualifierArgs, source)) {
                return;
            }
        }

        classNode.addMethod(beanMethod);
    }

    private boolean isEmpty(Statement code) {
        return code == null || code instanceof EmptyStatement ||
                (code instanceof BlockStatement && ((BlockStatement) code).getStatements().isEmpty());
    }

    /**
     * The construction a bodyless or empty-bodied {@code bean(...)} compiles to: the closure's
     * parameters passed straight through as the constructor's arguments. Which constructor that
     * selects is left to the compiler, resolved from these argument types exactly as it would be for
     * a hand-written {@code new Type(...)} - nothing here reads the declared type's constructors, so
     * adding one cannot change what this bean injects.
     *
     * <p>Every node carries the position of the {@code bean(...)} statement. Without it, the ordinary
     * failure of this form - a parameter list matching no constructor - reaches the compiler with no
     * location, and under static compilation that surfaces as a {@code GroovyBugError} at line -1
     * inviting the author to file a Groovy bug, rather than the located "Cannot find matching
     * constructor" the hand-written equivalent gets.</p>
     */
    private Statement synthesizedConstruction(ClassNode beanType, Parameter[] parameters, ASTNode origin) {
        ArgumentListExpression arguments = new ArgumentListExpression();
        for (Parameter parameter : parameters) {
            VariableExpression argument = new VariableExpression(parameter);
            argument.setSourcePosition(origin);
            arguments.addExpression(argument);
        }
        arguments.setSourcePosition(origin);

        ConstructorCallExpression construction = new ConstructorCallExpression(beanType, arguments);
        construction.setSourcePosition(origin);

        ReturnStatement returnStatement = new ReturnStatement(construction);
        returnStatement.setSourcePosition(origin);
        return returnStatement;
    }

    private String syntheticBeanMethodName(ClassNode beanType, Set<String> usedNames) {
        String base = decapitalize(beanType.getNameWithoutPackage());
        String candidate;
        int index = 0;
        do {
            candidate = base + "$" + index;
            index++;
        }
        while (usedNames.contains(candidate));
        return candidate;
    }

    private void processFieldStatement(ClassNode classNode, MethodCallExpression baseCall,
            List<MethodCallExpression> qualifierCalls, SourceUnit source, Set<String> usedNames) {
        List<Expression> baseArgs = flatten(baseCall.getArguments());
        TypeAndName typeAndName = parseNameAndType(baseArgs, baseCall, source, FIELD_CALL, true);
        if (typeAndName == null) {
            return;
        }
        if (!registerName(typeAndName.name, baseCall, source, usedNames,
                "is already used by another member of the class (declared, inherited, or another field(...)/method(...) statement) - " +
                        "generated member names must be unique")) {
            return;
        }

        FieldNode field = classNode.addField(typeAndName.name, Modifier.PRIVATE, typeAndName.type.getType(), null);
        field.setSourcePosition(baseCall);

        for (MethodCallExpression qualifierCall : qualifierCalls) {
            List<Expression> qualifierArgs = flatten(qualifierCall.getArguments());
            if (VALUE_CALL.equals(qualifierCall.getMethodAsString())) {
                AnnotationNode valueAnnotation = valueAnnotation(qualifierArgs, qualifierCall, source);
                if (valueAnnotation == null || !addAnnotationIfAbsent(field, qualifierCall, valueAnnotation, source)) {
                    return;
                }
            }
            else if (!applyGenericAnnotation(field, qualifierCall, qualifierArgs, source)) {
                return;
            }
        }
    }

    // .value(key, default) builds the '${key:default}' placeholder itself, as a concatenation the
    // compiler folds into a constant - which is what lets the key be a bare static-final constant
    // reference, the one shape a directly-written annotation value rejects. .value(single) is a
    // bare config key with no default, auto-wrapped into '${key}' - injecting the key's literal
    // text is never what .value(...) is for - unless the string already contains a '${'
    // placeholder or '#{' SpEL expression, which passes through verbatim (including mixed
    // literals like 'http://${app.host}/'). A genuine literal stays expressible via
    // .annotate(Value, value: ...).
    private AnnotationNode valueAnnotation(List<Expression> args, MethodCallExpression qualifierCall, SourceUnit source) {
        if (args.isEmpty() || args.size() > 2) {
            addError(qualifierCall, source, ".value(...) requires a config key and default - e.g. " +
                    ".value(Settings.GSP_VIEW_ENCODING, \"UTF-8\") - or a single config key/placeholder/SpEL string");
            return null;
        }
        // The pieces are resolved and folded HERE, into a plain constant, rather than being left
        // as a concatenation for Groovy's own annotation folding: under @CompileStatic the static
        // compiler rewrites '+' into .plus() calls before that folding runs, which would reject
        // the member as a non-constant.
        String memberValue;
        if (args.size() == 1) {
            String placeholder = resolveStringConstant(args.get(0));
            if (placeholder == null) {
                addError(args.get(0), source, ".value(...) arguments must be compile-time String constants " +
                        "(a literal, a static final constant reference, or a concatenation of those)");
                return null;
            }
            if (placeholder.isBlank()) {
                addError(args.get(0), source, ".value(...) requires a non-blank config key - a blank one " +
                        "would compile to the unresolvable placeholder ${}");
                return null;
            }
            memberValue = placeholder.contains("${") || placeholder.contains("#{") ?
                    placeholder : "${" + placeholder + "}";
        }
        else {
            String key = resolveStringConstant(args.get(0));
            String defaultValue = resolveStringConstant(args.get(1));
            if (key == null || defaultValue == null) {
                addError(key == null ? args.get(0) : args.get(1), source, ".value(...) arguments must be " +
                        "compile-time String constants (a literal, a static final constant reference, or a " +
                        "concatenation of those)");
                return null;
            }
            // Only the KEY must be non-blank: a deliberately blank default ('${key:}') is legal
            // and used (e.g. grails.i18n.default.locale falls back to the JVM default locale).
            if (key.isBlank()) {
                addError(args.get(0), source, ".value(key, default) requires a non-blank config key - " +
                        "only the default may be blank");
                return null;
            }
            memberValue = "${" + key + ":" + defaultValue + "}";
        }
        AnnotationNode annotation = new AnnotationNode(ClassHelper.make(Value.class));
        annotation.setMember("value", new ConstantExpression(memberValue));
        return annotation;
    }

    // Resolves an expression to its compile-time String value: literals directly; a static final
    // constant reference either from its AST initial expression (a constant declared in the same
    // compilation unit) or reflectively from the already-compiled class on the classpath; and
    // concatenations of resolvable pieces recursively.
    private String resolveStringConstant(Expression expression) {
        if (expression instanceof ConstantExpression) {
            Object value = ((ConstantExpression) expression).getValue();
            return value instanceof String ? (String) value : null;
        }
        if (expression instanceof BinaryExpression) {
            BinaryExpression binary = (BinaryExpression) expression;
            if (binary.getOperation().getType() != Types.PLUS) {
                return null;
            }
            String left = resolveStringConstant(binary.getLeftExpression());
            String right = resolveStringConstant(binary.getRightExpression());
            return left != null && right != null ? left + right : null;
        }
        if (expression instanceof PropertyExpression) {
            PropertyExpression property = (PropertyExpression) expression;
            if (!(property.getObjectExpression() instanceof ClassExpression)) {
                return null;
            }
            ClassNode owner = property.getObjectExpression().getType();
            String fieldName = property.getPropertyAsString();
            if (fieldName == null) {
                return null;
            }
            FieldNode field = findStaticFinalField(owner, fieldName, new HashSet<>());
            if (field != null && field.getInitialExpression() instanceof ConstantExpression) {
                Object value = ((ConstantExpression) field.getInitialExpression()).getValue();
                return value instanceof String ? (String) value : null;
            }
            // The reflective fallback needs a loaded Class, which a ClassNode from this same
            // compilation unit does not have - getTypeClass() would throw GroovyBugError, an
            // AssertionError that no catch below would hold, aborting the compilation with an
            // internal compiler error instead of the located message the caller reports.
            try {
                Object value = owner.getTypeClass().getField(fieldName).get(null);
                return value instanceof String ? (String) value : null;
            }
            catch (ReflectiveOperationException | RuntimeException | LinkageError | GroovyBugError ignored) {
                return null;
            }
        }
        return null;
    }

    // ClassNode.getField walks superclasses but not interfaces, so a constant declared on an
    // implemented interface - the shape every grails.config.Settings key has - is invisible to it
    // while the owner is still being compiled.
    private FieldNode findStaticFinalField(ClassNode owner, String fieldName, Set<String> visited) {
        for (ClassNode type = owner; type != null; type = type.getSuperClass()) {
            if (!visited.add(type.getName())) {
                return null;
            }
            FieldNode declared = type.getDeclaredField(fieldName);
            if (declared != null && declared.isStatic() && declared.isFinal()) {
                return declared;
            }
            for (ClassNode implemented : type.getInterfaces()) {
                FieldNode inherited = findStaticFinalField(implemented, fieldName, visited);
                if (inherited != null) {
                    return inherited;
                }
            }
        }
        return null;
    }

    private void processMethodStatement(ClassNode classNode, MethodCallExpression outerCall, MethodCallExpression baseCall,
            List<MethodCallExpression> qualifierCalls, SourceUnit source, Set<String> usedNames) {
        List<Expression> closureCallArgs = flatten(outerCall.getArguments());
        if (closureCallArgs.isEmpty() || !(closureCallArgs.get(closureCallArgs.size() - 1) instanceof ClosureExpression)) {
            addError(outerCall, source, "method(...) must end with a body closure: method(\"name\", Type) { ... }");
            return;
        }
        ClosureExpression body = (ClosureExpression) closureCallArgs.get(closureCallArgs.size() - 1);

        List<Expression> baseArgs = flatten(baseCall.getArguments());
        if (baseCall == outerCall && !baseArgs.isEmpty()) {
            baseArgs = baseArgs.subList(0, baseArgs.size() - 1);
        }

        TypeAndName typeAndName = parseNameAndType(baseArgs, baseCall, source, METHOD_CALL, true);
        if (typeAndName == null) {
            return;
        }
        if (!registerName(typeAndName.name, baseCall, source, usedNames,
                "is already used by another member of the class (declared, inherited, or another field(...)/method(...) statement) - " +
                        "generated member names must be unique")) {
            return;
        }

        MethodNode helperMethod = new MethodNode(
                typeAndName.name,
                Modifier.PRIVATE,
                typeAndName.type.getType(),
                body.getParameters() == null ? Parameter.EMPTY_ARRAY : body.getParameters(),
                ClassNode.EMPTY_ARRAY,
                body.getCode());
        helperMethod.setSourcePosition(baseCall);

        for (MethodCallExpression qualifierCall : qualifierCalls) {
            List<Expression> qualifierArgs = flatten(qualifierCall.getArguments());
            if (qualifierCall == outerCall) {
                qualifierArgs = qualifierArgs.subList(0, qualifierArgs.size() - 1);
            }
            if (!applyGenericAnnotation(helperMethod, qualifierCall, qualifierArgs, source)) {
                return;
            }
        }

        classNode.addMethod(helperMethod);
    }

    private static final class TypeAndName {
        private final ClassExpression type;
        private final String name;

        TypeAndName(ClassExpression type, String name) {
            this.type = type;
            this.name = name;
        }
    }

    private TypeAndName parseNameAndType(List<Expression> args, MethodCallExpression call, SourceUnit source,
            String callName, boolean requireValidIdentifier) {
        if (args.isEmpty() || args.size() > 2 || !(args.get(args.size() - 1) instanceof ClassExpression)) {
            if (args.size() == 2 && args.get(0) instanceof ClassExpression) {
                addError(call, source, callName + "(...) takes the name before the type: " +
                        callName + "(\"myGreeter\", Greeter), not " + callName + "(Greeter, \"myGreeter\")");
            }
            else {
                addError(call, source, callName + "(...) requires a type, optionally preceded by a name, " +
                        "e.g. " + callName + "(Greeter) or " + callName + "(\"myGreeter\", Greeter)");
            }
            return null;
        }
        ClassExpression type = (ClassExpression) args.get(args.size() - 1);

        String name;
        if (args.size() == 1) {
            name = decapitalize(type.getType().getNameWithoutPackage());
            // The derived name becomes the generated member's name just as an explicit one does, so
            // it has to clear the same bar - decapitalizing Boolean, Long or Class hands back a Java
            // keyword, and no closure body could then reference the member.
            if (requireValidIdentifier && !isValidJavaIdentifier(name)) {
                addError(call, source, "\"" + name + "\", derived from " +
                        type.getType().getNameWithoutPackage() + ", is not a valid name: it becomes the " +
                        "generated member's name, so it must be a valid Java identifier - give one " +
                        "explicitly, e.g. " + callName + "(\"" + name + "Value\", " +
                        type.getType().getNameWithoutPackage() + ")");
                return null;
            }
        }
        else {
            Expression nameArg = args.get(0);
            Object nameValue = nameArg instanceof ConstantExpression ? ((ConstantExpression) nameArg).getValue() : null;
            if (!(nameValue instanceof String)) {
                addError(nameArg, source, callName + "(name, Type) requires the name to be a String literal, " +
                        "e.g. " + callName + "(\"myGreeter\", Greeter)");
                return null;
            }
            name = (String) nameValue;
            if (requireValidIdentifier && !isValidJavaIdentifier(name)) {
                addError(nameArg, source, "\"" + name + "\" is not a valid name: it becomes the generated " +
                        "member's name, so it must be a valid Java identifier");
                return null;
            }
            // Even bean(...), which otherwise allows any Spring name, must reject a blank one:
            // Spring treats a blank @Bean name as absent and falls back to the method name, so
            // the name actually written would be silently discarded.
            if (!requireValidIdentifier && name.isBlank()) {
                addError(nameArg, source, callName + "(name, Type) requires a non-blank name");
                return null;
            }
        }
        return new TypeAndName(type, name);
    }

    private boolean applyQualifier(MethodNode beanMethod, String beanName, MethodCallExpression qualifierCall,
            List<Expression> args, SourceUnit source) {
        String name = qualifierCall.getMethodAsString();
        if (CONDITIONAL_ON_MISSING_BEAN_CALL.equals(name)) {
            AnnotationNode annotation = conditionalOnMissingBeanAnnotation(args, qualifierCall, source);
            return annotation != null && addAnnotationIfAbsent(beanMethod, qualifierCall, annotation, source);
        }
        if (CONDITIONAL_ON_MISSING_BEAN_NAME_CALL.equals(name)) {
            AnnotationNode annotation = conditionalOnMissingBeanNameAnnotation(args, beanName, qualifierCall, source);
            return annotation != null && addAnnotationIfAbsent(beanMethod, qualifierCall, annotation, source);
        }
        if (PRIMARY_CALL.equals(name) || LAZY_CALL.equals(name)) {
            if (!args.isEmpty()) {
                addError(qualifierCall, source, "." + name + "() takes no arguments");
                return false;
            }
            Class<?> annotationType = PRIMARY_CALL.equals(name) ? Primary.class : Lazy.class;
            return addAnnotationIfAbsent(beanMethod, qualifierCall,
                    new AnnotationNode(ClassHelper.make(annotationType)), source);
        }
        // Generates a static factory method - Spring's recommended shape for a
        // BeanFactoryPostProcessor/BeanPostProcessor bean, which must be creatable without
        // instantiating its declaring configuration class. The body consequently cannot touch
        // field(...)/method(...) members, which are instance members of that class.
        if (STATIC_METHOD_CALL.equals(name)) {
            if (!args.isEmpty()) {
                addError(qualifierCall, source, ".staticMethod() takes no arguments");
                return false;
            }
            beanMethod.setModifiers(beanMethod.getModifiers() | Modifier.STATIC);
            return true;
        }
        if (SCOPE_CALL.equals(name)) {
            return applyScopeQualifier(beanMethod, qualifierCall, args, source);
        }
        return applyGenericAnnotation(beanMethod, qualifierCall, args, source);
    }

    private boolean applyScopeQualifier(MethodNode beanMethod, MethodCallExpression qualifierCall,
            List<Expression> args, SourceUnit source) {
        Expression scopeArg = args.size() == 1 ? args.get(0) : null;
        Object scopeValue = scopeArg instanceof ConstantExpression ? ((ConstantExpression) scopeArg).getValue() : null;
        if (!(scopeValue instanceof String) || ((String) scopeValue).isEmpty()) {
            addError(qualifierCall, source, ".scope(...) requires exactly one non-empty String argument, " +
                    "e.g. .scope(\"prototype\")");
            return false;
        }
        AnnotationNode scopeAnnotation = new AnnotationNode(ClassHelper.make(Scope.class));
        scopeAnnotation.setMember("value", scopeArg);
        return addAnnotationIfAbsent(beanMethod, qualifierCall, scopeAnnotation, source);
    }

    private boolean applyGenericAnnotation(AnnotatedNode target, MethodCallExpression qualifierCall,
            List<Expression> args, SourceUnit source) {
        MapExpression members = !args.isEmpty() && args.get(0) instanceof MapExpression ? (MapExpression) args.get(0) : null;
        List<Expression> remaining = members != null ? args.subList(1, args.size()) : args;
        if (remaining.size() != 1 || !(remaining.get(0) instanceof ClassExpression)) {
            addError(qualifierCall, source, ".annotate(...) requires an annotation type as its only " +
                    "positional argument, e.g. .annotate(Order, value: 1) or .annotate(ConditionalOnWebApplication)");
            return false;
        }

        ClassNode annotationType = ((ClassExpression) remaining.get(0)).getType();
        if (!annotationType.isAnnotationDefinition()) {
            addError(qualifierCall, source, "\"" + annotationType.getName() + "\" is not an annotation type");
            return false;
        }

        AnnotationNode annotation = new AnnotationNode(annotationType);
        if (members != null && !addMembersFromMap(annotation, members, qualifierCall, source)) {
            return false;
        }
        return addAnnotationIfAbsent(target, qualifierCall, annotation, source);
    }

    private boolean addMembersFromMap(AnnotationNode annotation, MapExpression members,
            MethodCallExpression qualifierCall, SourceUnit source) {
        for (MapEntryExpression entry : members.getMapEntryExpressions()) {
            Object keyValue = entry.getKeyExpression() instanceof ConstantExpression ?
                    ((ConstantExpression) entry.getKeyExpression()).getValue() : null;
            if (!(keyValue instanceof String)) {
                addError(qualifierCall, source, "." + qualifierCall.getMethodAsString() +
                        "(...) attribute names must be simple identifiers, e.g. .annotate(Order, value: 1)");
                return false;
            }
            annotation.setMember((String) keyValue, foldStringValue(entry.getValueExpression()));
        }
        return true;
    }

    // Same reason .value(...) folds its arguments: an attribute written as a concatenation is a
    // BinaryExpression here, and under @CompileStatic the static compiler rewrites it into a
    // .plus() call before Groovy folds annotation members, at which point it is no longer an inline
    // constant. Folding now keeps .annotate(...) and .value(...) consistent, and leaves anything
    // that is not a resolvable String - class literals, numbers, enum constants, arrays - alone.
    private Expression foldStringValue(Expression value) {
        if (value instanceof ConstantExpression) {
            return value;
        }
        String resolved = resolveStringConstant(value);
        return resolved != null ? new ConstantExpression(resolved) : value;
    }

    private boolean addAnnotationIfAbsent(AnnotatedNode target, MethodCallExpression qualifierCall,
            AnnotationNode annotation, SourceUnit source) {
        ClassNode type = annotation.getClassNode();
        if (!target.getAnnotations(type).isEmpty()) {
            addError(qualifierCall, source, "@" + type.getNameWithoutPackage() + " is already attached " +
                    "here, via an earlier qualifier or .annotate(...)");
            return false;
        }
        target.addAnnotation(withPosition(annotation, qualifierCall));
        return true;
    }

    private AnnotationNode withPosition(AnnotationNode annotation, ASTNode origin) {
        annotation.setSourcePosition(origin);
        return annotation;
    }

    private List<Expression> flatten(Expression arguments) {
        List<Expression> result = new ArrayList<>();
        if (arguments instanceof org.codehaus.groovy.ast.expr.TupleExpression) {
            result.addAll(((org.codehaus.groovy.ast.expr.TupleExpression) arguments).getExpressions());
        }
        else {
            result.add(arguments);
        }
        return result;
    }

    private AnnotationNode beanAnnotation(String beanName) {
        AnnotationNode annotation = new AnnotationNode(ClassHelper.make(Bean.class));
        ListExpression names = new ListExpression();
        names.addExpression(new ConstantExpression(beanName));
        annotation.setMember("value", names);
        return annotation;
    }

    // Zero args -> a bare annotation, letting Spring infer the back-off type from the method's
    // return type. Positional types -> the value member. Named args -> the annotation's own
    // attributes (name:, search:, ...), so the common name-based back-off doesn't need the
    // generic .annotate(...) escape hatch.
    private AnnotationNode conditionalOnMissingBeanAnnotation(List<Expression> args,
            MethodCallExpression qualifierCall, SourceUnit source) {
        AnnotationNode annotation = new AnnotationNode(ClassHelper.make(ConditionalOnMissingBean.class));
        MapExpression members = !args.isEmpty() && args.get(0) instanceof MapExpression ? (MapExpression) args.get(0) : null;
        List<Expression> types = members != null ? args.subList(1, args.size()) : args;
        if (members != null && !types.isEmpty()) {
            for (MapEntryExpression entry : members.getMapEntryExpressions()) {
                Object keyValue = entry.getKeyExpression() instanceof ConstantExpression ?
                        ((ConstantExpression) entry.getKeyExpression()).getValue() : null;
                if ("value".equals(keyValue)) {
                    addError(qualifierCall, source, "conditionalOnMissingBean(...) was given types both " +
                            "positionally and via value: - use one or the other");
                    return null;
                }
            }
        }
        if (members != null && !addMembersFromMap(annotation, members, qualifierCall, source)) {
            return null;
        }
        if (!types.isEmpty()) {
            ListExpression typeList = new ListExpression();
            for (Expression type : types) {
                if (!(type instanceof ClassExpression)) {
                    addError(qualifierCall, source, "conditionalOnMissingBean(...) arguments must be types " +
                            "and/or named attributes, e.g. conditionalOnMissingBean(Greeter) or " +
                            "conditionalOnMissingBean(name: \"greeter\", search: SearchStrategy.CURRENT)");
                    continue;
                }
                typeList.addExpression(type);
            }
            annotation.setMember("value", typeList);
        }
        return annotation;
    }

    // "Register this bean unless a bean with THIS bean's name already exists" - the name member is
    // set from the bean's own (explicit or convention-derived) name, so the two strings the plain
    // form would have to keep in sync cannot diverge. name:/value:/positional types are rejected:
    // supplying them contradicts the qualifier's purpose, and the fully-explicit forms remain
    // available on .conditionalOnMissingBean(...).
    private AnnotationNode conditionalOnMissingBeanNameAnnotation(List<Expression> args, String beanName,
            MethodCallExpression qualifierCall, SourceUnit source) {
        MapExpression members = !args.isEmpty() && args.get(0) instanceof MapExpression ? (MapExpression) args.get(0) : null;
        if (args.size() > (members != null ? 1 : 0)) {
            addError(qualifierCall, source, "conditionalOnMissingBeanName(...) takes only named attributes " +
                    "(e.g. search: SearchStrategy.CURRENT) - it always backs off by this bean's own name; " +
                    "use conditionalOnMissingBean(...) for type-based or fully explicit conditions");
            return null;
        }
        if (members != null) {
            for (MapEntryExpression entry : members.getMapEntryExpressions()) {
                Object keyValue = entry.getKeyExpression() instanceof ConstantExpression ?
                        ((ConstantExpression) entry.getKeyExpression()).getValue() : null;
                if ("name".equals(keyValue) || "value".equals(keyValue)) {
                    addError(qualifierCall, source, "conditionalOnMissingBeanName(...) sets name automatically " +
                            "from this bean's own name - use conditionalOnMissingBean(...) to spell out name: or types");
                    return null;
                }
            }
        }
        AnnotationNode annotation = new AnnotationNode(ClassHelper.make(ConditionalOnMissingBean.class));
        if (members != null && !addMembersFromMap(annotation, members, qualifierCall, source)) {
            return null;
        }
        annotation.setMember("name", new ConstantExpression(beanName));
        return annotation;
    }

    private String decapitalize(String name) {
        return Introspector.decapitalize(name);
    }

    private boolean isValidJavaIdentifier(String name) {
        return SourceVersion.isIdentifier(name) && !SourceVersion.isKeyword(name);
    }

    // A single identifier or a dotted sequence of them, none of which is a keyword.
    private boolean isValidQualifiedName(String name) {
        return SourceVersion.isName(name);
    }

    private void addError(ASTNode node, SourceUnit source, String message) {
        source.getErrorCollector().addErrorAndContinue(
                new org.codehaus.groovy.control.messages.SyntaxErrorMessage(
                        new SyntaxException(message, node.getLineNumber(), node.getColumnNumber()), source));
    }

}
