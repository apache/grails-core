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
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.process.CommandLineArgumentProvider

import grails.util.BuildSettings

/**
 * Gives the Grails annotation processor the version of the Java plugins it writes descriptors for,
 * as the {@code grails.plugin.version} processor option. Nothing is passed when the compilation runs no
 * annotation processor, so javac never warns about an option no processor recognized.
 *
 * @since 9.0
 */
@CompileStatic
class JavacPluginVersionProvider implements CommandLineArgumentProvider {

    private final Provider<String> version
    private final CompileOptions options

    JavacPluginVersionProvider(Provider<String> version, CompileOptions options) {
        this.version = version
        this.options = options
    }

    @Input
    @Optional
    Provider<String> getVersion() {
        version
    }

    @Internal
    CompileOptions getOptions() {
        options
    }

    @Override
    Iterable<String> asArguments() {
        String value = version.orNull
        boolean processes = options.annotationProcessorPath != null && !options.annotationProcessorPath.empty
        value && processes ? ["-A${BuildSettings.PLUGIN_VERSION}=${value}".toString()] : List.<String>of()
    }
}
