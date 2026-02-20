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
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.javadoc.Groovydoc

@CompileStatic
class GrailsGroovydocPlugin implements Plugin<Project> {

    static final String MATOMO_FOOTER = '''\
<!-- Matomo -->
<script>
    var _paq = window._paq = window._paq || [];
    /* tracker methods like "setCustomDimension" should be called before "trackPageView" */
    _paq.push(["setDoNotTrack", true]);
    _paq.push(["disableCookies"]);
    _paq.push(['trackPageView']);
    _paq.push(['enableLinkTracking']);
    (function() {
        var u="https://analytics.apache.org/";
        _paq.push(['setTrackerUrl', u+'matomo.php']);
        _paq.push(['setSiteId', '79']);
        var d=document, g=d.createElement('script'), s=d.getElementsByTagName('script')[0];
        g.async=true; g.src=u+'matomo.js'; s.parentNode.insertBefore(g,s);
    })();
</script>
<!-- End Matomo Code -->'''

    @Override
    void apply(Project project) {
        GrailsGroovydocExtension extension = project.extensions.create(
                'grailsGroovydoc', GrailsGroovydocExtension, project
        )
        registerDocumentationConfiguration(project)
        configureGroovydocDefaults(project)
        configureAntBuilderExecution(project, extension)
    }

    private static void registerDocumentationConfiguration(Project project) {
        if (project.configurations.names.contains('documentation')) {
            return
        }
        project.configurations.register('documentation') { Configuration config ->
            config.canBeConsumed = false
            config.canBeResolved = true
            config.attributes { container ->
                container.attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category, Category.LIBRARY))
                container.attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling, Bundling.EXTERNAL))
                container.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
            }
        }
    }

    @CompileDynamic
    private static void configureGroovydocDefaults(Project project) {
        project.tasks.withType(Groovydoc).configureEach { Groovydoc gdoc ->
            gdoc.includeAuthor = false
            gdoc.includeMainForScripts = false
            gdoc.processScripts = false
            gdoc.noTimestamp = true
            gdoc.noVersionStamp = false
            gdoc.footer = MATOMO_FOOTER
            if (project.configurations.names.contains('documentation')) {
                gdoc.groovyClasspath = project.configurations.getByName('documentation')
            }
        }
    }

    @CompileDynamic
    private static void configureAntBuilderExecution(Project project, GrailsGroovydocExtension extension) {
        project.tasks.withType(Groovydoc).configureEach { Groovydoc gdoc ->
            gdoc.actions.clear()
            gdoc.doLast {
                File destDir = gdoc.destinationDir
                destDir.mkdirs()

                List<File> sourceDirs = resolveSourceDirectories(gdoc, project)
                if (sourceDirs.isEmpty()) {
                    project.logger.lifecycle("Skipping groovydoc for ${gdoc.name}: no source directories found")
                    return
                }

                Configuration docConfig = project.configurations.findByName('documentation')
                if (!docConfig) {
                    project.logger.warn("Skipping groovydoc for ${gdoc.name}: 'documentation' configuration not found")
                    return
                }

                project.ant.taskdef(
                        name: 'groovydoc',
                        classname: 'org.codehaus.groovy.ant.Groovydoc',
                        classpath: docConfig.asPath
                )

                List<Map<String, String>> links = resolveLinks(gdoc)
                String sourcepath = sourceDirs.collect { it.absolutePath }.join(File.pathSeparator)

                Map<String, Object> antArgs = [
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
                    for (Map<String, String> l in links) {
                        link(packages: l.packages, href: l.href)
                    }
                }
            }
        }
    }

    @CompileDynamic
    private static List<File> resolveSourceDirectories(Groovydoc gdoc, Project project) {
        if (gdoc.ext.has('groovydocSourceDirs') && gdoc.ext.groovydocSourceDirs) {
            return (gdoc.ext.groovydocSourceDirs as List<File>).findAll { it.exists() }.unique() as List<File>
        }

        List<File> sourceDirs = []
        SourceSetContainer sourceSets = project.extensions.findByType(SourceSetContainer)
        if (sourceSets) {
            SourceSet mainSS = sourceSets.findByName('main')
            if (mainSS) {
                sourceDirs.addAll(mainSS.groovy.srcDirs.findAll { it.exists() })
                sourceDirs.addAll(mainSS.java.srcDirs.findAll { it.exists() })
            }
        }
        sourceDirs.unique() as List<File>
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
