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
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.lang.model.SourceVersion;

import groovy.transform.CompilationUnitAware;
import groovy.transform.CompileStatic;
import groovy.transform.TypeChecked;
import org.apache.groovy.util.BeanUtils;
import org.codehaus.groovy.GroovyBugError;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.AstToTextHelper;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.ConstructorNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.InnerClassNode;
import org.codehaus.groovy.ast.GenericsType;
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
import org.codehaus.groovy.ast.expr.TupleExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.EmptyStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.ast.tools.GenericsUtils;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScans;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.context.annotation.Scope;

import grails.compiler.beans.ConditionalOnGrailsEnv;

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
    private static final String GROUP_CALL = "group";
    private static final Set<String> ROOT_STATEMENT_CALL_NAMES =
            Set.of(BEAN_CALL, FIELD_CALL, METHOD_CALL, GROUP_CALL);
    private static final String CONDITIONAL_ON_BEAN_CALL = "conditionalOnBean";
    private static final String CONDITIONAL_ON_MISSING_BEAN_CALL = "conditionalOnMissingBean";
    private static final String CONDITIONAL_ON_MISSING_BEAN_NAME_CALL = "conditionalOnMissingBeanName";
    private static final String CONDITIONAL_ON_PROPERTY_CALL = "conditionalOnProperty";
    private static final String CONDITIONAL_ON_EXPRESSION_CALL = "conditionalOnExpression";
    private static final String CONDITIONAL_ON_CLASS_CALL = "conditionalOnClass";
    private static final String PRIMARY_CALL = "primary";
    private static final String LAZY_CALL = "lazy";
    private static final String SCOPE_CALL = "scope";
    private static final String STATIC_METHOD_CALL = "staticMethod";
    private static final String ANNOTATE_CALL = "annotate";
    private static final String VALUE_CALL = "value";
    private static final String TYPE_ARGUMENTS_CALL = "typeArguments";
    private static final String ALIASES_CALL = "aliases";
    private static final String CONDITIONAL_ON_GRAILS_ENV_CALL = "conditionalOnGrailsEnv";
    private static final Set<String> BEAN_QUALIFIER_CALL_NAMES = Set.of(
            CONDITIONAL_ON_BEAN_CALL, CONDITIONAL_ON_MISSING_BEAN_CALL, CONDITIONAL_ON_MISSING_BEAN_NAME_CALL,
            CONDITIONAL_ON_PROPERTY_CALL, CONDITIONAL_ON_EXPRESSION_CALL, CONDITIONAL_ON_CLASS_CALL,
            PRIMARY_CALL, LAZY_CALL, SCOPE_CALL, STATIC_METHOD_CALL,
            ANNOTATE_CALL, TYPE_ARGUMENTS_CALL, CONDITIONAL_ON_GRAILS_ENV_CALL, ALIASES_CALL);
    // field(...) and method(...) declare plain class members, not beans - bean-specific
    // qualifiers don't apply; .value(...) (@Value config injection) is field-only.
    private static final Set<String> FIELD_QUALIFIER_CALL_NAMES = Set.of(ANNOTATE_CALL, VALUE_CALL, TYPE_ARGUMENTS_CALL);
    private static final Set<String> METHOD_QUALIFIER_CALL_NAMES = Set.of(ANNOTATE_CALL, TYPE_ARGUMENTS_CALL);
    // A group declares a nested configuration class, so it takes what applies to a class: the
    // conditions, and the escape hatch. Bean-shaped qualifiers (primary, scope, aliases, the
    // type arguments of a declared type) have nothing to attach to here.
    private static final Set<String> GROUP_QUALIFIER_CALL_NAMES = Set.of(
            CONDITIONAL_ON_BEAN_CALL, CONDITIONAL_ON_MISSING_BEAN_CALL, CONDITIONAL_ON_PROPERTY_CALL,
            CONDITIONAL_ON_EXPRESSION_CALL, CONDITIONAL_ON_CLASS_CALL, CONDITIONAL_ON_GRAILS_ENV_CALL,
            ANNOTATE_CALL);
    // Every qualifier any declaration accepts. Derived, not restated: this set decides whether a
    // chained call is a qualifier at all, so a name present in one of the three sets above but
    // missing here would be rejected by the chain walk as if the whole statement were malformed -
    // "Expected bean([\"name\", ] Type)..." pointing at a qualifier that is in fact supported.
    private static final Set<String> ALL_QUALIFIER_CALL_NAMES =
            Stream.of(BEAN_QUALIFIER_CALL_NAMES, FIELD_QUALIFIER_CALL_NAMES, METHOD_QUALIFIER_CALL_NAMES)
                    .flatMap(Set::stream)
                    .collect(Collectors.toUnmodifiableSet());
    private static final String PLUGIN_SUPERCLASS_NAME = "grails.plugins.Plugin";
    private static final String GRAILS_PLUGIN_SUFFIX = "GrailsPlugin";
    private static final String AUTO_CONFIGURATION_SUFFIX = "AutoConfiguration";
    private static final String AUTO_CONFIGURATION_NAME_MEMBER = "autoConfigurationName";
    private static final String MOVE_ANNOTATIONS_MEMBER = "moveAnnotations";
    private static final String PROXY_BEAN_METHODS_MEMBER = "proxyBeanMethods";
    private static final String DUMP_DIR_PROPERTY = "grails.beans.dsl.dumpdir";

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
        List<MethodNode> preExistingMethods = new ArrayList<>(beanMethodHost.getMethods());
        List<FieldNode> preExistingFields = new ArrayList<>(beanMethodHost.getFields());
        for (Statement statement : statements) {
            if (!isBeanRootedStatement(statement)) {
                processStatement(beanMethodHost, classNode, statement, source, usedNames);
            }
        }
        for (Statement statement : statements) {
            if (isBeanRootedStatement(statement)) {
                processStatement(beanMethodHost, classNode, statement, source, usedNames);
            }
        }
        List<MethodNode> generatedMethods = generatedMembers(beanMethodHost, preExistingMethods);
        List<FieldNode> generatedFields = new ArrayList<>(beanMethodHost.getFields());
        generatedFields.removeAll(preExistingFields);
        rejectUnproxiedSiblingBeanCalls(beanMethodHost, generatedMethods, source);
        dumpGeneratedMembers(beanMethodHost, generatedMethods, generatedFields, source);

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
                            "condition (e.g. .conditionalOnProperty(...), .conditionalOnBean(...), or " +
                            ".conditionalOnGrailsEnv(...)), so that at most one of them registers at runtime");
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

    /**
     * The qualifiers that condition a bean on something outside itself, and so can tell two
     * declarations of one name apart whenever they are given anything to compare.
     *
     * <p>Listed rather than tested one by one, because forgetting to add a new condition qualifier
     * here does not fail any build: it rejects the author's block instead. They write the mutually
     * exclusive pair Spring Boot documents and are told their two beans need "its own
     * discriminating condition" while looking straight at the condition that discriminates them.</p>
     *
     * <p>{@code .conditionalOnMissingBean(...)} is deliberately absent, handled separately just
     * below: it discriminates only when given a type, since two same-named beans backing off by
     * that shared name carry an identical condition. {@code .conditionalOnMissingBeanName()} never
     * discriminates, for the same reason.</p>
     */
    private static final Set<String> DISCRIMINATING_QUALIFIER_CALL_NAMES =
            Set.of(CONDITIONAL_ON_BEAN_CALL, CONDITIONAL_ON_PROPERTY_CALL, CONDITIONAL_ON_EXPRESSION_CALL,
                    CONDITIONAL_ON_CLASS_CALL, CONDITIONAL_ON_GRAILS_ENV_CALL);

    private boolean hasDiscriminatingCondition(List<MethodCallExpression> qualifierCalls, MethodCallExpression outerCall) {
        for (MethodCallExpression qualifierCall : qualifierCalls) {
            String qualifierName = qualifierCall.getMethodAsString();
            List<Expression> args = withoutTrailingClosure(flatten(qualifierCall.getArguments()), qualifierCall, outerCall);
            if (CONDITIONAL_ON_MISSING_BEAN_CALL.equals(qualifierName) && discriminatesByType(args)) {
                return true;
            }
            if (DISCRIMINATING_QUALIFIER_CALL_NAMES.contains(qualifierName) && !args.isEmpty()) {
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

    private void processStatement(ClassNode classNode, ClassNode declaringClass, Statement statement,
            SourceUnit source, Set<String> usedNames) {
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
                FIELD_CALL.equals(rootName) ? FIELD_QUALIFIER_CALL_NAMES :
                        GROUP_CALL.equals(rootName) ? GROUP_QUALIFIER_CALL_NAMES : METHOD_QUALIFIER_CALL_NAMES;
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
            processBeanStatement(classNode, declaringClass, outerCall, baseCall, qualifierCalls, source, usedNames);
        }
        else if (GROUP_CALL.equals(rootName)) {
            processGroupStatement(classNode, declaringClass, outerCall, baseCall, qualifierCalls, source, usedNames);
        }
        else if (FIELD_CALL.equals(rootName)) {
            processFieldStatement(classNode, declaringClass, baseCall, qualifierCalls, source, usedNames);
        }
        else {
            processMethodStatement(classNode, declaringClass, outerCall, baseCall, qualifierCalls, source, usedNames);
        }
    }

    /**
     * Compiles {@code group("name").<conditions> { ... }} into a nested static
     * {@code @Configuration(proxyBeanMethods = false)} class holding the declarations in its body,
     * with the chained qualifiers attached to that class rather than to each bean.
     *
     * <p>This is the shape real auto-configurations take, and the one a condition on an optional
     * type has to take. Spring reads a condition from the bytecode before loading anything, but a
     * {@code @Bean} method's parameter and return types are resolved when its configuration class
     * is parsed - so a bean whose own signature names a class that may be absent cannot be guarded
     * on the method. Moving it into a nested class moves the guard with it, and the nested class is
     * never parsed when the condition fails. Spring Boot writes exactly this: JacksonAutoConfiguration
     * carries four nested {@code @ConditionalOnClass} configuration classes.</p>
     *
     * <p>Spring finds the nested class itself - {@code ConfigurationClassParser} processes the member
     * classes of a configuration class - so nothing has to import or register it.</p>
     */
    private void processGroupStatement(ClassNode classNode, ClassNode declaringClass, MethodCallExpression outerCall,
            MethodCallExpression baseCall, List<MethodCallExpression> qualifierCalls, SourceUnit source,
            Set<String> usedNames) {
        List<Expression> closureCallArgs = flatten(outerCall.getArguments());
        if (closureCallArgs.isEmpty() || !(closureCallArgs.get(closureCallArgs.size() - 1) instanceof ClosureExpression)) {
            addError(outerCall, source, "group(...) must end with a body closure: group(\"name\") { ... }");
            return;
        }
        ClosureExpression body = (ClosureExpression) closureCallArgs.get(closureCallArgs.size() - 1);
        if (body.getParameters() != null && body.getParameters().length > 0) {
            addError(outerCall, source, "group(...) takes no closure parameters - a group declares a class, " +
                    "not a bean, so there is nothing to inject into. Put the parameters on the bean(...) " +
                    "declarations inside it");
            return;
        }

        List<Expression> baseArgs = flatten(baseCall.getArguments());
        if (baseCall == outerCall && !baseArgs.isEmpty()) {
            baseArgs = baseArgs.subList(0, baseArgs.size() - 1);
        }
        if (baseArgs.size() != 1) {
            addError(baseCall, source, "group(...) takes a name, e.g. group(\"imageServing\") { ... }");
            return;
        }
        String name = resolveStringConstant(baseArgs.get(0), declaringClass);
        if (name == null || !isValidJavaIdentifier(BeanUtils.capitalize(name))) {
            addError(baseArgs.get(0), source, "group(name) requires the name to be a String literal or a " +
                    "compile-time String constant that is a valid Java identifier - it becomes the nested " +
                    "class's name, e.g. group(\"imageServing\")");
            return;
        }

        // JacksonObjectMapperConfiguration rather than JacksonObjectMapper: the suffix is what says
        // this is a configuration class when it turns up in a stack trace or /actuator/beans.
        String simpleName = BeanUtils.capitalize(name);
        if (!simpleName.endsWith("Configuration")) {
            simpleName = simpleName + "Configuration";
        }
        if (!registerName(simpleName, baseCall, source, usedNames,
                "is already used by another member of the class - generated member names must be unique")) {
            return;
        }

        List<Statement> statements = beanStatements(body);
        if (statements.isEmpty()) {
            addError(outerCall, source, "group(\"" + name + "\") declares nothing - a group exists to put a " +
                    "condition on the declarations inside it");
            return;
        }
        for (Statement statement : statements) {
            if (isGroupRootedStatement(statement)) {
                addError(statement, source, "group(...) cannot be nested - flatten it, or give the inner " +
                        "group its own conditions at the top level");
                return;
            }
        }

        InnerClassNode group = new InnerClassNode(classNode, classNode.getName() + "$" + simpleName,
                Modifier.PUBLIC | Modifier.STATIC, ClassHelper.OBJECT_TYPE);
        group.setSourcePosition(baseCall);
        source.getAST().addClass(group);

        // proxyBeanMethods = false, matching what Spring Boot's own nested configuration classes
        // carry - and keeping the sibling-call check below meaningful inside the group.
        AnnotationNode configuration = new AnnotationNode(ClassHelper.make(Configuration.class));
        configuration.setMember(PROXY_BEAN_METHODS_MEMBER, new ConstantExpression(Boolean.FALSE));
        group.addAnnotation(withPosition(configuration, baseCall));

        for (MethodCallExpression qualifierCall : qualifierCalls) {
            List<Expression> qualifierArgs = flatten(qualifierCall.getArguments());
            if (qualifierCall == outerCall) {
                qualifierArgs = qualifierArgs.subList(0, qualifierArgs.size() - 1);
            }
            if (!applyGroupQualifier(group, qualifierCall, qualifierArgs, source)) {
                return;
            }
        }

        Set<String> groupNames = existingMemberNames(group);
        List<MethodNode> preExisting = new ArrayList<>(group.getMethods());
        validateSharedBeanNames(statements, source);
        for (Statement statement : statements) {
            if (!isBeanRootedStatement(statement)) {
                processStatement(group, declaringClass, statement, source, groupNames);
            }
        }
        for (Statement statement : statements) {
            if (isBeanRootedStatement(statement)) {
                processStatement(group, declaringClass, statement, source, groupNames);
            }
        }
        List<MethodNode> generated = generatedMembers(group, preExisting);
        rejectUnproxiedSiblingBeanCalls(group, generated, source);
        dumpGeneratedMembers(group, generated, new ArrayList<>(group.getFields()), source);

        // The group is compiled as its own class, so it needs the host's static-compilation
        // treatment in its own right - otherwise its bodies are dynamic inside a @CompileStatic file.
        applyStaticCompilation(classNode, group, source);
    }

    // Same silent classification as isBeanRootedStatement, for the nesting check.
    private boolean isGroupRootedStatement(Statement statement) {
        if (!(statement instanceof ExpressionStatement) ||
                !(((ExpressionStatement) statement).getExpression() instanceof MethodCallExpression)) {
            return false;
        }
        MethodCallExpression call = (MethodCallExpression) ((ExpressionStatement) statement).getExpression();
        while (!ROOT_STATEMENT_CALL_NAMES.contains(call.getMethodAsString()) &&
                call.getObjectExpression() instanceof MethodCallExpression) {
            call = (MethodCallExpression) call.getObjectExpression();
        }
        return GROUP_CALL.equals(call.getMethodAsString());
    }

    private boolean applyGroupQualifier(ClassNode group, MethodCallExpression qualifierCall,
            List<Expression> args, SourceUnit source) {
        String name = qualifierCall.getMethodAsString();
        AnnotationNode annotation;
        if (CONDITIONAL_ON_BEAN_CALL.equals(name)) {
            annotation = conditionalOnBeanAnnotation(args, qualifierCall, source);
        }
        else if (CONDITIONAL_ON_MISSING_BEAN_CALL.equals(name)) {
            annotation = conditionalOnMissingBeanAnnotation(args, qualifierCall, source);
        }
        else if (CONDITIONAL_ON_PROPERTY_CALL.equals(name)) {
            annotation = conditionalOnPropertyAnnotation(args, qualifierCall, source);
        }
        else if (CONDITIONAL_ON_EXPRESSION_CALL.equals(name)) {
            annotation = conditionalOnExpressionAnnotation(args, qualifierCall, source);
        }
        else if (CONDITIONAL_ON_CLASS_CALL.equals(name)) {
            annotation = conditionalOnClassAnnotation(args, qualifierCall, source);
        }
        else if (CONDITIONAL_ON_GRAILS_ENV_CALL.equals(name)) {
            // attaches directly rather than returning a node, like the qualifier path it shares
            return applyConditionalOnGrailsEnvQualifier(group, qualifierCall, args, source);
        }
        else {
            return applyGenericAnnotation(group, qualifierCall, args, source);
        }
        return annotation != null && addAnnotationIfAbsent(group, qualifierCall, annotation, source);
    }

    private boolean registerName(String name, ASTNode location, SourceUnit source, Set<String> usedNames, String errorSuffix) {
        if (!usedNames.add(name)) {
            addError(location, source, "\"" + name + "\" " + errorSuffix);
            return false;
        }
        return true;
    }

    private void processBeanStatement(ClassNode classNode, ClassNode declaringClass,
            MethodCallExpression outerCall, MethodCallExpression baseCall,
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

        TypeAndName typeAndName = parseNameAndType(baseArgs, baseCall, source, BEAN_CALL, false, true, declaringClass);
        if (typeAndName == null) {
            return;
        }

        ClassNode beanType = declaredType(typeAndName, qualifierCalls, outerCall, factory != null, source, BEAN_CALL);
        if (beanType == null) {
            return;
        }
        ClassNode implementationType = typeAndName.implementation == null ? null : typeAndName.implementation.getType();
        String declaredName = typeAndName.type.getType().getNameWithoutPackage();
        // A closure whose body is empty declares construction too, from its own parameters: the
        // parameters say what is injected, and the generated body is the constructor call the author
        // would otherwise have written out. bean(Type) { } with no parameters is bean(Type).
        boolean constructsBean = factory == null || isEmpty(factory.getCode());
        if (implementationType != null) {
            String implementationName = implementationType.getNameWithoutPackage();
            // Naming the implementation IS the construction, so a body answering the same question
            // again can only disagree with it.
            if (!constructsBean) {
                addError(baseCall, source, "bean(" + declaredName + ", " + implementationName + ") already " +
                        "declares what to construct, so it takes no factory closure body - drop the body, or " +
                        "drop " + implementationName + " and construct it there");
                return;
            }
            if (!isSubtypeOf(implementationType, typeAndName.type.getType())) {
                addError(baseCall, source, implementationName + " is not a " + declaredName + ", so it cannot " +
                        "be the implementation of a bean declared as " + declaredName);
                return;
            }
        }
        // What the generated body actually calls new on: the implementation when one was named,
        // the declared type otherwise.
        ClassNode constructedType = implementationType != null ? implementationType : beanType;
        // The construction can already prove the declared type's type arguments - a bean declared as
        // AuditorAware and built from a SpringSecurityAuditorAware is an AuditorAware<Long>, and
        // Spring matches injection points against exactly that. Restating it in .typeArguments(...)
        // is then only an opportunity to state it differently from the truth.
        if (!hasExplicitTypeArguments(qualifierCalls)) {
            ClassNode evidence = implementationType != null ? implementationType : constructedTypeFromBody(factory);
            ClassNode inferred = inferTypeArguments(typeAndName.type.getType(), evidence);
            if (inferred != null) {
                beanType = inferred;
            }
        }
        if (constructsBean && (constructedType.isInterface() || Modifier.isAbstract(constructedType.getModifiers()))) {
            if (implementationType != null) {
                addError(baseCall, source, constructedType.getNameWithoutPackage() + " is an interface or abstract " +
                        "class, so it cannot be the implementation - name a concrete type: bean(" + declaredName +
                        ", SomeImplementation)");
            }
            else {
                addError(baseCall, source, "bean(" + declaredName + ") with no factory closure body " +
                        "constructs the declared type, which cannot be done for an interface or abstract class - " +
                        "name the implementation: bean(" + declaredName + ", SomeImplementation), or give it a " +
                        "body: bean(" + declaredName + ") { new SomeImplementation() }");
            }
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
        Statement beanBody = constructsBean ?
                synthesizedConstruction(constructedType, beanParameters, baseCall) :
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

        if (!rejectNonStaticPostProcessor(beanMethod, beanType, baseCall, source)) {
            return;
        }

        if (!rehomeAnonymousInnerClasses(beanBody, classNode, Modifier.isStatic(beanMethod.getModifiers()),
                typeAndName.name, source)) {
            return;
        }

        classNode.addMethod(beanMethod);
    }

    private static final String BEAN_FACTORY_POST_PROCESSOR = "org.springframework.beans.factory.config.BeanFactoryPostProcessor";
    private static final String BEAN_POST_PROCESSOR = "org.springframework.beans.factory.config.BeanPostProcessor";

    /**
     * A {@code BeanFactoryPostProcessor}/{@code BeanPostProcessor} bean must be creatable without
     * instantiating its declaring class, because Spring has to obtain it before the ordinary bean
     * lifecycle it participates in has started. Declared as an instance method it still "works",
     * which is the problem: the configuration class is instantiated far too early, taking every bean
     * its methods depend on with it, out of order and past the post-processors that would have
     * configured them - a class of startup bug that shows up as an unrelated bean being unconfigured
     * rather than as anything pointing here.
     *
     * <p>{@code .staticMethod()} is the fix and is already in the DSL; this only stops the mistake
     * being silent. An instance-bound post-processor, if one is genuinely wanted, is still writable
     * as an ordinary {@code @Bean} method on the same class - the block does not claim them.</p>
     */
    private boolean rejectNonStaticPostProcessor(MethodNode beanMethod, ClassNode beanType,
            ASTNode location, SourceUnit source) {
        if (Modifier.isStatic(beanMethod.getModifiers())) {
            return true;
        }
        String postProcessorType = null;
        if (isSubtypeOf(beanType, ClassHelper.make(BEAN_FACTORY_POST_PROCESSOR))) {
            postProcessorType = "BeanFactoryPostProcessor";
        }
        else if (isSubtypeOf(beanType, ClassHelper.make(BEAN_POST_PROCESSOR))) {
            postProcessorType = "BeanPostProcessor";
        }
        if (postProcessorType == null) {
            return true;
        }
        addError(location, source, "a " + postProcessorType + " bean must be declared " +
                ".staticMethod(), so Spring can obtain it without instantiating this class - as an " +
                "instance method it forces that instantiation before the beans it post-processes " +
                "are configured");
        return false;
    }

    /**
     * Writes the members this block generated to {@code -Dgrails.beans.dsl.dumpdir=<dir>}, one file
     * per host class.
     *
     * <p>Everything the DSL decides that the source does not say is a declaration, not a body: the
     * bean name Spring will resolve by, the annotations the qualifiers became, the modifiers, the
     * declared type and whether it ended up carrying type arguments, and the parameter annotations
     * that make a dependency optional or qualified. Bodies are excluded on purpose - a bean body is
     * the author's own closure body, lifted verbatim, so it is already readable where they wrote it.
     *
     * <p>Without this, the only way to see any of it is {@code javap} on the compiled class, which
     * is a poor place to answer "did that qualifier attach anything" while writing the block.
     * Grails already takes this shape for its other compile-time generator, where
     * {@code grails.views.gsp.keepgenerateddir} keeps the Groovy a GSP compiles to.</p>
     */
    private void dumpGeneratedMembers(ClassNode host, List<MethodNode> methods, List<FieldNode> fields,
            SourceUnit source) {
        String dir = System.getProperty(DUMP_DIR_PROPERTY);
        if (dir == null || dir.isBlank()) {
            return;
        }
        StringBuilder text = new StringBuilder();
        text.append("// Generated from the 'beans' DSL in ").append(host.getName()).append('\n');
        text.append("// Bodies are omitted: each is the closure body from that source, lifted verbatim.\n");
        for (FieldNode field : fields) {
            text.append('\n');
            for (AnnotationNode annotation : field.getAnnotations()) {
                text.append(annotationText(annotation)).append('\n');
            }
            text.append(AstToTextHelper.getModifiersText(field.getModifiers())).append(' ')
                    .append(typeText(field.getType())).append(' ').append(field.getName()).append('\n');
        }
        for (MethodNode method : methods) {
            text.append('\n');
            for (AnnotationNode annotation : method.getAnnotations()) {
                text.append(annotationText(annotation)).append('\n');
            }
            text.append(AstToTextHelper.getModifiersText(method.getModifiers())).append(' ')
                    .append(typeText(method.getReturnType())).append(' ').append(method.getName())
                    .append('(').append(parametersText(method.getParameters())).append(")\n");
        }
        try {
            Path target = Paths.get(dir);
            Files.createDirectories(target);
            Files.writeString(target.resolve(host.getName() + ".beans.txt"), text.toString(),
                    StandardCharsets.UTF_8);
        }
        catch (IOException | RuntimeException e) {
            // Opt-in by definition, so this can only fire for someone who asked for the dump and
            // would otherwise be left looking for a file that was never written.
            addError(host, source, "could not write the beans DSL dump for " + host.getName() + " to \"" +
                    dir + "\" (" + DUMP_DIR_PROPERTY + "): " + e);
        }
    }

    private String parametersText(Parameter[] parameters) {
        StringBuilder text = new StringBuilder();
        for (Parameter parameter : parameters) {
            if (text.length() > 0) {
                text.append(", ");
            }
            for (AnnotationNode annotation : parameter.getAnnotations()) {
                text.append(annotationText(annotation)).append(' ');
            }
            text.append(typeText(parameter.getType())).append(' ').append(parameter.getName());
        }
        return text.toString();
    }

    // Type arguments are printed only when every one of them is concrete. A raw declared type
    // resolved from a class still reports its own type PARAMETERS here, and printing those would
    // read as <String> when nothing of the sort was declared.
    private String typeText(ClassNode type) {
        GenericsType[] generics = type.getGenericsTypes();
        if (generics == null || generics.length == 0) {
            return type.getName();
        }
        StringBuilder text = new StringBuilder(type.getName());
        for (GenericsType generic : generics) {
            if (generic.isPlaceholder() || generic.isWildcard()) {
                return type.getName();
            }
        }
        text.append('<');
        for (int i = 0; i < generics.length; i++) {
            text.append(i == 0 ? "" : ", ").append(generics[i].getType().getName());
        }
        return text.append('>').toString();
    }

    private String annotationText(AnnotationNode annotation) {
        StringBuilder text = new StringBuilder("@").append(annotation.getClassNode().getNameWithoutPackage());
        Map<String, Expression> members = annotation.getMembers();
        if (members.isEmpty()) {
            return text.toString();
        }
        text.append('(');
        boolean first = true;
        for (Map.Entry<String, Expression> member : members.entrySet()) {
            text.append(first ? "" : ", ").append(member.getKey()).append(" = ")
                    .append(memberValueText(member.getValue()));
            first = false;
        }
        return text.append(')').toString();
    }

    // Expression.getText() renders a String constant bare, so @DependsOn("names") would print as
    // value = names and read as an identifier. Quote them, and descend into a list so an
    // array-valued attribute reads the way it was written.
    private String memberValueText(Expression value) {
        if (value instanceof ConstantExpression && ((ConstantExpression) value).getValue() instanceof String) {
            return "\"" + ((ConstantExpression) value).getValue() + "\"";
        }
        if (value instanceof ListExpression) {
            StringBuilder text = new StringBuilder("[");
            List<Expression> entries = ((ListExpression) value).getExpressions();
            for (int i = 0; i < entries.size(); i++) {
                text.append(i == 0 ? "" : ", ").append(memberValueText(entries.get(i)));
            }
            return text.append(']').toString();
        }
        return value.getText();
    }

    // The methods this block just generated, in declaration order: everything on the host that was
    // not there before the two processing loops ran. MethodNode does not override equals, so the
    // removal is by identity and cannot drop a same-signature method the user wrote.
    private List<MethodNode> generatedMembers(ClassNode host, List<MethodNode> preExisting) {
        List<MethodNode> generated = new ArrayList<>(host.getMethods());
        generated.removeAll(preExisting);
        return generated;
    }

    /**
     * Rejects a call from one generated method to another generated {@code @Bean} method, on a host
     * whose bean methods Spring does not proxy.
     *
     * <p>Calling a sibling {@code @Bean} method and getting the singleton back is a CGLIB trick, and
     * Spring only plays it for a full {@code @Configuration} class. On a <i>lite</i> configuration
     * source the same call is a plain Java call that constructs a second instance - and lite is the
     * common case for this DSL: {@code @AutoConfiguration} is
     * {@code @Configuration(proxyBeanMethods = false)}, the sibling generated for a plugin descriptor
     * carries exactly that, and a Grails {@code Application} class is a configuration source without
     * being annotated {@code @Configuration} at all.</p>
     *
     * <p>A full {@code @Configuration} class is not wholly exempt: the interception is CGLIB
     * subclassing, so it cannot override a {@code static} method, and Spring documents that calls to
     * a static {@code @Bean} method are never intercepted - not even there. A {@code .staticMethod()}
     * bean is therefore checked on every host, and is the only thing checked on a proxied one.</p>
     *
     * <p>Nothing about that failure is visible at runtime. The context starts, every bean exists, and
     * two objects live where the author meant one - so a listener registers on the wrong instance, or
     * configuration applied to one is missing from the other. It is also the exact mistake a
     * migration invites, since moving bean methods off a real {@code @Configuration} class into this
     * DSL silently changes what those calls mean.</p>
     */
    private void rejectUnproxiedSiblingBeanCalls(ClassNode host, List<MethodNode> generated, SourceUnit source) {
        boolean proxied = beanMethodsAreProxied(host);
        Map<String, MethodNode> beanMethodsByName = new LinkedHashMap<>();
        // Every @Bean method on the host, not only the ones this block generated. A class that
        // mixes hand-written @Bean methods with the DSL is what a migration looks like midway
        // through, and a call to one of those from a generated body misses the singleton in exactly
        // the same way - more easily, in fact, since it was correct in the @Configuration class the
        // beans are being moved out of. Only generated bodies are scanned: what a hand-written
        // method does is its author's business, not this transform's.
        for (MethodNode method : host.getMethods()) {
            if (method.getAnnotations(ClassHelper.make(Bean.class)).isEmpty()) {
                continue;
            }
            // A proxied host still cannot intercept a .staticMethod() bean: the interception is
            // CGLIB subclassing, and a static method cannot be overridden. So on a full
            // @Configuration class those are the only sibling calls still worth rejecting.
            if (!proxied || method.isStatic()) {
                beanMethodsByName.put(method.getName(), method);
            }
        }
        if (beanMethodsByName.isEmpty()) {
            return;
        }
        for (MethodNode method : generated) {
            if (method.getCode() == null) {
                continue;
            }
            MethodNode caller = method;
            method.getCode().visit(new CodeVisitorSupport() {
                // Deliberately not descending. Inside a closure an unqualified call is resolved
                // against the delegate first, so `new Registry().tap { initialize() }` calls the
                // registry - not this class - even though the AST records implicit-this either way.
                // Reading that as a sibling bean call would reject working code, which is a far
                // worse trade than missing the rare bean call written inside a nested closure.
                @Override
                public void visitClosureExpression(ClosureExpression expression) {
                }

                @Override
                public void visitMethodCallExpression(MethodCallExpression call) {
                    super.visitMethodCallExpression(call);
                    if (!isSelfCall(call)) {
                        return;
                    }
                    MethodNode target = beanMethodsByName.get(call.getMethodAsString());
                    if (target == null || target == caller) {
                        return;
                    }
                    addError(call, source, siblingCallCause(host, call.getMethodAsString(), proxied) +
                            ", so this call does not return the bean Spring registered - it constructs a second " +
                            "instance. Inject it instead, by declaring it as a parameter of this closure; if what " +
                            "you want is shared logic rather than the bean, move it into a method(...) declaration.");
                }
            });
        }
    }

    // Why this particular call misses the singleton. On a proxied host the map holds only static
    // bean methods, so reaching here means the target is one.
    private String siblingCallCause(ClassNode host, String name, boolean proxied) {
        if (proxied) {
            return "\"" + name + "(...)\" is another bean declared in this block, and is declared " +
                    ".staticMethod(). A static @Bean method is never intercepted by the container - not even " +
                    "on a proxied @Configuration class like " + host.getNameWithoutPackage() + " - because " +
                    "that interception is CGLIB subclassing, which cannot override a static method";
        }
        return "\"" + name + "(...)\" is another bean declared in this block, and " +
                host.getNameWithoutPackage() + " is not a proxied @Configuration class";
    }

    // An unqualified call, or one written against this. Anything with a real receiver is somebody
    // else's method that happens to share the name.
    private boolean isSelfCall(MethodCallExpression call) {
        return call.isImplicitThis() ||
                (call.getObjectExpression() instanceof VariableExpression &&
                        ((VariableExpression) call.getObjectExpression()).isThisExpression());
    }

    // Whether Spring will CGLIB-proxy this host's @Bean methods: true only when @Configuration is
    // reachable from the class's own annotations without passing through one that sets
    // proxyBeanMethods = false. @AutoConfiguration answers false through that second clause - its
    // meta-annotation is @Configuration(proxyBeanMethods = false) - and a Grails Application class
    // answers false by carrying no @Configuration at all.
    private boolean beanMethodsAreProxied(ClassNode host) {
        return proxiesBeanMethods(host.getAnnotations(), new HashSet<>());
    }

    // `visited` guards descent, and only descent. Recording a type when the branch is pruned - by
    // the meta-annotation filter, by proxyBeanMethods = false, or by being @Configuration itself -
    // would memoize an answer that was never computed: @Configuration reached through
    // @AutoConfiguration is pruned, and a real proxying @Configuration written alongside it would
    // then be skipped as already-seen, answering false for a class Spring does proxy.
    private boolean proxiesBeanMethods(List<AnnotationNode> annotations, Set<String> visited) {
        for (AnnotationNode annotation : annotations) {
            ClassNode type = annotation.getClassNode();
            if (type.getName().startsWith("java.lang.annotation.")) {
                continue;
            }
            if (isFalseConstant(annotation.getMember(PROXY_BEAN_METHODS_MEMBER))) {
                continue;
            }
            if (Configuration.class.getName().equals(type.getName())) {
                return true;
            }
            if (visited.add(type.getName()) && proxiesBeanMethods(type.getAnnotations(), visited)) {
                return true;
            }
        }
        return false;
    }

    private boolean isFalseConstant(Expression expression) {
        return expression instanceof ConstantExpression &&
                Boolean.FALSE.equals(((ConstantExpression) expression).getValue());
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

    /**
     * Whether {@code candidate} is a {@code target}. Used for the implementation type, where
     * checking it here rather than leaving it to the generated {@code return new Impl()} means the
     * failure names both types and points at the {@code bean(...)} statement instead of surfacing as
     * an assignment error inside a body the author never wrote.
     */
    private boolean isSubtypeOf(ClassNode candidate, ClassNode target) {
        ClassNode resolved = target.redirect();
        return candidate.redirect().equals(resolved) || candidate.isDerivedFrom(resolved) ||
                candidate.implementsInterface(resolved);
    }

    private boolean hasExplicitTypeArguments(List<MethodCallExpression> qualifierCalls) {
        for (MethodCallExpression qualifierCall : qualifierCalls) {
            if (TYPE_ARGUMENTS_CALL.equals(qualifierCall.getMethodAsString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The type a factory closure constructs, when its body is exactly that and nothing else: a last
     * statement that is a {@code new ...} expression, which in Groovy is the closure's return value.
     * Anything else - a local, a method call, a conditional - is not evidence of anything, and this
     * returns null rather than guess.
     */
    private ClassNode constructedTypeFromBody(ClosureExpression factory) {
        if (factory == null || !(factory.getCode() instanceof BlockStatement)) {
            return null;
        }
        List<Statement> statements = ((BlockStatement) factory.getCode()).getStatements();
        if (statements.isEmpty()) {
            return null;
        }
        Statement last = statements.get(statements.size() - 1);
        Expression expression = null;
        if (last instanceof ReturnStatement) {
            expression = ((ReturnStatement) last).getExpression();
        }
        else if (last instanceof ExpressionStatement) {
            expression = ((ExpressionStatement) last).getExpression();
        }
        return expression instanceof ConstructorCallExpression ? expression.getType() : null;
    }

    /**
     * The declared type parameterized by what {@code evidence} binds it to, or null when that cannot
     * be answered concretely - {@code evidence} is unrelated, the declared type is not generic, or
     * the binding is itself a type variable ({@code class Box<T> implements Holder<T>} proves
     * nothing about a {@code Holder} bean). Inference only ever adds information the compiler could
     * already see; where it cannot, the raw type stands exactly as before and
     * {@code .typeArguments(...)} remains the way to say it.
     */
    private ClassNode inferTypeArguments(ClassNode declaredRaw, ClassNode evidence) {
        GenericsType[] declared = declaredRaw.redirect().getGenericsTypes();
        if (evidence == null || declared == null || declared.length == 0) {
            return null;
        }
        if (!isSubtypeOf(evidence, declaredRaw)) {
            return null;
        }
        // A raw construction of a generic type proves nothing: Groovy resolves its parameters to
        // their bounds, so new GenericBox() would infer Holder<Object> - not merely uninformative
        // but wrong, since a bean typed Holder<Object> no longer matches a Holder<String> injection
        // point it previously did as a raw Holder.
        GenericsType[] evidenceParameters = evidence.redirect().getGenericsTypes();
        if (evidenceParameters != null && evidenceParameters.length > 0 &&
                (evidence.getGenericsTypes() == null || evidence.getGenericsTypes().length == 0)) {
            return null;
        }
        ClassNode parameterized;
        try {
            parameterized = GenericsUtils.parameterizeType(evidence, declaredRaw.redirect());
        }
        catch (RuntimeException ignored) {
            // parameterizeType is best-effort on partially resolved hierarchies; an unusable answer
            // is the same as no answer.
            return null;
        }
        GenericsType[] resolved = parameterized == null ? null : parameterized.getGenericsTypes();
        if (resolved == null || resolved.length != declared.length) {
            return null;
        }
        for (GenericsType candidate : resolved) {
            if (candidate.isPlaceholder() || candidate.isWildcard() || candidate.getType() == null ||
                    candidate.getType().isGenericsPlaceHolder()) {
                return null;
            }
        }
        return GenericsUtils.makeClassSafeWithGenerics(declaredRaw, resolved);
    }

    /**
     * Re-homes anonymous inner classes in a body lifted out of the {@code beans} closure.
     *
     * <p>Groovy's {@code InnerClassVisitor} runs at SEMANTIC_ANALYSIS, before this transform, and
     * gives an anonymous class its enclosing instance from wherever it was written: a class
     * declared inside a closure gets {@code final Closure this$0} and a constructor taking a
     * {@code Closure}, where one declared in a method gets the declaring class. Lifting the body
     * into a method moves the code and not that decision, so the generated call passes {@code this}
     * - the configuration class - to a constructor still expecting the closure. It compiles, and
     * fails at runtime with a {@code GroovyCastException} naming neither the bean nor the DSL.</p>
     *
     * <p>So the three places that decision landed are corrected here: the {@code this$0} field, the
     * synthetic constructor's first parameter, and the argument at the call site.</p>
     */
    private boolean rehomeAnonymousInnerClasses(Statement body, ClassNode host, boolean staticMethod,
            String beanName, SourceUnit source) {
        List<ConstructorCallExpression> anonymous = new ArrayList<>();
        body.visit(new CodeVisitorSupport() {
            @Override
            public void visitConstructorCallExpression(ConstructorCallExpression call) {
                if (call.isUsingAnonymousInnerClass()) {
                    anonymous.add(call);
                }
                super.visitConstructorCallExpression(call);
            }
        });
        for (ConstructorCallExpression call : anonymous) {
            ClassNode inner = call.getType();
            FieldNode outerField = inner.getDeclaredField("this$0");
            if (outerField == null || !ClassHelper.CLOSURE_TYPE.equals(outerField.getType())) {
                continue; // already homed somewhere real, or static - nothing the lift broke
            }
            // A static factory method has no enclosing instance to give it, and the field cannot be
            // dropped here: InnerClassVisitor added it and the constructor body assigns it.
            if (staticMethod) {
                addError(call, source, "\"" + beanName + "\" is declared .staticMethod() and its body " +
                        "constructs an anonymous inner class, which needs an enclosing instance the " +
                        "static method has not got - give the anonymous class a name and declare it " +
                        "as a static nested class, or drop .staticMethod()");
                return false;
            }
            ClassNode enclosing = host.getPlainNodeReference();
            outerField.setType(enclosing);
            for (ConstructorNode constructor : inner.getDeclaredConstructors()) {
                Parameter[] parameters = constructor.getParameters();
                if (parameters.length > 0 && ClassHelper.CLOSURE_TYPE.equals(parameters[0].getType())) {
                    parameters[0].setType(enclosing);
                    // Both, and originType is the one that matters: static type checking compares
                    // arguments against Parameter.getOriginType() while the error it raises prints
                    // getType(), so setting only the latter fails the call and reports the two
                    // types as identical.
                    parameters[0].setOriginType(enclosing);
                }
            }
            List<Expression> arguments = ((TupleExpression) call.getArguments()).getExpressions();
            if (!arguments.isEmpty() && arguments.get(0) instanceof VariableExpression &&
                    "this".equals(((VariableExpression) arguments.get(0)).getName())) {
                VariableExpression thisExpression = new VariableExpression("this", enclosing);
                thisExpression.setSourcePosition(arguments.get(0));
                arguments.set(0, thisExpression);
            }
        }
        return true;
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

    private void processFieldStatement(ClassNode classNode, ClassNode declaringClass, MethodCallExpression baseCall,
            List<MethodCallExpression> qualifierCalls, SourceUnit source, Set<String> usedNames) {
        List<Expression> baseArgs = flatten(baseCall.getArguments());
        TypeAndName typeAndName = parseNameAndType(baseArgs, baseCall, source, FIELD_CALL, true, declaringClass);
        if (typeAndName == null) {
            return;
        }
        if (!registerName(typeAndName.name, baseCall, source, usedNames,
                "is already used by another member of the class (declared, inherited, or another field(...)/method(...) statement) - " +
                        "generated member names must be unique")) {
            return;
        }

        ClassNode fieldType = declaredType(typeAndName, qualifierCalls, null, false, source, FIELD_CALL);
        if (fieldType == null) {
            return;
        }

        FieldNode field = classNode.addField(typeAndName.name, Modifier.PRIVATE, fieldType, null);
        field.setSourcePosition(baseCall);

        for (MethodCallExpression qualifierCall : qualifierCalls) {
            if (TYPE_ARGUMENTS_CALL.equals(qualifierCall.getMethodAsString())) {
                continue;
            }
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
    /**
     * As {@link #resolveStringConstant(Expression)}, but also resolving a <i>bare</i> reference to a
     * static final String field on {@code declaringClass} - the class the {@code beans} block was
     * written on, which for a plugin descriptor is not the class the members land on.
     *
     * <p>Unqualified is how such a constant is written from inside the class that declares it, so it
     * is the spelling that matters here; the qualified form already resolved.</p>
     */
    private String resolveStringConstant(Expression expression) {
        return resolveStringConstant(expression, null);
    }

    private String resolveStringConstant(Expression expression, ClassNode declaringClass) {
        if (declaringClass != null && expression instanceof VariableExpression) {
            FieldNode field = findStaticFinalField(declaringClass, ((VariableExpression) expression).getName(),
                    new HashSet<>());
            if (field != null && field.getInitialExpression() instanceof ConstantExpression) {
                Object value = ((ConstantExpression) field.getInitialExpression()).getValue();
                return value instanceof String ? (String) value : null;
            }
            return null;
        }
        if (expression instanceof ConstantExpression) {
            Object value = ((ConstantExpression) expression).getValue();
            return value instanceof String ? (String) value : null;
        }
        if (expression instanceof BinaryExpression) {
            BinaryExpression binary = (BinaryExpression) expression;
            if (binary.getOperation().getType() != Types.PLUS) {
                return null;
            }
            String left = resolveStringConstant(binary.getLeftExpression(), declaringClass);
            String right = resolveStringConstant(binary.getRightExpression(), declaringClass);
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

    private void processMethodStatement(ClassNode classNode, ClassNode declaringClass,
            MethodCallExpression outerCall, MethodCallExpression baseCall,
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

        TypeAndName typeAndName = parseNameAndType(baseArgs, baseCall, source, METHOD_CALL, true, declaringClass);
        if (typeAndName == null) {
            return;
        }
        if (!registerName(typeAndName.name, baseCall, source, usedNames,
                "is already used by another member of the class (declared, inherited, or another field(...)/method(...) statement) - " +
                        "generated member names must be unique")) {
            return;
        }

        ClassNode returnType = declaredType(typeAndName, qualifierCalls, outerCall, true, source, METHOD_CALL);
        if (returnType == null) {
            return;
        }

        MethodNode helperMethod = new MethodNode(
                typeAndName.name,
                Modifier.PRIVATE,
                returnType,
                body.getParameters() == null ? Parameter.EMPTY_ARRAY : body.getParameters(),
                ClassNode.EMPTY_ARRAY,
                body.getCode());
        helperMethod.setSourcePosition(baseCall);

        for (MethodCallExpression qualifierCall : qualifierCalls) {
            if (TYPE_ARGUMENTS_CALL.equals(qualifierCall.getMethodAsString())) {
                continue;
            }
            List<Expression> qualifierArgs = flatten(qualifierCall.getArguments());
            if (qualifierCall == outerCall) {
                qualifierArgs = qualifierArgs.subList(0, qualifierArgs.size() - 1);
            }
            if (!applyGenericAnnotation(helperMethod, qualifierCall, qualifierArgs, source)) {
                return;
            }
        }

        // A helper's body is lifted out of the closure exactly as a bean's is, so it needs the same
        // correction - always non-static, there being no .staticMethod() for method(...).
        if (!rehomeAnonymousInnerClasses(body.getCode(), classNode, false, typeAndName.name, source)) {
            return;
        }

        classNode.addMethod(helperMethod);
    }

    /**
     * The declared type of a bean, field or helper method, with any {@code .typeArguments(...)}
     * applied - {@code bean("auditorAware", AuditorAware).typeArguments(String)} declares
     * {@code AuditorAware<String>}.
     *
     * <p>A type argument is not decoration. Spring resolves an injection point by its full generic
     * type, so a bean declared raw where a consumer asks for {@code Repository<User>} may not match,
     * and {@code ObjectProvider<Handler<Order>>} or an injected {@code List<Handler<Order>>} cannot
     * select it at all. The type in {@code bean(...)} is a class literal and Groovy has no syntax
     * for writing type arguments on one, so without this the only way to declare a parameterized
     * bean type was to declare the implementation class instead and let Spring read the arguments
     * off its hierarchy - which is not always the type the author wants the bean known by.
     *
     * @return the type to declare, or {@code null} when the qualifier is present but malformed
     * (the error is already reported)
     */
    private ClassNode declaredType(TypeAndName typeAndName, List<MethodCallExpression> qualifierCalls,
            MethodCallExpression outerCall, boolean outerCallCarriesClosure, SourceUnit source, String callName) {
        ClassNode raw = typeAndName.type.getType();
        for (MethodCallExpression qualifierCall : qualifierCalls) {
            if (!TYPE_ARGUMENTS_CALL.equals(qualifierCall.getMethodAsString())) {
                continue;
            }
            List<Expression> args = flatten(qualifierCall.getArguments());
            if (outerCallCarriesClosure && qualifierCall == outerCall && !args.isEmpty()) {
                args = args.subList(0, args.size() - 1);
            }
            String rawName = raw.getNameWithoutPackage();
            if (args.isEmpty()) {
                addError(qualifierCall, source, ".typeArguments(...) requires at least one type, e.g. " +
                        callName + "(..., " + rawName + ").typeArguments(String)");
                return null;
            }
            GenericsType[] typeArguments = new GenericsType[args.size()];
            for (int i = 0; i < args.size(); i++) {
                if (!(args.get(i) instanceof ClassExpression)) {
                    addError(args.get(i), source, ".typeArguments(...) takes types, e.g. " +
                            ".typeArguments(String) or .typeArguments(String, Integer)");
                    return null;
                }
                typeArguments[i] = new GenericsType(((ClassExpression) args.get(i)).getType());
            }
            // The type parameters the declared type actually has. Checking the count here turns a
            // mismatch into a located error naming both numbers, rather than an unchecked generic
            // signature that only misleads whoever reads the bean's type later.
            GenericsType[] declared = raw.redirect().getGenericsTypes();
            if (declared == null || declared.length == 0) {
                addError(qualifierCall, source, rawName + " is not a generic type, so it has no type " +
                        "arguments to give");
                return null;
            }
            if (declared.length != typeArguments.length) {
                addError(qualifierCall, source, rawName + " declares " + declared.length +
                        " type parameter" + (declared.length == 1 ? "" : "s") + ", so .typeArguments(...) takes " +
                        declared.length + ", not " + typeArguments.length);
                return null;
            }
            return GenericsUtils.makeClassSafeWithGenerics(raw, typeArguments);
        }
        return raw;
    }

    private static final class TypeAndName {
        private final ClassExpression type;
        private final String name;
        /** The type actually constructed, when stated separately from the declared type; else null. */
        private final ClassExpression implementation;

        TypeAndName(ClassExpression type, String name, ClassExpression implementation) {
            this.type = type;
            this.name = name;
            this.implementation = implementation;
        }
    }

    private TypeAndName parseNameAndType(List<Expression> args, MethodCallExpression call, SourceUnit source,
            String callName, boolean requireValidIdentifier, ClassNode declaringClass) {
        return parseNameAndType(args, call, source, callName, requireValidIdentifier, false, declaringClass);
    }

    /**
     * Reads the {@code [name, ] Type [, Implementation]} head of a DSL statement.
     *
     * <p>The implementation type is what separates the declared type from the constructed one, so
     * that a bean can be declared as the interface its consumers inject while still being built
     * without a factory closure. It is recognised only by two adjacent type literals - the trailing
     * one is the implementation - which cannot collide with the {@code (name, Type)} shape, since a
     * name is a String literal.</p>
     */
    private TypeAndName parseNameAndType(List<Expression> args, MethodCallExpression call, SourceUnit source,
            String callName, boolean requireValidIdentifier, boolean allowImplementation,
            ClassNode declaringClass) {
        // Two trailing type literals mean the second is the implementation. Split it off first so
        // everything below reads the same [name, ] Type head it always did.
        ClassExpression implementation = null;
        if (allowImplementation && args.size() >= 2 &&
                args.get(args.size() - 1) instanceof ClassExpression &&
                args.get(args.size() - 2) instanceof ClassExpression) {
            implementation = (ClassExpression) args.get(args.size() - 1);
            args = args.subList(0, args.size() - 1);
        }
        int maxArgs = 2;
        if (args.isEmpty() || args.size() > maxArgs || !(args.get(args.size() - 1) instanceof ClassExpression)) {
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
            // A literal or any compile-time String constant - the same folding .value(...) does for a
            // config key, and for the same reason. A bean name is often already a constant, because
            // something else has to look the bean up by it; making the DSL the one place that cannot
            // say the constant's name would mean writing the string twice and letting the two drift.
            String nameValue = resolveStringConstant(nameArg, declaringClass);
            if (nameValue == null) {
                addError(nameArg, source, callName + "(name, Type) requires the name to be a String literal " +
                        "or a compile-time String constant, e.g. " + callName + "(\"myGreeter\", Greeter) or " +
                        callName + "(VIEW_LOADER_BEAN, GroovyPageResourceLoader)");
                return null;
            }
            name = nameValue;
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
        return new TypeAndName(type, name, implementation);
    }

    private boolean applyQualifier(MethodNode beanMethod, String beanName, MethodCallExpression qualifierCall,
            List<Expression> args, SourceUnit source) {
        String name = qualifierCall.getMethodAsString();
        // Consumed by declaredType(...) before the method node existed - it shapes the declared type
        // rather than attaching anything to the member.
        if (TYPE_ARGUMENTS_CALL.equals(name)) {
            return true;
        }
        if (CONDITIONAL_ON_BEAN_CALL.equals(name)) {
            AnnotationNode annotation = conditionalOnBeanAnnotation(args, qualifierCall, source);
            return annotation != null && addAnnotationIfAbsent(beanMethod, qualifierCall, annotation, source);
        }
        if (CONDITIONAL_ON_MISSING_BEAN_CALL.equals(name)) {
            AnnotationNode annotation = conditionalOnMissingBeanAnnotation(args, qualifierCall, source);
            return annotation != null && addAnnotationIfAbsent(beanMethod, qualifierCall, annotation, source);
        }
        if (CONDITIONAL_ON_PROPERTY_CALL.equals(name)) {
            AnnotationNode annotation = conditionalOnPropertyAnnotation(args, qualifierCall, source);
            return annotation != null && addAnnotationIfAbsent(beanMethod, qualifierCall, annotation, source);
        }
        if (CONDITIONAL_ON_EXPRESSION_CALL.equals(name)) {
            AnnotationNode annotation = conditionalOnExpressionAnnotation(args, qualifierCall, source);
            return annotation != null && addAnnotationIfAbsent(beanMethod, qualifierCall, annotation, source);
        }
        if (CONDITIONAL_ON_CLASS_CALL.equals(name)) {
            AnnotationNode annotation = conditionalOnClassAnnotation(args, qualifierCall, source);
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
        if (ALIASES_CALL.equals(name)) {
            return applyAliasesQualifier(beanMethod, beanName, qualifierCall, args, source);
        }
        if (CONDITIONAL_ON_GRAILS_ENV_CALL.equals(name)) {
            return applyConditionalOnGrailsEnvQualifier(beanMethod, qualifierCall, args, source);
        }
        return applyGenericAnnotation(beanMethod, qualifierCall, args, source);
    }

    /**
     * Additional names for the bean, appended to {@code @Bean}'s value - which Spring reads as the
     * canonical name followed by aliases.
     *
     * <p>Separate from {@code bean("name", Type)} rather than more arguments to it, because the
     * canonical name is not one of a list: it is what the generated method is named after, what
     * duplicate-name validation runs against, and what {@code .conditionalOnMissingBeanName()}
     * resolves to. Aliases are none of those things, and saying so in their own call keeps which
     * name is which visible at the callsite.</p>
     *
     * <p>The case they exist for is a migration: something reachable under an old name that must
     * stay reachable while its callers move to the new one.</p>
     */
    private boolean applyAliasesQualifier(MethodNode beanMethod, String beanName,
            MethodCallExpression qualifierCall, List<Expression> args, SourceUnit source) {
        if (args.isEmpty()) {
            addError(qualifierCall, source, ".aliases(...) requires at least one additional name, " +
                    "e.g. .aliases(\"legacyName\")");
            return false;
        }
        List<AnnotationNode> existing = beanMethod.getAnnotations(ClassHelper.make(Bean.class));
        if (existing.isEmpty()) {
            addError(qualifierCall, source, ".aliases(...) applies to bean(...) declarations only");
            return false;
        }
        Expression namesMember = existing.get(0).getMember("value");
        if (!(namesMember instanceof ListExpression)) {
            addError(qualifierCall, source, ".aliases(...) cannot be combined with a @Bean whose names " +
                    "were set another way");
            return false;
        }
        ListExpression names = (ListExpression) namesMember;
        Set<String> seen = new HashSet<>();
        seen.add(beanName);
        for (Expression arg : args) {
            Expression folded = foldStringValue(arg);
            Object value = folded instanceof ConstantExpression ? ((ConstantExpression) folded).getValue() : null;
            if (!(value instanceof String) || ((String) value).isBlank()) {
                addError(arg, source, ".aliases(...) takes non-blank names as Strings, " +
                        "e.g. .aliases(\"legacyName\")");
                return false;
            }
            String alias = (String) value;
            if (!seen.add(alias)) {
                addError(arg, source, alias.equals(beanName) ?
                        "\"" + alias + "\" is this bean's own name, so it is not an alias of anything" :
                        "\"" + alias + "\" is given as an alias twice");
                return false;
            }
            names.addExpression(folded);
        }
        return true;
    }

    /**
     * {@code @Scope} - the scope name positionally, the annotation's other attributes by name, as
     * every other attribute-bearing qualifier takes them.
     *
     * <pre>{@code
     * .scope('prototype')
     * .scope('session', proxyMode: ScopedProxyMode.TARGET_CLASS)
     * }</pre>
     *
     * <p>{@code proxyMode} is why this takes attributes at all. A session- or request-scoped bean
     * injected into a singleton needs {@code TARGET_CLASS}, or the singleton captures one scope
     * instance for the lifetime of the application and serves it to everyone - a wrong answer
     * rather than an error. Leaving it to {@code .annotate(Scope, ...)} put the fix somewhere other
     * than where the mistake is written.</p>
     */
    private boolean applyScopeQualifier(MethodNode beanMethod, MethodCallExpression qualifierCall,
            List<Expression> args, SourceUnit source) {
        MapExpression members = !args.isEmpty() && args.get(0) instanceof MapExpression ? (MapExpression) args.get(0) : null;
        List<Expression> positional = members != null ? args.subList(1, args.size()) : args;
        if (positional.size() > 1) {
            addError(qualifierCall, source, ".scope(...) takes one scope name, e.g. " +
                    ".scope(\"session\", proxyMode: ScopedProxyMode.TARGET_CLASS)");
            return false;
        }
        if (positional.isEmpty() && !namesAnyOf(members, SCOPE_NAME_MEMBERS)) {
            addError(qualifierCall, source, ".scope(...) needs a scope name, positionally or as " +
                    "value:/scopeName: - e.g. .scope(\"prototype\")");
            return false;
        }
        if (!positional.isEmpty() && rejectPositionalAndNamed(members, SCOPE_NAME_MEMBERS, SCOPE_CALL,
                "a scope name", qualifierCall, source)) {
            return false;
        }
        AnnotationNode scopeAnnotation = new AnnotationNode(ClassHelper.make(Scope.class));
        if (members != null && !addMembersFromMap(scopeAnnotation, members, qualifierCall, source)) {
            return false;
        }
        if (!positional.isEmpty()) {
            Expression folded = foldStringValue(positional.get(0));
            Object scopeValue = folded instanceof ConstantExpression ? ((ConstantExpression) folded).getValue() : null;
            if (!(scopeValue instanceof String) || ((String) scopeValue).isEmpty()) {
                addError(qualifierCall, source, ".scope(...) requires a non-empty String scope name, " +
                        "e.g. .scope(\"prototype\")");
                return false;
            }
            scopeAnnotation.setMember("value", folded);
        }
        return addAnnotationIfAbsent(beanMethod, qualifierCall, scopeAnnotation, source);
    }

    /** {@code @Scope}'s attributes that name the scope; aliases of each other. */
    private static final Set<String> SCOPE_NAME_MEMBERS = Set.of("value", "scopeName");

    /**
     * Compiles {@code .conditionalOnGrailsEnv("development"[, ...])} into {@code @ConditionalOnGrailsEnv}.
     *
     * <p>Not {@code @ConditionalOnProperty(name = "grails.env", ...)}, which is what this would
     * otherwise be written as and is wrong: Grails infers an environment when none was set, so the
     * property is absent on exactly the runs the condition is meant to describe, and the bean goes
     * missing with nothing to show for it.</p>
     */
    // AnnotatedNode, not MethodNode: the same qualifier attaches to a group's nested class.
    private boolean applyConditionalOnGrailsEnvQualifier(AnnotatedNode beanMethod, MethodCallExpression qualifierCall,
            List<Expression> args, SourceUnit source) {
        if (args.isEmpty()) {
            addError(qualifierCall, source, ".conditionalOnGrailsEnv(...) requires at least one environment name, " +
                    "e.g. .conditionalOnGrailsEnv(\"development\")");
            return false;
        }
        ListExpression names = new ListExpression();
        for (Expression arg : args) {
            Expression folded = foldStringValue(arg);
            Object value = folded instanceof ConstantExpression ? ((ConstantExpression) folded).getValue() : null;
            if (!(value instanceof String) || ((String) value).isBlank()) {
                addError(arg, source, ".conditionalOnGrailsEnv(...) takes non-blank environment names as Strings, " +
                        "e.g. .conditionalOnGrailsEnv(\"development\", \"test\")");
                return false;
            }
            names.addExpression(folded);
        }
        AnnotationNode annotation = new AnnotationNode(ClassHelper.make(ConditionalOnGrailsEnv.class));
        annotation.setMember("value", names);
        return addAnnotationIfAbsent(beanMethod, qualifierCall, annotation, source);
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

        // Spring never reads a field(...) or method(...) member as a bean, so @Bean on one is a
        // mistake rather than something to merge into a @Bean that was never attached.
        if (annotationType.getName().equals(Bean.class.getName()) &&
                target.getAnnotations(ClassHelper.make(Bean.class)).isEmpty()) {
            addError(qualifierCall, source, ".annotate(Bean, ...) applies to bean(...) declarations - " +
                    "field(...) and method(...) declare plain members, which Spring never reads as beans");
            return false;
        }

        // An annotation a QUALIFIER attached is merged into, not collided with: a qualifier sets only
        // the attribute it exists for - bean(...) the name, .scope(...) the scope - so without this
        // the annotation's remaining attributes have no spelling in the DSL at all. One that an
        // earlier .annotate(...) attached is a different matter: .annotate(...) already takes every
        // attribute at once, so writing it twice for one type is a mistake, not an addition, and
        // still reports as one.
        List<AnnotationNode> existing = target.getAnnotations(annotationType);
        if (!existing.isEmpty() && existing.get(0).getNodeMetaData(ANNOTATE_ATTACHED) == null) {
            return mergeIntoAnnotation(existing.get(0), annotationType, members, qualifierCall, source);
        }

        AnnotationNode annotation = new AnnotationNode(annotationType);
        annotation.putNodeMetaData(ANNOTATE_ATTACHED, Boolean.TRUE);
        if (members != null && !addMembersFromMap(annotation, members, qualifierCall, source)) {
            return false;
        }
        return addAnnotationIfAbsent(target, qualifierCall, annotation, source);
    }

    /** Marks an annotation node as one {@code .annotate(...)} attached, rather than a qualifier. */
    private static final String ANNOTATE_ATTACHED =
            GrailsBeansASTTransformation.class.getName() + ".annotateAttached";

    /**
     * Attributes that alias one another, so setting one when the other is already set is the same
     * contradiction as setting it twice - and reads as an addition rather than a conflict, which is
     * why it is worth naming rather than leaving to Spring's runtime "different values" error.
     */
    private static final Map<String, Set<String>> ALIASED_ANNOTATION_MEMBERS = Map.of(
            Bean.class.getName(), Set.of("value", "name"),
            Scope.class.getName(), Set.of("value", "scopeName"),
            ConditionalOnProperty.class.getName(), Set.of("value", "name"));

    /**
     * Folds {@code .annotate(X, ...)} attributes into an {@code @X} a qualifier already attached.
     *
     * <p>Without it those attributes are unreachable: a qualifier sets only what it exists for, so
     * {@code bean(...)} leaves every other {@code @Bean} attribute unsettable - {@code destroyMethod}
     * being the one that matters, since Spring otherwise infers {@code close()} and calls it on a
     * client the application does not own - and {@code .scope("session")} leaves
     * {@code proxyMode}, without which a scoped bean injected into a singleton silently captures one
     * instance forever.</p>
     *
     * <p>What the qualifier itself set is not re-settable here. Restating it could only disagree
     * with the value the rest of the block was built around, and for a name that value is what
     * duplicate-name validation already ran against.</p>
     */
    private boolean mergeIntoAnnotation(AnnotationNode annotation, ClassNode annotationType,
            MapExpression members, MethodCallExpression qualifierCall, SourceUnit source) {
        String name = annotationType.getNameWithoutPackage();
        if (members == null || members.getMapEntryExpressions().isEmpty()) {
            addError(qualifierCall, source, ".annotate(" + name + ") adds nothing, since @" + name +
                    " is already attached here - give the attributes to set, e.g. " +
                    ".annotate(Bean, destroyMethod: \"\")");
            return false;
        }
        Set<String> aliases = ALIASED_ANNOTATION_MEMBERS.getOrDefault(annotationType.getName(), Set.of());
        for (MapEntryExpression entry : members.getMapEntryExpressions()) {
            Object keyValue = entry.getKeyExpression() instanceof ConstantExpression ?
                    ((ConstantExpression) entry.getKeyExpression()).getValue() : null;
            if (!(keyValue instanceof String)) {
                addError(qualifierCall, source, ".annotate(...) attribute names must be simple " +
                        "identifiers, e.g. .annotate(Bean, destroyMethod: \"\")");
                return false;
            }
            String key = (String) keyValue;
            String taken = takenMember(annotation, key, aliases);
            if (taken != null) {
                if (annotationType.getName().equals(Bean.class.getName()) && aliases.contains(taken)) {
                    addError(qualifierCall, source, "a bean's name comes from bean(\"name\", Type), so " +
                            ".annotate(Bean, " + key + ": ...) would state it twice - rename it there instead");
                }
                else {
                    addError(qualifierCall, source, "@" + name + "'s \"" + taken + "\" is already set here" +
                            (taken.equals(key) ? "" : ", and \"" + key + "\" aliases it") +
                            " - set it where it is stated, not a second time");
                }
                return false;
            }
            annotation.setMember(key, foldStringValue(entry.getValueExpression()));
        }
        return true;
    }

    /** The member already carrying {@code key}'s value - {@code key} itself, or an alias of it. */
    private String takenMember(AnnotationNode annotation, String key, Set<String> aliases) {
        if (annotation.getMember(key) != null) {
            return key;
        }
        if (aliases.contains(key)) {
            for (String alias : aliases) {
                if (annotation.getMember(alias) != null) {
                    return alias;
                }
            }
        }
        return null;
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
        return beanConditionAnnotation(ConditionalOnMissingBean.class, CONDITIONAL_ON_MISSING_BEAN_CALL,
                args, qualifierCall, source);
    }

    /**
     * {@code @ConditionalOnBean}, the positive counterpart - "register this only when something else
     * already supplied that".
     *
     * <p>Unlike its opposite it takes no zero-argument form. With nothing named, Spring deduces the
     * type from the annotated method's own return type, which for
     * {@code @ConditionalOnMissingBean} is the whole point - back off if someone else provided this
     * - and for {@code @ConditionalOnBean} asks it to register a bean only when a bean of that same
     * type already exists. That is answerable, and almost never what anybody means.</p>
     */
    private AnnotationNode conditionalOnBeanAnnotation(List<Expression> args,
            MethodCallExpression qualifierCall, SourceUnit source) {
        if (args.isEmpty()) {
            addError(qualifierCall, source, ".conditionalOnBean() needs at least one type or attribute. With " +
                    "none, Spring deduces the type from this bean's own return type, so the condition reads " +
                    "\"register this bean only when a bean of its type already exists\" - name what it actually " +
                    "depends on, e.g. .conditionalOnBean(SmsTransport)");
            return null;
        }
        return beanConditionAnnotation(ConditionalOnBean.class, CONDITIONAL_ON_BEAN_CALL,
                args, qualifierCall, source);
    }

    /**
     * {@code @ConditionalOnProperty} - "register this only when the configuration says so", and by
     * some distance the most common condition in an application's own wiring. Positional arguments
     * are property names; named ones are the annotation's remaining attributes.
     *
     * <pre>{@code
     * .conditionalOnProperty('app.offline', havingValue: 'false', matchIfMissing: true)
     * .conditionalOnProperty(prefix: 'app', name: 'offline', havingValue: 'false')
     * }</pre>
     *
     * <p>A bare form would be an annotation naming no property, which Spring rejects at runtime, so
     * it is rejected here where the line is written.</p>
     */
    private AnnotationNode conditionalOnPropertyAnnotation(List<Expression> args,
            MethodCallExpression qualifierCall, SourceUnit source) {
        MapExpression members = !args.isEmpty() && args.get(0) instanceof MapExpression ? (MapExpression) args.get(0) : null;
        List<Expression> names = members != null ? args.subList(1, args.size()) : args;
        if (names.isEmpty() && !namesAnyOf(members, PROPERTY_NAME_MEMBERS)) {
            addError(qualifierCall, source, ".conditionalOnProperty(...) needs at least one property name, " +
                    "positionally or as name:/value: - e.g. .conditionalOnProperty(\"app.offline\", " +
                    "havingValue: \"false\")");
            return null;
        }
        if (!names.isEmpty() && rejectPositionalAndNamed(members, PROPERTY_NAME_MEMBERS,
                CONDITIONAL_ON_PROPERTY_CALL, "property names", qualifierCall, source)) {
            return null;
        }
        AnnotationNode annotation = new AnnotationNode(ClassHelper.make(ConditionalOnProperty.class));
        if (members != null && !addMembersFromMap(annotation, members, qualifierCall, source)) {
            return null;
        }
        if (!names.isEmpty()) {
            ListExpression nameList = new ListExpression();
            for (Expression name : names) {
                Expression folded = foldStringValue(name);
                Object value = folded instanceof ConstantExpression ? ((ConstantExpression) folded).getValue() : null;
                if (!(value instanceof String) || ((String) value).isBlank()) {
                    addError(name, source, ".conditionalOnProperty(...) takes non-blank property names as " +
                            "Strings, e.g. .conditionalOnProperty(\"app.offline\")");
                    return null;
                }
                nameList.addExpression(folded);
            }
            annotation.setMember("name", nameList);
        }
        return annotation;
    }

    /**
     * {@code @ConditionalOnExpression} - the condition for what the others cannot say, most often
     * because it is a disjunction and {@code @ConditionalOnProperty} only ever conjoins:
     *
     * <pre>{@code
     * .conditionalOnExpression('!${aws.ses.enabled:false} or ${app.offline:false}')
     * }</pre>
     *
     * <p>Write it single-quoted in Groovy: the {@code ${...}} placeholders are Spring's, resolved
     * before the expression is evaluated, and a double-quoted string would have Groovy interpolate
     * them away at compile time into whatever is in scope - usually nothing, silently.</p>
     */
    private AnnotationNode conditionalOnExpressionAnnotation(List<Expression> args,
            MethodCallExpression qualifierCall, SourceUnit source) {
        Expression folded = args.size() == 1 ? foldStringValue(args.get(0)) : null;
        Object value = folded instanceof ConstantExpression ? ((ConstantExpression) folded).getValue() : null;
        if (!(value instanceof String) || ((String) value).isBlank()) {
            addError(qualifierCall, source, ".conditionalOnExpression(...) takes exactly one non-blank " +
                    "String expression, e.g. .conditionalOnExpression('${app.offline:false}')");
            return null;
        }
        AnnotationNode annotation = new AnnotationNode(ClassHelper.make(ConditionalOnExpression.class));
        annotation.setMember("value", folded);
        return annotation;
    }

    /** The attributes of {@code @ConditionalOnProperty} that name properties; aliases of each other. */
    private static final Set<String> CLASS_TYPE_MEMBERS = Set.of("value");
    private static final Set<String> CLASS_NAME_MEMBERS = Set.of("name");

    /**
     * {@code @ConditionalOnClass} - "register this only when that class is on the classpath", the
     * condition an optional integration is gated on.
     *
     * <p>Takes class literals and String names in the same positional list, because the two are not
     * interchangeable and the choice is the whole point. A literal is the readable form and is
     * checked by the compiler, but it can only be written for a class this module compiles against;
     * naming a class that may be absent has to be a String, or the reference itself is the thing
     * that fails. Spring's annotation carries both for that reason ({@code value} and {@code name}),
     * and so does this.</p>
     *
     * <p>Note where the condition is written. Spring reads it from the bytecode before loading
     * anything, but a {@code @Bean} method's parameter and return types are resolved when the
     * configuration class is parsed - so guarding a method whose own signature names the absent
     * class is not reliably safe. Gate at class level for that: a separate
     * {@code @GrailsBeans @AutoConfiguration} class carrying {@code @ConditionalOnClass}, holding
     * the beans that mention the optional type.</p>
     */
    private AnnotationNode conditionalOnClassAnnotation(List<Expression> args,
            MethodCallExpression qualifierCall, SourceUnit source) {
        MapExpression members = !args.isEmpty() && args.get(0) instanceof MapExpression ? (MapExpression) args.get(0) : null;
        List<Expression> positional = members != null ? args.subList(1, args.size()) : args;
        if (positional.isEmpty() && !namesAnyOf(members, CLASS_TYPE_MEMBERS) && !namesAnyOf(members, CLASS_NAME_MEMBERS)) {
            addError(qualifierCall, source, ".conditionalOnClass(...) needs at least one class, as a type or " +
                    "a String name - e.g. .conditionalOnClass(MongoLockProvider) for a class this module " +
                    "compiles against, or .conditionalOnClass(name: \"net.javacrumbs.shedlock.provider.mongo." +
                    "MongoLockProvider\") for one that may be absent");
            return null;
        }
        ListExpression types = new ListExpression();
        ListExpression names = new ListExpression();
        for (Expression arg : positional) {
            if (arg instanceof ClassExpression) {
                types.addExpression(arg);
                continue;
            }
            Expression folded = foldStringValue(arg);
            Object value = folded instanceof ConstantExpression ? ((ConstantExpression) folded).getValue() : null;
            if (!(value instanceof String) || ((String) value).isBlank()) {
                addError(arg, source, ".conditionalOnClass(...) takes types or non-blank String class names, " +
                        "e.g. .conditionalOnClass(MongoLockProvider) or " +
                        ".conditionalOnClass(\"net.javacrumbs.shedlock.provider.mongo.MongoLockProvider\")");
                return null;
            }
            names.addExpression(folded);
        }
        if (!types.getExpressions().isEmpty() && rejectPositionalAndNamed(members, CLASS_TYPE_MEMBERS,
                CONDITIONAL_ON_CLASS_CALL, "types", qualifierCall, source)) {
            return null;
        }
        if (!names.getExpressions().isEmpty() && rejectPositionalAndNamed(members, CLASS_NAME_MEMBERS,
                CONDITIONAL_ON_CLASS_CALL, "class names", qualifierCall, source)) {
            return null;
        }
        AnnotationNode annotation = new AnnotationNode(ClassHelper.make(ConditionalOnClass.class));
        if (members != null && !addMembersFromMap(annotation, members, qualifierCall, source)) {
            return null;
        }
        if (!types.getExpressions().isEmpty()) {
            annotation.setMember("value", types);
        }
        if (!names.getExpressions().isEmpty()) {
            annotation.setMember("name", names);
        }
        return annotation;
    }

    private static final Set<String> PROPERTY_NAME_MEMBERS = Set.of("name", "value");

    private boolean namesAnyOf(MapExpression members, Set<String> keys) {
        if (members == null) {
            return false;
        }
        for (MapEntryExpression entry : members.getMapEntryExpressions()) {
            Object keyValue = entry.getKeyExpression() instanceof ConstantExpression ?
                    ((ConstantExpression) entry.getKeyExpression()).getValue() : null;
            if (keys.contains(keyValue)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rejects saying the same thing positionally and by name. Which member the positional arguments
     * fill differs per condition - types go to {@code value}, property names to {@code name} - so
     * the members that would collide are passed in rather than assumed.
     */
    private boolean rejectPositionalAndNamed(MapExpression members, Set<String> aliasMembers, String callName,
            String what, MethodCallExpression qualifierCall, SourceUnit source) {
        if (!namesAnyOf(members, aliasMembers)) {
            return false;
        }
        addError(qualifierCall, source, callName + "(...) was given " + what + " both positionally and via " +
                String.join(":/", aliasMembers.stream().sorted().toList()) + ": - use one or the other");
        return true;
    }

    // Shared by both: positional types go to value, named arguments are the annotation's own
    // attributes, and giving types both ways at once is rejected.
    private AnnotationNode beanConditionAnnotation(Class<?> annotationType, String callName, List<Expression> args,
            MethodCallExpression qualifierCall, SourceUnit source) {
        AnnotationNode annotation = new AnnotationNode(ClassHelper.make(annotationType));
        MapExpression members = !args.isEmpty() && args.get(0) instanceof MapExpression ? (MapExpression) args.get(0) : null;
        List<Expression> types = members != null ? args.subList(1, args.size()) : args;
        if (!types.isEmpty() && rejectPositionalAndNamed(members, Set.of("value"), callName, "types",
                qualifierCall, source)) {
            return null;
        }
        if (members != null && !addMembersFromMap(annotation, members, qualifierCall, source)) {
            return null;
        }
        if (!types.isEmpty()) {
            ListExpression typeList = new ListExpression();
            for (Expression type : types) {
                if (!(type instanceof ClassExpression)) {
                    addError(qualifierCall, source, callName + "(...) arguments must be types " +
                            "and/or named attributes, e.g. " + callName + "(Greeter) or " + callName +
                            "(name: \"greeter\", search: SearchStrategy.CURRENT)");
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
