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
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.process.JavaForkOptions

/**
 * Hands the system properties of the build whose names start with {@code grails.} to a forked
 * application or test JVM, each without that prefix: {@code ./gradlew bootRun -Dgrails.server.port=9090}
 * starts the application with {@code -Dserver.port=9090}.
 *
 * <p>The properties are read when the task runs, not when the build is configured. Read during
 * configuration they were written into the configuration cache entry, and a property that was not
 * given when the entry was stored was not an input of it: the entry was reused by a later build
 * that did give the property, which then never reached the JVM. Read here, the entry is reused
 * whatever properties a build is given, and the JVM is started with those of the build that runs
 * it.</p>
 *
 * <p>The tasks that run a command or a script of the application hand the same properties over
 * under their full names as well ({@code -Dgrails.env=test} stays {@code grails.env}), which is the
 * other form of this provider.</p>
 *
 * <p>A property without a value is not passed, and a property the task has itself
 * ({@code systemProperty 'server.port', ...} in the build script, or one the Grails plugin sets) keeps
 * the value it has there, also one an action of the task sets as it starts ({@code doFirst}). The
 * task's system properties are read when the task runs, with or without the configuration cache:
 * {@link #addTo} gives the task the action that hands them to the provider then, and a task class
 * that adds the provider itself hands them over in its own action ({@link #readTaskSystemProperties}).</p>
 *
 * @since 9.0
 */
@CompileStatic
class GrailsSystemPropertyArgumentProvider implements CommandLineArgumentProvider {

    /** What the names of the properties that are handed over start with, and what is taken off them. */
    static final String PREFIX = 'grails.'

    /** The name of the action {@link #addTo} gives a task. */
    static final String ACTION_NAME = 'readGrailsSystemPropertiesOfTask'

    private final Provider<Map<String, String>> given

    private Map<String, ?> taskSystemProperties

    private final boolean fullNames

    /**
     * @param given the system properties of the build to hand over, by their full names; usually
     * {@code providers.systemPropertiesPrefixedBy('grails.')}
     * @param taskSystemProperties the task's own system properties: a value set there is kept
     * @param fullNames whether the JVM sees a property under its full name, and not under the name
     * without the prefix
     */
    GrailsSystemPropertyArgumentProvider(Provider<Map<String, String>> given, Map<String, ?> taskSystemProperties, boolean fullNames = false) {
        this.given = given
        this.taskSystemProperties = taskSystemProperties
        this.fullNames = fullNames
    }

    /**
     * Gives a task that is already created a provider, and the action that hands the providers of the
     * task its system properties when it runs ({@link #readTaskSystemProperties}).
     *
     * <p>The provider is given the task's map of system properties, which a build that is not taken
     * from the configuration cache changes until the task starts its JVM. One that is taken from it
     * has the task and the provider restored apart, each with a map of its own as the entry has it,
     * and a property an action of the task sets as it starts (a {@code doFirst}) reached the task and
     * not the provider, whose value of the build was then passed after it and won. The action reads the
     * map of the running task, whichever runs first of it and the actions of the build script, so the
     * task's own value is kept in every build.</p>
     *
     * <p>Not for the constructor of a task: Gradle puts the action of the task class before the actions
     * that were added while the task was being created, so that a {@code doFirst} added there runs after
     * the JVM has been started. A task class that adds the provider in its constructor calls
     * {@link #readTaskSystemProperties} itself at the start of its action, and this method then adds no
     * action to it.</p>
     *
     * @param task a task that forks a JVM: one that has {@link JavaForkOptions}, as {@code JavaExec} and {@code Test}
     * @param given the system properties of the build to hand over, by their full names
     * @param fullNames whether the JVM sees a property under its full name
     */
    static void addTo(Task task, Provider<Map<String, String>> given, boolean fullNames = false) {
        JavaForkOptions options = (JavaForkOptions) task
        boolean first = !options.jvmArgumentProviders.any { CommandLineArgumentProvider each -> each instanceof GrailsSystemPropertyArgumentProvider }
        options.jvmArgumentProviders.add(new GrailsSystemPropertyArgumentProvider(given, options.systemProperties, fullNames))
        if (first) {
            task.doFirst(ACTION_NAME, new ReadTaskSystemProperties())
        }
    }

    /**
     * Hands every provider of this kind that a task has the task's map of system properties as the
     * task has it while it runs. Called before the task starts its JVM, by the action {@link #addTo} adds
     * or by the action of a task class that adds the provider in its constructor.
     *
     * @param task the running task
     */
    static void readTaskSystemProperties(JavaForkOptions task) {
        for (CommandLineArgumentProvider each : task.jvmArgumentProviders) {
            if (each instanceof GrailsSystemPropertyArgumentProvider) {
                ((GrailsSystemPropertyArgumentProvider) each).taskSystemProperties = task.systemProperties
            }
        }
    }

    /**
     * @return the system properties of the task, as the provider has them: the task's own once it
     * runs, and before that what the task was configured with
     */
    @Internal
    Map<String, ?> getTaskSystemProperties() {
        taskSystemProperties
    }

    /**
     * @return whether the JVM sees a property under its full name
     */
    @Input
    boolean isFullNames() {
        fullNames
    }

    /**
     * @return the system properties the JVM is started with, by the names it sees them under
     */
    @Input
    Map<String, String> getForwarded() {
        Map<String, String> forwarded = new TreeMap<String, String>()
        given.getOrElse([:]).each { String name, String value ->
            if (name.startsWith(PREFIX) && name.length() > PREFIX.length() && value) {
                String forwardedName = fullNames ? name : name.substring(PREFIX.length())
                if (!taskSystemProperties.containsKey(forwardedName)) {
                    forwarded.put(forwardedName, value)
                }
            }
        }
        forwarded
    }

    @Override
    Iterable<String> asArguments() {
        getForwarded().collect { String name, String value -> "-D${name}=${value}".toString() }
    }

    /**
     * The action of {@link #addTo}: {@link #readTaskSystemProperties} for the running task. A class of
     * its own and not a closure: it holds nothing, and the configuration cache stores it with the task.
     */
    static class ReadTaskSystemProperties implements Action<Task> {

        @Override
        void execute(Task task) {
            readTaskSystemProperties((JavaForkOptions) task)
        }
    }
}
