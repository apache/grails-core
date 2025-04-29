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
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.grails.gradle.plugin.util.SourceSets
import org.grails.io.support.MainClassFinder
import org.springframework.boot.gradle.tasks.run.BootRun

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
        if(project.tasks.names.contains('bootRun')) {
            BootRun bootRun = project.tasks.named("bootRun", BootRun).get()
            String mainClass = findMainClass()
            if (mainClass != null) {
                bootRun.mainClass.set(mainClass)
                ExtraPropertiesExtension extraProperties = (ExtraPropertiesExtension) project.getExtensions().getByName("ext")
                extraProperties.set("mainClassName", mainClass)
            }
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
        project.layout.buildDirectory.file('.mainClass')
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
                    throw new RuntimeException("Could not find Application main class. Please set 'springBoot.mainClass'.")
                }
            }
            return mainClass
        }
    }

    protected FileCollection resolveClassesDirs(SourceSetOutput output, Project project) {
        output?.classesDirs ?: project.files(new File(project.buildDir, "classes/main"))
    }

    protected MainClassFinder createMainClassFinder() {
        new MainClassFinder()
    }
}
