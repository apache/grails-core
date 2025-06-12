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
package org.grails.gradle.plugin.run

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetOutput
import org.gradle.api.tasks.TaskAction
import org.grails.gradle.plugin.util.SourceSets
import org.grails.io.support.MainClassFinder
import org.grails.io.support.MainClassHolder
import org.springframework.boot.gradle.plugin.SpringBootPlugin

import javax.inject.Inject

/**
 * A task that finds the main task, differs slightly from Boot's version as expects a subclass of GrailsConfiguration
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
@CacheableTask
abstract class FindMainClassTask extends DefaultTask {

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    final FileCollection classesDirectory

    @OutputFile
    final RegularFileProperty mainClassCacheFile

    @Input
    final Property<Boolean> isGrailsPlugin

    @Input
    final Property<Boolean> enabledBootJarTask

    @Input
    final Property<Boolean> enabledBootWarTask

    @Input
    final Property<Boolean> enabledBootRunTask

    @Inject
    FindMainClassTask(Project project, ObjectFactory objects) {
        classesDirectory = objects.fileCollection().convention(project.provider {
            SourceSet mainSourceSet = SourceSets.findMainSourceSet(project)

            FileCollection mainSourceSetCollection = null
            if (mainSourceSet) {
                mainSourceSetCollection = resolveClassesDirs(mainSourceSet.output, project)
            }

            FileCollection defaultCollection = resolveClassesDirs(null, project)
            (mainSourceSetCollection && defaultCollection) ? mainSourceSetCollection + defaultCollection : (mainSourceSetCollection ?: defaultCollection)
        })
        mainClassCacheFile = objects.fileProperty().convention(project.layout.buildDirectory.file('resolvedMainClassName'))
        isGrailsPlugin = objects.property(Boolean).convention(project.provider {
            project.plugins.hasPlugin('org.grails.gradle.plugin.core.GrailsPluginGradlePlugin')
        })
        enabledBootRunTask = objects.property(Boolean).convention(project.provider {
            Task bootRunTask = project.tasks.findByName('bootRun')
            (bootRunTask && bootRunTask.enabled) as boolean
        })
        enabledBootJarTask = objects.property(Boolean).convention(project.provider {
            Task bootJarTask = project.tasks.findByName(SpringBootPlugin.BOOT_JAR_TASK_NAME)
            (bootJarTask && bootJarTask.enabled) as boolean
        })
        enabledBootWarTask = objects.property(Boolean).convention(project.provider {
            Task bootWarTask = project.tasks.findByName(SpringBootPlugin.BOOT_WAR_TASK_NAME)
            (bootWarTask && bootWarTask.enabled) as boolean
        })
    }

    @TaskAction
    void setMainClassProperty() {
        if (!enabledBootRunTask.get()) {
            logger.info('The bootRun task does not exist or is disabled, so this must not be a runnable grails application. Skipping finding main class.')
            return
        }

        if (!enabledBootJarTask.get() && !enabledBootWarTask.get()) {
            logger.info('There is neither a {} or {} task that will run. Skipping finding main Application class.', SpringBootPlugin.BOOT_JAR_TASK_NAME, SpringBootPlugin.BOOT_WAR_TASK_NAME)
            return
        }

        MainClassHolder mainClassHolder = findMainClass()
        if (mainClassHolder) {
            File cacheFile = mainClassCacheFile.get().asFile
            cacheFile.parentFile.mkdirs()
            cacheFile.text = mainClassHolder.className
        } else if (!isGrailsPlugin.get()) {
            logger.warn('No main class found. Please set \'springBoot.mainClass\'.')
        }
    }

    protected MainClassHolder findMainClass() {
        MainClassFinder mainClassFinder = new MainClassFinder()

        // Get the directories from which to try to find the main class.
        Set<File> classesDirs = classesDirectory.getFiles()
        if (!classesDirs) {
            throw new IllegalStateException("No classes directory configured for FindMainClassTask. Please ensure the task is configured with a valid classes directory.")
        }
        MainClassHolder mainClassHolder = null
        for (File classesDir in classesDirs) {
            mainClassHolder = mainClassFinder.findMainClass(classesDir)
            if (mainClassHolder) {
                break
            }
        }

        if (!mainClassHolder) {
            if (isGrailsPlugin.get()) {
                // this is ok if the project is a plugin because it's likely not going to be a runnable grails app
                logger.lifecycle('WARNING: this plugin project does not have an Application.class and thus the bootJar / bootRun will be invalid.')
                return null
            }

            throw new RuntimeException('Could not find Application main class. Please set \'springBoot.mainClass\' or disable BootJar & BootArchive tasks.')
        }

        return mainClassHolder
    }

    protected FileCollection resolveClassesDirs(SourceSetOutput output, Project project) {
        if (output?.classesDirs) {
            return output.classesDirs
        }

        if (project.layout.buildDirectory.dir('classes/main').isPresent()) {
            return project.files(project.layout.buildDirectory.dir('classes/main'))
        }

        if (project.layout.buildDirectory.dir('classes/groovy/main').isPresent()) {
            return project.files(project.layout.buildDirectory.dir('classes/groovy/main'))
        }

        null
    }
}
