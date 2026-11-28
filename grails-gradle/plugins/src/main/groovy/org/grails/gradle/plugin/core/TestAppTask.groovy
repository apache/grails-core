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

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

/**
 * Runs the test phases of a project, which are its {@code Test} tasks: all of them, or the ones
 * that are named with {@code --phase}. It is what {@code grails test-app} runs, see
 * {@link TestAppEntryPoint} for what its options do to the test tasks and {@link TestPhaseNames}
 * for the names of the phases.
 *
 * <p>The task itself runs after the test tasks and fails when tests were named with
 * {@code --tests} and no phase ran a test: each phase passes without a test of that name, since the
 * names are given to every phase. A phase ran when it ran its tests, was up to date or was taken
 * from the build cache; one that is left out of the build ({@code -x}) or skipped (an
 * {@code onlyIf}, {@code enabled = false}) ran no test, whatever it found in an earlier build.</p>
 *
 * @since 9.0
 */
@CompileStatic
@DisableCachingByDefault(because = 'It runs other tasks, reads a few numbers and has no output')
abstract class TestAppTask extends DefaultTask {

    /** The name of the task. */
    public static final String TASK_NAME = 'testApp'

    /** The phases to run; every phase when none is named. */
    @Internal
    @Option(option = 'phase', description = 'A test phase to run, as unit, integration or functional (the task functionalTest). Can be given more than once; every phase is run when it is not given.')
    abstract ListProperty<String> getPhases()

    /** The filter of every phase that is run. */
    @Internal
    @Option(option = 'tests', description = 'The tests to run, as the --tests of a test task, in every phase that is run: a phase without such a test passes. Can be given more than once.')
    abstract ListProperty<String> getTests()

    /** Whether the tests are run again where nothing changed. */
    @Internal
    @Option(option = 'rerun-tests', description = 'Runs the tests of every phase that is run again, also where nothing changed.')
    abstract Property<Boolean> getRerunTests()

    /** Whether the JVM of the tests waits for a debugger. */
    @Internal
    @Option(option = 'debug-jvm', description = 'Starts the JVM of the tests suspended, waiting for a debugger on port 5005. With several phases they are run one at a time, each waiting in its turn.')
    abstract Property<Boolean> getDebugJvm()

    /** The paths of the test tasks of the phases that are part of the build. */
    @Internal
    abstract ListProperty<String> getTestTasks()

    /**
     * The files those tasks write the number of tests they ran to, by the paths of the tasks. They
     * are not inputs: the task runs every time.
     */
    @Internal
    abstract MapProperty<String, RegularFile> getCounts()

    /** The test tasks of the build that ran, which are the ones whose count is of this build. */
    @Internal
    abstract Property<TestFilterAcrossTasks.TestRuns> getRuns()

    @TaskAction
    void check() {
        TestFilterMatchCheck.check(getTests().get(), getTestTasks().get(), getCounts(), getRuns())
    }
}
