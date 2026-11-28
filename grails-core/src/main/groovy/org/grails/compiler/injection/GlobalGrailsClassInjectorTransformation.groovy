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
package org.grails.compiler.injection

import groovy.transform.CompilationUnitAware
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.codehaus.groovy.transform.TransformWithPriority

import org.jspecify.annotations.Nullable

import grails.artefact.Artefact
import grails.compiler.ast.ClassInjector
import grails.core.ArtefactHandler
import grails.plugins.metadata.GrailsPluginDescriptor
import org.apache.grails.common.compiler.GroovyTransformOrder
import org.grails.compiler.beans.AutoConfigurationImportsWriter
import org.grails.compiler.beans.GrailsBeansASTTransformation
import org.grails.core.io.support.GrailsFactoriesLoader
import org.grails.io.support.GrailsResourceUtils
import org.grails.io.support.UrlResource

/**
 * Global AST transformation that applies Grails compiler injection to Grails project sources,
 * including applications and plugins.
 *
 * <p>It identifies Grails artefacts, stamps {@link Artefact} metadata
 * annotations, applies the relevant {@link ClassInjector} and
 * {@link grails.compiler.traits.TraitInjector} implementations, compiles implicit {@code beans}
 * closures into Spring Boot auto-configurations, and registers artefact handlers and trait
 * injectors in {@code META-INF/grails.factories}.</p>
 *
 * <p>Plugin descriptors ({@code META-INF/grails/plugins.imports} and
 * {@code META-INF/grails/plugins/<name>/plugin.properties}) are written by
 * {@link PluginDescriptorTransformation} once the compilation has written its classes.</p>
 *
 * @since 3.0
 */
@Slf4j
@CompileStatic
@GroovyASTTransformation
class GlobalGrailsClassInjectorTransformation implements ASTTransformation, CompilationUnitAware, TransformWithPriority {

    /**
     * The system property signalling that a multi-project build compiles each project into its own
     * isolated output directory. When set, the transform must never fall back to a shared or guessed
     * location, which could leak one module's generated metadata into another.
     */
    public static final String ISOLATED_BUILD_PROPERTY = 'grails.isolated.build'

    public static final ClassNode ARTEFACT_CLASS_NODE = new ClassNode(Artefact)
    public static final ClassNode ARTEFACT_HANDLER_CLASS = ClassHelper.make('grails.core.ArtefactHandler')
    public static final ClassNode TRAIT_INJECTOR_CLASS = ClassHelper.make('grails.compiler.traits.TraitInjector')
    public static final ClassNode PLUGIN_DESCRIPTOR_ANNOTATION = ClassHelper.make(GrailsPluginDescriptor)

    private static final String GRAILS_AUTO_CONFIGURATION_CLASS_NAME = 'grails.boot.config.GrailsAutoConfiguration'
    private static final String BEANS_PROPERTY = 'beans'
    private static final ClassNode GRAILS_BEANS_ANNOTATION = ClassHelper.make('grails.compiler.beans.GrailsBeans')
    private static final Set<String> BEANS_DSL_ROOT_CALLS = ['bean', 'field', 'method', 'group'].toSet()

    CompilationUnit compilationUnit

    /**
     * Returns the ordering position used to run this transformation relative to other global
     * transformations.
     *
     * @return the global Grails transformation order
     */
    @Override
    int priority() {
        GroovyTransformOrder.GLOBAL_GRAILS_TRANSFORM_ORDER
    }

    /**
     * Applies Grails artefact and class injection to a project source.
     *
     * @param nodes AST nodes supplied by Groovy
     * @param source the source unit being compiled
     */
    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        def url = GrailsASTUtils.getSourceUrl(source)
        if (!shouldVisit(url)) {
            return
        }

        def transformedClassNames = new LinkedHashSet<String>()
        def compilationTargetDirectory = resolveCompilationTargetDirectory(source)
        def artefactHandlers = GrailsFactoriesLoader.loadFactories(ArtefactHandler)
        def classInjectorCache = new LinkedHashMap<String, List<ClassInjector>>().withDefault { String key ->
            ArtefactTypeAstTransformation.findInjectors(
                    key,
                    GrailsAwareInjectionOperation.classInjectors
            )
        }

