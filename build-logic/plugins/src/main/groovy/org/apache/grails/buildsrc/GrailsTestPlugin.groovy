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

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import groovy.xml.XmlSlurper
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import groovy.transform.CompileDynamic

class GrailsTestPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        if (project != project.rootProject) {
            return
        }

        project.tasks.register('runAll') { task ->
            task.group = 'verification'
            task.description = 'Runs selected test modules sequentially in isolated Gradle invocations'

            task.finalizedBy('aggregateTestFailures')

            task.doLast {
                List<String> modulePaths = resolveModulePaths(project)
                if (modulePaths.isEmpty()) {
                    project.logger.lifecycle('No test modules were configured.')
                    return
                }

                File gradleExecutable = resolveGradleExecutable(project)
                File initScript = project.layout.projectDirectory.file('local-init.gradle').asFile
                List<String> failedModules = []

                modulePaths.each { modulePath ->
                    project.logger.lifecycle("Running ${modulePath}:test")
                    def result = project.exec { exec ->
                        exec.workingDir = project.rootDir
                        List<String> commandLine = [gradleExecutable.absolutePath, '--no-daemon', '-PmaxTestParallel=1']
                        if (initScript.exists()) {
                            commandLine.addAll(['-I', initScript.absolutePath])
                        }
                        commandLine.addAll(['--continue', "${modulePath}:test"])
                        exec.commandLine = commandLine
                        exec.ignoreExitValue = true
                    }
                    if (result.exitValue != 0) {
                        failedModules << modulePath
                    }
                }

                if (!failedModules.isEmpty()) {
                    throw new GradleException("One or more modules failed: ${failedModules.join(', ')}")
                }
            }
        }

        project.tasks.register('aggregateTestFailures') { task ->
            task.group = 'verification'
            task.description = 'Aggregates all test failures across all modules into TEST_FAILURES.md'

            project.subprojects.each { subproject ->
                subproject.tasks.withType(Test).configureEach { testTask ->
                    task.mustRunAfter(testTask)
                }
            }

            task.doLast {
                def failures = []
                def slurper = new XmlSlurper()
                slurper.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                slurper.setFeature("http://xml.org/sax/features/namespaces", false)

                def processedDirs = [] as Set

                def processTestResults = { File testResultsDir, String moduleName ->
                    if (testResultsDir.exists() && processedDirs.add(testResultsDir.absolutePath)) {
                        testResultsDir.eachFileRecurse { file ->
                            if (file.name.endsWith('.xml') && file.name.startsWith('TEST-')) {
                                try {
                                    def xml = slurper.parse(file)
                                    xml.testcase.each { testcase ->
                                        if (testcase.failure.size() > 0 || testcase.error.size() > 0) {
                                            def failure = testcase.failure.size() > 0 ? testcase.failure[0] : testcase.error[0]
                                            failures << [
                                                module: moduleName,
                                                className: testcase.@classname.text(),
                                                testName: testcase.@name.text(),
                                                message: failure.@message.text() ?: failure.text().take(200),
                                                type: failure.@type.text()
                                            ]
                                        }
                                    }
                                } catch (e) {
                                    project.logger.warn("Failed to parse test result file: ${file.path}", e)
                                }
                            }
                        }
                    }
                }

                project.subprojects.each { subproject ->
                    def testResultsDir = subproject.layout.buildDirectory.dir("test-results").get().asFile
                    processTestResults(testResultsDir, subproject.name)
                }

                project.layout.projectDirectory.asFile.eachDir { dir ->
                    if (!dir.name.startsWith('.') && dir.name != 'build' && dir.name != 'node_modules') {
                        processTestResults(new File(dir, "build/test-results"), dir.name)
                        processTestResults(new File(dir, "test-results"), dir.name)
                    }
                }

                writeReport(project, failures)
            }
        }
    }

    private static List<String> resolveModulePaths(Project project) {
        def configuredModules = project.findProperty('grails.test.modules')?.toString()?.trim()
        if (configuredModules) {
            return configuredModules.split(',').collect { it.trim() }.findAll { it }
        }

        return project.subprojects.findAll { it.tasks.findByName('test') != null }
                .collect { it.path }
                .sort()
    }

    private static File resolveGradleExecutable(Project project) {
        def executableName = Os.isFamily(Os.FAMILY_WINDOWS) ? 'gradlew.bat' : 'gradlew'
        def executable = project.rootProject.layout.projectDirectory.file(executableName).asFile
        if (!executable.exists()) {
            throw new GradleException("Missing Gradle wrapper at ${executable.absolutePath}")
        }
        return executable
    }

    @CompileDynamic
    private void writeReport(Project project, List failures) {
        def reportFile = project.layout.projectDirectory.file('TEST_FAILURES.md').asFile
        def out = new StringBuilder()
        out.append("# Test Failures Summary\n")
        out.append("Generated on: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}\n\n")

        if (failures.isEmpty()) {
            out.append("All tests passed! 🎉\n")
        } else {
            out.append("Found ${failures.size()} failures.\n\n")
            def groupedByModule = failures.groupBy { it.module }.sort()
            groupedByModule.each { module, modFailures ->
                out.append("## Module: ${module}\n")
                out.append("| Class | Test | Type | Message |\n")
                out.append("| :--- | :--- | :--- | :--- |\n")
                modFailures.each { f ->
                    out.append("| ${f.className} | ${f.testName} | ${f.type} | ${f.message.replaceAll(/\|/, '\\|').replaceAll(/\n/, ' ')} |\n")
                }
                out.append("\n")
            }
        }
        reportFile.text = out.toString()
        project.logger.lifecycle("Test failure report: ${reportFile.absolutePath}")
    }
}
