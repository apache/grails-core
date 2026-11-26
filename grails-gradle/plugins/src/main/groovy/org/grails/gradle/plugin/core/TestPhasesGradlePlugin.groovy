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
package org.grails.gradle.plugin.core

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.SourceSetOutput
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestReport
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.plugins.ide.idea.model.IdeaModule

import org.grails.gradle.plugin.util.SourceSets

import static org.gradle.api.plugins.JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME
import static org.gradle.api.plugins.JavaPlugin.TEST_RUNTIME_ONLY_CONFIGURATION_NAME
import static org.gradle.api.plugins.JavaPlugin.TEST_TASK_NAME
import static org.gradle.api.tasks.SourceSet.TEST_SOURCE_SET_NAME

/**
 * Gradle plugin that provides a {@code testPhases} extension for defining
 * additional test phases (e.g. integrationTest, functionalTest).
 *
 * <p>Each {@link TestPhase} added to the container automatically gets its own
 * source set, dependency configurations, {@link Test} task, and merged test
 * report contribution.</p>
 *
 * @since 7.1
 */
@CompileStatic
class TestPhasesGradlePlugin implements Plugin<Project> {

    static final String EXTENSION_NAME = 'testPhases'
    static final String MERGE_TEST_REPORTS_TASK_NAME = 'mergeTestReports'

    private NamedDomainObjectContainer<TestPhase> testPhases

    @Override
    void apply(Project project) {
        testPhases = project.container(TestPhase) { String name ->
            project.objects.newInstance(TestPhase, name)
        }
        project.extensions.add(EXTENSION_NAME, testPhases)

        registerMergeTestReports(project)

        testPhases.configureEach { TestPhase phase ->
            configureTestPhase(project, phase)
        }
    }

    private void configureTestPhase(Project project, TestPhase phase) {
        String phaseName = phase.name
        String implConfigName = "${phaseName}Implementation"
        String runtimeOnlyConfigName = "${phaseName}RuntimeOnly"

        def sourceDirs = findTestPhaseSources(project, phase)
        List<File> acceptedSourceDirs = []

        final SourceSetContainer sourceSets = SourceSets.findSourceSets(project)
        final SourceSetOutput mainSourceSetOutput = SourceSets.findMainSourceSet(project).output
        final SourceSetOutput testSourceSetOutput = SourceSets.findSourceSet(project, TEST_SOURCE_SET_NAME).output
        final SourceSet phaseSourceSet = sourceSets.create(phaseName)
        phaseSourceSet.compileClasspath += mainSourceSetOutput + testSourceSetOutput
        phaseSourceSet.runtimeClasspath += mainSourceSetOutput + testSourceSetOutput

        if (sourceDirs != null) {
            for (File srcDir in sourceDirs) {
                registerSourceDir(phaseSourceSet, srcDir)
                acceptedSourceDirs.add(srcDir)
            }
        }

        def resources = new File(project.projectDir, 'grails-app/conf')
        phaseSourceSet.resources.srcDir(resources)

        def dependencies = project.dependencies
        dependencies.add(implConfigName, mainSourceSetOutput)
        dependencies.add(implConfigName, testSourceSetOutput)

        final ConfigurationContainer configurations = project.configurations
        configurations.named(implConfigName) {
            it.extendsFrom(configurations.named(TEST_IMPLEMENTATION_CONFIGURATION_NAME).get())
        }
        configurations.named(runtimeOnlyConfigName) {
            it.extendsFrom(configurations.named(TEST_RUNTIME_ONLY_CONFIGURATION_NAME).get())
        }

        def tasks = project.tasks
        def testTask = tasks.register(phaseName, Test)
        testTask.configure {
            it.group = LifecycleBasePlugin.VERIFICATION_GROUP
            it.testClassesDirs = phaseSourceSet.output.classesDirs
            it.classpath = phaseSourceSet.runtimeClasspath
            it.shouldRunAfter(TEST_TASK_NAME)
            it.finalizedBy(MERGE_TEST_REPORTS_TASK_NAME)
            it.reports.html.required.set(false)
            it.maxParallelForks = 1
            it.testLogging {
                events('passed')
            }
            it.systemProperty(phase.systemPropertyName.get(), true)
        }
        tasks.named('check') {
            it.dependsOn(testTask)
        }

        addPhaseToMergeTestReports(project, phaseName)

        if (phase.ideaIntegration.get()) {
            File[] files = acceptedSourceDirs.toArray(new File[acceptedSourceDirs.size()])
            integrateIdea(project, files)
        }
    }

    private static void registerMergeTestReports(Project project) {
        project.tasks.register(MERGE_TEST_REPORTS_TASK_NAME, TestReport) {
            it.mustRunAfter(project.tasks.withType(Test))
            it.destinationDirectory.set(project.layout.buildDirectory.dir('reports/tests'))
            it.testResults.from(
                    // WARNING: this must be a path & not a reference to the test task so Gradle doesn't force other tests to run
                    project.files(project.layout.buildDirectory.dir('test-results/test/binary'))
            )

            def testResultDirectory = project.layout.buildDirectory.dir('test-results')
            it.doFirst {
                // because a test task could be interrupted, support cleaning up bad data to prevent unnecessary errors from previous, partial runs
                cleanOrphanedBinaryResults(testResultDirectory)
            }
        }
    }

    private static void addPhaseToMergeTestReports(Project project, String phaseName) {
        project.tasks.named(MERGE_TEST_REPORTS_TASK_NAME, TestReport) {
            it.testResults.from(
                    // WARNING: this must be a path & not a reference to the test task so Gradle doesn't force other tests to run
                    project.files(project.layout.buildDirectory.dir("test-results/${phaseName}/binary" as String))
            )
        }
    }

    private static void cleanOrphanedBinaryResults(Provider<Directory> testResultDirectory) {
        File testResultsDir = testResultDirectory.get().asFile
        if (!testResultsDir.exists()) {
            return
        }
        testResultsDir.eachDir { File phaseDir ->
            File binaryDir = new File(phaseDir, 'binary')
            File outputBin = new File(binaryDir, 'output.bin')
            File outputBinIdx = new File(binaryDir, 'output.bin.idx')
            if (outputBin.exists() && !outputBinIdx.exists()) {
                outputBin.delete()
            }
        }
    }

    @CompileDynamic
    private static void registerSourceDir(SourceSet sourceSet, File srcDir) {
        sourceSet."${srcDir.name}".srcDir(srcDir)
    }

    @CompileDynamic
    private static void integrateIdea(Project project, File[] acceptedSourceDirs) {
        project.pluginManager.withPlugin('idea') { ->
            def ideaExtension = project.extensions.getByType(IdeaModel)
            ideaExtension.module { IdeaModule it ->
                it.testSources.from(acceptedSourceDirs)
            }
        }
    }

    static File[] findTestPhaseSources(Project project, TestPhase phase) {
        project.file(phase.sourceFolderName.get()).listFiles({ File file -> file.isDirectory() && !file.name.contains('.') } as FileFilter)
    }
}
