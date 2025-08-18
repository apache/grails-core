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

package org.grails.gradle.plugin.views

import grails.util.GrailsNameUtils
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSetOutput
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.bundling.Jar
import org.grails.gradle.plugin.core.IntegrationTestGradlePlugin
import org.grails.gradle.plugin.util.SourceSets

/**
 * Abstract implementation of a plugin that compiles views
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class AbstractGroovyTemplatePlugin implements Plugin<Project> {

    final Class<? extends AbstractGroovyTemplateCompileTask> taskClass
    final String fileExtension
    final String pathToSource

    AbstractGroovyTemplatePlugin(Class<? extends AbstractGroovyTemplateCompileTask> taskClass, String fileExtension) {
        this.taskClass = taskClass
        this.fileExtension = fileExtension
        this.pathToSource = 'grails-app/views'
    }

    AbstractGroovyTemplatePlugin(Class<? extends AbstractGroovyTemplateCompileTask> taskClass, String fileExtension, String pathToSource) {
        this.taskClass = taskClass
        this.fileExtension = fileExtension
        this.pathToSource = pathToSource
    }

    @Override
    @CompileDynamic
    void apply(Project project) {
        TaskContainer tasks = project.tasks
        String upperCaseName = GrailsNameUtils.getClassName(fileExtension)
        AbstractGroovyTemplateCompileTask templateCompileTask = (AbstractGroovyTemplateCompileTask) tasks.register(
                "compile${upperCaseName}Views".toString(),
                (Class<? extends Task>) taskClass
        ).get()
        SourceSetOutput output = SourceSets.findMainSourceSet(project)?.output
        FileCollection classesDir = resolveClassesDirs(output, project)
        Provider<Directory> destDir = project.layout.buildDirectory.dir("${templateCompileTask.fileExtension.get()}-classes/main")
        output?.dir(destDir)
        def allClasspath = classesDir + project.configurations.named('compileClasspath').get()
        templateCompileTask.destinationDirectory.set(destDir)
        templateCompileTask.classpath = allClasspath
        templateCompileTask.packageName.set(project.name)
        templateCompileTask.setSource(project.file("${project.projectDir}/$pathToSource"))
        templateCompileTask.dependsOn(tasks.named('classes').get())
        tasks.withType(Jar).configureEach { Task task ->
            if (task.name in ['jar', 'bootJar', 'war', 'bootWar']) {
                task.dependsOn(templateCompileTask)
            }
        }
        tasks.named('resolveMainClassName').configure { Task task ->
            task.dependsOn(templateCompileTask)
        }
        if(tasks.names.contains('compileTestGroovy')) {
            tasks.named('compileTestGroovy').configure { Task task ->
                task.dependsOn(templateCompileTask)
            }
        }
        if(project.plugins.hasPlugin(IntegrationTestGradlePlugin)) {
            project.plugins.withType(IntegrationTestGradlePlugin).configureEach { plugin ->
                if(tasks.names.contains('compileIntegrationTestGroovy')) {
                    tasks.named('compileIntegrationTestGroovy').configure { Task task ->
                        task.dependsOn(templateCompileTask)
                    }
                }
                if(tasks.names.contains('integrationTest')) {
                    tasks.named('integrationTest').configure { Task task ->
                        task.dependsOn(templateCompileTask)
                    }
                }
            }
        }
    }

    @CompileDynamic
    protected FileCollection resolveClassesDirs(SourceSetOutput output, Project project) {
        return output.classesDirs ?: project.files(project.layout.buildDirectory.dir('classes/groovy/main'))
    }
}
