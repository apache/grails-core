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
package org.apache.grails.gradle.groovydoc

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

/**
 * A Gradle plugin that enhances Groovydoc generation to support modern Java
 * source levels. Gradle's built-in {@link Groovydoc} task does not expose
 * the {@code javaVersion} parameter (added in Groovy 4.0.27 via
 * <a href="https://issues.apache.org/jira/browse/GROOVY-11668">GROOVY-11668</a>),
 * so projects using Java 17+ features (sealed classes, records, etc.) fail
 * to generate Groovydoc.
 *
 * <p>This plugin replaces the built-in task execution with a direct AntBuilder
 * invocation that passes the {@code javaVersion} parameter, enabling accurate
 * Groovydoc generation for modern Java source levels.</p>
 *
 * <p>Configure via the {@code groovydocEnhancer} extension:</p>
 * <pre>
 * groovydocEnhancer {
 *     javaVersion = 'JAVA_17'       // Java source level for parsing
 *     javaVersionEnabled = true      // set false for Groovy < 4.0.27
 *     useAntBuilder = true           // set false when Gradle adds native support
 *     footer = '&lt;p&gt;My Footer&lt;/p&gt;'
 * }
 * </pre>
 *
 * @since 7.0.8
 * @see GroovydocEnhancerExtension
 * @see <a href="https://github.com/gradle/gradle/issues/33659">gradle#33659</a>
 */
@CompileStatic
class GroovydocEnhancerPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        GroovydocEnhancerExtension extension = project.extensions.create(
                'groovydocEnhancer',
                GroovydocEnhancerExtension,
                project
        )
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
        }
    }

    @CompileDynamic
    private static void configureAntBuilderExecution(Project project, GroovydocEnhancerExtension extension) {
        project.tasks.withType(Groovydoc).configureEach { gdoc ->
            if (!extension.useAntBuilder.get()) {
                return
            }

            gdoc.actions.clear()
            gdoc.doLast {
                def destDir = gdoc.destinationDir.tap { it.mkdirs() }
                def sourceDirs = resolveSourceDirectories(gdoc, project)
                if (sourceDirs.isEmpty()) {
                    project.logger.lifecycle(
                            'Skipping groovydoc for {}: no source directories found',
                            gdoc.name
                    )
                    return
                }

                def docConfig = project.configurations.findByName('documentation')
                if (!docConfig) {
                    project.logger.warn(
                            'Skipping groovydoc for {}: \'documentation\' configuration not found',
                            gdoc.name
                    )
                    return
                }

                project.ant.taskdef(
                        name: 'groovydoc',
                        classname: 'org.codehaus.groovy.ant.Groovydoc',
                        classpath: docConfig.asPath
                )

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

                project.ant.groovydoc(antArgs) {
                    for (var l in links) {
                        link(packages: l.packages, href: l.href)
                    }
                }
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
            return gdoc.ext.groovydocLinks as List<Map<String, String>>
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
