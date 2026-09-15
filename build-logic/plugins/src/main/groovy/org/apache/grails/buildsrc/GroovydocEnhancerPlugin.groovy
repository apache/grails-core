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

import javax.inject.Inject

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.xml.MarkupBuilder

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.javadoc.Groovydoc
import org.gradle.process.ExecOperations

@CompileStatic
abstract class GroovydocEnhancerPlugin implements Plugin<Project> {

    @Inject
    protected abstract ExecOperations getExecOperations()

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
        configureAntBuilderExecution(project, extension, execOperations)
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
    private static void configureAntBuilderExecution(Project project, GroovydocEnhancerExtension extension,
                                                     ExecOperations execOperations) {
        project.tasks.withType(Groovydoc).configureEach { gdoc ->
            if (!extension.useAntBuilder.get()) {
                return
            }

            // The external javadoc mapping changes the generated HTML, so a change to it has to
            // invalidate the task's output.
            gdoc.inputs.property('groovydocLinks', project.provider { resolveLinks(gdoc) })
            gdoc.maxMemory.convention('3g')

            gdoc.actions.clear()
            gdoc.doLast {
                def destDir = gdoc.destinationDir.tap { it.mkdirs() }
                def sourceDirs = resolveSourceDirectories(gdoc, project)
                if (sourceDirs.isEmpty()) {
                    throw new org.gradle.api.GradleException(
                            "groovydoc task '${gdoc.name}': no source directories found. " +
                            'Every published module must produce a groovydoc jar for Maven Central.'
                    )
                }

                def classpath = gdoc.groovyClasspath
                if (!classpath || classpath.empty) {
                    throw new org.gradle.api.GradleException(
                            "groovydoc task '${gdoc.name}': groovyClasspath is empty. " +
                            'Every published module must produce a groovydoc jar for Maven Central.'
                    )
                }

                // Groovydoc resolves references to types outside the documented sources with
                // Class.forName against its own classloader; anything it cannot load becomes a
                // link to a page that was never generated. The groovydoc classpath includes
                // compile and runtime dependencies so types such as Hibernate (which need
                // runtime-only jars like jboss-logging) can load; the 'links' below then turn
                // those types into external javadoc URLs.
                def antClasspath = gdoc.classpath ? classpath.plus(gdoc.classpath) : classpath

                def links = resolveLinks(gdoc)
                def sourcepath = sourceDirs
                        .collect { it.absolutePath }
                        .join(File.pathSeparator)

                def antArgs = [
                        destdir: destDir.absolutePath,
                        sourcepath: sourcepath,
                        packagenames: '**.*',
                        windowtitle: gdoc.windowTitle ?: '',
                        doctitle: gdoc.docTitle ?: '',
                        footer: gdoc.footer ?: '',
                        access: resolveGroovydocProperty(gdoc.access)?.name()?.toLowerCase() ?: 'protected',
                        author: resolveGroovydocProperty(gdoc.includeAuthor) as String,
                        noTimestamp: resolveGroovydocProperty(gdoc.noTimestamp) as String,
                        noVersionStamp: resolveGroovydocProperty(gdoc.noVersionStamp) as String,
                        processScripts: resolveGroovydocProperty(gdoc.processScripts) as String,
                        includeMainForScripts: resolveGroovydocProperty(gdoc.includeMainForScripts) as String
                ]

                if (extension.javaVersionEnabled.get()) {
                    antArgs.put('javaVersion', extension.javaVersion.get())
                }

                // A fresh process releases parser trees and classloaders after each task.
                // Running sequentially inside Gradle still retains enough state to exhaust
                // its heap when the aggregate documentation follows the module docs.
                File buildFile = new File(gdoc.temporaryDir, 'groovydoc.xml')
                buildFile.withWriter('UTF-8') { writer ->
                    new MarkupBuilder(writer).project(name: 'groovydoc', default: 'docs') {
                        taskdef(name: 'groovydoc', classname: 'org.codehaus.groovy.ant.Groovydoc')
                        target(name: 'docs') {
                            groovydoc(antArgs) {
                                for (var l in links) {
                                    link(packages: l.packages, href: l.href)
                                }
                            }
                        }
                    }
                }
                execOperations.javaexec { spec ->
                    spec.executable = gdoc.javaLauncher.get().executablePath.asFile.absolutePath
                    spec.classpath(antClasspath)
                    spec.mainClass.set('org.apache.tools.ant.Main')
                    // Included builds (such as Forge) do not inherit the root JVM settings.
                    spec.systemProperty('spock.iKnowWhatImDoing.disableGroovyVersionCheck', 'true')
                    spec.maxHeapSize = gdoc.maxMemory.get()
                    spec.args('-f', buildFile.absolutePath)
                }.assertNormalExitValue()
            }
        }
    }

    @CompileDynamic
    private static List<File> resolveSourceDirectories(Groovydoc gdoc, Project project) {
        if (gdoc.ext.has('groovydocSourceDirs') && gdoc.ext.groovydocSourceDirs) {
            return (gdoc.ext.groovydocSourceDirs as List<File>)
                    .findAll { it.exists() }
                    .unique()
        }

        List<File> sourceDirs = []
        def sourceSets = project.extensions.findByType(SourceSetContainer)
        if (sourceSets) {
            def mainSS = sourceSets.findByName('main')
            if (mainSS) {
                sourceDirs.addAll(mainSS.groovy.srcDirs.findAll { it.exists() })
                sourceDirs.addAll(mainSS.java.srcDirs.findAll { it.exists() })
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
