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

import java.util.regex.Matcher
import java.util.regex.Pattern

import groovy.transform.CompileStatic

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.process.JavaForkOptions

/**
 * Gives a forked application or test JVM the heap of the Grails Gradle plugin, {@code -Xms768m -Xmx768m}, where the
 * options of its task do not name the heap themselves.
 *
 * <p>The options of a task name the maximum with {@code maxHeapSize}, or with an {@code -Xmx} or
 * {@code -XX:MaxHeapSize=} in its {@code jvmArgs}, and the initial heap with {@code minHeapSize}, or with an
 * {@code -Xms}, {@code -XX:InitialHeapSize=} or {@code -XX:MinHeapSize=} there. The plugin gives the {@code jvmArgs}
 * the options of the environment variable {@code GRAILS_OPTS}, or of {@code GRAILS_FORK_OPTS} in its place, so a heap
 * named there counts too.</p>
 *
 * <ul>
 *   <li>The plugin's {@code -Xms768m} is given only when the options name neither bound: beside a maximum that is
 *   named it would keep the JVM from starting whenever that maximum is smaller, as an {@code -Xmx512m} is, and the JVM
 *   chooses an initial heap below the maximum itself.</li>
 *   <li>The plugin's maximum is given only when the options name no maximum. It is {@code 768m}, or the largest
 *   initial heap that is named when that is larger ({@code -Xms1g} alone is given {@code -Xmx1g}), so the JVM is never
 *   given an initial heap above its maximum. It is not left out beside a named initial heap: Gradle gives the JVM of
 *   a test task that has no {@code maxHeapSize} an {@code -Xmx512m} of its own, which an initial heap above 512 MB
 *   does not fit.</li>
 * </ul>
 *
 * <p>The task is read as it is configured, which is what the configuration cache stores, and again as it starts
 * ({@link #addTo} gives it the action that does), so a heap an action of the task sets as it starts (a
 * {@code doFirst}) counts too, with or without the configuration cache. The arguments of other providers of the task
 * are not read.</p>
 *
 * @since 9.0
 */
@CompileStatic
class GrailsDefaultHeapProvider implements CommandLineArgumentProvider {

    /** The initial and the maximum heap of a JVM whose task names no heap. */
    static final String DEFAULT_HEAP_SIZE = '768m'

    /** The name of the action {@link #addTo} gives a task. */
    static final String ACTION_NAME = 'readHeapOptionsOfTask'

    /** The options that name the maximum heap, as they start. */
    static final List<String> MAXIMUM_OPTIONS = ['-Xmx', '-XX:MaxHeapSize=']

    /** The options that name the initial heap, or its minimum, as they start. */
    static final List<String> INITIAL_OPTIONS = ['-Xms', '-XX:InitialHeapSize=', '-XX:MinHeapSize=']

    // a size as the JVM reads it: a number of bytes, or of kilobytes, megabytes, gigabytes or terabytes
    private static final Pattern SIZE = Pattern.compile('([0-9]+)([kKmMgGtT]?)')

    private final Provider<List<String>> whenConfigured

    private List<String> atStart

    /**
     * @param whenConfigured the options of the plugin's heap for the task as it is configured
     */
    GrailsDefaultHeapProvider(Provider<List<String>> whenConfigured) {
        this.whenConfigured = whenConfigured
    }

    /**
     * Gives a task a provider, and the action that reads the options of the task again as it starts.
     *
     * @param task a task that forks a JVM: one that has {@link JavaForkOptions}, as {@code JavaExec} and {@code Test}
     * @param providers the provider factory of the project
     */
    static void addTo(Task task, ProviderFactory providers) {
        JavaForkOptions options = (JavaForkOptions) task
        Provider<List<String>> whenConfigured = providers.provider { heapOptionsFor(options) }
        options.jvmArgumentProviders.add(new GrailsDefaultHeapProvider(whenConfigured))
        task.doFirst(ACTION_NAME, new ReadHeapOptions())
    }

    /**
     * @param options the options of a task that forks a JVM
     * @return the options of the plugin's heap that its JVM is given beside them
     */
    static List<String> heapOptionsFor(JavaForkOptions options) {
        List<String> jvmArgs = options.jvmArgs ?: []
        if (options.maxHeapSize != null || jvmArgs.any { String argument -> optionOf(argument, MAXIMUM_OPTIONS) }) {
            return []
        }
        List<String> initial = jvmArgs.findAll { String argument -> optionOf(argument, INITIAL_OPTIONS) }
                .collect { String argument -> argument.substring(optionOf(argument, INITIAL_OPTIONS).length()) }
        if (options.minHeapSize != null) {
            initial.add(options.minHeapSize)
        }
        if (initial.isEmpty()) {
            return ["-Xms${DEFAULT_HEAP_SIZE}".toString(), "-Xmx${DEFAULT_HEAP_SIZE}".toString()]
        }
        String maximum = DEFAULT_HEAP_SIZE
        for (String size : initial) {
            if (bytesOf(size) > bytesOf(maximum)) {
                maximum = size
            }
        }
        ["-Xmx${maximum}".toString()]
    }

    /**
     * @return the options of the plugin's heap: for the task as it started once it has, and before that as it is
     * configured
     */
    @Input
    List<String> getHeapOptions() {
        atStart != null ? atStart : whenConfigured.get()
    }

    @Override
    Iterable<String> asArguments() {
        getHeapOptions()
    }

    private static String optionOf(String argument, List<String> options) {
        options.find { String option -> argument.startsWith(option) }
    }

    // the number of bytes of a size, or -1 for one the JVM does not read, which then refuses it itself
    private static BigInteger bytesOf(String size) {
        Matcher matcher = SIZE.matcher(size)
        if (!matcher.matches()) {
            return BigInteger.ONE.negate()
        }
        String unit = matcher.group(2).toLowerCase(Locale.ROOT)
        new BigInteger(matcher.group(1)).shiftLeft(unit ? 10 * ('kmgt'.indexOf(unit) + 1) : 0)
    }

    /**
     * The action of {@link #addTo}: reads the options of the running task for every provider of this kind it has. A
     * class of its own and not a closure: it holds nothing, and the configuration cache stores it with the task.
     */
    static class ReadHeapOptions implements Action<Task> {

        @Override
        void execute(Task task) {
            JavaForkOptions options = (JavaForkOptions) task
            List<String> heapOptions = heapOptionsFor(options)
            for (CommandLineArgumentProvider each : options.jvmArgumentProviders) {
                if (each instanceof GrailsDefaultHeapProvider) {
                    ((GrailsDefaultHeapProvider) each).atStart = heapOptions
                }
            }
        }
    }
}
