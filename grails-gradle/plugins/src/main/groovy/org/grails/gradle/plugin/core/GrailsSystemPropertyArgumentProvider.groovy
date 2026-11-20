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

import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.process.CommandLineArgumentProvider

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
 * the value it has there. The task's system properties are read when the task runs, so a value set
 * after the plugin is applied counts too.</p>
 *
 * @since 9.0
 */
@CompileStatic
class GrailsSystemPropertyArgumentProvider implements CommandLineArgumentProvider {

    /** What the names of the properties that are handed over start with, and what is taken off them. */
    static final String PREFIX = 'grails.'

    private final Provider<Map<String, String>> given

    @Internal
    final Map<String, ?> taskSystemProperties

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
}
