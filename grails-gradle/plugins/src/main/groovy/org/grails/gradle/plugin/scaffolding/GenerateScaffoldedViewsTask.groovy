/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.grails.gradle.plugin.scaffolding

import java.util.jar.JarEntry
import java.util.jar.JarFile

import grails.util.GrailsNameUtils
import groovy.text.GStringTemplateEngine
import groovy.transform.CompileStatic
import groovyjarjarasm.asm.AnnotationVisitor
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.FieldVisitor
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.Opcodes
import groovyjarjarasm.asm.Type

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import org.grails.gradle.plugin.views.gsp.GroovyPagePlugin

/**
 * Writes the views a scaffolded controller would otherwise generate on its first request.
 *
 * <p>Scaffolding expands a template into GSP source and compiles the result, and until now it did
 * both when the view was first asked for. That costs the first request on the JVM, and a native
 * image cannot do it at all: defining a class at runtime is exactly what an ahead-of-time image
 * gives up. Expanding the templates here instead lets the ordinary GSP compiler precompile the
 * result, so at runtime the views are found rather than produced.</p>
 *
 * <p>Only naming is substituted -- the templates read {@code className}, {@code propertyName},
 * {@code fullName} and {@code packageName}, and defer everything else about the domain class to the
 * field tag libraries at render time. That is why this needs no GORM, no application context and no
 * loading of application classes: the controllers are read with ASM and the domain class name is
 * enough. The qualified name is bound too, so that a template can declare the type of its model in a
 * form that resolves from the generated page.</p>
 *
 * <p>A view the application already declares is never overwritten, which keeps the existing
 * precedence: a hand-written {@code grails-app/views} page wins over a scaffolded one.</p>
 *
 * @since 8.0
 */
@CacheableTask
@CompileStatic
abstract class GenerateScaffoldedViewsTask extends DefaultTask {

    /** Descriptor of the annotation that marks a scaffolded controller. */
    private static final String SCAFFOLD_ANNOTATION = 'Lgrails/plugin/scaffolding/annotation/Scaffold;'

    /** Path within an artifact holding the scaffolding templates. */
    private static final String TEMPLATE_PATH = 'META-INF/templates/scaffolding/'

    /**
     * Where a plugin's compiled-view index is read from, in the order {@code BinaryGrailsPlugin}
     * tries them: beside the plugin descriptor first, then the location the GSP compiler writes.
     */
    private static final List<String> VIEW_INDEXES = ['META-INF/views.properties', 'gsp/views.properties']

    /** The views scaffolding knows how to produce. */
    private static final List<String> VIEW_NAMES = ['index', 'create', 'edit', 'show']

    /** Compiled application classes, searched for scaffolded controllers. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getClassesDirs()

    /**
     * The classpath the scaffolding templates are read from. The application's own
     * {@code src/main/templates/scaffolding} takes precedence, matching the runtime lookup.
     */
    @Classpath
    abstract ConfigurableFileCollection getTemplateClasspath()

    /**
     * The classpath a controller's superclasses are read from, to find a namespace it inherits.
     * Kept apart from {@link #getTemplateClasspath()} so that narrowing where templates are read
     * from cannot quietly stop inherited namespaces being seen.
     */
    @Classpath
    abstract ConfigurableFileCollection getControllerClasspath()

    /** Dependency views, including plugins used only at runtime. */
    @Classpath
    abstract ConfigurableFileCollection getViewClasspath()

    /** Application template overrides, normally {@code src/main/templates/scaffolding}. */
    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getTemplateOverrides()

    /** The application's own views; anything declared here is left alone. */
    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getApplicationViews()

    /** Where the generated views are written. */
    @OutputDirectory
    abstract DirectoryProperty getOutputDirectory()

