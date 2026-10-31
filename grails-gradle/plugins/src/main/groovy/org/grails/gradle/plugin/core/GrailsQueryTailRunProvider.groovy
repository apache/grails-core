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

import java.util.function.Supplier

import groovy.transform.CompileStatic

import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.process.CommandLineArgumentProvider

/**
 * Hands a test JVM the name of the query-tail run of the build, {@code -Dgrails.gorm.queryTail.run}: a capture
 * that finds its file headed by that run appends to it. Every test JVM is told, whether capture is enabled by a
 * Gradle property or by the configuration of the application, and a JVM that does not capture ignores it; a run
 * the build is given as a Gradle property itself is passed as it is instead. The name changes with every build,
 * so it is neither an input of the task nor part of what the build cache keys its results by.
 *
 * @since 9.0
 */
@CompileStatic
class GrailsQueryTailRunProvider implements CommandLineArgumentProvider {

    /** The system property the run is named in, which the capture reads. */
    static final String RUN_PROPERTY = 'grails.gorm.queryTail.run'

    private final Provider<Map<String, String>> settings
    private final Provider<? extends Supplier<String>> run

    /**
     * @param settings the query-tail settings given as Gradle properties, by name, which may name the run
     * @param run the run of the build, read through {@link Supplier} and never cast to {@link GrailsQueryTailRun},
     *     since it is the class of whichever class loader loaded the plugin for the project that registered it
     */
    GrailsQueryTailRunProvider(Provider<Map<String, String>> settings, Provider<? extends Supplier<String>> run) {
        this.settings = settings
        this.run = run
    }

    @Internal
    Provider<Map<String, String>> getSettings() {
        settings
    }

    @Internal
    Provider<? extends Supplier<String>> getRun() {
        run
    }

    @Override
    Iterable<String> asArguments() {
        settings.getOrElse([:]).containsKey(RUN_PROPERTY) ? [] : ["-D${RUN_PROPERTY}=${run.get().get()}".toString()]
    }
}
