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
package grails.plugins.metadata

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

import grails.util.GrailsNameUtils

/**
 * Published metadata for one Grails plugin. Class names are symbolic so tools can
 * inspect a plugin without loading it or its dependencies. A complete definition
 * supplies all fields required for filtering and ordering before activation.
 *
 * @since 9.0
 */
@CompileStatic
@EqualsAndHashCode
final class PluginDefinition {

    static final String FORMAT_VERSION = '1'

    final String pluginName
    final String pluginVersion
    final String grailsVersionRange
    final List<String> providedPlugins
    final List<String> providedClasses
    final List<String> loadAfter
    final List<String> loadBefore
    final List<String> evict
    final List<String> observe
    final List<String> environmentsIncludes
    final List<String> environmentsExcludes
    final Map<String, List<String>> providedArtefactsByType
    final Map<String, String> dependsOn
    final boolean enabled
    final boolean metadataComplete

    private PluginDefinition(String importedName, Properties values) {
        String format = values.getProperty('format.version', FORMAT_VERSION).trim()
        if (format != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported plugin descriptor format ${format} for ${importedName}")
        }

        pluginName = normalize(values.getProperty('name')?.trim() ?: importedName)
        if (importedName && pluginName != normalize(importedName)) {
            throw new IllegalArgumentException("Plugin ${importedName} declares a conflicting name ${pluginName}")
        }
        pluginVersion = values.getProperty('version')?.trim() ?: null
        grailsVersionRange = values.getProperty('grailsVersion')?.trim() ?: null
        providedPlugins = entries(values.getProperty('class'))
        loadAfter = entries(values.getProperty('loadAfter'))
        loadBefore = entries(values.getProperty('loadBefore'))
        evict = entries(values.getProperty('evict'))
        observe = entries(values.getProperty('observe'))
        environmentsIncludes = entries(values.getProperty('environments.includes'))
        environmentsExcludes = entries(values.getProperty('environments.excludes'))
        enabled = flag(values, 'enabled', true)
        metadataComplete = flag(values, 'metadata.complete', providedPlugins.empty)

        Map<String, String> dependencies = [:]
        Map<String, List<String>> artefacts = [:]
        Set<String> classes = new LinkedHashSet<>()
        for (String key in values.stringPropertyNames().sort()) {
            if (key.startsWith('dependsOn.')) {
                String name = key.substring('dependsOn.'.length()).trim()
                if (name) {
                    dependencies.put(normalize(name), values.getProperty(key).trim() ?: '*')
                }
            } else if (key.startsWith('artefacts.')) {
                String type = key.substring('artefacts.'.length()).trim()
                List<String> names = entries(values.getProperty(key))
                if (type && names) {
                    artefacts.put(type, names)
                    classes.addAll(names)
                }
            }
        }
        classes.addAll(entries(values.getProperty('artefacts')))
        providedClasses = List.copyOf(classes)
        providedArtefactsByType = Collections.unmodifiableMap(artefacts)
        dependsOn = Collections.unmodifiableMap(dependencies)
    }

    /** Reads a UTF-8 plugin.properties file. The caller owns the reader. */
    static PluginDefinition read(String importedName, Reader reader) {
        Properties values = new Properties()
        values.load(reader)
        fromProperties(importedName, values)
    }

    static PluginDefinition fromProperties(String importedName, Properties values) {
        new PluginDefinition(importedName, values)
    }

    private static String normalize(String name) {
        name?.contains('-') ? GrailsNameUtils.getPropertyNameForLowerCaseHyphenSeparatedName(name) : name
    }

    private static List<String> entries(String value) {
        List.copyOf((value ?: '').tokenize(',').collect { it.trim() }.findAll { it })
    }

    private static boolean flag(Properties values, String key, boolean fallback) {
        String value = values.getProperty(key)?.trim()
        if (!value) {
            return fallback
        }
        if (!value.equalsIgnoreCase('true') && !value.equalsIgnoreCase('false')) {
            throw new IllegalArgumentException("Invalid boolean for ${key}: ${value}")
        }
        Boolean.parseBoolean(value)
    }
}
