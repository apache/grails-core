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
package org.grails.compiler.injection

import java.nio.file.Files

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

import grails.plugins.metadata.PluginDefinition
import grails.util.BuildSettings
import grails.util.Environment
import org.apache.grails.plugin.descriptor.PluginDescriptorWriter

/**
 * Writes the descriptors of the plugins in a class directory, from the classes the compilation left
 * there, and removes those of plugins that are gone. The Groovy compiler and javac both call it once
 * a compilation has written its classes.
 *
 * <p>The version comes from the {@value BuildSettings#PLUGIN_VERSION} system property (or the javac
 * option of that name) and the configuration files from the directory named by
 * {@value BuildSettings#PLUGIN_CONFIG_DIR}; the Grails Gradle plugin sets both for the main source set.
 * A plugin compiled without them, such as one in a test source set, has no version and no
 * configuration file.</p>
 *
 * <p>A {@code plugins.imports} that the compiler did not write, such as one in a project's resources,
 * is left alone.</p>
 */
@CompileStatic
@PackageScope
final class CompilerPluginDescriptors {

    private static final List<String> CONFIG_FILE_NAMES = ['plugin.yml', 'plugin.groovy']

    private CompilerPluginDescriptors() {
    }

    static void reconcile(File directory, String pluginVersion) {
        File imports = new File(directory, PluginDescriptorWriter.PLUGINS_IMPORTS_PATH)
        List<String> previous = imports.isFile() ? imports.readLines('UTF-8') : []
        if (imports.exists() && (!previous || previous.first() != PluginDescriptorWriter.GENERATED_HEADER)) {
            return
        }
        String version = pluginVersion?.trim() ?: System.getProperty(BuildSettings.PLUGIN_VERSION)?.trim() ?: null
        String grailsVersion = Environment.grailsVersion
        List<PluginDefinition> definitions = PluginDescriptorWriter.scan(Collections.singleton(directory))
                .definitions(version, grailsVersion ? grailsVersion + ' > *' : null)
        List<String> names = definitions ?
                PluginDescriptorWriter.writeDescriptors(definitions, configFiles(), directory) : List.<String>of()
        for (String line : previous.drop(1)) {
            if (line ==~ /[A-Za-z0-9][A-Za-z0-9._-]*/ && !line.contains('..') && !names.contains(line)) {
                new File(directory, PluginDescriptorWriter.PLUGIN_DESCRIPTORS_PREFIX + line).deleteDir()
            }
        }
        if (!names) {
            Files.deleteIfExists(imports.toPath())
        }
    }

    private static List<File> configFiles() {
        String configDir = System.getProperty(BuildSettings.PLUGIN_CONFIG_DIR)
        configDir ? CONFIG_FILE_NAMES.collect { String name -> new File(configDir, name) } : List.<File>of()
    }
}
