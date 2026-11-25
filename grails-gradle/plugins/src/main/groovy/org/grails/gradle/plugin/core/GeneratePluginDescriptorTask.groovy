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

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import org.apache.grails.plugin.descriptor.PluginDescriptorWriter
import org.apache.grails.plugin.descriptor.PluginDescriptorWriter.ScanResult
import org.apache.grails.plugin.descriptor.PluginDescriptorWriter.ScannedPlugin

import static org.apache.grails.plugin.descriptor.PluginDescriptorWriter.normalizePluginName
import static org.apache.grails.plugin.descriptor.PluginDescriptorWriter.trimToNull

/**
 * Generates {@code META-INF/grails/plugins.imports}, per-plugin
 * {@code META-INF/grails/plugins/<name>/plugin.properties} descriptors, and namespaced plugin
 * configuration files ({@code META-INF/grails/plugins/<name>/plugin.yml} or {@code plugin.groovy})
 * by scanning compiled plugin classes with ASM.
 *
 * <p>Supports three plugin authoring models:</p>
 * <ul>
 *     <li>Traditional {@code *GrailsPlugin} Groovy classes</li>
 *     <li>Java or Groovy classes annotated with {@code @GrailsPluginDescriptor}</li>
 *     <li>Descriptor-less starter plugins with no plugin class at all (metadata taken from
 *     {@code grails { plugin { ... } }} and project coordinates)</li>
 * </ul>
 *
 * @since 9.0
 */
@CacheableTask
@CompileStatic
abstract class GeneratePluginDescriptorTask extends DefaultTask {

    @InputFiles
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getClassesDirs()

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.NAME_ONLY)
    abstract ConfigurableFileCollection getPluginConfigFiles()

    @Input
    abstract Property<String> getProjectName()

    @Input
    @Optional
    abstract Property<String> getProjectVersion()

    @Nested
    abstract GrailsPluginOptions getPluginOptions()

    @OutputDirectory
    abstract DirectoryProperty getOutputDirectory()

    @TaskAction
    void generate() {
        File outDir = outputDirectory.get().asFile
        if (outDir.exists()) {
            outDir.deleteDir()
        }
        outDir.mkdirs()

        ScanResult result = PluginDescriptorWriter.scan(classesDirs.files)
        List<ScannedPlugin> plugins = new ArrayList<>(result.plugins)

        String configuredName = trimToNull(pluginOptions.name.getOrNull())
        String configuredVersion = trimToNull(pluginOptions.version.getOrNull()) ?: trimToNull(projectVersion.getOrNull())
        List<String> extraExcludes = pluginOptions.pluginExcludes.getOrElse([])

        if (plugins.isEmpty()) {
            if (!pluginOptions.starter.getOrElse(false)) {
                return
            }
            String rawName = configuredName ?: projectName.get()
            String logicalName = normalizePluginName(rawName)
            plugins.add(new ScannedPlugin(
                    null,
                    logicalName,
                    configuredVersion ?: '0.1',
                    trimToNull(pluginOptions.grailsVersion.getOrNull()),
                    pluginOptions.loadAfter.getOrElse([]),
                    pluginOptions.loadBefore.getOrElse([]),
                    pluginOptions.dependsOn.getOrElse([:]),
                    pluginOptions.evict.getOrElse([]),
                    pluginOptions.observe.getOrElse([]),
                    pluginOptions.environmentsIncludes.getOrElse([]),
                    pluginOptions.environmentsExcludes.getOrElse([]),
                    extraExcludes,
                    pluginOptions.enabled.getOrElse(Boolean.TRUE),
                    true
            ))
        }
        else {
            if (configuredName != null && plugins.size() == 1) {
                plugins = [plugins[0].withLogicalName(normalizePluginName(configuredName))]
            }
            if (extraExcludes) {
                plugins = plugins.collect { ScannedPlugin p -> p.withExtraExcludes(extraExcludes) }
            }
        }

        File configFile = resolvePluginConfigFile(pluginConfigFiles.files)
        PluginDescriptorWriter.writeDescriptors(plugins, result.artefactsByType, configuredVersion, configFile, outDir)
    }

    private static File resolvePluginConfigFile(Set<File> candidates) {
        Collection<File> existing = candidates.findAll { File f -> f.isFile() }
        if (existing.size() > 1) {
            throw new GradleException(
                    "A plugin may define a plugin.yml or a plugin.groovy, but not both: ${existing*.name.sort().join(', ')}"
            )
        }
        existing ? existing.iterator().next() : null
    }
}
