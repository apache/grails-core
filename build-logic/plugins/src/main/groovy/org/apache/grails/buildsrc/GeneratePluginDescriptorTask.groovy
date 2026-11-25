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

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import org.apache.grails.plugin.descriptor.PluginDescriptorWriter
import org.apache.grails.plugin.descriptor.PluginDescriptorWriter.ScanResult

/**
 * Scans this project's compiled main classes with ASM for Grails plugin descriptor classes
 * ({@code *GrailsPlugin} or {@code @GrailsPluginDescriptor}) and {@code @Artefact} classes,
 * writing {@code META-INF/grails/plugins.imports} and per-plugin
 * {@code META-INF/grails/plugins/<name>/plugin.properties} descriptors.
 *
 * <p>Modules that declare no plugin descriptor classes produce an empty output directory, so this
 * task is safe to register on every framework library module.</p>
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

    @Input
    @Optional
    abstract Property<String> getProjectVersion()

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
        if (result.plugins.isEmpty()) {
            return
        }

        String defaultVersion = projectVersion.getOrNull()
        PluginDescriptorWriter.writeDescriptors(result.plugins, result.artefactsByType, defaultVersion, null, outDir)
    }
}
