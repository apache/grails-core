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

import groovy.transform.CompileStatic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Fails when a forbidden coordinate appears on a project's {@code runtimeClasspath}. Used to keep
 * developer-only tooling - the console, the shell and their terminal libraries - out of what an
 * application ships.
 *
 * <p>The dependency graph arrives as a {@link ResolvedComponentResult} provider rather than being read
 * off the project at execution time, so the task carries everything it needs and stays compatible with
 * the configuration cache.</p>
 *
 * @since 8.0
 */
@CompileStatic
abstract class ValidateProductionClasspathTask extends DefaultTask {

    /** {@code "group:name"} keys that must not appear; {@code name} may be {@code *}. */
    @Input
    abstract SetProperty<String> getForbiddenCoordinates()

    /** The root of the resolved {@code runtimeClasspath} graph. */
    @Input
    abstract Property<ResolvedComponentResult> getRootComponent()

    /** Identifies the offending project in the failure message. */
    @Input
    abstract Property<String> getProjectPath()

    @TaskAction
    void validate() {
        Set<String> forbidden = forbiddenCoordinates.get()
        if (!forbidden) {
            return
        }
        List<List<String>> matchers = forbidden.collect { String key ->
            String[] parts = key.split(':')
            [parts[0], parts.length > 1 ? parts[1] : '*']
        }

        Set<String> offenders = new TreeSet<>()
        Set<ResolvedComponentResult> seen = new HashSet<>()
        Deque<ResolvedComponentResult> queue = new ArrayDeque<>()
        queue.add(rootComponent.get())
        while (!queue.isEmpty()) {
            ResolvedComponentResult component = queue.poll()
            if (!seen.add(component)) {
                continue
            }
            if (component.id instanceof ModuleComponentIdentifier) {
                ModuleComponentIdentifier id = (ModuleComponentIdentifier) component.id
                for (List<String> matcher in matchers) {
                    if (id.group == matcher[0] && (matcher[1] == '*' || id.module == matcher[1])) {
                        offenders.add("${id.group}:${id.module}:${id.version}".toString())
                    }
                }
            }
            for (def dependency in component.dependencies) {
                if (dependency instanceof ResolvedDependencyResult) {
                    queue.add(((ResolvedDependencyResult) dependency).selected)
                }
            }
        }

        if (offenders) {
            throw new GradleException(
                    "Developer-only tooling reached the production runtime classpath of ${projectPath.get()}: " +
                            "${offenders.join(', ')}. These belong to the cli tier " +
                            '(auto-provisioned onto grailsCli) and must not ship with an application.')
        }
    }
}