    @TaskAction
    void generate() {
        File outputDir = outputDirectory.get().asFile
        outputDir.deleteDir()
        outputDir.mkdirs()

        Map<String, String> templates = loadTemplates()
        if (templates.isEmpty()) {
            logger.info('No scaffolding templates on the classpath; nothing to generate')
            return
        }

        Set<File> declared = applicationViews.files
        Set<String> pluginViews = findPluginViews()
        int written = 0
        for (Map.Entry<String, String> controller : findScaffoldedControllers()) {
            String fullName = controller.value
            String className = fullName.tokenize('.').last()
            String propertyName = GrailsNameUtils.getPropertyName(className)
            String packageName = fullName.contains('.') ? fullName[0..<fullName.lastIndexOf('.')] : ''
            for (String viewName : VIEW_NAMES) {
                String template = templates.get(viewName)
                if (template == null) {
                    continue
                }
                // a view the application wrote itself already wins at runtime, so leaving it out
                // keeps build-time and runtime resolution agreeing
                if (declared.any { it.path.endsWith("views/${controller.key}/${viewName}.gsp".toString()) }) {
                    logger.info('Skipping {}/{}.gsp, the application declares it', controller.key, viewName)
                    continue
                }
                if (pluginViews.contains("${GroovyPagePlugin.VIEWS_SERVER_PATH}${controller.key}/${viewName}.gsp".toString())) {
                    logger.info('Skipping {}/{}.gsp, a plugin declares it', controller.key, viewName)
                    continue
                }
                File target = new File(outputDir, "${controller.key}/${viewName}.gsp")
                target.parentFile.mkdirs()
                target.text = expand(template, className, propertyName, fullName, packageName)
                written++
            }
        }
        logger.info('Generated {} scaffolded view(s)', written)
    }

    /**
     * Expands a template the same way the runtime resolver does, binding the same names it does.
     *
     * <p>{@code fullName} and {@code packageName} are bound alongside the naming because a template
     * that declares the type of its model has to name a type that resolves from the page, and the
     * simple name does not.</p>
     */
    private String expand(String template, String className, String propertyName, String fullName, String packageName) {
        StringWriter out = new StringWriter()
        new GStringTemplateEngine()
                .createTemplate(template)
                .make([className: className, propertyName: propertyName,
                       fullName: fullName, packageName: packageName, modelName: propertyName])
                .writeTo(out)
        out.toString()
    }

    /**
     * Maps view name to template text, with the application's overrides winning over the templates
     * a plugin contributes.
     */
    private Map<String, String> loadTemplates() {
        Map<String, String> templates = [:]
        for (File entry : templateClasspath.files) {
            if (entry.isDirectory()) {
                File dir = new File(entry, TEMPLATE_PATH)
                if (dir.isDirectory()) {
                    dir.eachFileMatch(~/.*\.gsp/) { File f -> templates.putIfAbsent(baseName(f.name), f.text) }
                }
            }
            else if (entry.name.endsWith('.jar') && entry.isFile()) {
                new JarFile(entry).withCloseable { JarFile jar ->
                    for (JarEntry e : jar.entries()) {
                        if (e.name.startsWith(TEMPLATE_PATH) && e.name.endsWith('.gsp')) {
                            templates.putIfAbsent(baseName(e.name.substring(TEMPLATE_PATH.length())),
                                    jar.getInputStream(e).getText('UTF-8'))
                        }
                    }
                }
            }
        }
        for (File override : templateOverrides.files) {
            if (override.isFile() && override.name.endsWith('.gsp')) {
                templates.put(baseName(override.name), override.text)
            }
        }
        templates
    }

    /**
     * Compiled plugin pages win over runtime scaffolding, so they must also win at build time. Each
     * plugin JAR contributes the first index it carries, as the runtime reads only one per plugin.
     * JARs without a plugin descriptor do not expose views at runtime. Directories are not gated:
     * project dependencies can put the descriptor and compiled index in separate output directories.
     */
    private Set<String> findPluginViews() {
        Set<String> views = []
        for (File entry : viewClasspath.files) {
            Properties index = new Properties()
            if (entry.isDirectory()) {
                File resource = VIEW_INDEXES.collect { new File(entry, it) }.find { it.isFile() }
                resource?.withInputStream { InputStream input -> index.load(input) }
            }
            else if (entry.name.endsWith('.jar') && entry.isFile()) {
                new JarFile(entry).withCloseable { JarFile jar ->
                    if (jar.getJarEntry('META-INF/grails-plugin.xml') == null) {
                        return
                    }
                    JarEntry resource = VIEW_INDEXES.collect { jar.getJarEntry(it) }.find { it != null }
                    if (resource != null) {
                        jar.getInputStream(resource).withCloseable { InputStream input -> index.load(input) }
                    }
                }
            }
            views.addAll(index.stringPropertyNames())
        }
        views
    }

