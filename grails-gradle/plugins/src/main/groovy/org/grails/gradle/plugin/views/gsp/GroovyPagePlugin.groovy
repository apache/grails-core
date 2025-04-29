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
package org.grails.gradle.plugin.views.gsp

import groovy.transform.CompileStatic
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetOutput
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.War
import org.grails.gradle.plugin.core.GrailsExtension
import org.grails.gradle.plugin.util.SourceSets

/**
 * A plugin that adds support for compiling Groovy Server Pages (GSP)
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class GroovyPagePlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        TaskContainer tasks = project.tasks

        project.configurations.register('gspCompile')
        project.dependencies.add('gspCompile', 'jakarta.servlet:jakarta.servlet-api:6.0.0')

        SourceSet mainSourceSet = SourceSets.findMainSourceSet(project)
        SourceSetOutput output = mainSourceSet?.output
        FileCollection classesDirs = resolveClassesDirs(output, project)
        Provider<Directory> destDir = project.layout.buildDirectory.dir('gsp-classes/main')
        output?.dir("gsp-classes")

        FileCollection allClasspath = project.getObjects().fileCollection().from(
                [
                        project.configurations.named('compileClasspath'),
                        project.configurations.named('gspCompile'),
                        classesDirs,
                        project.configurations.findByName('providedCompile') ?: null
                ].findAll { it }
        )

        def compileGroovyPages = tasks.register("compileGroovyPages", GroovyPageForkCompileTask) {
            it.destinationDirectory.set(destDir)
            it.tmpDirPath = getTmpDirPath(project)
            it.source = project.layout.projectDirectory.dir('grails-app/views')
            it.serverpath.set("/WEB-INF/grails-app/views/")
            it.classpath = allClasspath
        }

        def compileWebappGroovyPages = tasks.register("compileWebappGroovyPages", GroovyPageForkCompileTask) {
            it.destinationDirectory.set(destDir)
            it.source = project.layout.projectDirectory.dir('src/main/webapp')
            it.tmpDirPath = getTmpDirPath(project)
            it.serverpath.set("/")
            it.classpath = allClasspath
        }

        registerGrailsExtension(project)
        project.afterEvaluate {
            GrailsExtension grailsExt = project.extensions.getByType(GrailsExtension)
            if (grailsExt.pathingJar && Os.isFamily(Os.FAMILY_WINDOWS)) {
                Jar pathingJar = tasks.named('pathingJar', Jar).get()
                ConfigurableFileCollection withPathingJarClasspath = project.files(
                        project.layout.buildDirectory.dir('classes/groovy/main'),
                        project.layout.buildDirectory.dir('resources/main'),
                        destDir,
                        pathingJar.archiveFile
                )
                compileGroovyPages.configure {
                    it.dependsOn(pathingJar)
                    it.classpath = withPathingJarClasspath.asFileTree
                }
                compileWebappGroovyPages.configure {
                    it.dependsOn(pathingJar)
                    it.classpath = withPathingJarClasspath.asFileTree
                }
            }
        }

        compileGroovyPages.configure {
            it.dependsOn(
                    tasks.named('classes'),
                    compileWebappGroovyPages
            )
        }

        tasks.withType(War).configureEach { War war ->
            war.dependsOn compileGroovyPages
            war.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            if (war.name == 'bootWar') {
                war.from(destDir)
                war.into("WEB-INF/classes")
            } else if (war.name == 'war') {
                war.from destDir
            }

            if (war.classpath) {
                war.classpath = war.classpath + project.files(destDir)
            } else {
                war.classpath = project.files(destDir)
            }
        }

        tasks.withType(Jar).configureEach { Jar jar ->
            jar.dependsOn compileGroovyPages
            jar.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            if (!(jar instanceof War)) {
                if (jar.name == 'bootJar') {
                    jar.from(destDir)
                    jar.into("BOOT-INF/classes")
                } else if (jar.name == 'jar') {
                    jar.from destDir
                }
            }
        }
    }

    protected GrailsExtension registerGrailsExtension(Project project) {
        if (project.extensions.findByName("grails") == null) {
            project.extensions.add("grails", new GrailsExtension(project))
        }
    }

    protected FileCollection resolveClassesDirs(SourceSetOutput output, Project project) {
        output?.classesDirs ?: project.files(project.layout.buildDirectory.dir('classes/main'))
    }

    protected String getTmpDirPath(Project project) {
        File tmpdir = project.layout.buildDirectory.dir('gsptmp').get().asFile
        return tmpdir.absolutePath
    }

}
