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

import groovy.text.GStringTemplateEngine
import groovy.transform.CompileStatic
import groovyjarjarasm.asm.AnnotationVisitor
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.Opcodes
import groovyjarjarasm.asm.Type

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

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
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getTemplateClasspath()

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
        int written = 0
        for (Map.Entry<String, String> controller : findScaffoldedControllers()) {
            String fullName = controller.value
            String className = fullName.tokenize('.').last()
            String propertyName = decapitalize(className)
            String packageName = fullName.contains('.') ? fullName[0..<fullName.lastIndexOf('.')] : ''
            for (String viewName : VIEW_NAMES) {
                String template = templates.get(viewName)
                if (template == null) {
                    continue
                }
                // a view the application wrote itself already wins at runtime, so leaving it out
                // keeps build-time and runtime resolution agreeing
                if (declared.any { it.path.endsWith("views/${controller.key}/${viewName}.gsp".toString()) }) {
                    logger.info("Skipping ${controller.key}/${viewName}.gsp, the application declares it")
                    continue
                }
                File target = new File(outputDir, "${controller.key}/${viewName}.gsp")
                target.parentFile.mkdirs()
                target.text = expand(template, className, propertyName, fullName, packageName)
                written++
            }
        }
        logger.info("Generated ${written} scaffolded view(s)")
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
     * Maps view directory name to the fully qualified domain class, for every {@code @Scaffold}
     * controller. Qualified rather than simple because a view declaring the type of its model has to
     * name a type that resolves.
     *
     * <p>A view directory is named for the controller alone - {@code getDeployedViewURI} builds
     * {@code /WEB-INF/grails-app/views/<controller>/<view>.gsp} and never consults the namespace -
     * so two controllers of the same simple name in different packages share one directory whatever
     * their namespaces are. Where they scaffold different domains, no single page can serve both:
     * whichever was written would declare one domain as its model and be rendered by the controller
     * of the other. Both are left out rather than one of them guessed at, and the resolver goes on
     * expanding a template per request for them, which is what it did before any of this and is the
     * one thing that gets each controller its own domain. Everything else in the project is still
     * precompiled.</p>
     */
    private Map<String, String> findScaffoldedControllers() {
        Map<String, String> found = [:]
        Map<String, List<String>> claimants = [:]
        for (File dir : classesDirs.files) {
            if (!dir.isDirectory()) {
                continue
            }
            dir.eachFileRecurse { File f ->
                if (!f.name.endsWith('Controller.class')) {
                    return
                }
                String domain = readScaffoldDomain(f)
                if (domain == null) {
                    return
                }
                String controllerName = decapitalize(f.name - 'Controller.class')
                claimants.computeIfAbsent(controllerName) { [] }.add(domain)
                found.put(controllerName, domain)
            }
        }
        claimants.each { String controllerName, List<String> domains ->
            List<String> distinct = domains.unique(false)
            if (distinct.size() > 1) {
                found.remove(controllerName)
                logger.warn("Not precompiling the views of ${controllerName}: " +
                        "${distinct.size()} controllers named ${capitalize(controllerName)}Controller " +
                        "scaffold different domains (${distinct.join(', ')}) and share the one view " +
                        'directory. They are expanded per request instead, as they were before.')
            }
        }
        found
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
    private String readScaffoldDomain(File classFile) {
        boolean scaffolded = false
        String fromValue = null
        String fromDomain = null
        classFile.withInputStream { InputStream input ->
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
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
        }
        scaffolded ? (fromDomain ?: fromValue) : null
    }

    private static String baseName(String fileName) {
        fileName.endsWith('.gsp') ? fileName[0..<fileName.length() - 4] : fileName
    }

    private static String decapitalize(String name) {
        name ? name[0].toLowerCase() + name.substring(1) : name
    }

    private static String capitalize(String name) {
        name ? name[0].toUpperCase() + name.substring(1) : name
    }
}
