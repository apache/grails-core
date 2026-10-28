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
import org.gradle.process.CommandLineArgumentProvider

/**
 * Hands Gradle properties to a forked application JVM as system properties of the same name: a
 * developer's {@code -Pgrails.gorm.queryTail.enabled} becomes {@code -Dgrails.gorm.queryTail.enabled=true}
 * for the application. A switch given without a value is passed as {@code true}; any other property
 * given without one is passed empty, as it was given, so that a file or a list left empty is never
 * named {@code true}.
 *
 * @since 9.0
 */
@CompileStatic
class GrailsPropertyArgumentProvider implements CommandLineArgumentProvider {

    private final Provider<Map<String, String>> settings
    private final List<String> switches

    /**
     * @param settings the Gradle properties to hand over, by name; usually
     * {@code providers.gradlePropertiesPrefixedBy(prefix)}
     * @param switches the names of the properties that are switches, which a property given without a value turns on
     */
    GrailsPropertyArgumentProvider(Provider<Map<String, String>> settings, Collection<String> switches) {
        this.settings = settings
        this.switches = switches.toSorted()
    }

    @Input
    Map<String, String> getSettings() {
        settings.getOrElse([:])
    }

    @Input
    List<String> getSwitches() {
        switches
    }

    @Override
    Iterable<String> asArguments() {
        getSettings().collect { String name, String value ->
            "-D${name}=${value ?: (switches.contains(name) ? 'true' : '')}".toString()
        }
    }
}
