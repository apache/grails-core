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

import java.lang.reflect.Modifier
import java.nio.charset.StandardCharsets

import javax.xml.parsers.ParserConfigurationException

import groovy.transform.CompilationUnitAware
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import groovy.xml.MarkupBuilder
import groovy.xml.StreamingMarkupBuilder
import groovy.xml.slurpersupport.GPathResult
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
import org.xml.sax.SAXException

import grails.artefact.Artefact
import grails.compiler.ast.ClassInjector
import grails.core.ArtefactHandler
import grails.io.IOUtils
import grails.plugins.metadata.GrailsPlugin
import grails.util.GrailsNameUtils
import org.apache.grails.common.compiler.GroovyTransformOrder
import org.grails.compiler.beans.AutoConfigurationImportsWriter
import org.grails.compiler.beans.GrailsBeansASTTransformation
import org.grails.core.io.support.GrailsFactoriesLoader
import org.grails.io.support.AntPathMatcher
import org.grails.io.support.GrailsResourceUtils
import org.grails.io.support.UrlResource

/**
 * Global AST transformation that applies Grails compiler injection to Grails project sources,
 * including applications and plugins.
 *
 * <p>It identifies Grails artefacts, applies the relevant {@link ClassInjector} and
 * {@link grails.compiler.traits.TraitInjector} implementations, and registers artefact handlers
 * and injector implementations. When compiling a plugin descriptor, it also creates or updates
 * the {@code META-INF/grails-plugin.xml} descriptor and records transformed plugin resources.</p>
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

    private static final String GRAILS_AUTO_CONFIGURATION_CLASS_NAME = 'grails.boot.config.GrailsAutoConfiguration'
    private static final String BEANS_PROPERTY = 'beans'
    private static final ClassNode GRAILS_BEANS_ANNOTATION = ClassHelper.make('grails.compiler.beans.GrailsBeans')
    private static final Set<String> BEANS_DSL_ROOT_CALLS = ['bean', 'field', 'method', 'group'].toSet()

    private static final AntPathMatcher ANT_PATH_MATCHER = new AntPathMatcher()

    private final LinkedHashSet<String> pendingPluginClassNames = []
    private final Collection<String> pluginExcludePatterns = []

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
     * Applies Grails artefact and class injection to a project source and updates the generated
     * plugin metadata for the compiled classes.
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

        ClassNode pluginClassNode = null
        String pluginVersion = null
        def transformedClassNames = new LinkedHashSet<String>()
        def compilationTargetDirectory = resolveCompilationTargetDirectory(source)
        def pluginXmlFile = new File(compilationTargetDirectory, 'META-INF/grails-plugin.xml')
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
            def projectName = resolveProjectName(classNode)
            def projectVersion = resolveProjectVersion(classNode)
            if (isGrailsPluginDescriptorClass(classNode)) {
                pluginClassNode = classNode
                pluginVersion = resolvePluginVersion(classNode, projectVersion?.toString())
                addPluginVersionProperty(classNode, pluginVersion)
                compileBeansDsl(classNode, source)
                continue
            }
            if (GrailsASTUtils.isSubclassOfOrImplementsInterface(classNode, GRAILS_AUTO_CONFIGURATION_CLASS_NAME)) {
                compileBeansDsl(classNode, source)
            }
            if (updateGrailsFactoriesWithTypes(classNode, [ARTEFACT_HANDLER_CLASS, TRAIT_INJECTOR_CLASS], compilationTargetDirectory)) {
                continue
            }
            if (!GrailsResourceUtils.isGrailsResource(new UrlResource(url))) {
                continue
            }
            if (projectName && projectVersion) {
                addPluginAnnotation(classNode, projectName, projectVersion)
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

        if (validatePluginVersionDefined(pluginClassNode, pluginVersion, pluginXmlFile, source)) {
            // create or update grails-plugin.xml
            generatePluginXml(pluginClassNode, pluginVersion, transformedClassNames, pluginXmlFile)
        }

        // The generated auto-configurations register themselves as they are created, but a descriptor
        // that was deleted, or that no longer has a beans closure, creates nothing and so says nothing
        // about the entry it used to leave behind. This runs for every source unit of a Grails
        // project, which is what makes the entry go when the class it names does.
        AutoConfigurationImportsWriter.reconcile(compilationTargetDirectory, compilationUnit, source)
    }

    /**
     * Resolves the project version recorded in compiler metadata, falling back to the Grails
     * implementation version when no project version is available.
     *
     * @param classNode the class whose compiler metadata is inspected
     * @return the resolved project version, or {@code null} when neither compiler metadata nor
     *         the Grails implementation provides a version
     */
    private static @Nullable String resolveProjectVersion(ClassNode classNode) {
        def projectVersion = classNode.getNodeMetaData('projectVersion')?.toString()
        if (projectVersion == null) {
            // fallback to the version of the grails-core jar if no project version is available
            projectVersion = GlobalGrailsClassInjectorTransformation.package.implementationVersion
        }
        projectVersion
    }

    /**
     * Resolves the project name recorded in compiler metadata.
     *
     * @param classNode the class whose compiler metadata is inspected
     * @return the project name, or {@code null} when it is not present
     */
    private static @Nullable String resolveProjectName(ClassNode classNode) {
        classNode.getNodeMetaData('projectName')?.toString()
    }

    /**
     * Determines whether a source belongs to a project that should be processed by this
     * transformation.
     *
     * @param url the source URL
     * @return {@code true} when the URL identifies project source
     */
    private static boolean shouldVisit(@Nullable URL url) {
        url != null && GrailsResourceUtils.isProjectSource(new UrlResource(url))
    }

    /**
     * Determines whether a class is a concrete Grails plugin descriptor class.
     *
     * @param classNode the class to inspect
     * @return {@code true} when the class name ends with {@code GrailsPlugin} and is not abstract
     */
    private static boolean isGrailsPluginDescriptorClass(ClassNode classNode) {
        classNode.name.endsWith('GrailsPlugin') && !classNode.abstract
    }

    /**
     * Resolves the plugin version from compiler metadata or from the plugin class's declared
     * version property.
     *
     * @param classNode the plugin descriptor class
     * @param projectVersion the version recorded in compiler metadata
     * @return the resolved plugin version, or {@code null} when neither source defines one
     */
    private static @Nullable String resolvePluginVersion(ClassNode classNode, @Nullable String projectVersion) {
        if (projectVersion) {
            return projectVersion
        }
        def versionField = classNode.getDeclaredField('version')
        def initialExpression = versionField?.initialExpression
        initialExpression instanceof ConstantExpression ? initialExpression.text : null
    }

    /**
     * Adds the generated version property to a plugin descriptor class when it does not already
     * declare one.
     *
     * @param classNode the plugin descriptor class
     * @param pluginVersion the plugin version
     */
    private static void addPluginVersionProperty(ClassNode classNode, String pluginVersion) {
        if (!classNode.hasProperty('version')) {
            classNode.addProperty(
                    new PropertyNode(
                            'version',
                            Modifier.PUBLIC,
                            ClassHelper.make(Object),
                            classNode,
                            new ConstantExpression(pluginVersion),
                            null,
                            null
                    )
            )
        }
    }

    /**
     * Adds the Grails plugin annotation containing the project name and version to a class.
     *
     * @param classNode the class to annotate
     * @param projectName the project name
     * @param projectVersion the project version
     */
    private static void addPluginAnnotation(ClassNode classNode, String projectName, String projectVersion) {
        GrailsASTUtils.addAnnotationOrGetExisting(
                classNode,
                GrailsPlugin,
                [
                        name: GrailsNameUtils.getPropertyNameForLowerCaseHyphenSeparatedName(projectName),
                        version: projectVersion
                ] as Map<String, Object>
        )
    }

    /**
     * Adds an import to the module containing the class.
     *
     * @param classNode the class whose module should receive the import
     * @param className the fully qualified class name to import
     */
    private static void addImport(ClassNode classNode, String className) {
        classNode.module.addImport(
                className.tokenize('.')[-1],
                ClassHelper.make(className)
        )
    }

    /**
     * Adds an {@link Artefact} annotation identifying the artefact handler type.
     *
     * @param classNode the artefact class
     * @param handlerType the artefact handler type
     */
    private static void addArtefactAnnotation(ClassNode classNode, String handlerType) {
        def annotationNode = new AnnotationNode(new ClassNode(Artefact))
        annotationNode.addMember('value', new ConstantExpression(handlerType))
        classNode.addAnnotation(annotationNode)
    }

    private static boolean validatePluginVersionDefined(
            @Nullable ClassNode pluginClassNode,
            @Nullable String pluginVersion,
            File pluginXmlFile,
            SourceUnit sourceUnit
    ) {
        if (pluginClassNode && !pluginVersion) {
            GrailsASTUtils.error(sourceUnit, pluginClassNode,
                    "Unable to generate '$pluginXmlFile' because plugin class " +
                    "'$pluginClassNode.name' does not define a plugin version."
            )
            return false
        }
        true
    }

    /**
     * Compiles a plugin descriptor's or application class's {@code beans} closure into {@code @Bean}
     * factory methods, so {@code @GrailsBeans} does not have to be written out - the {@code beans}
     * property is a convention here in the same way {@code doWithSpring} and {@code watchedResources}
     * already are.
     *
     * <p>The transformation is invoked directly rather than by adding the annotation: annotation-driven
     * transformations are collected during semantic analysis, so an annotation added at
     * {@code CANONICALIZATION} would never fire. A class that already declares {@code @GrailsBeans}
     * is skipped, since its own transformation has run; a class without a {@code beans} property is
     * skipped too, which is every plugin that does not use the DSL.</p>
     *
     * <p>Because the annotation is implicit here, the property has to look like the DSL before it is
     * claimed. A pre-existing descriptor may already declare an unrelated {@code beans} property -
     * a Map, or a closure of something else entirely - and compiling that would fail it with a DSL
     * error it never asked for, which would be a source-incompatible change for third-party plugins.
     * Only a closure whose every top-level statement is a {@code bean}/{@code field}/{@code method}
     * call is taken; anything else is left alone. Writing {@code @GrailsBeans} explicitly opts back
     * in to the strict errors, which is the right behaviour when the author has said what they mean.</p>
     *
     * <p>A block that is <i>partly</i> DSL-shaped is neither, and is reported rather than dropped -
     * see {@link #reportStrayBeansStatement}.</p>
     */
    private void compileBeansDsl(ClassNode classNode, SourceUnit source) {
        PropertyNode beansProperty = classNode.getProperty(BEANS_PROPERTY)
        if (beansProperty == null || !classNode.getAnnotations(GRAILS_BEANS_ANNOTATION).isEmpty()) {
            return
        }
        List<Statement> statements = beansDslStatements(beansProperty)
        if (statements == null) {
            return
        }
        Statement stray = statements.find { Statement statement -> !isBeansDslStatement(statement) }
        if (stray != null) {
            reportStrayBeansStatement(statements, stray, source)
            return
        }

        // Referenced directly, as the registering below already does. grails-core declares
        // grails-beans-dsl api (see grails-core/build.gradle), so it reaches every project that has
        // grails-core at all; loading it reflectively described a class path this cannot be compiled
        // against, and guarded against something that would now fail on the next line regardless.
        GrailsBeansASTTransformation transformation = new GrailsBeansASTTransformation()
        transformation.compilationUnit = compilationUnit
        transformation.visit([new AnnotationNode(GRAILS_BEANS_ANNOTATION), classNode] as ASTNode[], source)
    }

    /**
     * The top-level statements of a {@code beans} closure, or {@code null} when the property could
     * never be the DSL - a Map, a String, a closure whose body is not a block. An empty block yields
     * an empty list rather than null: it is a no-op either way, and claiming it keeps the implicit
     * and explicit spellings agreeing.
     */
    private static List<Statement> beansDslStatements(PropertyNode beansProperty) {
        Expression initial = beansProperty.field?.initialExpression
        if (!(initial instanceof ClosureExpression)) {
            return null
        }
        Statement code = ((ClosureExpression) initial).code
        code instanceof BlockStatement ? ((BlockStatement) code).statements : null
    }

    /**
     * Whether one top-level statement is a {@code bean}/{@code field}/{@code method} declaration,
     * looking through any chained qualifiers to the call at the root of the chain.
     */
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

    /**
     * Fails a top-level statement that is not a {@code bean}/{@code field}/{@code method} declaration
     * when others in the same block are.
     *
     * <p>Silence is the wrong answer here. The all-or-nothing claim above exists to leave an
     * unrelated {@code beans} property alone, and a block with no declarations in it at all is
     * exactly that - so it stays silent. But one stray statement among real declarations is not an
     * unrelated property by any reading: it is the DSL with a mistake in it, most often a typo in a
     * call name or an {@code if} wrapped around beans that belong under a {@code @Conditional*}
     * qualifier instead. Dropping the whole block for that registers <i>nothing</i>, and the failure
     * surfaces far away, as beans that are simply absent at runtime.</p>
     *
     * <p>The rule is the same wherever the block is written: a {@code beans} closure containing any
     * top-level {@code bean}/{@code field}/{@code method} call is the DSL and must be entirely the
     * DSL; one containing none is not the DSL and is left alone. A plugin descriptor is not treated
     * more leniently than an application class, for two reasons. No Grails version has ever read a
     * {@code beans} <i>property</i> off a descriptor - the properties plugin loading reads are
     * {@code doWithSpring}, {@code watchedResources}, {@code onChange} and friends - so the
     * pre-8.0 descriptor this would protect has to be dead code that also happens to contain a
     * top-level {@code bean(...)} call. And a descriptor is compiled by the plugin's author but its
     * beans are missed by every downstream application, whose developers never see the plugin's
     * build output - so loudness matters more there, not less. The way out for a {@code beans}
     * property that genuinely is not the DSL is to rename it, which the message says.</p>
     */
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

    /**
     * Determines whether compilation is configured to use isolated project output directories.
     *
     * @return {@code true} when the {@code grails.isolated.build} system property is {@code true}
     */
    static boolean isIsolatedBuild() {
        System.getProperty(ISOLATED_BUILD_PROPERTY, 'false').toBoolean()
    }

    /**
     * Resolves the output directory to which compiler-generated Grails metadata should be written.
     *
     * @param source the source unit being compiled
     * @return the compilation target directory
     */
    static File resolveCompilationTargetDirectory(SourceUnit source) {
        resolveCompilationTargetDirectory(source, isolatedBuild)
    }

    /**
     * Resolves the compilation target directory, using the Eclipse-specific resolution when
     * compiling with Groovy-Eclipse and otherwise using the compiler configuration.
     *
     * @param source the source unit being compiled
     * @param isolatedBuild whether falling back to the shared legacy directory is prohibited
     * @return the compilation target directory
     * @throws IllegalStateException if no target directory is available during an isolated build
     */
    static File resolveCompilationTargetDirectory(SourceUnit source, boolean isolatedBuild) {
        File targetDirectory
        if (source.class.name == 'org.codehaus.jdt.groovy.control.EclipseSourceUnit') {
            targetDirectory = GroovyEclipseCompilationHelper.resolveEclipseCompilationTargetDirectory(source)
        } else {
            targetDirectory = source.configuration.targetDirectory
        }
        if (targetDirectory == null) {
            // The relative fallback is resolved against the compiler's working directory, which is shared
            // across every module of a multi-project build (e.g. the reused Gradle compiler worker). That
            // makes it a single path for all modules, so one module's generated grails.factories /
            // grails-plugin.xml can leak into another. In an isolated build, fail loudly instead.
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

    /**
     * Adds the compiled class to the {@code META-INF/grails.factories} entry for the supplied type
     * when it is a concrete subtype of that type. Existing generated entries and matching
     * project-source entries are preserved, and the resulting factory file is written to the
     * compilation target directory.
     *
     * @param classNode the class being compiled
     * @param superType the factory interface or superclass whose implementations are registered
     * @param compilationTargetDirectory the compilation output directory containing the factory file
     * @return {@code true} when {@code classNode} is a non-abstract subtype of {@code superType} and
     *         was registered; {@code false} otherwise
     */
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
        superTypes.any {
            updateGrailsFactoriesWithType(classNode, it, compilationTargetDirectory)
        }
    }

    /**
     * Creates or updates the generated {@code META-INF/grails-plugin.xml} descriptor and carries
     * forward artefact classes collected during compilation.
     *
     * @param pluginClassNode the compiled plugin descriptor class, or {@code null} when none was found
     * @param pluginVersion the plugin version, or {@code null} when no concrete plugin descriptor
     *                        is being generated
     * @param transformedClassNames the artefact classes transformed in the current source unit
     * @param pluginXmlFile the generated plugin descriptor file
     */
    protected void generatePluginXml(
            @Nullable ClassNode pluginClassNode,
            @Nullable String pluginVersion,
            Set<String> transformedClassNames,
            File pluginXmlFile
    ) {
        // first check if plugin.xml exists
        pluginXmlFile.parentFile.mkdirs()
        def pluginXmlExists = pluginXmlFile.exists()
        def pluginClasses = [] as LinkedHashSet<String>
        pluginClasses.addAll(transformedClassNames)
        pluginClasses.addAll(pendingPluginClassNames)

        // Create or update grails-plugin.xml when a concrete plugin class is present; otherwise,
        // update an existing descriptor or defer resource names until the descriptor is compiled.
        if (pluginClassNode && !pluginClassNode.abstract) {
            if (!pluginXmlExists) {
                // The plugin descriptor is being compiled for the first time.
                writePluginXml(pluginClassNode, pluginVersion, pluginXmlFile, pluginClasses)
            } else {
                // Refresh the existing descriptor with the current plugin metadata and resources.
                updatePluginXml(pluginClassNode, pluginVersion, pluginXmlFile, pluginClasses)
            }
        } else if (pluginXmlExists) {
            // Add resources from this source unit to the existing descriptor.
            updatePluginXml(null, pluginVersion, pluginXmlFile, pluginClasses)
        } else {
            // Defer these resource names until a source unit compiles the plugin descriptor.
            pendingPluginClassNames.addAll(transformedClassNames)
        }
    }

    /**
     * Writes a new plugin descriptor from the plugin class metadata and supplied artefact classes.
     *
     * @param pluginClassNode the plugin descriptor class
     * @param pluginVersion the required plugin version when {@code pluginClassNode} is present, or
     *                      {@code null} when writing a resources-only descriptor
     * @param pluginXml the output descriptor file
     * @param artefactClassNames artefact class names to include as resources
     */
    void writePluginXml(
            @Nullable ClassNode pluginClassNode,
            @Nullable String pluginVersion,
            File pluginXml,
            Collection<String> artefactClassNames
    ) {
        pluginXml.parentFile.mkdirs()
        if (pluginClassNode) {
            writePluginXmlWithDescriptor(pluginClassNode, pluginVersion, pluginXml, artefactClassNames)
        } else {
            writePluginXmlWithoutDescriptor(pluginXml, artefactClassNames)
        }
        pendingPluginClassNames.clear()
    }

    @CompileDynamic
    private void writePluginXmlWithDescriptor(
            ClassNode pluginClassNode,
            String pluginVersion,
            File pluginXml,
            Collection<String> artefactClassNames
    ) {
        def pluginInfo = new PluginAstReader().readPluginInfo(pluginClassNode)
        pluginXml.withWriter(StandardCharsets.UTF_8.name()) { Writer writer ->
            def markupBuilder = new MarkupBuilder(writer)
            def pluginName = GrailsNameUtils.getLogicalPropertyName(pluginClassNode.name, 'GrailsPlugin')
            def pluginProperties = pluginInfo.properties
            def pluginExcludes = pluginProperties.get('pluginExcludes')
            if (pluginExcludes instanceof List) {
                pluginExcludePatterns.clear()
                pluginExcludePatterns.addAll(pluginExcludes)
            }

            def grailsVersion = resolveGrailsVersion(pluginProperties)
            def pluginAttributes = [name: pluginName, version: pluginVersion]
            if (grailsVersion) {
                pluginAttributes.grailsVersion = grailsVersion
            }

            markupBuilder.plugin(pluginAttributes) {
                type(pluginClassNode.name)

                for (def entry : pluginProperties) {
                    delegate."$entry.key"(entry.value)
                }

                // if there are pending class names to add to the plugin.xml - add them as resources
                if (artefactClassNames) {
                    resources {
                        for (def artefactClassName : artefactClassNames) {
                            if (!isResourceExcludedByPlugin(artefactClassName)) {
                                resource(artefactClassName)
                            }
                        }
                    }
                }
            }
        }
    }

    @CompileDynamic
    private void writePluginXmlWithoutDescriptor(File pluginXml, Collection<String> artefactClassNames) {
        pluginXml.withWriter(StandardCharsets.UTF_8.name()) { Writer writer ->
            new MarkupBuilder(writer).plugin {
                resources {
                    for (def artefactClassName : artefactClassNames) {
                        if (!isResourceExcludedByPlugin(artefactClassName)) {
                            resource(artefactClassName)
                        }
                    }
                }
            }
        }
    }

    /**
     * Updates an existing plugin descriptor with plugin metadata and newly discovered artefact
     * resources. If the descriptor cannot be parsed or written, it is recreated; other failures
     * propagate to abort compilation.
     *
     * @param pluginClassNode the plugin descriptor class, or {@code null} when only resources are updated
     * @param pluginVersion the plugin version, or {@code null} when only resources are updated
     * @param pluginXmlFile the existing plugin descriptor file
     * @param artefactClassNames artefact class names to add as resources
     */
    void updatePluginXml(
            @Nullable ClassNode pluginClassNode,
            @Nullable String pluginVersion,
            File pluginXmlFile,
            Collection<String> artefactClassNames
    ) {
        if (!pluginClassNode && !artefactClassNames) return
        try {
            def pluginXml = IOUtils.createXmlSlurper().parse(pluginXmlFile)
            if (pluginClassNode) {
                def pluginProperties = updatePluginXmlProperties(pluginClassNode, pluginVersion, pluginXml)
                def pluginExcludes = pluginProperties.get('pluginExcludes')
                if (pluginExcludes instanceof List) {
                    pluginExcludePatterns.clear()
                    pluginExcludePatterns.addAll(pluginExcludes as List<String>)
                }
            }
            updatePluginXmlResources(pluginXml, artefactClassNames)
            handleExcludes(pluginXml)

            pluginXmlFile.withWriter(StandardCharsets.UTF_8.name()) {
                createMarkup(pluginXml).writeTo(it)
            }

            pendingPluginClassNames.clear()

        } catch (IOException | ParserConfigurationException | SAXException e) {
            // Invalid or unreadable descriptor; recreate it
            log.warn('Failed to update existing file {}. Recreating it instead...', pluginXmlFile.absolutePath, e)
            if (pluginClassNode) {
                writePluginXml(pluginClassNode, pluginVersion, pluginXmlFile, artefactClassNames)
            } else {
                pluginXmlFile.delete()
                pendingPluginClassNames.addAll(artefactClassNames)
            }
        }
    }

    /**
     * Removes resources matching the configured plugin exclusion patterns from a parsed descriptor.
     *
     * @param pluginXml the parsed plugin descriptor
     */
    @CompileDynamic
    protected void handleExcludes(GPathResult pluginXml) {
        if (pluginExcludePatterns) {
            pluginXml.resources.resource.each { resourceNode ->
                if (isResourceExcludedByPlugin((resourceNode as GPathResult).text())) {
                    resourceNode.replaceNode {}
                }
            }
        }
    }

    /**
     * Determines whether a resource name matches any configured plugin exclusion pattern.
     *
     * @param resourceName the resource name to test
     * @return {@code true} when the resource should be excluded
     */
    private boolean isResourceExcludedByPlugin(String resourceName) {
        def resourcePath = resourceName.replace('.', '/')
        pluginExcludePatterns.any {
            ANT_PATH_MATCHER.match(it, resourcePath)
        }
    }

    /**
     * Creates a writable representation of a parsed plugin descriptor.
     *
     * @param node the parsed XML node
     * @return a writable representation of the node
     */
    private static Writable createMarkup(GPathResult node) {
        (Writable) new StreamingMarkupBuilder().bindNode(node)
    }

    /**
     * Copies plugin metadata from a plugin class into an existing parsed descriptor.
     *
     * @param pluginClassNode the plugin descriptor class
     * @param pluginVersion the plugin version
     * @param pluginXml the parsed plugin descriptor
     * @return the plugin properties extracted from the class
     */
    @CompileDynamic
    private static Map updatePluginXmlProperties(
            ClassNode pluginClassNode,
            String pluginVersion,
            GPathResult pluginXml
    ) {
        def pluginProperties = new PluginAstReader().readPluginInfo(pluginClassNode).properties
        def grailsVersion = resolveGrailsVersion(pluginProperties)
        pluginXml.@name = GrailsNameUtils.getLogicalPropertyName(pluginClassNode.name, 'GrailsPlugin')
        pluginXml.@version = pluginVersion
        if (grailsVersion) {
            pluginXml.@grailsVersion = grailsVersion
        }
        pluginXml.type = pluginClassNode.name
        for (def entry : pluginProperties) {
            pluginXml."$entry.key" = entry.value
        }
        pluginProperties
    }

    private static @Nullable String resolveGrailsVersion(Map pluginProperties) {
        resolveGrailsVersionWithFrameworkVersion(
                pluginProperties,
                GlobalGrailsClassInjectorTransformation.package.implementationVersion
        )
    }

    static @Nullable String resolveGrailsVersionWithFrameworkVersion(
            Map pluginProperties,
            @Nullable String frameworkVersion
    ) {
        def declaredVersion = pluginProperties['grailsVersion']?.toString()
        if (declaredVersion) {
            return declaredVersion
        }
        frameworkVersion ? "${frameworkVersion} > *" : null
    }

    /**
     * Adds artefact classes to the resources section of a parsed plugin descriptor when they are
     * not already present.
     *
     * @param pluginXml the parsed plugin descriptor
     * @param artefactClassNames artefact class names to add
     */
    @CompileDynamic
    private static void updatePluginXmlResources(GPathResult pluginXml, Collection<String> artefactClassNames) {
        def resources = pluginXml.resources
        for (def className : artefactClassNames) {
            if (!resources.resource.find { it.text() == className }) {
                resources.appendNode {
                    resource(className)
                }
            }
        }
    }
}