    /**
     * Maps view directory name to the fully qualified domain class, for every {@code @Scaffold}
     * controller. Qualified rather than simple because a view declaring the type of its model has to
     * name a type that resolves.
     *
     * <p>Namespaced controllers are left to the runtime resolver, which can evaluate the namespace
     * and select namespace-specific templates. Emitting their pages into a shared, unqualified
     * directory would make them visible to unrelated controllers. The entire shared directory is
     * left out, including when an unqualified controller also claims it.</p>
     *
     * <p>This is deliberately broader than it needs to be for a namespaced controller that has no
     * namespace-specific template, whose page would come out identical to the plain one. Narrowing
     * it needs to know whether {@code <namespace>/<view>.gsp} exists, and neither half is available
     * here: the namespace value is assigned in {@code <clinit>} for the usual Groovy declarations,
     * so the bytecode carries no constant for it, and the runtime also finds namespace templates in
     * places this task does not read - the application's own resources beside the controller class,
     * {@code src/main/templates/scaffolding} in development, and a template-override plugin.
     * Guessing wrong would precompile a plain page over a namespace-specific one, silently.</p>
     *
     * <p>Likewise, controllers sharing a name but scaffolding different domains cannot share a
     * precompiled page. The runtime resolver expands a template for the appropriate domain.</p>
     */
    private Map<String, String> findScaffoldedControllers() {
        Map<String, String> found = [:]
        Map<String, List<String>> claimants = [:]
        Set<String> namespaced = []
        Map<String, Boolean> ancestors = [:]
        URL[] classpath = (classesDirs.files + controllerClasspath.files).collect { it.toURI().toURL() } as URL[]
        new URLClassLoader(classpath, (ClassLoader) null).withCloseable { URLClassLoader resources ->
            for (File dir : classesDirs.files) {
                if (!dir.isDirectory()) {
                    continue
                }
                dir.eachFileRecurse { File f ->
                    if (!f.name.endsWith('Controller.class')) {
                        return
                    }
                    String controllerName = viewDirectory(f.name - '.class')
                    ClassReader reader = new ClassReader(f.bytes)
                    if (hasNamespace(reader, resources, ancestors)) {
                        namespaced.add(controllerName)
                    }
                    String domain = readScaffoldDomain(reader)
                    if (domain == null) {
                        return
                    }
                    claimants.computeIfAbsent(controllerName) { [] }.add(domain)
                    found.put(controllerName, domain)
                }
            }
        }
        namespaced.each { String controllerName ->
            if (found.remove(controllerName) != null) {
                logger.warn('Not precompiling the views of {}: a controller with this name declares or inherits a namespace. ' +
                        'These scaffold views are expanded at runtime; native images require concrete GSP views.', controllerName)
            }
        }
        claimants.each { String controllerName, List<String> domains ->
            List<String> distinct = domains.unique(false)
            if (distinct.size() > 1) {
                found.remove(controllerName)
                logger.warn("Not precompiling the views of ${controllerName}: " +
                        "${distinct.size()} controllers with the view directory ${controllerName} " +
                        "scaffold different domains (${distinct.join(', ')}) and share the one view " +
                        'directory. They are expanded per request instead; native images require concrete GSP views.')
            }
        }
        found
    }

