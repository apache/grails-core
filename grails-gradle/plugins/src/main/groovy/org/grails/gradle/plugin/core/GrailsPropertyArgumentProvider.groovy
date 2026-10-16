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
 * developer's {@code -Pgrails.gorm.sqlTail.enabled} becomes {@code -Dgrails.gorm.sqlTail.enabled=true}
 * for the application. A property given without a value is passed as {@code true}.
 *
 * @since 8.1
 */
@CompileStatic
class GrailsPropertyArgumentProvider implements CommandLineArgumentProvider {

    private final Provider<Map<String, String>> settings

    /**
     * @param settings the Gradle properties to hand over, by name; usually
     * {@code providers.gradlePropertiesPrefixedBy(prefix)}
     */
    GrailsPropertyArgumentProvider(Provider<Map<String, String>> settings) {
        this.settings = settings
    }

    @Input
    Map<String, String> getSettings() {
        settings.getOrElse([:])
    }

    @Override
    Iterable<String> asArguments() {
        getSettings().collect { String name, String value ->
            "-D${name}=${value ?: 'true'}".toString()
        }
    }
}