        // Seeded before anything is transformed: an explicitly annotated descriptor is compiled by
        // the local transform after this runs, and the directory resolved here is the only one that
        // is right under Groovy-Eclipse.
        for (def classNode : source.AST.classes) {
            classNode.putNodeMetaData(
                    GrailsBeansASTTransformation.RESOLVED_TARGET_DIRECTORY_METADATA, compilationTargetDirectory)
        }

        for (def classNode : source.AST.classes.toList()) { // toList() to avoid concurrent modification exception
            if (isGrailsPluginDescriptorClass(classNode)) {
                compileBeansDsl(classNode, source)
                continue
            }
            if (GrailsASTUtils.isSubclassOfOrImplementsInterface(classNode, GRAILS_AUTO_CONFIGURATION_CLASS_NAME)) {
                compileBeansDsl(classNode, source)
            }
            // A unit test's beans compile onto a nested configuration class the testing support registers
            if (GrailsBeansASTTransformation.isUnitTest(classNode)) {
                compileBeansDsl(classNode, source)
            }
            if (updateGrailsFactoriesWithTypes(classNode, [ARTEFACT_HANDLER_CLASS, TRAIT_INJECTOR_CLASS], compilationTargetDirectory)) {
                continue
            }
            if (!GrailsResourceUtils.isGrailsResource(new UrlResource(url))) {
                continue
            }
            addImport(classNode, 'org.springframework.beans.factory.annotation.Autowired')

            for (def handler : artefactHandlers) {
                if (handler.isArtefact(classNode)) {
                    if (!classNode.getAnnotations(ARTEFACT_CLASS_NODE)) {
                        transformedClassNames.add(classNode.name)
                        addArtefactAnnotation(classNode, handler.type)
                        def classInjectors = classInjectorCache[handler.type]
                        for (def classInjector : classInjectors) {
                            if (classInjector instanceof CompilationUnitAware) {
                                ((CompilationUnitAware) classInjector).compilationUnit = compilationUnit
                            }
                        }
                        ArtefactTypeAstTransformation.performInjection(source, classNode, classInjectors)
                        TraitInjectionUtils.processTraitsForNode(source, classNode, handler.type, compilationUnit)
                    }
                }
            }

            if (!transformedClassNames.contains(classNode.name)) {
                def globalClassInjectors = GrailsAwareInjectionOperation.globalClassInjectors
                for (def classInjector : globalClassInjectors) {
                    classInjector.performInjection(source, classNode)
                }
            }
        }

