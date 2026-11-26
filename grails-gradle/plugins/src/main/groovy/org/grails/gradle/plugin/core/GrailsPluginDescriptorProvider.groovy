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

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

import grails.util.BuildSettings

/**
 * Gives the Groovy compiler what it writes into the descriptors of the plugins it compiles: the plugin
 * version ({@link BuildSettings#PLUGIN_VERSION}) and the directory holding the plugin's
 * {@code plugin.yml} or {@code plugin.groovy} ({@link BuildSettings#PLUGIN_CONFIG_DIR}).
 *
 * <p>Both reach the compiler worker as system properties. The configuration files are inputs, so
 * editing, adding or removing one recompiles the source set, and the descriptors are written again.</p>
 *
 * @since 9.0
 */
@CompileStatic
abstract class GrailsPluginDescriptorProvider implements CommandLineArgumentProvider {

    /** The plugin version, the project version; absent when the project has none. */
    @Input
    @Optional
    abstract Property<String> getPluginVersion()

    /** The directory of the configuration files, {@code grails-app/conf}. */
    @Internal
    abstract Property<File> getConfigDirectory()

    /** The configuration files themselves, which need not exist. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getConfigFiles()

    @Override
    Iterable<String> asArguments() {
        List<String> arguments = []
        String version = pluginVersion.orNull
        if (version) {
            arguments.add("-D${BuildSettings.PLUGIN_VERSION}=${version}".toString())
        }
        if (configDirectory.present) {
            arguments.add("-D${BuildSettings.PLUGIN_CONFIG_DIR}=${configDirectory.get().absolutePath}".toString())
        }
        arguments
    }
}
