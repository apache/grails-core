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

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

/**
 * Gives the Groovy compiler of a framework module what it writes into the descriptors of the plugins
 * the module declares: the version, as {@code grails.plugin.version}, and the directory holding the
 * plugin's {@code plugin.yml} or {@code plugin.groovy}, as {@code grails.plugin.config.dir}. It is the
 * framework build's copy of the Grails Gradle plugin's {@code GrailsPluginDescriptorProvider}, for the
 * modules that do not apply that plugin.
 */
@CompileStatic
abstract class PluginDescriptorArgumentProvider implements CommandLineArgumentProvider {

    @Input
    @Optional
    abstract Property<String> getPluginVersion()

    @Internal
    abstract Property<File> getConfigDirectory()

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getConfigFiles()

    @Override
    Iterable<String> asArguments() {
        List<String> arguments = []
        if (pluginVersion.orNull) {
            arguments.add("-Dgrails.plugin.version=${pluginVersion.get()}".toString())
        }
        if (configDirectory.present) {
            arguments.add("-Dgrails.plugin.config.dir=${configDirectory.get().absolutePath}".toString())
        }
        arguments
    }
}
