/*
 * Copyright 2014-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.gradle.plugin.doc

import grails.util.BuildSettings
import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.javadoc.Groovydoc
import org.gradle.api.tasks.javadoc.Javadoc

/**
 * Adds Grails doc publishing support
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class GrailsDocGradlePlugin implements Plugin<Project> {

    public static final String DOC_CONFIGURATION = 'docs'

    @Override
    void apply(Project project) {
        Configuration docConfiguration = project.configurations.create(DOC_CONFIGURATION)
        project.dependencies.add(DOC_CONFIGURATION, "org.grails:grails-docs:${BuildSettings.getPackage().getImplementationVersion()}")

        Groovydoc groovydocTask = (Groovydoc)project.tasks.findByName('groovydoc')
        Javadoc javadocTask = (Javadoc)project.tasks.findByName('javadoc')

        if(groovydocTask && javadocTask) {

            Task docsTask = project.tasks.create('docs', PublishGuideTask)

            docsTask.classpath = docConfiguration

            File applicationYml = project.file("${project.projectDir}/grails-app/conf/application.yml")
            if(applicationYml.exists()) {
                docsTask.propertiesFile = applicationYml
            }
            docsTask.destinationDir = project.file("${project.buildDir}/docs/manual")
            docsTask.source = project.file("${project.projectDir}/src/docs")
            docsTask.resourcesDir = project.file("${project.projectDir}/src/docs")
            docsTask.groovydocDir = groovydocTask.destinationDir
            docsTask.javadocDir = javadocTask.destinationDir
            docsTask.dependsOn(groovydocTask)
            docsTask.dependsOn(javadocTask)
        }
    }
}
