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

import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic

import org.gradle.TaskExecutionRequest
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.specs.Spec
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
 * and no test task of the project that ran in the build ran a test: a filter that matches nothing
 * anywhere is still a failure. A test task that ran is one that ran its tests, was up to date or was
 * taken from the build cache; one that is left out of the build ({@code -x}) or skipped (an
 * {@code onlyIf}, {@code enabled = false}) ran no test, and what it counted in an earlier build is not
 * counted. Without the property nothing is changed.</p>
 *
 * @since 9.0
 */
@CompileStatic
class TestFilterAcrossTasks {

    /** The project property that turns this on. */
    static final String PROPERTY = 'grails.test.filter.lenient'

    /** The task that fails when no test task ran a test for the filter. */
    static final String CHECK_TASK_NAME = 'checkTestFilterMatched'

    /** The build service the test tasks of the build that run say so to. */
    static final String RUNS_SERVICE_NAME = 'grailsTestFilterRuns'

    private static final String TESTS_OPTION = '--tests'

    static void apply(Project project) {
        if (!isAsked(project)) {
            return
        }
        Provider<TestRuns> runs = runs(project)
        TaskProvider<TestFilterMatchCheck> check = project.tasks.register(CHECK_TASK_NAME, TestFilterMatchCheck) { TestFilterMatchCheck task ->
            task.description = 'Fails when a test filter of the command line matched no test in any test task of the build.'
            task.mustRunAfter(project.tasks.withType(Test))
            task.patterns.set(patterns(project.gradle.startParameter.taskRequests))
            task.runs.set(runs)
            task.usesService(runs)
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
            List<Test> tests = graph.allTasks.findAll { Task each -> each instanceof Test && each.project == project } as List<Test>
            check.configure { TestFilterMatchCheck task ->
                for (Test each : tests) {
                    task.testTasks.add(each.path)
                    task.counts.put(each.path, countFile(project, each.name))
                }
            }
            for (Test each : tests) {
                track(each, runs)
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

    /** The build service the test tasks of the build that run say so to. */
    static Provider<TestRuns> runs(Project project) {
        return project.gradle.sharedServices.registerIfAbsent(RUNS_SERVICE_NAME, TestRuns)
    }

    /**
     * Has a test task say, when it runs, that it ran: its count is then of this build, whether it runs
     * its tests, is up to date or is taken from the build cache. A task that is skipped, by an
     * {@code onlyIf} or because it is not enabled, does not say so, and its count, which is the one an
     * earlier build left, is not counted.
     *
     * <p>Gradle asks the {@code onlyIf} predicates of a task in the order they were added and stops at
     * the first that is false, after it has seen that the task is enabled. The one this adds is added
     * when the graph of tasks is ready, after those of the build script, and is asked only where they
     * all let the task run.</p>
     */
    static void track(Test test, Provider<TestRuns> runs) {
        test.usesService(runs)
        test.onlyIf(new Ran(runs))
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

    /** The test tasks that ran in this build, by their paths: one service, and one set, for each build. */
    abstract static class TestRuns implements BuildService<BuildServiceParameters.None> {

        private final Set<String> paths = ConcurrentHashMap.newKeySet()

        /** Says that the test task of this path runs. */
        void ran(String path) {
            paths.add(path)
        }

        /** Whether the test task of this path ran. */
        boolean hasRun(String path) {
            return paths.contains(path)
        }
    }

    /** The predicate of {@link #track}: always true, and says that the task runs. A class of its own and not a closure. */
    static class Ran implements Spec<Task> {

        private final Provider<TestRuns> runs

        Ran(Provider<TestRuns> runs) {
            this.runs = runs
        }

        @Override
        boolean isSatisfiedBy(Task task) {
            runs.get().ran(task.path)
            return true
        }
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
