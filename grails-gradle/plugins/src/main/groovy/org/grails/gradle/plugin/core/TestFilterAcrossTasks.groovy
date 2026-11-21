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

import groovy.transform.CompileStatic

import org.gradle.TaskExecutionRequest
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult

/**
 * Lets one test filter be given to several test tasks of a project, as {@code grails test-app BookSpec}
 * gives it to {@code test} and to {@code integrationTest}: Gradle fails every test task that has no
 * test for its filter, so a test that is among the unit tests alone failed the build in
 * {@code integrationTest}.
 *
 * <p>With the project property {@value #PROPERTY} (any value but {@code false}), a test task of the
 * project that has no test for its filter passes, and the task {@value #CHECK_TASK_NAME} runs after
 * the test tasks of the build and fails when a filter was given on the command line ({@code --tests})
 * and no test task of the project that was part of the build ran a test: a filter that matches
 * nothing anywhere is still a failure. Without the property nothing is changed.</p>
 *
 * @since 9.0
 */
@CompileStatic
class TestFilterAcrossTasks {

    /** The project property that turns this on. */
    static final String PROPERTY = 'grails.test.filter.lenient'

    /** The task that fails when no test task ran a test for the filter. */
    static final String CHECK_TASK_NAME = 'checkTestFilterMatched'

    private static final String TESTS_OPTION = '--tests'

    static void apply(Project project) {
        if (!isAsked(project)) {
            return
        }
        TaskProvider<TestFilterMatchCheck> check = project.tasks.register(CHECK_TASK_NAME, TestFilterMatchCheck) { TestFilterMatchCheck task ->
            task.description = 'Fails when a test filter of the command line matched no test in any test task of the build.'
            task.mustRunAfter(project.tasks.withType(Test))
            task.patterns.set(patterns(project.gradle.startParameter.taskRequests))
        }
        project.tasks.withType(Test).configureEach { Test test ->
            count(test, countFile(project, test.name))
            test.finalizedBy(check)
        }
        // the test tasks that are part of this build, and no others: a count that an earlier build left says nothing of this one
        project.gradle.taskGraph.whenReady { TaskExecutionGraph graph ->
            if (!graph.allTasks.any { Task task -> task.project == project && task.name == CHECK_TASK_NAME }) {
                return
            }
            check.configure { TestFilterMatchCheck task ->
                for (Task each : graph.allTasks) {
                    if (each instanceof Test && each.project == project) {
                        task.testTasks.add(each.path)
                        task.counts.from(countFile(project, each.name))
                    }
                }
            }
        }
    }

    /** Whether the project property is given, with any value but {@code false}. */
    static boolean isAsked(Project project) {
        Provider<String> asked = project.providers.gradleProperty(PROPERTY)
        return asked.isPresent() && asked.get() != 'false'
    }

    /** Lets a test task pass that has no test for its filter, and has it write the number of tests it ran to a file. */
    static void count(Test test, Provider<RegularFile> count) {
        test.filter.failOnNoMatchingTests = false
        // an output, so that a task that is up to date or taken from the build cache has the count of the run its results are of
        test.outputs.file(count).withPropertyName('grailsTestFilterCount')
        test.addTestListener(new TestCount(count.get().asFile))
    }

    /** The file a test task of the project writes the number of tests it ran to. */
    static Provider<RegularFile> countFile(Project project, String taskName) {
        return project.layout.buildDirectory.file("grails/test-filter/${taskName}.count".toString())
    }

    /** The patterns that follow {@code --tests} among the tasks that were asked for, each once. */
    static List<String> patterns(List<TaskExecutionRequest> requests) {
        List<String> patterns = []
        for (TaskExecutionRequest request : requests) {
            List<String> args = request.args
            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i)
                String pattern = arg == TESTS_OPTION && i + 1 < args.size() ? args.get(++i) :
                        arg.startsWith(TESTS_OPTION + '=') ? arg.substring(TESTS_OPTION.length() + 1) : null
                if (pattern != null && !patterns.contains(pattern)) {
                    patterns.add(pattern)
                }
            }
        }
        return patterns
    }

    /** Writes the number of tests a test task ran to a file, when the task has run them. */
    @CompileStatic
    static class TestCount implements TestListener, Serializable {

        private static final long serialVersionUID = 1L

        private final File file

        TestCount(File file) {
            this.file = file
        }

        @Override
        void beforeSuite(TestDescriptor suite) {
        }

        @Override
        void afterSuite(TestDescriptor suite, TestResult result) {
            if (suite.parent == null) {
                file.parentFile.mkdirs()
                file.setText(String.valueOf(result.testCount), 'UTF-8')
            }
        }

        @Override
        void beforeTest(TestDescriptor testDescriptor) {
        }

        @Override
        void afterTest(TestDescriptor testDescriptor, TestResult result) {
        }
    }
}
