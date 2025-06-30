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
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
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

    /**
     * allows forcing the main class name & ensures this task retriggers to save to the cache file if overridden
     */
    @Input
    @Optional
    final Property<String> mainClassName

    @Input
    final Property<Boolean> isGrailsPlugin

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
            project.plugins.hasPlugin('org.apache.grails.gradle.grails-plugin')
        })
        mainClassName = objects.property(String)
    }

    @TaskAction
    void setMainClassProperty() {
        File cacheFile = mainClassCacheFile.get().asFile
        if (cacheFile.exists()) {
            // the only time this task should invoke is when gradle has deemed it necessary to run, always remove the
            // the cache file to prevent invalid states when running tasks other than bootRun, bootJar, or bootWar
            cacheFile.delete()
        }

        if (mainClassName.isPresent()) {
            def overrideClassName = mainClassName.get()
            logger.info('Overriding main class with: {}', overrideClassName)
            cacheFile.parentFile.mkdirs()
            cacheFile.text = overrideClassName
            return
        }

        MainClassHolder mainClassHolder = findMainClass()
        if (mainClassHolder) {
            cacheFile.parentFile.mkdirs()
            cacheFile.text = mainClassHolder.className
            logger.info('Found main class: {}', mainClassHolder.className)
        } else if (!isGrailsPlugin.get()) {
            // caching based on the task graph isn't practical here, so we just log a warning in case troubleshooting is needed
            // for a task that will depend on the main class
            logger.info('No main class found. Considering adding one or setting \'springBoot.mainClass\' if one already exists to use tasks such as runCommand, runScript, console, shell, or boot* tasks.')
        }
    }

    protected MainClassHolder findMainClass() {
        MainClassFinder mainClassFinder = new MainClassFinder()

        // Get the directories from which to try to find the main class.
        Set<File> classesDirs = classesDirectory.getFiles()
        if (!classesDirs) {
            throw new IllegalStateException('No classes directory configured for FindMainClassTask. Please ensure the task is configured with a valid classes directory.')
        }
        MainClassHolder mainClassHolder = null
        for (File classesDir in classesDirs) {
            logger.debug('Searching for main class in: {}', classesDir.absolutePath)
            mainClassHolder = mainClassFinder.findMainClass(classesDir, false)
            // do not cache inside of the finder since gradle is responsible for caching
            if (mainClassHolder) {
                logger.debug('Found main class: {} at {}', mainClassHolder.className, mainClassHolder.classFile.absolutePath)
                break
            }
        }

        if (!mainClassHolder) {
            if (isGrailsPlugin.get()) {
                // this is ok if the project is a plugin because it's likely not going to be a runnable grails app
                logger.info('WARNING: this plugin project does not have an Application.class and thus tasks requiring a main class such as runScript / runCommand / bootJar / bootWar / bootRun / etc will be invalid.')
                return null
            }

            throw new RuntimeException('Could not find Application main class. Please set \'springBoot.mainClass\' or disable bootRun & bootArchive (bootJar / bootWar) tasks.')
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
