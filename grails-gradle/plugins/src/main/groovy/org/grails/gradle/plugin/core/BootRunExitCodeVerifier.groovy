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

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.tasks.JavaExec

/**
 * A {@code bootRun} {@code doLast} action that treats a deliberate stop as a successful build.
 *
 * <p>{@code stop-app} and {@code Ctrl+C} terminate the forked application gracefully (SIGTERM /
 * SIGINT), so it exits 143 / 130. {@link JavaExec} would otherwise report that non-zero exit as
 * {@code BUILD FAILED}; this tolerates the signal-termination codes while still failing for any
 * other non-zero exit. Used with {@code ignoreExitValue = true} on the task.</p>
 */
@CompileStatic
class BootRunExitCodeVerifier implements Action<Task> {

    // 0 = clean exit, 143 = 128 + SIGTERM (stop-app), 130 = 128 + SIGINT (Ctrl+C)
    private static final Set<Integer> EXPECTED_EXIT_CODES = [0, 143, 130] as Set<Integer>

    @Override
    void execute(Task task) {
        verify(((JavaExec) task).executionResult.get().exitValue)
    }

    void verify(int exitValue) {
        if (!EXPECTED_EXIT_CODES.contains(exitValue)) {
            throw new GradleException("Application exited abnormally (exit code ${exitValue})".toString())
        }
    }
}
