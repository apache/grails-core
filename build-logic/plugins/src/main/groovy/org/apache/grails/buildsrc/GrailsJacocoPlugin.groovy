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
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.tasks.JacocoReportsContainer

/**
 * Convention plugin for JaCoCo code coverage. Apply to each subproject that compiles code.
 *
 * In addition to configuring per-subproject coverage, this plugin lazily registers a
 * jacocoAggregateReport task on the root project the first time it is applied, then wires
 * each subproject's exec data into that task. The aggregate produces a single XML report
 * at build/reports/jacoco/aggregate/jacocoAggregateReport.xml suitable for Codecov upload.
 *
 * A JVM that a test starts itself (a spec that runs a class the way a build runs it, as a process of
 * its own) is counted as well: see {@link ForkedJvmCoverageArgumentProvider}.
 */
@CompileStatic
class GrailsJacocoPlugin implements Plugin<Project> {

    static final String AGGREGATE_TASK_NAME = 'jacocoAggregateReport'

    /** The source set of the tools the build runs ({@code src/cli}), ToolBuildNames.CLI_SOURCE_SET in grails-gradle-model. */
    static final String CLI_SOURCE_SET_NAME = 'cli'

    /**
     * The source set of the tools that run as a process of their own ({@code src/standalone}),
     * ToolBuildNames.STANDALONE_SOURCE_SET in grails-gradle-model.
     */
    static final String STANDALONE_SOURCE_SET_NAME = 'standalone'

    /** The source sets of production code beside {@code main}, whose classes the aggregate reports as well. */
    static final List<String> TOOL_SOURCE_SET_NAMES = [CLI_SOURCE_SET_NAME, STANDALONE_SOURCE_SET_NAME].asImmutable()

    /** What the name of the directory with the execution data of the JVMs a test task's tests started ends with. */
    static final String FORKS_DIRECTORY_SUFFIX = '-forks'

    @Override
    void apply(Project project) {
        project.logger.info('Configuring JaCoCo for project: {}', project.name)
        project.pluginManager.apply(JacocoPlugin)

        project.extensions.configure(JacocoPluginExtension) {
            it.toolVersion = project.findProperty('jacocoVersion')
        }

        project.tasks.withType(Test).configureEach { Test test ->
            test.finalizedBy('jacocoTestReport')
            countStartedJvms(project, test)
        }

        project.tasks.withType(JacocoReport).configureEach {
            it.dependsOn(project.tasks.withType(Test))
            if (it.name == 'jacocoTestReport') {
                // the report of the test task has what the JVMs its tests started ran, as the aggregate has
                it.executionData.from(project.fileTree(project.file("build/jacoco/test${FORKS_DIRECTORY_SUFFIX}")) {
                    include('*.exec')
                })
            }
            it.reports({ JacocoReportsContainer reports ->
                reports.xml.required.set(true)
                reports.html.required.set(true)
                reports.csv.required.set(true)
            } as Action<JacocoReportsContainer>)
        }

        contributeToRootAggregateReport(project)
    }

    /**
     * Has the JVMs that the tests of a task start themselves counted: see {@link ForkedJvmCoverageArgumentProvider}.
     * Their execution data goes into {@code build/jacoco/<task>-forks}, one file for each JVM. The directory is an
     * output of the task, so that a result taken from the build cache brings the data of its started JVMs with it,
     * and is emptied before the tests run, so that the report has nothing of an earlier run.
     */
    private static void countStartedJvms(Project project, Test test) {
        JacocoTaskExtension jacoco = test.extensions.findByType(JacocoTaskExtension)
        if (jacoco == null) {
            return
        }
        Provider<Directory> forks = project.layout.buildDirectory.dir("jacoco/${test.name}${FORKS_DIRECTORY_SUFFIX}".toString())
        test.outputs.dir(forks).withPropertyName('jacocoStartedJvmExecutionData').optional()
        test.jvmArgumentProviders.add(new ForkedJvmCoverageArgumentProvider(jacoco, forks))
        test.doFirst(new Action<Task>() {

            @Override
            void execute(Task task) {
                File directory = forks.get().asFile
                directory.deleteDir()
                directory.mkdirs()
            }
        })
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
                    // what the JVMs that the tests started themselves ran
                    include("*${FORKS_DIRECTORY_SUFFIX}/*.exec".toString())
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
                SourceSetContainer sourceSets = project.extensions.getByType(SourceSetContainer)
                def mainSourceSet = sourceSets.named(SourceSet.MAIN_SOURCE_SET_NAME)
                aggregateTask.configure {
                    it.sourceDirectories.from(mainSourceSet.map { it.allSource.srcDirs })
                    it.classDirectories.from(mainSourceSet.map { it.output.classesDirs })
                }
                // The tools (src/cli and src/standalone, the source sets of the grails-plugin-cli plugin) are
                // production code, tested by the testCli phase. Their exec data is wired in above like any other
                // test task's; without their classes here the report has nothing to match that data against, and
                // the tools are missing from Codecov. Both file collections are live: a directory a source set
                // gains after the aggregate task is realized is still reported, where a snapshot of srcDirs
                // would miss it.
                sourceSets.matching { SourceSet sourceSet -> sourceSet.name in TOOL_SOURCE_SET_NAMES }
                        .configureEach { SourceSet toolSourceSet ->
                            aggregateTask.configure {
                                it.sourceDirectories.from(toolSourceSet.allSource.sourceDirectories)
                                it.classDirectories.from(toolSourceSet.output.classesDirs)
                            }
                        }
            }
        }
    }
}
