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
import java.util.zip.ZipException

import javax.inject.Inject

import groovy.transform.CompileStatic
import groovyjarjarasm.asm.AnnotationVisitor
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.FieldVisitor
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.Opcodes
import groovyjarjarasm.asm.Type

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.process.ExecOperations
import org.gradle.process.JavaExecSpec

/**
 * Expands the scaffolding templates at build time, so the pages a scaffolded controller renders are
 * compiled with the rest of the application instead of on the request that first asks for them.
 *
 * <p>Scaffolding expands a template into GSP source and compiles the result. At runtime that costs
 * the first request on the JVM, and a native image cannot do it at all: defining a class at runtime
 * is exactly what an ahead-of-time image gives up. The pages written here are compiled by the
 * ordinary GSP compiler instead.</p>
 *
 * <p>They are written under {@code grails-scaffolded/<domain class>/}, a directory no controller's
 * views resolve from, and each is named for its template and a digest of the template and the model
 * it was expanded with. The runtime resolver decides which page a request gets exactly as it would
 * without them - a view the application or a plugin declares, a namespace-specific template, a
 * template override - and only where it would expand a template does it look for the page expanded
 * from the same template and model. So a page cannot shadow a declared view, and a template this
 * task did not expand finds nothing and is expanded at runtime as before.</p>
 *
 * <p>For each scaffolded controller - the application's own, and those plugins on the runtime
 * classpath provide - this expands for its domain class every copy of each template the resolver
 * could choose for it:</p>
 * <ul>
 *   <li>Which copy the resolver finds is not something the build can know. On the JVM it looks
 *   beside the controller's class first, then at a plugin that overrides the templates, then along
 *   the classpath; a native image keeps no class files to look beside, and goes by the order of the
 *   classpath it was built from. So every distinct copy is expanded - the application's own, from
 *   {@code src/main/templates/scaffolding} or its resources, and each on the runtime classpath -
 *   and whichever the resolver chooses has its page.</li>
 *   <li>A namespace-specific template such as {@code admin/show.gsp} can only be chosen for a
 *   controller with a namespace, so it is expanded only for the domain classes such controllers
 *   scaffold.</li>
 * </ul>
 *
 * <p>No application class is loaded: the controllers are read with ASM, and the pages are expanded
 * and named by {@code org.apache.grails.scaffolding.ScaffoldedPagesGenerator}, run in a JVM on the
 * application's runtime classpath, from the same code and the same Groovy as the resolver's.</p>
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

    /** What marks an artifact as a Grails plugin, whose controllers are artefacts of the application. */
    private static final String PLUGIN_DESCRIPTOR = 'META-INF/grails-plugin.xml'

    /** The class that expands and names the pages, from the application's scaffolding library. */
    static final String GENERATOR = 'org.apache.grails.scaffolding.ScaffoldedPagesGenerator'

    /**
     * The version of the exchange with the generator this task speaks - the command line, and the
     * files handed over and back - as the generator declares its own in a {@code PROTOCOL}
     * constant. The two ship apart, the generator in grails-scaffolding and this in the Gradle plugin,
     * so a build can pair one version with another; this reads the generator's before running it.
     */
    public static final int GENERATOR_PROTOCOL = 1

    /** Compiled application classes, searched for scaffolded controllers. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getClassesDirs()

    /**
     * The application's own templates, as trees rooted at the template directory: normally
     * {@code src/main/templates/scaffolding}. A template's path within its tree is its path as the
     * resolver asks for it, so {@code admin/show.gsp} is the {@code show} template of the
     * {@code admin} namespace.
     */
    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getTemplateOverrides()

    /**
     * The application's templates as it packages them, each at
     * {@code META-INF/templates/scaffolding/<template path>} in its tree: normally the output of
     * {@code processResources}, filtered to them. That is where the resolver finds them beside the
     * application's controllers, however the build put them there - from
     * {@code src/main/templates}, from the resources, or from a task that feeds them.
     */
    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getPackagedTemplates()

    /**
     * The application's runtime classpath. The templates and the plugins' controllers are read from
     * it, as the running application reads them, and the pages are expanded on it, by the
     * scaffolding library and the Groovy the application runs with.
     */
    @Classpath
    abstract ConfigurableFileCollection getRuntimeClasspath()

    /**
     * The encoding the pages are written in, which must be the one they are compiled with, so that
     * a page reads back as it was expanded. Normally {@code compileGroovyPages}' encoding.
     */
    @Input
    abstract Property<String> getPageEncoding()

    /**
     * The separator the pages were expanded under. A template that mentions {@code packagePath}
     * expands differently on Windows, so pages built on one platform are not taken from the build
     * cache for another.
     */
    @Input
    String getFileSeparator() {
        File.separator
    }

    /** The Java the pages are expanded with; the build's own when not set. */
    @Nested
    @Optional
    abstract Property<JavaLauncher> getJavaLauncher()

    /** Where the pages are written, as a tree to be compiled with the application's views. */
    @OutputDirectory
    abstract DirectoryProperty getOutputDirectory()

    /**
     * Lists, a line apiece, the pages expanded from templates a dependency supplies, as paths under
     * {@link #getOutputDirectory()}, for the page compilation to leave out any that do not compile.
     * A page expanded from a template of the application's own is not listed, so it has to compile,
     * as a view does.
     */
    @OutputFile
    abstract RegularFileProperty getOptionalPages()

    @Inject
    abstract ExecOperations getExecOperations()

    GenerateScaffoldedViewsTask() {
        pageEncoding.convention('UTF-8')
        optionalPages.convention(project.layout.buildDirectory.file("generated/${name}-optional-pages.txt"))
    }

    @TaskAction
    void generate() {
        File outputDir = outputDirectory.get().asFile
        outputDir.deleteDir()
        outputDir.mkdirs()
        File optionalPagesFile = optionalPages.get().asFile
        optionalPagesFile.parentFile.mkdirs()
        optionalPagesFile.text = ''

        Templates templates = findTemplates()
        if (templates.copies.isEmpty()) {
            logger.info('No scaffolding templates on the classpath; nothing to generate')
            return
        }
        List<Controller> controllers = findScaffoldedControllers()
        if (controllers.isEmpty()) {
            logger.info('No scaffolded controllers; nothing to generate')
            return
        }
        if (templates.generator == null) {
            logger.warn('The scaffolding library on the runtime classpath does not provide {}, so no scaffolded page is ' +
                    'compiled and each is expanded when it is first rendered, which a native image cannot do. ' +
                    'Use a grails-scaffolding matching this Gradle plugin.', GENERATOR)
            return
        }
        Integer protocol = readProtocol(templates.generator)
        if (protocol != GENERATOR_PROTOCOL) {
            logger.warn('The scaffolding library on the runtime classpath provides a {} for version {} of its exchange with ' +
                    'this Gradle plugin, which speaks version {}, so no scaffolded page is compiled and each is expanded when ' +
                    'it is first rendered, which a native image cannot do. Use a grails-scaffolding matching this Gradle plugin.',
                    GENERATOR, protocol ?: 'none', GENERATOR_PROTOCOL)
            return
        }

        // every distinct copy once, in a directory of its own, so a domain class is handed the copies
        // its controllers can choose
        File work = temporaryDir
        File templatesRoot = new File(work, 'templates')
        templatesRoot.deleteDir()
        templates.copies.eachWithIndex { TemplateCopy copy, int index ->
            copy.directory = new File(templatesRoot, String.valueOf(index))
            File file = new File(copy.directory, "${copy.path}.gsp")
            file.parentFile.mkdirs()
            file.bytes = copy.content
        }
        Map<String, Set<TemplateCopy>> plan = new TreeMap<>()
        for (Controller controller : controllers) {
            plan.computeIfAbsent(controller.domain) { new LinkedHashSet<TemplateCopy>() }.addAll(templates.choosableBy(controller))
        }
        File planFile = new File(work, 'plan.txt')
        planFile.setText(plan.collect { String domain, Set<TemplateCopy> copies ->
            ([domain] + copies*.directory*.absolutePath).join('\t')
        }.join('\n'), 'UTF-8')
        File originsFile = new File(work, 'origins.txt')
        originsFile.setText(templates.copies.collect { TemplateCopy copy ->
            "${copy.directory.absolutePath}\t${copy.origin}"
        }.join('\n'), 'UTF-8')
        File reportFile = new File(work, 'report.txt')
        reportFile.delete()

        execOperations.javaexec(new Action<JavaExecSpec>() {
            @Override
            void execute(JavaExecSpec spec) {
                if (javaLauncher.present) {
                    spec.executable = javaLauncher.get().executablePath.asFile.absolutePath
                }
                spec.classpath = runtimeClasspath
                spec.mainClass.set(GENERATOR)
                spec.args(planFile.absolutePath, originsFile.absolutePath, outputDir.absolutePath, pageEncoding.get(),
                        reportFile.absolutePath)
            }
        }).assertNormalExitValue()
        report(templates, reportFile)
    }

    /**
     * Acts on what the generator reports, by whose each template is.
     *
     * <p>A page expanded from a template a dependency supplies is listed as optional, and a template
     * a dependency supplies that could not be expanded for a domain class is a warning: it may never
     * be the copy the resolver chooses - a stock template the application has replaced, say - and
     * where it is, it is expanded when rendered, as it was before any page was compiled. A template
     * of the application's own is its code as much as a view is, and a view that does not compile
     * fails the build: its pages are not optional, and one that cannot be expanded fails the build
     * here, after every one is reported.</p>
     */
    private void report(Templates templates, File reportFile) {
        Map<String, TemplateCopy> byDirectory = templates.copies.collectEntries { TemplateCopy copy ->
            [copy.directory.absolutePath, copy]
        }
        List<String> optional = []
        List<String> applicationFailures = []
        (reportFile.isFile() ? reportFile.readLines('UTF-8') : []).each { String line ->
            List<String> fields = line.split('\t', 4).toList()
            TemplateCopy copy = fields.size() > 1 ? byDirectory.get(new File(fields[1]).absolutePath) : null
            if (fields.size() == 3 && fields[0] == 'page') {
                if (!copy?.application) {
                    optional.add(fields[2])
                }
                return
            }
            if (fields.size() < 4 || fields[0] != 'failed') {
                return
            }
            String failure = "the scaffolding template ${copy?.path}, from ${copy?.origin}, for ${fields[2]}: ${fields[3]}"
            if (copy?.application) {
                applicationFailures.add(failure)
            }
            else {
                logger.warn('Could not expand {}. No page is compiled for it; if it is rendered it fails the same way.', failure)
            }
        }
        optionalPages.get().asFile.setText(optional.sort().join('\n'), 'UTF-8')
        if (applicationFailures) {
            throw new GradleException('Could not expand ' + applicationFailures.join('\nCould not expand ') +
                    '\nA template of the application\'s own fails the build, as a view that does not compile does.')
        }
    }

    /**
     * Reads the templates: the application's own, then every distinct copy the runtime classpath
     * carries; and whether the classpath carries the generator, which a scaffolding library older
     * than this plugin does not.
     */
    private Templates findTemplates() {
        Templates templates = new Templates()
        templateOverrides.asFileTree.visit { FileVisitDetails details ->
            if (!details.directory && details.name.endsWith('.gsp')) {
                templates.add(baseName(details.relativePath.pathString), details.file.bytes,
                        "the application's ${details.relativePath.pathString}", true)
            }
        }
        packagedTemplates.asFileTree.visit { FileVisitDetails details ->
            String path = details.relativePath.pathString
            if (!details.directory && path.startsWith(TEMPLATE_PATH) && path.endsWith('.gsp')) {
                String templatePath = path.substring(TEMPLATE_PATH.length())
                templates.add(baseName(templatePath), details.file.bytes, "the application's ${templatePath}", true)
            }
        }
        String generator = GENERATOR.replace('.', '/') + '.class'
        for (File entry : runtimeClasspath.files) {
            if (entry.isDirectory()) {
                File generatorClass = new File(entry, generator)
                if (templates.generator == null && generatorClass.isFile()) {
                    templates.generator = generatorClass.bytes
                }
                File dir = new File(entry, TEMPLATE_PATH)
                if (dir.isDirectory()) {
                    dir.eachFileRecurse { File f ->
                        if (f.isFile() && f.name.endsWith('.gsp')) {
                            String path = dir.toPath().relativize(f.toPath()).toString().replace(File.separatorChar, '/' as char)
                            templates.add(baseName(path), f.bytes, "${entry.name}/${TEMPLATE_PATH}${path}", false)
                        }
                    }
                }
            }
            else if (entry.isFile()) {
                openArchive(entry)?.withCloseable { JarFile jar ->
                    JarEntry generatorClass = jar.getJarEntry(generator)
                    if (templates.generator == null && generatorClass != null) {
                        templates.generator = jar.getInputStream(generatorClass).withCloseable { InputStream input -> input.bytes }
                    }
                    for (JarEntry e : jar.entries()) {
                        if (!e.directory && e.name.startsWith(TEMPLATE_PATH) && e.name.endsWith('.gsp')) {
                            templates.add(baseName(e.name.substring(TEMPLATE_PATH.length())),
                                    jar.getInputStream(e).withCloseable { InputStream input -> input.bytes }, "${entry.name}!/${e.name}", false)
                        }
                    }
                }
            }
        }
        templates
    }

    /**
     * Every scaffolded controller: the application's own, and each a plugin on the runtime classpath
     * provides, with the domain class it scaffolds and whether it has a namespace.
     */
    private List<Controller> findScaffoldedControllers() {
        List<Controller> controllers = []
        Map<String, Boolean> ancestors = [:]
        URL[] classpath = (classesDirs.files + runtimeClasspath.files).collect { it.toURI().toURL() } as URL[]
        new URLClassLoader(classpath, (ClassLoader) null).withCloseable { URLClassLoader resources ->
            for (File dir : classesDirs.files) {
                if (dir.isDirectory()) {
                    dir.eachFileRecurse { File f ->
                        if (f.name.endsWith('Controller.class')) {
                            Controller controller = readController(f.bytes, f.path, resources, ancestors)
                            if (controller != null) {
                                controllers.add(controller)
                            }
                        }
                    }
                }
            }
            for (File entry : runtimeClasspath.files) {
                if (entry.isFile()) {
                    openArchive(entry)?.withCloseable { JarFile jar ->
                        if (jar.getJarEntry(PLUGIN_DESCRIPTOR) == null) {
                            return
                        }
                        for (JarEntry e : jar.entries()) {
                            if (!e.directory && e.name.endsWith('Controller.class')) {
                                byte[] bytes = jar.getInputStream(e).withCloseable { InputStream input -> input.bytes }
                                Controller controller = readController(bytes, "${entry.name}!/${e.name}", resources, ancestors)
                                if (controller != null) {
                                    controllers.add(controller)
                                }
                            }
                        }
                    }
                }
                else if (entry.isDirectory() && new File(entry, PLUGIN_DESCRIPTOR).isFile()) {
                    entry.eachFileRecurse { File f ->
                        if (f.name.endsWith('Controller.class')) {
                            Controller controller = readController(f.bytes, f.path, resources, ancestors)
                            if (controller != null) {
                                controllers.add(controller)
                            }
                        }
                    }
                }
            }
        }
        controllers
    }

    /**
     * A class this cannot read - newer than the ASM bundled with Gradle reads, or damaged - is left
     * out with a warning rather than failing the build: its views are then expanded when rendered,
     * as they were before any was compiled, which works on the JVM and not in a native image.
     */
    private Controller readController(byte[] bytes, String origin, ClassLoader resources, Map<String, Boolean> ancestors) {
        ClassReader reader
        try {
            reader = new ClassReader(bytes)
        }
        catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            logger.warn('Could not read {}, so if it is a scaffolded controller no page is compiled for it and its views are ' +
                    'expanded when rendered, which a native image cannot do: {}', origin, e.message)
            return null
        }
        String domain = readScaffoldDomain(reader)
        domain == null ? null : new Controller(domain, hasNamespace(reader, resources, ancestors))
    }

    /**
     * Whether a controller declares a namespace, itself or through a superclass or an interface,
     * read from its declarations without running any of its code.
     *
     * <p>The runtime reads the namespace through the controller's metaclass, which sees a static
     * field or accessor on the class and its superclasses, and a constant on any interface it
     * implements. Groovy traits rename their namespace fields but emit a static
     * {@code getNamespace()} accessor on the implementing class, so they are covered by the
     * accessor.</p>
     *
     * <p>A declaration is all this can see, not its value, so {@code static namespace = null}
     * still counts even though the runtime, which tests the value, gives that controller no
     * namespace. The value lives in {@code <clinit>} for the usual Groovy forms and code is not
     * read here; such a controller only has namespace-specific templates expanded for it that it
     * will not use.</p>
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
        if (declared) {
            return true
        }
        List<String> supertypes = ([reader.superName] + (reader.interfaces as List<String>)).findAll { String type ->
            type != null && type != 'java/lang/Object'
        }
        for (String supertype : supertypes) {
            Boolean inherited = ancestors.get(supertype)
            if (inherited == null) {
                inherited = ancestorHasNamespace(supertype, resources, ancestors)
                ancestors.put(supertype, inherited)
            }
            if (inherited) {
                return true
            }
        }
        false
    }

    /**
     * Supertypes can come from dependencies, whose class files may be newer than the bundled ASM
     * reads, or whose bytecode may be damaged or unreadable. One that cannot be read is taken to
     * declare no namespace, with a warning, rather than failing the build.
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
            logger.warn('Could not read {} to look for a namespace its subclasses inherit, so none is assumed and no ' +
                    'namespace-specific page is compiled for them: {}', internalName.replace('/', '.'), e.message)
            return false
        }
    }

    /**
     * Returns the fully qualified name of the domain class a controller scaffolds, or {@code null}
     * when it is not scaffolded. Qualified rather than simple because a page declaring the type of
     * its model has to name a type that resolves.
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

    /**
     * Opens a file on the runtime classpath as the archive the application's class loader reads it
     * as, whatever it is named; {@code null} for one that is not an archive, which holds nothing
     * this looks for.
     */
    private JarFile openArchive(File entry) {
        try {
            return new JarFile(entry)
        }
        catch (ZipException e) {
            logger.info('{} on the runtime classpath is not an archive, so no template or controller is read from it: {}',
                    entry, e.message)
            return null
        }
    }

    /**
     * The version of the exchange a generator's class file declares, or {@code null} for one that
     * declares none - a generator from before the exchange was versioned - or cannot be read.
     */
    private Integer readProtocol(byte[] generatorClass) {
        Integer protocol = null
        try {
            new ClassReader(generatorClass).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    if (name == 'PROTOCOL' && (access & Opcodes.ACC_STATIC) != 0 && value instanceof Integer) {
                        protocol = (Integer) value
                    }
                    null
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)
        }
        catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            logger.info('Could not read {} for the version of its exchange: {}', GENERATOR, e.message)
        }
        protocol
    }

    private static String baseName(String fileName) {
        fileName.endsWith('.gsp') ? fileName[0..<fileName.length() - 4] : fileName
    }

    /** A scaffolded controller: the domain class it scaffolds, and whether it has a namespace. */
    private static final class Controller {

        final String domain

        final boolean namespaced

        Controller(String domain, boolean namespaced) {
            this.domain = domain
            this.namespaced = namespaced
        }

    }

    /**
     * One copy of a template, and where it was first found, named without the machine's own paths:
     * the page it expands to says so, and a page is part of what the build caches.
     */
    private static final class TemplateCopy {

        final String path

        final byte[] content

        final String origin

        /** Whether the application has this copy of its own, so that it is the application's code. */
        boolean application

        File directory

        TemplateCopy(String path, byte[] content, String origin) {
            this.path = path
            this.content = content
            this.origin = origin
        }

    }

    /**
     * Every distinct copy of every template, and the class file of the generator the application's
     * class loader finds first, if any.
     */
    private static final class Templates {

        final List<TemplateCopy> copies = []

        byte[] generator

        void add(String path, byte[] content, String origin, boolean application) {
            TemplateCopy copy = copies.find { TemplateCopy c -> c.path == path && Arrays.equals(c.content, content) }
            if (copy == null) {
                copy = new TemplateCopy(path, content, origin)
                copies.add(copy)
            }
            copy.application = copy.application || application
        }

        /**
         * The copies the resolver could choose for a controller: every copy of every template, but a
         * namespace-specific one only for a controller with a namespace.
         */
        List<TemplateCopy> choosableBy(Controller controller) {
            controller.namespaced ? copies : copies.findAll { TemplateCopy c -> !c.path.contains('/') }
        }

    }
}
