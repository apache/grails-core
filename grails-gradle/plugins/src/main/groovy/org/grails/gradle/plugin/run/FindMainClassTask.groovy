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
package org.grails.gradle.plugin.run

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetOutput
import org.gradle.api.tasks.TaskAction
import org.grails.gradle.plugin.util.SourceSets
import org.grails.io.support.MainClassFinder
import org.springframework.boot.gradle.dsl.SpringBootExtension
import org.springframework.boot.gradle.plugin.SpringBootPlugin

/**
 * A task that finds the main task, differs slightly from Boot's version as expects a subclass of GrailsConfiguration
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
@CacheableTask
class FindMainClassTask extends DefaultTask {

    @TaskAction
    void setMainClassProperty() {
        Project project = this.project

        def bootRunTask = project.tasks.findByName('bootRun')
        if (!bootRunTask || !bootRunTask.enabled) {
            project.logger.info('The bootRun task will not so this must not be a runnable grails application. Skipping finding main class.')
            return
        }

        def bootJarTask = project.tasks.findByName(SpringBootPlugin.BOOT_JAR_TASK_NAME)
        def bootWarTask = project.tasks.findByName(SpringBootPlugin.BOOT_WAR_TASK_NAME)
        if ((!bootJarTask || !bootJarTask.enabled) && (!bootWarTask || !bootWarTask.enabled)) {
            project.logger.info("There is neither a ${SpringBootPlugin.BOOT_JAR_TASK_NAME} or ${SpringBootPlugin.BOOT_WAR_TASK_NAME} task that will run. Skipping finding main Application class.")
            return
        }

        String mainClass = findMainClass()
        if (mainClass) {
            ExtraPropertiesExtension extraProperties = (ExtraPropertiesExtension) project.getExtensions().getByName('ext')
            extraProperties.set('mainClassName', mainClass)

            SpringBootExtension springBootExtension = project.getExtensions().getByType(SpringBootExtension)
            springBootExtension.mainClass.convention(mainClass)
        } else {
            project.logger.warn("No main class found. Please set 'springBoot.mainClass'.")
        }
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    FileCollection getClassesDirs() {
        SourceSet mainSourceSet = SourceSets.findMainSourceSet(project)
        if(mainSourceSet) {
            return resolveClassesDirs(mainSourceSet.output, project)
        }

        project.files(project.layout.buildDirectory.dir('classes/main'))
    }

    @OutputFile
    Provider<RegularFile> getMainClassCacheFile() {
        project.layout.buildDirectory.file('resolvedMainClassName')
    }

    protected String findMainClass() {
        Project project = this.project

        File buildDir = project.layout.buildDirectory.get().asFile
        buildDir.mkdirs()

        File mainClassFile = getMainClassCacheFile().getOrNull()?.asFile
        if (mainClassFile.exists()) {
            return mainClassFile.text
        } else {
            // Look up the main source set.
            SourceSet mainSourceSet = SourceSets.findMainSourceSet(project)
            if (!mainSourceSet) {
                return null
            }

            MainClassFinder mainClassFinder = createMainClassFinder()
            // Get the directories from which to try to find the main class.
            Set<File> classesDirs = getClassesDirs().files
            String mainClass = null
            for (File classesDir in classesDirs) {
                mainClass = mainClassFinder.findMainClass(classesDir)
                if (mainClass != null) {
                    mainClassFile.text = mainClass
                    break
                }
            }
            if (mainClass == null) {
                // Fallback attempt on a legacy directory.
                mainClass = mainClassFinder.findMainClass(project.layout.buildDirectory.dir('classes/groovy/main').getOrNull()?.asFile)
                if (mainClass != null) {
                    mainClassFile.text = mainClass
                } else {
                    throw new RuntimeException("Could not find Application main class. Please set 'springBoot.mainClass' or disable BootJar & BootArchive tasks.")
                }
            }
            return mainClass
        }
    }

    protected FileCollection resolveClassesDirs(SourceSetOutput output, Project project) {
        output?.classesDirs ?: project.files(new File(project.buildDir, 'classes/main'))
    }

    protected MainClassFinder createMainClassFinder() {
        new MainClassFinder()
    }
}
