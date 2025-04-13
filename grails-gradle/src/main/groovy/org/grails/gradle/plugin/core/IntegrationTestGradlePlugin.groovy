/*
 * Copyright 2015-2024 original authors
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

package org.grails.gradle.plugin.core

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.SourceSetOutput
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestReport
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.grails.gradle.plugin.util.SourceSets

import static org.gradle.api.plugins.JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME
import static org.gradle.api.plugins.JavaPlugin.TEST_RUNTIME_ONLY_CONFIGURATION_NAME
import static org.gradle.api.plugins.JavaPlugin.TEST_TASK_NAME
import static org.gradle.api.tasks.SourceSet.TEST_SOURCE_SET_NAME

/**
 * Gradle plugin for adding separate src/integration-test folder to hold integration tests
 *
 * Adds integrationTestImplementation and integrationTestRuntimeOnly configurations that extend from testCompileClasspath and testRuntimeClasspath
 *
 *
 */
@CompileStatic
class IntegrationTestGradlePlugin implements Plugin<Project> {

    static final String INTEGRATION_TEST_IMPLEMENTATION_CONFIGURATION_NAME = "integrationTestImplementation"
    static final String INTEGRATION_TEST_RUNTIME_ONLY_CONFIGURATION_NAME = "integrationTestRuntimeOnly"
    static final String INTEGRATION_TEST_SOURCE_SET_NAME = "integrationTest"
    static final String INTEGRATION_TEST_TASK_NAME = "integrationTest"
    static final String MERGE_TEST_REPORTS_TASK_NAME = "mergeTestReports"

    boolean ideaIntegration = true
    String sourceFolderName = "src/integration-test"

    @Override
    void apply(Project project) {
        File[] sourceDirs = findIntegrationTestSources(project)

        List<File> acceptedSourceDirs = []
        final SourceSetContainer sourceSets = SourceSets.findSourceSets(project)
        final SourceSetOutput mainSourceSetOutput = SourceSets.findMainSourceSet(project).output
        final SourceSetOutput testSourceSetOutput = SourceSets.findSourceSet(project, TEST_SOURCE_SET_NAME).output
        final SourceSet integrationTest = sourceSets.create(INTEGRATION_TEST_SOURCE_SET_NAME)
        integrationTest.compileClasspath += mainSourceSetOutput + testSourceSetOutput
        integrationTest.runtimeClasspath += mainSourceSetOutput + testSourceSetOutput

        for (File srcDir in sourceDirs) {
            registerSourceDir(integrationTest, srcDir)
            acceptedSourceDirs.add srcDir
        }

        final File resources = new File(project.projectDir, "grails-app/conf")
        integrationTest.resources.srcDir(resources)

        final DependencyHandler dependencies = project.dependencies
        dependencies.add(INTEGRATION_TEST_IMPLEMENTATION_CONFIGURATION_NAME, mainSourceSetOutput)
        dependencies.add(INTEGRATION_TEST_IMPLEMENTATION_CONFIGURATION_NAME, testSourceSetOutput)

        final ConfigurationContainer configurations = project.configurations
        configurations.named(INTEGRATION_TEST_IMPLEMENTATION_CONFIGURATION_NAME) {
            it.extendsFrom(configurations.named(TEST_IMPLEMENTATION_CONFIGURATION_NAME).get())
        }
        configurations.named(INTEGRATION_TEST_RUNTIME_ONLY_CONFIGURATION_NAME) {
            it.extendsFrom(configurations.named(TEST_RUNTIME_ONLY_CONFIGURATION_NAME).get())
        }

        final TaskContainer tasks = project.tasks
        final TaskProvider<Test> integrationTestTask = tasks.register(INTEGRATION_TEST_TASK_NAME, Test) {
            it.group = LifecycleBasePlugin.VERIFICATION_GROUP
            it.testClassesDirs = integrationTest.output.classesDirs
            it.classpath = integrationTest.runtimeClasspath
            it.shouldRunAfter(TEST_TASK_NAME)
            it.finalizedBy(MERGE_TEST_REPORTS_TASK_NAME)
            it.reports.html.required.set(false)
            it.maxParallelForks = 1
            it.testLogging {
                events "passed"
            }
        }
        tasks.named("check") {
            it.dependsOn(integrationTestTask)
        }

        tasks.register(MERGE_TEST_REPORTS_TASK_NAME, TestReport) {
            it.mustRunAfter(tasks.withType(Test).toArray())
            it.destinationDirectory.set(project.layout.buildDirectory.dir("reports/tests"))
            // These must point to the binary test results directory generated by a Test task instance.
            // If Test task instances are specified directly, this task would depend on them and run them.
            it.testResults.from(
                    project.files("$project.buildDir/test-results/binary/test", "$project.buildDir/test-results/binary/integrationTest"),
                    // different versions of Gradle store these results in different places. ugh.
                    project.files("$project.buildDir/test-results/test/binary", "$project.buildDir/test-results/integrationTest/binary")
            )
        }

        if (ideaIntegration) {
            final File[] files = acceptedSourceDirs.toArray(new File[acceptedSourceDirs.size()])
            integrateIdea(project, files)
        }
    }

    @CompileDynamic
    private void registerSourceDir(SourceSet integrationTest, File srcDir) {
        integrationTest."${srcDir.name}".srcDir srcDir
    }

    @CompileDynamic
    private integrateIdea(Project project, File[] acceptedSourceDirs) {
        project.pluginManager.withPlugin('idea') { ->
            project.idea {
                module {
                    testSourceDirs += acceptedSourceDirs
                }
            }
        }
    }

    File[] findIntegrationTestSources(Project project) {
        project.file(sourceFolderName).listFiles({ File file -> file.isDirectory() && !file.name.contains('.') } as FileFilter)
    }
}
