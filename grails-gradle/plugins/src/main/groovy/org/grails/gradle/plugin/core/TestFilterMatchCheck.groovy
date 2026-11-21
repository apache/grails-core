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
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Fails when a test filter that was given on the command line matched no test in any of the test
 * tasks of the build, each of which passes without a match when the filter is given to several of
 * them, see {@link TestFilterAcrossTasks}.
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
     * The files those tasks write the number of tests they ran to. They are not inputs: the task
     * runs every time, and only after the test tasks, which it does not ask for.
     */
    @Internal
    abstract ConfigurableFileCollection getCounts()

    @TaskAction
    void check() {
        check(getPatterns().get(), getTestTasks().get(), getCounts().files)
    }

    /**
     * @param patterns the filter that was given
     * @param tasks the paths of the test tasks it was given to
     * @param counts the files those tasks wrote the number of tests they ran to
     * @throws GradleException when there is a filter and no task ran a test
     */
    static void check(List<String> patterns, List<String> tasks, Set<File> counts) {
        if (patterns.isEmpty() || tasks.isEmpty()) {
            return
        }
        long tests = 0
        for (File count : counts) {
            // a task without test classes writes nothing, and Gradle removes what it wrote before
            if (count.isFile()) {
                String text = count.getText('UTF-8').trim()
                tests += text.isLong() ? text.toLong() : 0L
            }
        }
        if (tests == 0) {
            throw new GradleException("No tests found for the given filter (${patterns.join(', ')}) in ${tasks.size() == 1 ? 'the test task' : 'any of the test tasks'} " +
                    "${tasks.join(', ')}: a filter is the name of a test class or a pattern for it, such as example.BookSpec, *BookSpec or \"*BookSpec.saves a book\"")
        }
    }
}
