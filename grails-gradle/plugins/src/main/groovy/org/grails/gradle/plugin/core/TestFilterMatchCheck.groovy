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
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Fails when a test filter that was given on the command line matched no test in any of the test
 * tasks of the build that ran, each of which passes without a match when the filter is given to
 * several of them, see {@link TestFilterAcrossTasks}.
 *
 * @since 9.0
 */
@CompileStatic
@DisableCachingByDefault(because = 'It reads a few numbers and has no output')
abstract class TestFilterMatchCheck extends DefaultTask {

    /** The filter of the command line. */
    @Internal
    abstract ListProperty<String> getPatterns()

    /** The paths of the test tasks of the project that are part of the build. */
    @Internal
    abstract ListProperty<String> getTestTasks()

    /**
     * The files those tasks write the number of tests they ran to, by the paths of the tasks. They
     * are not inputs: the task runs every time, and only after the test tasks, which it does not ask
     * for.
     */
    @Internal
    abstract MapProperty<String, RegularFile> getCounts()

    /** The test tasks of the build that ran, which are the ones whose count is of this build. */
    @Internal
    abstract Property<TestFilterAcrossTasks.TestRuns> getRuns()

    @TaskAction
    void check() {
        check(getPatterns().get(), getTestTasks().get(), getCounts(), getRuns())
    }

    /**
     * @param patterns the filter that was given
     * @param tasks the paths of the test tasks of the build it was given to
     * @param counts the files those tasks write the number of tests they ran to, by their paths
     * @param runs the test tasks of the build that ran
     * @throws GradleException when there is a filter and no test task that ran ran a test
     */
    static void check(List<String> patterns, List<String> tasks, Provider<Map<String, RegularFile>> counts,
                      Provider<TestFilterAcrossTasks.TestRuns> runs) {
        if (patterns.isEmpty()) {
            return
        }
        TestFilterAcrossTasks.TestRuns ran = runs.get()
        Map<String, File> files = [:]
        counts.get().each { String path, RegularFile count -> files.put(path, count.asFile) }
        check(patterns, tasks, tasks.findAll { String path -> ran.hasRun(path) } as Set<String>, files)
    }

    /**
     * A test task that ran in this build has the count of the tests it ran for this filter: it ran them,
     * or it is up to date or taken from the build cache, and then its count is of the run whose results
     * it has, with the same filter. A test task that did not run (an {@code onlyIf} of the build script
     * or {@code enabled = false} skipped it) still has the count an earlier build left, which is not
     * counted.
     *
     * @param patterns the filter that was given
     * @param tasks the paths of the test tasks of the build it was given to
     * @param ran the paths of those that ran
     * @param counts the files those tasks write the number of tests they ran to, by their paths
     * @throws GradleException when there is a filter and no test task that ran ran a test
     */
    static void check(List<String> patterns, List<String> tasks, Set<String> ran, Map<String, File> counts) {
        if (patterns.isEmpty()) {
            return
        }
        List<String> searched = tasks.findAll { String path -> ran.contains(path) }
        long tests = 0
        for (String path : searched) {
            File count = counts.get(path)
            // a task without test classes writes nothing, and Gradle removes what it wrote before
            if (count != null && count.isFile()) {
                String text = count.getText('UTF-8').trim()
                tests += text.isLong() ? text.toLong() : 0L
            }
        }
        if (tests > 0) {
            return
        }
        String filter = patterns.join(', ')
        List<String> skipped = tasks.findAll { String path -> !ran.contains(path) }
        String skippedText = "${skipped.join(', ')} ${skipped.size() == 1 ? 'was' : 'were'} skipped"
        if (searched.isEmpty()) {
            throw new GradleException("No test task ran for the given filter (${filter}): " +
                    (skipped.isEmpty() ? 'none of the test tasks is part of the build' : skippedText))
        }
        throw new GradleException("No tests found for the given filter (${filter}) in ${searched.size() == 1 ? 'the test task' : 'any of the test tasks'} " +
                "${searched.join(', ')}${skipped.isEmpty() ? '' : '; ' + skippedText}: " +
                'a filter is the name of a test class or a pattern for it, such as example.BookSpec, *BookSpec or "*BookSpec.saves a book"')
    }
}
