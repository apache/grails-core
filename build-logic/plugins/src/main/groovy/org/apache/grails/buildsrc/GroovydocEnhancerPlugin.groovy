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
package org.apache.grails.buildsrc

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.javadoc.Groovydoc

@CompileStatic
class GroovydocEnhancerPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        GroovydocEnhancerExtension extension = project.extensions.create(
                'groovydocEnhancer',
                GroovydocEnhancerExtension,
                project
        )
        Provider<GroovydocMemoryThrottle> throttle = project.gradle.sharedServices.registerIfAbsent(
                'groovydocMemoryThrottle', GroovydocMemoryThrottle) {
            it.maxParallelUsages.set(1)
        }
        project.tasks.withType(Groovydoc).configureEach {
            it.usesService(throttle)
        }
        registerDocumentationConfiguration(project)
        configureGroovydocDefaults(project, extension)
        configureAntBuilderExecution(project, extension)
    }

    private static void registerDocumentationConfiguration(Project project) {
        if (project.configurations.names.contains('documentation')) {
            return
        }
        project.configurations.register('documentation') {
            it.canBeConsumed = false
            it.canBeResolved = true
            it.attributes {
                it.attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category, Category.LIBRARY))
                it.attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling, Bundling.EXTERNAL))
                it.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
            }
        }
    }

    @CompileDynamic
    private static void configureGroovydocDefaults(Project project, GroovydocEnhancerExtension extension) {
        project.tasks.withType(Groovydoc).configureEach {
            it.includeAuthor.set(false)
            it.includeMainForScripts.set(false)
            it.processScripts.set(false)
            it.noTimestamp = true
            it.noVersionStamp = false
            def footerValue = extension.footer.getOrElse('')
            if (footerValue) {
                it.footer = footerValue
            }
            if (project.configurations.names.contains('documentation')) {
                it.groovyClasspath = project.configurations.getByName('documentation')
            }
            // Groovydoc Class.forName's referenced types against this classpath. Compile
            // classpath is not enough: Hibernate 7 (and similar libraries) publish logging
            // APIs such as jboss-logging as runtime-only transitives, and loading those
            // classes without the jar fails with NoClassDefFoundError.
            def runtimeClasspath = project.configurations.findByName('runtimeClasspath')
            if (runtimeClasspath != null) {
                it.classpath = it.classpath ? it.classpath.plus(runtimeClasspath) : runtimeClasspath
            }
        }
    }

    @CompileDynamic
    private static void configureAntBuilderExecution(Project project, GroovydocEnhancerExtension extension) {
        GroovydocRunner runner = project.objects.newInstance(GroovydocRunner)
        // Same form as PublishGuideTask uses for guideMaxHeapSize
        Provider<String> maxHeapSize = project.providers.gradleProperty('groovydocMaxHeapSize')
                .orElse(extension.maxHeapSize)
        Provider<String> javaVersion = extension.javaVersionEnabled.flatMap { boolean enabled ->
            enabled ? extension.javaVersion : project.providers.provider { (String) null }
        }

        project.tasks.withType(Groovydoc).configureEach { gdoc ->
            if (!extension.useAntBuilder.get()) {
                return
            }

            // The task's extra properties are read while the task graph is stored, since the
            // configuration cache does not restore a task's extensions when it runs the task.
            Provider<List<Map<String, String>>> groovydocLinks = project.provider { resolveLinks(gdoc) }
            Provider<List<File>> groovydocSourceDirs = project.provider { resolveSourceDirectories(gdoc, project) }

            // The external javadoc mapping changes the generated HTML, so a change to it has to
            // invalidate the task's output.
            gdoc.inputs.property('groovydocLinks', groovydocLinks)

            gdoc.actions.clear()
            gdoc.doLast { Groovydoc task ->
                def destDir = task.destinationDir.tap { it.mkdirs() }
                def sourceDirs = groovydocSourceDirs.get().findAll { it.exists() }
                if (sourceDirs.isEmpty()) {
                    throw new org.gradle.api.GradleException(
                            "groovydoc task '${task.name}': no source directories found. " +
                            'Every published module must produce a groovydoc jar for Maven Central.'
                    )
                }

                def classpath = task.groovyClasspath
                if (!classpath || classpath.empty) {
                    throw new org.gradle.api.GradleException(
                            "groovydoc task '${task.name}': groovyClasspath is empty. " +
                            'Every published module must produce a groovydoc jar for Maven Central.'
                    )
                }

                // Groovydoc resolves references to types outside the documented sources with
                // Class.forName against its own classloader; anything it cannot load becomes a
                // link to a page that was never generated. The groovydoc classpath includes
                // compile and runtime dependencies so types such as Hibernate (which need
                // runtime-only jars like jboss-logging) can load; the 'links' below then turn
                // those types into external javadoc URLs.
                def antClasspath = task.classpath ? classpath.plus(task.classpath) : classpath

                def sourcepath = sourceDirs
                        .collect { it.absolutePath }
                        .join(File.pathSeparator)

                def antArgs = [
                        destdir: destDir.absolutePath,
                        sourcepath: sourcepath,
                        packagenames: '**.*',
                        windowtitle: task.windowTitle ?: '',
                        doctitle: task.docTitle ?: '',
                        footer: task.footer ?: '',
                        access: GroovydocEnhancerPlugin.resolveGroovydocProperty(task.access)?.name()?.toLowerCase() ?: 'protected',
                        author: GroovydocEnhancerPlugin.resolveGroovydocProperty(task.includeAuthor) as String,
                        noTimestamp: GroovydocEnhancerPlugin.resolveGroovydocProperty(task.noTimestamp) as String,
                        noVersionStamp: GroovydocEnhancerPlugin.resolveGroovydocProperty(task.noVersionStamp) as String,
                        processScripts: GroovydocEnhancerPlugin.resolveGroovydocProperty(task.processScripts) as String,
                        includeMainForScripts: GroovydocEnhancerPlugin.resolveGroovydocProperty(task.includeMainForScripts) as String
                ]

                if (javaVersion.present) {
                    antArgs.put('javaVersion', javaVersion.get())
                }

                runner.run(
                        antClasspath,
                        maxHeapSize.get(),
                        task.temporaryDir,
                        antArgs,
                        groovydocLinks.get(),
                        task.logger.infoEnabled
                )
            }
        }
    }

    @CompileDynamic
    private static List<File> resolveSourceDirectories(Groovydoc gdoc, Project project) {
        if (gdoc.ext.has('groovydocSourceDirs') && gdoc.ext.groovydocSourceDirs) {
            return (gdoc.ext.groovydocSourceDirs as List<File>).unique()
        }

        List<File> sourceDirs = []
        def sourceSets = project.extensions.findByType(SourceSetContainer)
        if (sourceSets) {
            def mainSS = sourceSets.findByName('main')
            if (mainSS) {
                sourceDirs.addAll(mainSS.groovy.srcDirs)
                sourceDirs.addAll(mainSS.java.srcDirs)
            }
        }
        sourceDirs.unique()
    }

    @CompileDynamic
    private static List<Map<String, String>> resolveLinks(Groovydoc gdoc) {
        if (gdoc.ext.has('groovydocLinks')) {
            def links = resolveGroovydocProperty(gdoc.ext.groovydocLinks)
            if (links) {
                return links as List<Map<String, String>>
            }
        }
        []
    }

    static Object resolveGroovydocProperty(Object value) {
        if (value instanceof Provider) {
            return ((Provider) value).getOrNull()
        }
        value
    }
}
