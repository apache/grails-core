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
package org.grails.gradle.plugin.dependencies

import java.util.zip.ZipFile

import groovy.transform.CompileStatic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

import grails.plugins.metadata.PluginDefinition
import org.apache.grails.plugin.descriptor.PluginDescriptorWriter

/**
 * Checks that every plugin a Grails plugin {@code dependsOn} is provided by a dependency of the project.
 *
 * <p>{@code dependsOn} means the plugin cannot work without the other one, so the other one must come with
 * it: some artifact on the project's runtime class path holds its descriptor,
 * {@code META-INF/grails/plugins/<name>/plugin.properties}, or the project itself declares it. A plugin that
 * only has to come after another one when that one is present names it in {@code after} instead.</p>
 *
 * @since 9.0
 */
@CompileStatic
@DisableCachingByDefault(because = 'Reading a few descriptors is cheaper than a build-cache round trip')
abstract class VerifyPluginDependenciesTask extends DefaultTask {

    /** The descriptors of the plugins the project declares. */
    @InputFiles
    @SkipWhenEmpty
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getPluginDescriptors()

    /** The class path the plugins run with, whose artifacts provide the plugins they depend on. */
    @Classpath
    abstract ConfigurableFileCollection getClasspath()

    /** The project, as the failure names it. */
    @Input
    abstract Property<String> getProjectPath()

    @TaskAction
    void verify() {
        Map<String, PluginDefinition> declared = new TreeMap<>()
        pluginDescriptors.asFileTree.visit { FileVisitDetails details ->
            if (!details.directory) {
                String name = details.file.parentFile.name
                PluginDefinition definition = details.file.withReader('UTF-8') { Reader reader ->
                    PluginDefinition.read(name, reader)
                }
                declared.put(definition.pluginName, definition)
            }
        }
        List<String> unbacked = []
        declared.values().each { PluginDefinition plugin ->
            plugin.dependsOn.keySet().each { String dependency ->
                if (!declared.containsKey(dependency) && !provided(dependency)) {
                    unbacked.add("- Grails plugin [${plugin.pluginName}] depends on [${dependency}], but no dependency of " +
                            "${projectPath.get()} provides it (none of its runtime class path has " +
                            "${descriptorPath(dependency)})" as String)
                }
            }
        }
        if (unbacked) {
            throw new GradleException('Grails plugins depend on plugins their project does not provide:\n' +
                    unbacked.join('\n') + '\nAdd the dependency that provides each plugin, or, for a plugin that ' +
                    'only has to come after another one when that one is present, name it in after instead of ' +
                    'dependsOn on @GrailsPluginDescriptor.')
        }
    }

    private boolean provided(String pluginName) {
        String path = descriptorPath(pluginName)
        classpath.files.any { File entry ->
            if (entry.directory) {
                return new File(entry, path).file
            }
            if (entry.file && entry.name.endsWith('.jar')) {
                return new ZipFile(entry).withCloseable { ZipFile jar -> jar.getEntry(path) != null }
            }
            false
        }
    }

    private static String descriptorPath(String pluginName) {
        PluginDescriptorWriter.PLUGIN_DESCRIPTORS_PREFIX + pluginName + '/' + PluginDescriptorWriter.PLUGIN_PROPERTIES_FILE
    }
}
