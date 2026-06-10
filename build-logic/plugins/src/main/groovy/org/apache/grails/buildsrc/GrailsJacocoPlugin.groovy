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

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.tasks.JacocoReportsContainer

/**
 * Convention plugin for JaCoCo code coverage. Apply to each subproject that compiles code.
 *
 * In addition to configuring per-subproject coverage, this plugin lazily registers a
 * jacocoAggregateReport task on the root project the first time it is applied, then wires
 * each subproject's exec data into that task. The aggregate produces a single XML report
 * at build/reports/jacoco/aggregate/jacocoAggregateReport.xml suitable for Codecov upload.
 */
@CompileStatic
class GrailsJacocoPlugin implements Plugin<Project> {

    static final String AGGREGATE_TASK_NAME = 'jacocoAggregateReport'

    @Override
    void apply(Project project) {
        project.logger.info('Configuring JaCoCo for project: {}', project.name)
        project.pluginManager.apply(JacocoPlugin)

        project.extensions.configure(JacocoPluginExtension) {
            it.toolVersion = project.findProperty('jacocoVersion')
        }

        project.tasks.withType(Test).configureEach {
            it.finalizedBy('jacocoTestReport')
        }

        project.tasks.withType(JacocoReport).configureEach {
            it.dependsOn(project.tasks.withType(Test))
            it.reports({ JacocoReportsContainer reports ->
                reports.xml.required.set(true)
                reports.html.required.set(true)
                reports.csv.required.set(true)
            } as Action<JacocoReportsContainer>)
        }

        contributeToRootAggregateReport(project)
    }

    private static void contributeToRootAggregateReport(Project project) {
        def rootProject = project.rootProject

        // Ensure JacocoPlugin is on the root so its JacocoReport task has tooling available.
        // pluginManager.apply is idempotent — safe to call from every subproject.
        rootProject.pluginManager.apply(JacocoPlugin)

        // Register the aggregate task once on the first apply; subsequent subprojects find it by name.
        def aggregateTask
        if (rootProject.tasks.names.contains(AGGREGATE_TASK_NAME)) {
            aggregateTask = rootProject.tasks.named(AGGREGATE_TASK_NAME, JacocoReport)
        } else {
            aggregateTask = rootProject.tasks.register(AGGREGATE_TASK_NAME, JacocoReport) {
                it.group = 'verification'
                it.description = 'Aggregates JaCoCo coverage from all subprojects into a single XML report for Codecov.'
                it.reports({ JacocoReportsContainer reports ->
                    reports.xml.required.set(true)
                    reports.xml.outputLocation.set(rootProject.layout.buildDirectory.file(
                        'reports/jacoco/aggregate/jacocoAggregateReport.xml'
                    ))
                    reports.html.required.set(false)
                    reports.csv.required.set(false)

                } as Action<JacocoReportsContainer>)
                it.onlyIf { JacocoReport t ->
                    !t.executionData.files.isEmpty()
                }
            }
        }

        // Wire this subproject's test exec data into the aggregate.
        aggregateTask.configure { JacocoReport task ->
            task.dependsOn(project.tasks.withType(Test))
            task.executionData.from(
                project.fileTree(project.file('build/jacoco')) {
                    include('*.exec')
                }
            )
        }

        // Add source and class directories once the Java plugin is confirmed present.
        // Hibernate 7 variant subprojects compile identical class names to their Hibernate 5
        // counterparts; including both causes JaCoCo to throw "Can't add different class with
        // same name". Exec data from H7 test runs is still included above so their coverage
        // is attributed to the H5 class definitions.
        if (!project.path.contains('hibernate7')) {
            project.plugins.withType(JavaPlugin).configureEach {
                def mainSourceSet = project.extensions
                        .getByType(SourceSetContainer)
                        .named(SourceSet.MAIN_SOURCE_SET_NAME)
                aggregateTask.configure {
                    it.sourceDirectories.from(mainSourceSet.map { it.allSource.srcDirs })
                    it.classDirectories.from(mainSourceSet.map { it.output.classesDirs })
                }
            }
        }
    }
}