        // The generated auto-configurations register themselves as they are created, but a descriptor
        // that was deleted, or that no longer has a beans closure, creates nothing and so says nothing
        // about the entry it used to leave behind. This runs for every source unit of a Grails
        // project, which is what makes the entry go when the class it names does.
        AutoConfigurationImportsWriter.reconcile(compilationTargetDirectory, compilationUnit, source)
    }

    private static boolean shouldVisit(@Nullable URL url) {
        url != null && GrailsResourceUtils.isProjectSource(new UrlResource(url))
    }

    /** Whether a class declares a Grails plugin: a concrete class carrying {@link GrailsPluginDescriptor}. */
    private static boolean isGrailsPluginDescriptorClass(ClassNode classNode) {
        !classNode.abstract && !classNode.getAnnotations(PLUGIN_DESCRIPTOR_ANNOTATION).isEmpty()
    }

    private static void addImport(ClassNode classNode, String className) {
        classNode.module.addImport(
                className.tokenize('.')[-1],
                ClassHelper.make(className)
        )
    }

    private static void addArtefactAnnotation(ClassNode classNode, String handlerType) {
        def annotationNode = new AnnotationNode(new ClassNode(Artefact))
        annotationNode.addMember('value', new ConstantExpression(handlerType))
        classNode.addAnnotation(annotationNode)
    }

    private void compileBeansDsl(ClassNode classNode, SourceUnit source) {
        PropertyNode beansProperty = classNode.getProperty(BEANS_PROPERTY)
        if (!classNode.getAnnotations(GRAILS_BEANS_ANNOTATION).isEmpty()) {
            return
        }
        if (beansProperty == null) {
            GrailsBeansASTTransformation.reportSharedBeans(classNode, source)
            return
        }
        List<Statement> statements = beansDslStatements(classNode, beansProperty)
        if (statements == null) {
            return
        }
        Statement stray = statements.find { Statement statement -> !isBeansDslStatement(statement) }
        if (stray != null) {
            reportStrayBeansStatement(statements, stray, source)
            return
        }
        GrailsBeansASTTransformation.reclaimMovedInitializer(classNode, beansProperty)

        GrailsBeansASTTransformation transformation = new GrailsBeansASTTransformation()
        transformation.compilationUnit = compilationUnit
        transformation.visit([new AnnotationNode(GRAILS_BEANS_ANNOTATION), classNode] as ASTNode[], source)
    }

    private static List<Statement> beansDslStatements(ClassNode classNode, PropertyNode beansProperty) {
        Expression initial = beansProperty.field?.initialExpression ?:
                GrailsBeansASTTransformation.movedInitializer(classNode, beansProperty.field)
        if (!(initial instanceof ClosureExpression)) {
            return null
        }
        Statement code = ((ClosureExpression) initial).code
        code instanceof BlockStatement ? ((BlockStatement) code).statements : null
    }

    private static boolean isBeansDslStatement(Statement statement) {
        if (!(statement instanceof ExpressionStatement)) {
            return false
        }
        Expression expression = ((ExpressionStatement) statement).expression
        while (expression instanceof MethodCallExpression) {
            MethodCallExpression call = (MethodCallExpression) expression
            if (call.methodAsString in BEANS_DSL_ROOT_CALLS) {
                return true
            }
            expression = call.objectExpression
        }
        false
    }

    private static void reportStrayBeansStatement(List<Statement> statements, Statement stray, SourceUnit source) {
        if (!statements.any { Statement statement -> isBeansDslStatement(statement) }) {
            return
        }
        GrailsASTUtils.error(source, stray, 'this statement is not a bean(...), field(...) or method(...) ' +
                "declaration, and every top-level statement in a 'beans' block must be one of those three. " +
                'To declare a bean conditionally, put the condition on the bean itself - ' +
                '.annotate(ConditionalOnProperty, ...) or .conditionalOnMissingBean() - rather than wrapping ' +
                'it in an if; for state or logic shared between beans, use field(...) or method(...). ' +
                "If this 'beans' property is not the beans DSL at all, rename it.")
    }

    static boolean isIsolatedBuild() {
        System.getProperty(ISOLATED_BUILD_PROPERTY, 'false').toBoolean()
    }

    static File resolveCompilationTargetDirectory(SourceUnit source) {
        resolveCompilationTargetDirectory(source, isolatedBuild)
    }

    static File resolveCompilationTargetDirectory(SourceUnit source, boolean isolatedBuild) {
        File targetDirectory
        if (source.class.name == 'org.codehaus.jdt.groovy.control.EclipseSourceUnit') {
            targetDirectory = GroovyEclipseCompilationHelper.resolveEclipseCompilationTargetDirectory(source)
        } else {
            targetDirectory = source.configuration.targetDirectory
        }
        if (targetDirectory == null) {
            if (isolatedBuild) {
                throw new IllegalStateException(
                        "Unable to resolve the compilation target directory for '${source?.name}' while the " +
                        "'${ISOLATED_BUILD_PROPERTY}' system property is set. Refusing to fall back to the shared " +
                        "relative 'build/classes/main' path, which would leak generated metadata between modules. " +
                        'Ensure the Groovy compiler supplies CompilerConfiguration.targetDirectory.')
            }
            targetDirectory = new File('build/classes/main')
        }
        return targetDirectory
    }

    static boolean updateGrailsFactoriesWithType(ClassNode classNode, ClassNode superType, File compilationTargetDirectory) {
        FactoriesFileWriter.updateFactoriesWithType(
                classNode,
                superType,
                compilationTargetDirectory,
                'META-INF/grails.factories',
                ['src/main/resources/META-INF/grails.factories']
        )
    }

    private static boolean updateGrailsFactoriesWithTypes(ClassNode classNode, Collection<ClassNode> superTypes, File compilationTargetDirectory) {
        boolean registered = false
        for (ClassNode superType : superTypes) {
            registered = updateGrailsFactoriesWithType(classNode, superType, compilationTargetDirectory) || registered
        }
        registered
    }
}
