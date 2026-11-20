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

import groovy.transform.CompileStatic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.plugins.ExtraPropertiesExtension

import static org.apache.grails.buildsrc.GradleUtils.findRootGrailsCoreDir

/**
 * Gradle can't share properties across buildSrc or composite projects. This plugin ensures that properties not defined
 * in this project, but are in the root grails-core project, are accessible in this project. This plugin must be applied
 * prior to the access of any property for it to work properly
 */
@CompileStatic
class SharedPropertyPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        Directory rootDirectory = findRootGrailsCoreDir(project)
        populateParentProperties(
                project.layout.projectDirectory,
                rootDirectory,
                project.extensions.extraProperties,
                project
        )
        populateLocalProperties(rootDirectory, project.extensions.extraProperties, project)
    }

    void populateLocalProperties(Directory rootDirectory, ExtraPropertiesExtension ext, Project project) {
        def localPropertiesFile = rootDirectory.file('local.properties').asFile
        if (localPropertiesFile.exists()) {
            project.logger.info('Using local property overrides from grails-core/local.properties')
            localPropertiesFile.withInputStream {
                Properties localProperties = new Properties()
                localProperties.load(it)

                for (String key : localProperties.stringPropertyNames()) {
                    ext.set(key, localProperties.getProperty(key))
                }
            }
        }
    }

    void populateParentProperties(Directory projectDirectory, Directory rootDirectory, ExtraPropertiesExtension ext, Project project) {
        if (!rootDirectory) {
            throw new IllegalStateException('Could not locate the root directory to populate up to')
        }

        if (projectDirectory.file('gradle.properties').asFile.exists()) {
            def propertyPath = rootDirectory.asFile.relativePath(projectDirectory.asFile)
            project.logger.info('Using properties from grails-core/{}gradle.properties', propertyPath ? "${propertyPath}/" : '')
            projectDirectory.file('gradle.properties').asFile.withInputStream {
                Properties rootProperties = new Properties()
                rootProperties.load(it)

                for (String key : rootProperties.stringPropertyNames()) {
                    if (!ext.has(key)) {
                        ext.set(key, rootProperties.getProperty(key))
                    }
                }

                if (rootProperties.containsKey('projectVersion')) {
                    ext.set('grailsVersion', rootProperties.getProperty('projectVersion'))
                }
            }
        }

        if (projectDirectory.asFile.absolutePath != rootDirectory.asFile.absolutePath) {
            populateParentProperties(projectDirectory.dir('..'), rootDirectory, ext, project)
        }
    }
}
