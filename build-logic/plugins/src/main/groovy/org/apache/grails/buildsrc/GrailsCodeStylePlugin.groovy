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

import java.nio.file.Files
import java.nio.file.Path

import groovy.transform.CompileStatic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.plugins.quality.CheckstylePlugin
import org.gradle.api.plugins.quality.CodeNarc
import org.gradle.api.plugins.quality.CodeNarcExtension
import org.gradle.api.plugins.quality.CodeNarcPlugin

@CompileStatic
class GrailsCodeStylePlugin implements Plugin<Project> {

    static String CHECKSTYLE_DIR_PROPERTY = 'grails.codestyle.dir.checkstyle'
    static String CHECKSTYLE_CONFIG_FILE_NAME = 'checkstyle.xml'
    static String CHECKSTYLE_SUPPRESSION_CONFIG_FILE_NAME = 'checkstyle-suppressions.xml'

    static String CODENARC_DIR_PROPERTY = 'grails.codestyle.dir.codenarc'
    static String CODENARC_CONFIG_FILE_NAME = 'codenarc.groovy'

    static String BASE_RESOURCE_PATH = '/META-INF/org.apache.grails.buildsrc.codestyle'

    @Override
    void apply(Project project) {
        initExtension(project)
        configureCodeStyle(project)
        doNotApplyStylingToTests(project)
    }

    private static void initExtension(Project project) {
        def gce = project.extensions.create('grailsCodeStyle', GrailsCodeStyleExtension)

        // Unfortunately, the codenarc plugin is still using a non-lazy property.
        // Rather than rewrite the plugin to use afterEvaluate,
        // this plugin uses properties to override the configuration location by default


        gce.checkstyleDirectory.set(project.provider {
            def directory = project.hasProperty(CHECKSTYLE_DIR_PROPERTY) ?
                    project.rootProject.layout.projectDirectory.dir(project.property(CHECKSTYLE_DIR_PROPERTY) as String) :
                    project.rootProject.layout.buildDirectory.get().dir('codestyle').dir('checkstyle')

            def toCreate = directory.asFile.toPath()
            Files.createDirectories(toCreate)

            createOrLoad(
                    toCreate.resolve(CHECKSTYLE_CONFIG_FILE_NAME),
                    "${BASE_RESOURCE_PATH}/checkstyle/${CHECKSTYLE_CONFIG_FILE_NAME}"
            )
            createOrLoad(
                    toCreate.resolve(CHECKSTYLE_SUPPRESSION_CONFIG_FILE_NAME),
                    "${BASE_RESOURCE_PATH}/checkstyle/${CHECKSTYLE_SUPPRESSION_CONFIG_FILE_NAME}"
            )

            directory
        })

        gce.codenarcDirectory.set(project.provider {
            def directory = project.hasProperty(CODENARC_DIR_PROPERTY) ?
                    project.rootProject.layout.projectDirectory.dir(project.property(CODENARC_DIR_PROPERTY) as String) :
                    project.rootProject.layout.buildDirectory.get().dir('codestyle').dir('codenarc')

            def toCreate = directory.asFile.toPath()
            Files.createDirectories(toCreate)

            createOrLoad(
                    toCreate.resolve(CODENARC_CONFIG_FILE_NAME),
                    "${BASE_RESOURCE_PATH}/codenarc/${CODENARC_CONFIG_FILE_NAME}"
            )

            directory
        })
    }

    private static void createOrLoad(Path expectedPath, String defaultResource) {
        if (!Files.exists(expectedPath) || expectedPath.size() == 0) {
            def defaultValue = GrailsCodeStylePlugin.getResourceAsStream(defaultResource)
            if (!defaultValue) {
                throw new IllegalStateException("Could not locate default configuration file: ${defaultResource}")
            }
            expectedPath.text = defaultValue.text
        }
    }

    private static void doNotApplyStylingToTests(Project project) {
        project.tasks.named('checkstyleTest') {
            it.enabled = false // Do not check test sources at this time
        }

        project.afterEvaluate {
            // Do not check test sources at this time
            ['codenarcIntegrationTest', 'codenarcTest'].each { testTaskName ->
                if (project.tasks.names.contains(testTaskName)) {
                    project.tasks.named(testTaskName) {
                        it.enabled = false
                    }
                }
            }
        }
    }

    private static void configureCodeStyle(Project project) {
        configureCheckstyle(project)
        configureCodenarc(project)

        project.tasks.register('codeStyle') {
            it.group = 'verification'
            it.description = 'Runs code style checks'
            it.dependsOn(project.tasks.withType(Checkstyle))
            it.dependsOn(project.tasks.withType(CodeNarc))
        }
    }

    static void configureCheckstyle(Project project) {
        project.pluginManager.apply(CheckstylePlugin)

        project.extensions.configure(CheckstyleExtension) {
            // Explicit `it` is required in extension configuration
            it.getConfigDirectory().set(project.extensions.getByType(GrailsCodeStyleExtension).checkstyleDirectory)
            it.maxWarnings = 0
            it.showViolations = true
            it.ignoreFailures = false
            it.toolVersion = project.findProperty('checkstyleVersion')
        }

        project.tasks.withType(Checkstyle).configureEach { Checkstyle task ->
            task.group = 'verification'
            task.onlyIf { !project.hasProperty('skipCodeStyle') }

            // Redirect XML report output to a single directory to consolidate
            // reports across all subprojects into one known location.
            // Include the task name to avoid overlapping outputs when a project has
            // multiple source sets (e.g. grails-cache has ast + main).
            task.reports.xml.outputLocation.set(
                    project.extensions.getByType(GrailsCodeStyleExtension)
                            .reportsDirectory.get()
                            .dir('checkstyle')
                            .file("${project.name}-${task.name}.xml")
            )
        }
    }

    static void configureCodenarc(Project project) {
        project.pluginManager.apply(CodeNarcPlugin)

        project.extensions.configure(CodeNarcExtension) {
            it.configFile = project.extensions.getByType(GrailsCodeStyleExtension)
                    .codenarcDirectory.get().file(CODENARC_CONFIG_FILE_NAME).asFile
            it.toolVersion = project.findProperty('codenarcVersion')
        }

        project.tasks.withType(CodeNarc).configureEach { CodeNarc task ->
            task.group = 'verification'
            task.onlyIf { !project.hasProperty('skipCodeStyle') }

            // Redirect XML report output to a single directory to consolidate
            // reports across all subprojects into one known location.
            // Include the task name to avoid overlapping outputs when a project has
            // multiple source sets.
            task.reports.xml.required.set(true)
            task.reports.xml.outputLocation.set(
                    project.extensions.getByType(GrailsCodeStyleExtension)
                            .reportsDirectory.get()
                            .dir('codenarc')
                            .file("${project.name}-${task.name}.xml")
            )
        }
    }
}
