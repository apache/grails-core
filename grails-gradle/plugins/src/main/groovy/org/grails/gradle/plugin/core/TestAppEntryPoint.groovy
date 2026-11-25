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

import java.util.concurrent.Callable

import groovy.transform.CompileStatic

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceSpec
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * The one task that runs the test phases of a project, {@value TestAppTask#TASK_NAME}: only the
 * build knows which {@code Test} tasks a project has, so the command line asks for this task and
 * not for the tasks by their names.
 *
 * <p>The task depends on every {@code Test} task of the project, or on the ones of the phases that
 * were named, and a name that is no phase of the project fails the build with the phases there are.
 * Its options are given to the test tasks it runs once Gradle knows which tasks the build is of, to
 * those of them that are part of the build (a task left out with {@code -x} is not):
 * the names of tests are the {@code --tests} of each, with which a task without such a test passes;
 * the tests are run again where nothing changed; the JVMs of the tests wait for a debugger, one at
 * a time. The unit tests are run first, then the integration tests, then the other phases, where
 * the build script does not ask for another order: see {@link TestPhaseOrder}.</p>
 *
 * @since 9.0
 */
@CompileStatic
class TestAppEntryPoint {

    private static final String DEBUGGER = 'grailsTestDebugger'

    static void apply(Project project) {
        if (project.tasks.names.contains(TestAppTask.TASK_NAME)) {
            project.logger.warn('Project {} already has a task named {}, so the Grails Gradle plugin does not register its own and ' +
                    'grails test-app runs that task. Give the task another name.', project.path, TestAppTask.TASK_NAME)
            return
        }
        TaskCollection<Test> testTasks = project.tasks.withType(Test)
        Provider<TestFilterAcrossTasks.TestRuns> runs = TestFilterAcrossTasks.runs(project)
        TaskProvider<TestAppTask> testApp = project.tasks.register(TestAppTask.TASK_NAME, TestAppTask) { TestAppTask task ->
            task.group = LifecycleBasePlugin.VERIFICATION_GROUP
            task.description = 'Runs the test phases of the project: every Test task, or the phases named with --phase.'
            task.rerunTests.convention(false)
            task.debugJvm.convention(false)
            task.dependsOn({ selected(project, task) } as Callable<List<TaskProvider<Test>>>)
            task.runs.set(runs)
            task.usesService(runs)
        }
        TestPhaseOrder.apply(project, testTasks)
        // a debugger is attached to one JVM at a time: the test tasks that wait for one do not run together
        Provider<Debugger> debugger = project.gradle.sharedServices.registerIfAbsent(DEBUGGER, Debugger) { BuildServiceSpec<BuildServiceParameters.None> spec ->
            spec.maxParallelUsages.set(1)
        }
        String path = project.path == ':' ? ":${TestAppTask.TASK_NAME}".toString() : "${project.path}:${TestAppTask.TASK_NAME}".toString()
        boolean counted = TestFilterAcrossTasks.isAsked(project)
        // the options of the task are those of the command line by now, and no task has run
        project.gradle.taskGraph.whenReady { TaskExecutionGraph graph ->
            if (!graph.hasTask(path)) {
                return
            }
            TestAppTask task = testApp.get()
            List<String> patterns = task.tests.get()
            for (TaskProvider<Test> each : selected(project, task)) {
                Test test = each.get()
                // a phase left out with -x runs no test, and the count an earlier build left for it is not of this one
                if (!graph.hasTask(test)) {
                    continue
                }
                task.testTasks.add(test.path)
                if (!patterns.isEmpty()) {
                    test.setTestNameIncludePatterns(patterns)
                    Provider<RegularFile> count = TestFilterAcrossTasks.countFile(project, test.name)
                    task.counts.put(test.path, count)
                    // with the property, TestFilterAcrossTasks has every test task of the project count
                    if (!counted) {
                        TestFilterAcrossTasks.count(test, count)
                    }
                    TestFilterAcrossTasks.lenient(test)
                    TestFilterAcrossTasks.track(test, runs)
                }
                if (task.rerunTests.get()) {
                    test.outputs.upToDateWhen(new Never())
                }
                if (task.debugJvm.get()) {
                    test.debug = true
                    test.usesService(debugger)
                }
            }
        }
    }

    /**
     * @return the test tasks of the phases that were asked for, or every test task of the project
     * @throws GradleException when a phase was asked for that the project does not have
     */
    static List<TaskProvider<Test>> selected(Project project, TestAppTask task) {
        TaskCollection<Test> testTasks = project.tasks.withType(Test)
        List<String> asked = task.phases.get()
        Map<String, String> phases = TestPhaseNames.phases(testTasks.names)
        // in the order of the phases, and after them the tasks that are none
        Set<String> names = new LinkedHashSet<String>(phases.values())
        names.addAll(testTasks.names)
        if (!asked.isEmpty()) {
            names = new LinkedHashSet<String>()
            for (String phase : asked) {
                String found = TestPhaseNames.find(phases, phase)
                if (found == null) {
                    throw new GradleException("This project has no test phase ${phase}; its test phases are: ${phases.keySet().join(', ')}. " +
                            'A phase is a Test task of the project: test is unit, integrationTest is integration, and a task such as functionalTest is functional')
                }
                names.add(phases.get(found))
            }
        }
        return names.collect { String name -> testTasks.named(name) }
    }

    /** What a JVM of tests that waits for a debugger holds while it runs: there is one. */
    abstract static class Debugger implements BuildService<BuildServiceParameters.None> {
    }

    /** A class of its own and not a closure, which the configuration cache would have to store with what it encloses. */
    static class Never implements Spec<Task>, Serializable {

        private static final long serialVersionUID = 1L

        @Override
        boolean isSatisfiedBy(Task task) {
            return false
        }
    }
}
