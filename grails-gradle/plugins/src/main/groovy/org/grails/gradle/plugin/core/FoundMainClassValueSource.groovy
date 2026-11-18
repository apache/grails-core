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

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * The main class the {@code findMainClass} task wrote to its cache file, or no value before the task has run.
 *
 * <p>Each source instance memoizes its first read. A configuration-time read fixes that instance's value:
 * no value on a clean build, or the class left by an earlier build. Reading the same instance after
 * {@code findMainClass} runs does not refresh it, with or without the configuration cache.</p>
 *
 * <p>The public main-class providers therefore create a fresh source in a {@code flatMap} query of the
 * {@code findMainClass} output. An early query does not fix the value of a later query, and an unread source
 * is stored in the configuration cache for execution-time reads. A source obtained during configuration is
 * also a configuration-cache input, checked when Gradle considers reusing the entry.</p>
 *
 * @since 8.0
 */
@CompileStatic
abstract class FoundMainClassValueSource implements ValueSource<String, Parameters> {

    interface Parameters extends ValueSourceParameters {

        /** The file the {@code findMainClass} task writes the main class to. */
        RegularFileProperty getMainClassCacheFile()
    }

    @Override
    String obtain() {
        File file = parameters.mainClassCacheFile.get().asFile
        file.exists() ? file.text : null
    }
}
