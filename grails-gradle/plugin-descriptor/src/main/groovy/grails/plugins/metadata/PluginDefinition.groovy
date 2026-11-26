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

    /** Mutable declaration inputs. Building takes an immutable, normalized snapshot. */
    static final class Builder {
        String pluginName
        String pluginVersion
        String grailsVersionRange
        List<String> providedPlugins
        List<String> providedClasses
        List<String> loadAfter
        List<String> loadBefore
        List<String> evict
        List<String> observe
        List<String> environmentsIncludes
        List<String> environmentsExcludes
        Map<String, List<String>> providedArtefactsByType
        Map<String, String> dependsOn
        boolean enabled = true
        boolean metadataComplete = true

        PluginDefinition build() {
            new PluginDefinition(this)
        }
    }

    private PluginDefinition(Builder source) {
        pluginName = normalizePluginName(source.pluginName)
        if (!(pluginName ==~ /[A-Za-z0-9][A-Za-z0-9._-]*/) || pluginName.contains('..')) {
            throw new IllegalArgumentException("Invalid Grails plugin name '${source.pluginName}'")
        }
        pluginVersion = source.pluginVersion?.trim() ?: null
        grailsVersionRange = resolveVersion(source.grailsVersionRange?.trim())
        providedPlugins = copyEntries(source.providedPlugins)
        loadAfter = names(source.loadAfter)
        loadBefore = names(source.loadBefore)
        observe = names(source.observe)
        evict = names(source.evict)
        environmentsIncludes = copyEntries(source.environmentsIncludes)
        environmentsExcludes = copyEntries(source.environmentsExcludes)
        enabled = source.enabled
        metadataComplete = source.metadataComplete

        Map<String, String> dependencies = [:]
        source.dependsOn?.each { String name, String constraint ->
            String normalized = normalizePluginName(name)
            if (normalized) {
                dependencies.put(normalized, resolveVersion(constraint?.trim()) ?: '*')
            }
        }
        dependsOn = Collections.unmodifiableMap(dependencies)

        Map<String, List<String>> typed = [:]
        Set<String> classes = new LinkedHashSet<>()
        source.providedArtefactsByType?.each { String type, List<String> entries ->
            List<String> included = copyEntries(entries)
            if (type?.trim() && included) {
                typed.put(type.trim(), included)
                classes.addAll(included)
            }
        }
        classes.addAll(copyEntries(source.providedClasses))
        providedArtefactsByType = Collections.unmodifiableMap(typed)
        providedClasses = List.copyOf(classes)
    }

    private String resolveVersion(String value) {
        if (!value?.contains('${plugin.version}')) {
            return value ?: null
        }
        if (!pluginVersion) {
            throw new IllegalArgumentException("Plugin ${pluginName} must declare a version to resolve ${value}")
        }
        value.replace('${plugin.version}', pluginVersion)
    }

    static Map<String, String> parseDependencies(Iterable<String> declarations) {
        Map<String, String> dependencies = [:]
        for (String declaration : declarations) {
            String spec = declaration?.trim()
            if (spec) {
                int separator = spec.indexOf(':')
                String name = separator < 0 ? spec : spec.substring(0, separator).trim()
                if (name) {
                    dependencies.put(name, separator < 0 ? '*' : spec.substring(separator + 1).trim() ?: '*')
                }
            }
        }
        dependencies
    }

    /** Reads a UTF-8 plugin.properties file. The caller owns the reader. */
    static PluginDefinition read(String importedName, Reader reader) {
        Properties values = new Properties()
        values.load(reader)
        fromProperties(importedName, values)
    }

    static PluginDefinition fromProperties(String importedName, Properties values) {
        String format = values.getProperty('format.version', FORMAT_VERSION).trim()
        if (format != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported plugin descriptor format ${format} for ${importedName}")
        }
        Builder builder = new Builder()
        builder.pluginName = values.getProperty('name')?.trim() ?: importedName
        if (importedName && normalizePluginName(builder.pluginName) != normalizePluginName(importedName)) {
            throw new IllegalArgumentException("Plugin ${importedName} declares a conflicting name ${builder.pluginName}")
        }
        builder.pluginVersion = values.getProperty('version')
        builder.grailsVersionRange = values.getProperty('grailsVersion')
        builder.providedPlugins = entries(values.getProperty('class'))
        builder.providedClasses = entries(values.getProperty('artefacts'))
        builder.loadAfter = entries(values.getProperty('loadAfter'))
        builder.loadBefore = entries(values.getProperty('loadBefore'))
        builder.evict = entries(values.getProperty('evict'))
        builder.observe = entries(values.getProperty('observe'))
        builder.environmentsIncludes = entries(values.getProperty('environments.includes'))
        builder.environmentsExcludes = entries(values.getProperty('environments.excludes'))
        builder.enabled = flag(values, 'enabled', true)
        builder.metadataComplete = flag(values, 'metadata.complete', builder.providedPlugins.empty)
        builder.dependsOn = [:]
        builder.providedArtefactsByType = [:]
        for (String key : values.stringPropertyNames().sort()) {
            if (key.startsWith('dependsOn.')) {
                builder.dependsOn.put(key.substring('dependsOn.'.length()), values.getProperty(key))
            } else if (key.startsWith('artefacts.')) {
                builder.providedArtefactsByType.put(key.substring('artefacts.'.length()), entries(values.getProperty(key)))
            }
        }
        builder.build()
    }

    /** Writes reproducible UTF-8 properties without timestamps. */
    void write(Writer writer) {
        Map<String, String> entries = new TreeMap<>()
        entries.put('format.version', FORMAT_VERSION)
        entries.put('name', pluginName)
        entries.put('metadata.complete', Boolean.toString(metadataComplete))
        if (pluginVersion) {
            entries.put('version', pluginVersion)
        }
        if (grailsVersionRange) {
            entries.put('grailsVersion', grailsVersionRange)
        }
        if (!enabled) {
            entries.put('enabled', 'false')
        }
        addList(entries, 'class', providedPlugins)
        addList(entries, 'loadAfter', loadAfter)
        addList(entries, 'loadBefore', loadBefore)
        addList(entries, 'evict', evict)
        addList(entries, 'observe', observe)
        addList(entries, 'environments.includes', environmentsIncludes)
        addList(entries, 'environments.excludes', environmentsExcludes)
        dependsOn.each { String key, String value -> entries.put('dependsOn.' + key, value) }
        Set<String> untyped = new LinkedHashSet<>(providedClasses)
        providedArtefactsByType.each { String key, List<String> values ->
            addList(entries, 'artefacts.' + key, values)
            untyped.removeAll(values)
        }
        addList(entries, 'artefacts', untyped)
        writer.write('# Generated by Grails. Do not edit.\n')
        entries.each { String key, String value -> writer.write(escapeKey(key) + '=' + escapeValue(value) + '\n') }
    }

    private static void addList(Map<String, String> entries, String key, Collection<String> values) {
        if (values) {
            entries.put(key, values.join(','))
        }
    }

    private static List<String> entries(String value) {
        (value ?: '').tokenize(',')
    }

    private static List<String> names(List<String> values) {
        List.copyOf(copyEntries(values).collect { normalizePluginName(it) })
    }

    private static List<String> copyEntries(List<String> values) {
        values ? List.copyOf(values.collect { it?.trim() }.findAll { it }) : List.of()
    }

    static String normalizePluginName(String raw) {
        String trimmed = raw?.trim() ?: null
        if (trimmed == null) {
            return null
        }
        if (!trimmed.contains('-')) {
            return trimmed
        }
        StringBuilder pascal = new StringBuilder()
        for (String token : trimmed.split('-')) {
            if (!token.isEmpty()) {
                pascal.append(Character.toUpperCase(token.charAt(0)))
                if (token.length() > 1) {
                    pascal.append(token.substring(1))
                }
            }
        }
        if (pascal.length() == 0) {
            return ''
        }
        if (pascal.length() > 1 && Character.isUpperCase(pascal.charAt(0)) && Character.isUpperCase(pascal.charAt(1))) {
            return pascal.toString()
        }
        Character.toLowerCase(pascal.charAt(0)).toString() + pascal.substring(1)
    }

    private static String escapeKey(String key) {
        StringBuilder sb = new StringBuilder(key.length())
        for (int i = 0; i < key.length(); i++) {
            char ch = key.charAt(i)
            if (ch == ('\\' as char)) {
                sb.append('\\\\')
            }
            else if (ch == ('=' as char)) {
                sb.append('\\=')
            }
            else if (ch == (':' as char)) {
                sb.append('\\:')
            }
            else if (ch == (' ' as char)) {
                sb.append('\\ ')
            }
            else if (ch == ('\t' as char)) {
                sb.append('\\t')
            }
            else if (ch == ('\n' as char)) {
                sb.append('\\n')
            }
            else if (ch == ('\r' as char)) {
                sb.append('\\r')
            }
            else if (ch == ('\f' as char)) {
                sb.append('\\f')
            }
            else {
                sb.append(ch)
            }
        }
        sb.toString()
    }

    private static String escapeValue(String value) {
        StringBuilder sb = new StringBuilder(value.length())
        boolean leading = true
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i)
            if (ch == (' ' as char)) {
                sb.append(leading ? '\\ ' : ' ')
            }
            else if (ch == ('\\' as char)) {
                sb.append('\\\\')
                leading = false
            }
            else if (ch == ('\t' as char)) {
                sb.append('\\t')
                leading = false
            }
            else if (ch == ('\n' as char)) {
                sb.append('\\n')
                leading = false
            }
            else if (ch == ('\r' as char)) {
                sb.append('\\r')
                leading = false
            }
            else if (ch == ('\f' as char)) {
                sb.append('\\f')
                leading = false
            }
            else {
                sb.append(ch)
                leading = false
            }
        }
        sb.toString()
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
