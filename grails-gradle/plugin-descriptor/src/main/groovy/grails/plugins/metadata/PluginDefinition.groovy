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
 * The metadata of one Grails plugin, as its {@link GrailsPluginDescriptor} declares it and the compiler
 * publishes it in {@code META-INF/grails/plugins/<name>/plugin.properties}. Class names are symbolic, so
 * tools read a plugin without loading it or its dependencies, and discovery filters and orders plugins
 * without creating them.
 *
 * @since 9.0
 */
@CompileStatic
@EqualsAndHashCode
final class PluginDefinition {

    static final String FORMAT_VERSION = '1'

    /** The {@code ${plugin.version}} placeholder a dependency range may use for the plugin's own version. */
    static final String PLUGIN_VERSION_PLACEHOLDER = '${plugin.version}'

    private static final String GRAILS_PLUGIN_SUFFIX = 'GrailsPlugin'

    final String pluginName
    final String pluginVersion
    final String grailsVersionRange
    final String pluginClass
    final List<String> loadAfter
    final List<String> loadBefore
    final List<String> evict
    final List<String> observe
    final List<String> environmentsIncludes
    final List<String> environmentsExcludes
    final List<String> watchedResources
    final List<String> pluginExcludes
    final List<String> providedArtefacts
    final Map<String, List<String>> artefactsByType
    final Map<String, String> dependsOn
    final boolean enabled

    /** Mutable declaration inputs. Building takes an immutable, normalized snapshot. */
    static final class Builder {
        String pluginName
        String pluginVersion
        String grailsVersionRange
        String pluginClass
        List<String> loadAfter
        List<String> loadBefore
        List<String> evict
        List<String> observe
        List<String> environmentsIncludes
        List<String> environmentsExcludes
        List<String> watchedResources
        List<String> pluginExcludes
        List<String> providedArtefacts
        Map<String, List<String>> artefactsByType
        Map<String, String> dependsOn
        boolean enabled = true

        PluginDefinition build() {
            new PluginDefinition(this)
        }
    }

    private PluginDefinition(Builder source) {
        pluginName = normalizePluginName(source.pluginName)
        if (!(pluginName ==~ /[A-Za-z0-9][A-Za-z0-9._-]*/) || pluginName.contains('..')) {
            throw new IllegalArgumentException("Invalid Grails plugin name '${source.pluginName}'")
        }
        pluginClass = source.pluginClass?.trim() ?: null
        if (!pluginClass) {
            throw new IllegalArgumentException("Grails plugin '${pluginName}' names no plugin class")
        }
        pluginVersion = source.pluginVersion?.trim() ?: null
        grailsVersionRange = resolveVersion(source.grailsVersionRange?.trim())
        loadAfter = names(source.loadAfter)
        loadBefore = names(source.loadBefore)
        observe = names(source.observe)
        evict = names(source.evict)
        environmentsIncludes = copyEntries(source.environmentsIncludes)
        environmentsExcludes = copyEntries(source.environmentsExcludes)
        watchedResources = copyEntries(source.watchedResources)
        pluginExcludes = copyEntries(source.pluginExcludes)
        String sourcePath = pluginExcludes.find { String pattern ->
            pattern.startsWith('grails-app/') || pattern.startsWith('src/') || pattern.contains('.')
        }
        if (sourcePath) {
            throw new IllegalArgumentException("Plugin ${pluginName} excludes '${sourcePath}', which is not a pattern of " +
                    "artefact class names: pluginExcludes are Ant patterns of class names, such as 'com/example/demo/**'")
        }
        providedArtefacts = copyEntries(source.providedArtefacts)
        enabled = source.enabled

        Map<String, String> dependencies = new LinkedHashMap<>()
        source.dependsOn?.each { String name, String constraint ->
            String normalized = normalizePluginName(name)
            if (normalized) {
                dependencies.put(normalized, resolveVersion(constraint?.trim()) ?: '*')
            }
        }
        dependsOn = Collections.unmodifiableMap(dependencies)

        Map<String, List<String>> typed = new TreeMap<>()
        source.artefactsByType?.each { String type, List<String> entries ->
            List<String> included = copyEntries(entries)
            if (type?.trim() && included) {
                typed.put(type.trim(), included)
            }
        }
        artefactsByType = Collections.unmodifiableMap(typed)
    }

    /**
     * The artefact classes the plugin contributes: those compiled with it, by type, and those its
     * descriptor names in {@code providedArtefacts}.
     */
    List<String> getProvidedClasses() {
        Set<String> classes = new LinkedHashSet<>()
        artefactsByType.values().each { List<String> names -> classes.addAll(names) }
        classes.addAll(providedArtefacts)
        List.copyOf(classes)
    }

    private String resolveVersion(String value) {
        if (!value?.contains(PLUGIN_VERSION_PLACEHOLDER)) {
            return value ?: null
        }
        if (!pluginVersion) {
            // Without a version (a class compiled outside a build) the plugin's own version is unknown,
            // and a range written against it cannot be checked.
            return '*'
        }
        value.replace(PLUGIN_VERSION_PLACEHOLDER, pluginVersion)
    }