    /**
     * Read declarations, including inherited ones, without evaluating application code.
     *
     * <p>Groovy traits rename their namespace fields but emit a static {@code getNamespace()}
     * accessor on the implementing class. Checking that accessor covers trait-supplied namespaces
     * without walking interfaces; only superclass declarations require an ancestor walk.</p>
     *
     * <p>A declaration is all this can see, not its value, so {@code static namespace = null}
     * still counts even though the runtime, which tests the value, gives that controller no
     * namespace. The value lives in {@code <clinit>} for the usual Groovy forms and code is not
     * read here, so the difference cannot be recovered; the controller is only expanded at runtime
     * rather than precompiled.</p>
     */
    private boolean hasNamespace(ClassReader reader, ClassLoader resources, Map<String, Boolean> ancestors) {
        boolean declared = false
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                if (name == 'namespace' && (access & Opcodes.ACC_STATIC) != 0) {
                    declared = true
                }
                null
            }

            @Override
            MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (name == 'getNamespace' && descriptor.startsWith('()') && (access & Opcodes.ACC_STATIC) != 0) {
                    declared = true
                }
                null
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)
        if (declared || reader.superName == null || reader.superName == 'java/lang/Object') {
            return declared
        }
        String superName = reader.superName
        Boolean known = ancestors.get(superName)
        if (known != null) {
            return known
        }
        boolean inherited = ancestorHasNamespace(superName, resources, ancestors)
        ancestors.put(superName, inherited)
        inherited
    }

    /**
     * Superclasses can come from dependencies, whose class files may be newer than the bundled ASM
     * reads, or whose bytecode may be damaged or unreadable. One that cannot be read is taken to
     * declare no namespace rather than failing the build.
     */
    private boolean ancestorHasNamespace(String internalName, ClassLoader resources, Map<String, Boolean> ancestors) {
        try {
            InputStream parent = resources.getResourceAsStream("${internalName}.class")
            if (parent == null) {
                return false
            }
            ClassReader reader = parent.withCloseable { InputStream input -> new ClassReader(input) }
            return hasNamespace(reader, resources, ancestors)
        }
        catch (IllegalArgumentException | IOException | IndexOutOfBoundsException e) {
            logger.info('Could not read {} to look for an inherited namespace; treating it as declaring none: {}',
                    internalName.replace('/', '.'), e.message)
            return false
        }
    }

    /**
     * Returns the fully qualified name of the domain class a controller scaffolds, or {@code null}
     * when it is not scaffolded. Read with ASM so the application's classes are never loaded, which
     * keeps the task independent of the runtime classpath.
     *
     * <p>{@code domain} is what names the domain class, and it is read in preference to
     * {@code value}, which names it only when it is the sole attribute given. Every form is
     * normalised by the time this reads it - ScaffoldingControllerInjector writes the domain into
     * {@code domain} whether it was written as {@code @Scaffold(User)},
     * {@code @Scaffold(domain = User)} or {@code @Scaffold(RestfulServiceController<User>)} - so
     * for the last of those {@code value} is the class to extend, and taking it would name the
     * controller superclass as the domain. The precedence matters rather than merely tidying,
     * because the two attributes are written in no guaranteed order.</p>
     */
    private String readScaffoldDomain(ClassReader reader) {
        boolean scaffolded = false
        String fromValue = null
        String fromDomain = null
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (descriptor != SCAFFOLD_ANNOTATION) {
                    return null
                }
                scaffolded = true
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    void visit(String name, Object value) {
                        if (!(value instanceof Type)) {
                            return
                        }
                        String candidate = ((Type) value).className
                        if (candidate.tokenize('.').last() == 'Void') {
                            return
                        }
                        if (name == 'domain') {
                            fromDomain = candidate
                        }
                        else if (name == 'value') {
                            fromValue = candidate
                        }
                    }
                }
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)
        scaffolded ? (fromDomain ?: fromValue) : null
    }

    private static String baseName(String fileName) {
        fileName.endsWith('.gsp') ? fileName[0..<fileName.length() - 4] : fileName
    }

    /**
     * The directory the runtime resolves a controller's views from, derived the way
     * {@code AbstractGrailsClass} derives it, so {@code APIController} maps to {@code API}.
     */
    private static String viewDirectory(String controllerClassName) {
        String logicalName = GrailsNameUtils.getLogicalName(controllerClassName, 'Controller')
        GrailsNameUtils.getPropertyNameRepresentation(logicalName ?: controllerClassName)
    }
}
