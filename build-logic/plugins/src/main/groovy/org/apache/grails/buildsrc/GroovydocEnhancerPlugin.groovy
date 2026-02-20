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