    /**
     * Builds the definition a {@link GrailsPluginDescriptor} declares. The compiler reads the
     * annotation from bytecode and the runtime by reflection; both hand its attributes here, keyed by
     * attribute name, so the two agree on every default.
     *
     * @param pluginClass the annotated class
     * @param attributes the annotation attributes that are set: {@code String}, {@code Boolean} or a
     *        {@code List<String>} of values (class names for {@code providedArtefacts})
     * @param pluginVersion the plugin's version (the build's project version), or {@code null}
     * @param defaultGrailsVersion the Grails range used when the descriptor declares none, or {@code null}
     * @param artefactsByType the artefact classes compiled with the plugin, by artefact type
     */
    static PluginDefinition fromDeclaration(
            String pluginClass,
            Map<String, Object> attributes,
            String pluginVersion,
            String defaultGrailsVersion,
            Map<String, List<String>> artefactsByType) {
        Builder builder = new Builder()
        builder.pluginClass = pluginClass
        builder.pluginName = deriveName(pluginClass, (String) attributes.get('name'))
        builder.pluginVersion = pluginVersion
        builder.grailsVersionRange = ((String) attributes.get('grailsVersion'))?.trim() ?: defaultGrailsVersion
        builder.loadAfter = list(attributes, 'loadAfter')
        builder.loadBefore = list(attributes, 'loadBefore')
        builder.evict = list(attributes, 'evict')
        builder.observe = list(attributes, 'observe')
        builder.environmentsIncludes = list(attributes, 'environmentsIncludes')
        builder.environmentsExcludes = list(attributes, 'environmentsExcludes')
        builder.watchedResources = list(attributes, 'watchedResources')
        builder.pluginExcludes = list(attributes, 'pluginExcludes')
        builder.providedArtefacts = list(attributes, 'providedArtefacts')
        builder.dependsOn = parseDependencies(list(attributes, 'dependsOn'))
        Object enabled = attributes.get('enabled')
        builder.enabled = enabled == null || (Boolean) enabled
        builder.artefactsByType = artefactsByType ?: [:]
        builder.build()
    }

    @SuppressWarnings('unchecked')
    private static List<String> list(Map<String, Object> attributes, String name) {
        (List<String>) attributes.get(name) ?: List.<String>of()
    }

    /**
     * The logical name of a plugin: the declared name, or else the simple class name without a
     * {@code GrailsPlugin} suffix, in property-name form ({@code FooBarGrailsPlugin} is {@code fooBar}).
     */
    static String deriveName(String pluginClass, String declaredName) {
        if (declaredName?.trim()) {
            return normalizePluginName(declaredName)
        }
        String simpleName = pluginClass.substring(pluginClass.lastIndexOf('.') + 1)
        simpleName = simpleName.substring(simpleName.lastIndexOf('$') + 1)
        if (simpleName.endsWith(GRAILS_PLUGIN_SUFFIX) && simpleName.length() > GRAILS_PLUGIN_SUFFIX.length()) {
            simpleName = simpleName.substring(0, simpleName.length() - GRAILS_PLUGIN_SUFFIX.length())
        }
        if (simpleName.length() > 1 && Character.isUpperCase(simpleName.charAt(0)) && Character.isUpperCase(simpleName.charAt(1))) {
            return simpleName
        }
        Character.toLowerCase(simpleName.charAt(0)).toString() + simpleName.substring(1)
    }

    static Map<String, String> parseDependencies(Iterable<String> declarations) {
        Map<String, String> dependencies = new LinkedHashMap<>()
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
        builder.pluginClass = values.getProperty('class')
        builder.pluginVersion = values.getProperty('version')
        builder.grailsVersionRange = values.getProperty('grailsVersion')
        builder.loadAfter = entries(values.getProperty('loadAfter'))
        builder.loadBefore = entries(values.getProperty('loadBefore'))
        builder.evict = entries(values.getProperty('evict'))
        builder.observe = entries(values.getProperty('observe'))
        builder.environmentsIncludes = entries(values.getProperty('environments.includes'))
        builder.environmentsExcludes = entries(values.getProperty('environments.excludes'))
        builder.watchedResources = entries(values.getProperty('watchedResources'))
        builder.pluginExcludes = entries(values.getProperty('pluginExcludes'))
        builder.providedArtefacts = entries(values.getProperty('providedArtefacts'))
        builder.enabled = flag(values, 'enabled', true)
        builder.dependsOn = new LinkedHashMap<>()
        builder.artefactsByType = new LinkedHashMap<>()
        for (String key : values.stringPropertyNames().sort()) {
            if (key.startsWith('dependsOn.')) {
                builder.dependsOn.put(key.substring('dependsOn.'.length()), values.getProperty(key))
            } else if (key.startsWith('artefacts.')) {
                builder.artefactsByType.put(key.substring('artefacts.'.length()), entries(values.getProperty(key)))
            }
        }
        builder.build()
    }

    /** Writes reproducible UTF-8 properties without timestamps. */
    void write(Writer writer) {
        Map<String, String> entries = new TreeMap<>()
        entries.put('format.version', FORMAT_VERSION)
        entries.put('name', pluginName)
        entries.put('class', pluginClass)
        if (pluginVersion) {
            entries.put('version', pluginVersion)
        }
        if (grailsVersionRange) {
            entries.put('grailsVersion', grailsVersionRange)
        }
        if (!enabled) {
            entries.put('enabled', 'false')
        }
        addList(entries, 'loadAfter', loadAfter)
        addList(entries, 'loadBefore', loadBefore)
        addList(entries, 'evict', evict)
        addList(entries, 'observe', observe)
        addList(entries, 'environments.includes', environmentsIncludes)
        addList(entries, 'environments.excludes', environmentsExcludes)
        addList(entries, 'watchedResources', watchedResources)
        addList(entries, 'pluginExcludes', pluginExcludes)
        addList(entries, 'providedArtefacts', providedArtefacts)
        dependsOn.each { String key, String value -> entries.put('dependsOn.' + key, value) }
        artefactsByType.each { String key, List<String> values -> addList(entries, 'artefacts.' + key, values) }
        writer.write('# Generated by the Grails compiler. Do not edit.\n')
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
