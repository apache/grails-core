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

import grails.artefact.Artefact
import grails.compiler.ast.ClassInjector
import grails.core.ArtefactHandler
import grails.io.IOUtils
import grails.plugins.metadata.GrailsPlugin
import grails.util.GrailsNameUtils
import groovy.transform.CompilationUnitAware
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.xml.MarkupBuilder
import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult
import org.apache.grails.common.compiler.GroovyTransformOrder
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.codehaus.groovy.transform.TransformWithPriority
import org.grails.core.io.support.GrailsFactoriesLoader
import org.grails.io.support.AntPathMatcher
import org.grails.io.support.GrailsResourceUtils
import org.grails.io.support.UrlResource

import java.lang.reflect.Modifier
import java.nio.charset.StandardCharsets
import java.time.Instant

/**
 * A global transformation that applies Grails' transformations to classes within a Grails project
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
@CompileStatic
class GlobalGrailsClassInjectorTransformation implements ASTTransformation, CompilationUnitAware, TransformWithPriority {

    public static final ClassNode ARTEFACT_HANDLER_CLASS = ClassHelper.make("grails.core.ArtefactHandler")
    public static final ClassNode APPLICATION_CONTEXT_COMMAND_CLASS = ClassHelper.make("grails.dev.commands.ApplicationCommand")
    public static final ClassNode TRAIT_INJECTOR_CLASS = ClassHelper.make("grails.compiler.traits.TraitInjector")

    @Override
    int priority() {
        return GroovyTransformOrder.GLOBAL_GRAILS_TRANSFORM_ORDER
    }

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {

        ModuleNode ast = source.getAST();
        List<ClassNode> classes = new ArrayList<>(ast.getClasses());

        URL url = GrailsASTUtils.getSourceUrl(source);

        if (url == null) return
        if (!GrailsResourceUtils.isProjectSource(new UrlResource(url))) return;

        List<ArtefactHandler> artefactHandlers = GrailsFactoriesLoader.loadFactories(ArtefactHandler)
        ClassInjector[] classInjectors = GrailsAwareInjectionOperation.getClassInjectors()

        Map<String, List<ClassInjector>> cache = new LinkedHashMap<String, List<ClassInjector>>().withDefault { String key ->
            ArtefactTypeAstTransformation.findInjectors(key, classInjectors)
        }

        LinkedHashSet<String> transformedClasses = []
        String pluginVersion = null
        ClassNode pluginClassNode = null
        def compilationTargetDirectory = resolveCompilationTargetDirectory(source)
        def pluginXmlFile = new File(compilationTargetDirectory, "META-INF/grails-plugin.xml")

        for (ClassNode classNode : classes) {
            def projectName = classNode.getNodeMetaData("projectName")
            def projectVersion = classNode.getNodeMetaData("projectVersion")
            if (projectVersion == null) {
                projectVersion = getClass().getPackage().getImplementationVersion()
            }

            pluginVersion = projectVersion

            def classNodeName = classNode.name

            if (classNodeName.endsWith("GrailsPlugin") && !classNode.isAbstract()) {
                pluginClassNode = classNode

                if (!classNode.getProperty('version')) {
                    classNode.addProperty(new PropertyNode('version', Modifier.PUBLIC, ClassHelper.make(Object), classNode, new ConstantExpression(projectVersion.toString()), null, null))
                }

                continue
            }

            if (updateGrailsFactoriesWithType(classNode, ARTEFACT_HANDLER_CLASS, compilationTargetDirectory)) {
                continue
            }
            if (updateGrailsFactoriesWithType(classNode, APPLICATION_CONTEXT_COMMAND_CLASS, compilationTargetDirectory)) {
                continue
            }
            if (updateGrailsFactoriesWithType(classNode, TRAIT_INJECTOR_CLASS, compilationTargetDirectory)) {
                continue
            }

            if (!GrailsResourceUtils.isGrailsResource(new UrlResource(url))) continue;

            if (projectName && projectVersion) {
                GrailsASTUtils.addAnnotationOrGetExisting(classNode, GrailsPlugin, [name: GrailsNameUtils.getPropertyNameForLowerCaseHyphenSeparatedName(projectName.toString()), version: projectVersion])
            }

            classNode.getModule().addImport("Autowired", ClassHelper.make("org.springframework.beans.factory.annotation.Autowired"))

            for (ArtefactHandler handler in artefactHandlers) {
                if (handler.isArtefact(classNode)) {
                    if (!classNode.getAnnotations(ARTEFACT_CLASS_NODE)) {
                        transformedClasses.add classNodeName
                        def annotationNode = new AnnotationNode(new ClassNode(Artefact.class))
                        annotationNode.addMember("value", new ConstantExpression(handler.getType()))
                        classNode.addAnnotation(annotationNode)

                        List<ClassInjector> injectors = cache[handler.type]
                        for (ClassInjector injector : injectors) {
                            if (injector instanceof CompilationUnitAware) {
                                ((CompilationUnitAware) injector).compilationUnit = compilationUnit
                            }
                        }
                        ArtefactTypeAstTransformation.performInjection(source, classNode, injectors)
                        TraitInjectionUtils.processTraitsForNode(source, classNode, handler.getType(), compilationUnit)
                    }
                }
            }

            if (!transformedClasses.contains(classNodeName)) {
                def globalClassInjectors = GrailsAwareInjectionOperation.globalClassInjectors

                for (ClassInjector injector in globalClassInjectors) {
                    injector.performInjection(source, classNode)
                }
            }
        }

        // now create or update grails-plugin.xml
        // first check if plugin.xml exists
        pluginXmlFile.parentFile.mkdirs()

        generatePluginXml(pluginClassNode, pluginVersion, transformedClasses, pluginXmlFile)
    }

    static File resolveCompilationTargetDirectory(SourceUnit source) {
        File targetDirectory = null
        if (source.getClass().name == 'org.codehaus.jdt.groovy.control.EclipseSourceUnit') {
            targetDirectory = GroovyEclipseCompilationHelper.resolveEclipseCompilationTargetDirectory(source)
        } else {
            targetDirectory = source.configuration.targetDirectory
        }
        if (targetDirectory == null) {
            targetDirectory = new File('build/classes/main')
        }
        return targetDirectory
    }

    static boolean updateGrailsFactoriesWithType(ClassNode classNode, ClassNode superType, File compilationTargetDirectory) {
        if (GrailsASTUtils.isSubclassOfOrImplementsInterface(classNode, superType)) {
            if (Modifier.isAbstract(classNode.getModifiers())) return false

            def classNodeName = classNode.name
            def props = new Properties()
            def superTypeName = superType.getName()

            // generate META-INF/grails.factories
            File factoriesFile = new File(compilationTargetDirectory, "META-INF/grails.factories")
            if (!factoriesFile.parentFile.exists()) {
                factoriesFile.parentFile.mkdirs()
            }
            loadFromFile(props, factoriesFile)

            File sourceDirectory = findSourceDirectory(compilationTargetDirectory)
            File sourceFactoriesFile = new File(sourceDirectory, "src/main/resources/META-INF/grails.factories")
            loadFromFile(props, sourceFactoriesFile)

            addToProps(props, superTypeName, classNodeName)

            factoriesFile.withWriter { Writer writer ->
                props.store(writer, "Grails Factories File")
            }

            makeFactoriesFileReproducible(factoriesFile)

            return true
        }
        return false
    }

    private static void makeFactoriesFileReproducible(File factoriesFile) {
        String sourceDateEpoch = System.getenv('SOURCE_DATE_EPOCH')
        if (!sourceDateEpoch) {
            return
        }

        Instant buildInstant = Instant.ofEpochSecond(sourceDateEpoch as Long)
        List<String> lines = factoriesFile.readLines(StandardCharsets.ISO_8859_1.name())
        lines[1] = "# ${Date.from(buildInstant).toString()}" as String
        factoriesFile.withWriter { BufferedWriter writer ->
            lines.each { String line ->
                writer.writeLine(line)
            }
        }
    }

    private static void loadFromFile(Properties props, File factoriesFile) {
        if (factoriesFile.exists()) {
            Properties fileProps = new Properties()
            factoriesFile.withInputStream { InputStream input ->
                fileProps.load(input)
                fileProps.each { Map.Entry prop ->
                    addToProps(props, (String) prop.key, (String) prop.value)
                }
            }
        }
    }

    private static Properties addToProps(Properties props, String superTypeName, String classNodeNames) {
        final List<String> classNodesNameList = classNodeNames.tokenize(',')
        classNodesNameList.forEach(classNodeName -> {
            String existing = props.getProperty(superTypeName)
            if (!existing) {
                props.put(superTypeName, classNodeName)
            } else if (existing && !existing.contains(classNodeName)) {
                props.put(superTypeName, [existing, classNodeName].join(','))
            }
        })
        props
    }

    private static File findSourceDirectory(File compilationTargetDirectory) {
        File sourceDirectory = compilationTargetDirectory
        while (sourceDirectory != null && !(sourceDirectory.name in ["build", "target"])) {
            sourceDirectory = sourceDirectory.parentFile
        }
        sourceDirectory.parentFile
    }

    static LinkedHashSet<String> pendingPluginClasses = []
    static Collection<String> pluginExcludes = []

    protected static void generatePluginXml(ClassNode pluginClassNode, String pluginVersion, Set<String> transformedClasses, File pluginXmlFile) {
        def pluginXmlExists = pluginXmlFile.exists()
        LinkedHashSet<String> pluginClasses = []
        pluginClasses.addAll(transformedClasses)
        pluginClasses.addAll(pendingPluginClasses)

        // if the class being transformed is a *GrailsPlugin class then if it doesn't exist create it
        if (pluginClassNode && !pluginClassNode.isAbstract()) {
            if (!pluginXmlExists) {
                writePluginXml(pluginClassNode, pluginVersion, pluginXmlFile, pluginClasses)
            } else {
                // otherwise if the file does exist, update it with the plugin name
                updatePluginXml(pluginClassNode, pluginVersion, pluginXmlFile, pluginClasses)
            }
        } else if (pluginXmlExists) {
            // if the class isn't the *GrailsPlugin class then only update the plugin.xml if it already exists
            updatePluginXml(null, pluginVersion, pluginXmlFile, pluginClasses)
        } else {
            // otherwise add it to a list of pending classes to populated when the plugin.xml is created
            pendingPluginClasses.addAll(transformedClasses)
        }
    }

    @CompileDynamic
    static void writePluginXml(ClassNode pluginClassNode, String pluginVersion, File pluginXml, Collection<String> artefactClasses) {
        if (pluginClassNode) {
            PluginAstReader pluginAstReader = new PluginAstReader()
            def info = pluginAstReader.readPluginInfo(pluginClassNode)

            pluginXml.withWriter("UTF-8") { Writer writer ->
                def mkp = new MarkupBuilder(writer)
                def pluginName = GrailsNameUtils.getLogicalPropertyName(pluginClassNode.name, "GrailsPlugin")

                def pluginProperties = info.getProperties()
                def excludes = pluginProperties.get('pluginExcludes')
                if (excludes instanceof List) {
                    pluginExcludes.clear()
                    pluginExcludes.addAll(excludes)
                }

                def grailsVersion = pluginProperties['grailsVersion'] ?: getClass().getPackage().getImplementationVersion() + " > *"
                mkp.plugin(name: pluginName, version: pluginVersion, grailsVersion: grailsVersion) {
                    type(pluginClassNode.name)

                    for (entry in pluginProperties) {
                        delegate."$entry.key"(entry.value)
                    }

                    // if there are pending classes to add to the plugin.xml add those
                    if (artefactClasses) {
                        def antPathMatcher = new AntPathMatcher()
                        resources {
                            for (String cn in artefactClasses) {
                                if (!pluginExcludes.any() { String exc -> antPathMatcher.match(exc, cn.replace('.', '/')) }) {
                                    resource cn
                                }
                            }
                        }
                    }
                }
            }

            pendingPluginClasses.clear()
        }
    }

    @CompileDynamic
    static void updatePluginXml(ClassNode pluginClassNode, String pluginVersion, File pluginXmlFile, Collection<String> artefactClasses) {
        if (!artefactClasses) return

        try {
            XmlSlurper xmlSlurper = IOUtils.createXmlSlurper()

            def pluginXml = xmlSlurper.parse(pluginXmlFile)
            if (pluginClassNode) {
                def pluginName = GrailsNameUtils.getLogicalPropertyName(pluginClassNode.name, "GrailsPlugin")
                pluginXml.@name = pluginName
                pluginXml.@version = pluginVersion
                pluginXml.type = pluginClassNode.name

                PluginAstReader pluginAstReader = new PluginAstReader()
                def info = pluginAstReader.readPluginInfo(pluginClassNode)

                def pluginProperties = info.getProperties()
                def grailsVersion = pluginProperties['grailsVersion'] ?: getClass().getPackage().getImplementationVersion() + " > *"
                pluginXml.@grailsVersion = grailsVersion
                for (entry in pluginProperties) {
                    pluginXml."$entry.key" = entry.value
                }

                def excludes = pluginProperties.get('pluginExcludes')
                if (excludes instanceof List) {
                    pluginExcludes.clear()
                    pluginExcludes.addAll(excludes)
                }
            }

            def resources = pluginXml.resources

            for (String cn in artefactClasses) {
                if (!resources.resource.find { it.text() == cn }) {
                    resources.appendNode {
                        resource(cn)
                    }
                }
            }

            handleExcludes(pluginXml)

            Writable writable = new StreamingMarkupBuilder().bind {
                mkp.yield pluginXml
            }

            pluginXmlFile.withWriter("UTF-8") { Writer writer ->
                writable.writeTo(writer)
            }

            pendingPluginClasses.clear()

        } catch (e) {
            // corrupt, recreate
            writePluginXml(pluginClassNode, pluginVersion, pluginXmlFile, artefactClasses)
        }
    }

    @CompileDynamic
    protected static void handleExcludes(GPathResult pluginXml) {
        if (pluginExcludes) {

            def antPathMatcher = new AntPathMatcher()
            pluginXml.resources.resource.each { res ->
                if (pluginExcludes.any() { String exc -> antPathMatcher.match(exc, res.text().replace('.', '/')) }) {
                    res.replaceNode {}
                }
            }
        }
    }

    public static final ClassNode ARTEFACT_CLASS_NODE = new ClassNode(Artefact.class)

    CompilationUnit compilationUnit
}
