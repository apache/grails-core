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

import groovy.transform.CompileDynamic
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
 * project that was given a filter on the command line ({@code --tests}) and has no test for it passes,
 * and the task {@value #CHECK_TASK_NAME} runs after the test tasks of the build and fails when none of
 * the test tasks that were given a filter ran a test: a filter that matches nothing anywhere is still a
 * failure. A test task of the same build that was given no filter runs all of its tests, and they are
 * no match of the filter of another task: it is not counted, and it is as Gradle has it in every way.
 * A test task that ran is one that ran its tests, was up to date or was taken from the build cache; one
 * that is left out of the build ({@code -x}) or skipped (an {@code onlyIf}, {@code enabled = false})
 * ran no test, and what it counted in an earlier build is not counted.</p>
 *
 * <p>A test task without a test for its filter passes in these two cases and in no other: the property
 * is given and the task has a {@code --tests} of the command line, or the task is run by
 * {@code testApp} with the {@code --tests} of that task, see {@link TestAppEntryPoint}. Without the
 * property and without {@code testApp} nothing is changed: {@code gradlew test --tests X} fails in
 * {@code test}, as Gradle has it, when the task has no such test.</p>
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
            // the tasks the filter was given to, and no others: what a task without a filter runs is no match of it
            Map<Test, Set<String>> given = [:]
            for (Test each : tests) {
                given.put(each, commandLinePatterns(each))
            }
            boolean known = !given.values().contains(null)
            List<Test> filtered = known ? tests.findAll { Test each -> !given.get(each).isEmpty() } : tests
            check.configure { TestFilterMatchCheck task ->
                if (known) {
                    task.patterns.set(filtered.collectMany { Test each -> given.get(each) as List<String> }.unique())
                }
                for (Test each : filtered) {
                    task.testTasks.add(each.path)
                    task.counts.put(each.path, countFile(project, each.name))
                }
            }
            for (Test each : filtered) {
                lenient(each)
                track(each, runs)
            }
        }
    }

    /** Whether the project property is given, with any value but {@code false}. */
    static boolean isAsked(Project project) {
        Provider<String> asked = project.providers.gradleProperty(PROPERTY)
        return asked.isPresent() && asked.get() != 'false'
    }

    /** Lets a test task pass that has no test for its filter. */
    static void lenient(Test test) {
        test.filter.failOnNoMatchingTests = false
    }

    /**
     * The patterns a test task was given with {@code --tests} on the command line, which are not those
     * its build script gives its filter.
     *
     * @return the patterns, none for a task without {@code --tests}, or {@code null} where the filter of
     * the task does not say: Gradle has no public way to ask, and every filter it makes says it
     */
    @CompileDynamic
    static Set<String> commandLinePatterns(Test test) {
        Object filter = test.filter
        if (!filter.metaClass.hasProperty(filter, 'commandLineIncludePatterns')) {
            return null
        }
        Object patterns = filter.commandLineIncludePatterns
        return patterns instanceof Collection ? new LinkedHashSet<String>((Collection<String>) patterns) : null
    }

    /** Has a test task write the number of tests it ran to a file. */
    static void count(Test test, Provider<RegularFile> count) {
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
