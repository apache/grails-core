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

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.testing.Test

/**
 * The order of the test phases of a project: the unit tests, then the integration tests, then every
 * other {@code Test} task. It is never what makes a build fail: a build script that has the unit tests
 * depend on another {@code Test} task, or run after one, is built as it is without this plugin.
 *
 * <p>In every build the order is one Gradle is asked to keep where it can ({@code shouldRunAfter}):
 * Gradle sets it aside where it is against what the build script asks for, and also where the tasks of
 * a project run in parallel, as they do with the configuration cache, so two phases can run together.</p>
 *
 * <p>In a build that asks for {@value TestAppTask#TASK_NAME} by its name or its path, the order is also
 * one Gradle has to keep ({@code mustRunAfter}), so the phases run one after the other. Two tasks are
 * left out of it where the build script already has the earlier phase run after the later one: by a
 * dependency of the earlier phase, by its own {@code mustRunAfter}, or because the later one is
 * finalized by it. That is worked out once, when Gradle asks for the order, which is after every
 * build script was read.</p>
 *
 * @since 9.0
 */
@CompileStatic
class TestPhaseOrder {

    private final TaskCollection<Test> testTasks
    private Map<String, List<Test>> kept
    private boolean working

    private TestPhaseOrder(TaskCollection<Test> testTasks) {
        this.testTasks = testTasks
    }

    static void apply(Project project, TaskCollection<Test> testTasks) {
        boolean strict = asksFor(project.gradle.startParameter.taskNames, TestAppTask.TASK_NAME)
        TestPhaseOrder order = new TestPhaseOrder(testTasks)
        testTasks.configureEach { Test test ->
            List<String> earlier = earlier(test.name)
            if (earlier.isEmpty()) {
                return
            }
            test.shouldRunAfter(testTasks.named({ String name -> name in earlier } as Spec<String>))
            if (strict) {
                test.mustRunAfter({ order.before(test) } as Callable<List<Test>>)
            }
        }
    }

    /** Whether the command line asks for the task of this name, by the name or by a path. */
    static boolean asksFor(List<String> arguments, String taskName) {
        return arguments.any { String argument -> argument == taskName || argument.endsWith(":${taskName}") }
    }

    /** The names of the phases that come before the test task of this name. */
    static List<String> earlier(String taskName) {
        if (taskName == TestPhaseNames.UNIT_TASK) {
            return []
        }
        return taskName == TestPhaseNames.INTEGRATION_TASK ? [TestPhaseNames.UNIT_TASK] :
                [TestPhaseNames.UNIT_TASK, TestPhaseNames.INTEGRATION_TASK]
    }

    /** The phases this test task has to run after. */
    private synchronized List<Test> before(Test test) {
        // asked for while the order is worked out, by what looks at the order the build script asks for
        if (working) {
            return []
        }
        if (kept == null) {
            working = true
            try {
                kept = workOut()
            } finally {
                working = false
            }
        }
        return kept.get(test.name) ?: []
    }

    private Map<String, List<Test>> workOut() {
        Map<String, List<Test>> order = [:]
        Map<Task, Set<Task>> known = [:]
        List<Test> all = testTasks.toSorted { Test a, Test b -> a.name <=> b.name }
        // the integration tests first: what comes after them is held against their place after the unit tests
        List<Test> later = all.findAll { Test test -> test.name == TestPhaseNames.INTEGRATION_TASK } +
                all.findAll { Test test -> test.name != TestPhaseNames.INTEGRATION_TASK }
        for (Test test : later) {
            for (String name : earlier(test.name)) {
                Test first = all.find { Test each -> each.name == name }
                if (first != null && !runsAfter(first, test, order, known, all)) {
                    order.computeIfAbsent(test.name) { String key -> [] as List<Test> }.add(first)
                }
            }
        }
        return order
    }

    /** Whether the build has a task run after another, by what the build script asks for and the order that is kept so far. */
    private static boolean runsAfter(Task task, Task other, Map<String, List<Test>> order, Map<Task, Set<Task>> known, List<Test> all) {
        Set<Task> seen = new HashSet<Task>()
        Deque<Task> open = new ArrayDeque<Task>()
        open.push(task)
        while (!open.isEmpty()) {
            Task each = open.pop()
            if (!seen.add(each)) {
                continue
            }
            Set<Task> before = new LinkedHashSet<Task>(known.computeIfAbsent(each) { Task key -> asked(key, all) })
            before.addAll(order.get(each.name)?.findAll { Test kept -> each instanceof Test && each.project == kept.project } ?: [] as List<Test>)
            if (before.contains(other)) {
                return true
            }
            before.each { Task next -> open.push(next) }
        }
        return false
    }

    /** The tasks the build script has a task run after: what it depends on, what it must run after, and the test tasks it finalizes. */
    private static Set<Task> asked(Task task, List<Test> all) {
        Set<Task> before = new LinkedHashSet<Task>()
        before.addAll(task.taskDependencies.getDependencies(task))
        before.addAll(task.mustRunAfter.getDependencies(task))
        before.addAll(all.findAll { Test test -> !test.is(task) && test.finalizedBy.getDependencies(test).contains(task) })
        return before
    }
}
