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
package grails.dev.commands

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.core.env.PropertySource

/**
 * An {@link ApplicationCommand} that generates an AsciiDoc report
 * of the application's resolved configuration properties.
 *
 * <p>Properties are collected directly from the Spring {@link ConfigurableEnvironment},
 * iterating all {@link EnumerablePropertySource} instances to capture every
 * resolvable property regardless of how it was defined (YAML, Groovy config,
 * system properties, environment variables, etc.).
 *
 * <p>Usage:
 * <pre>
 *     grails config-report
 *     ./gradlew configReport
 * </pre>
 *
 * <p>The report is written to {@code config-report.adoc} in the project's base directory.
 *
 * @since 7.0
 */
@Slf4j
@CompileStatic
class ConfigReportCommand implements ApplicationCommand {

    static final String DEFAULT_REPORT_FILE = 'config-report.adoc'

    final String description = 'Generates an AsciiDoc report of the application configuration'

    @Override
    boolean handle(ExecutionContext executionContext) {
        try {
            ConfigurableEnvironment environment = (ConfigurableEnvironment) applicationContext.getEnvironment()
            Map<String, String> sorted = collectProperties(environment)

            File reportFile = new File(executionContext.baseDir, DEFAULT_REPORT_FILE)
            writeReport(sorted, reportFile)

            log.info('Configuration report written to {}', reportFile.absolutePath)
            return true
        }
        catch (Throwable e) {
            log.error("Failed to generate configuration report: ${e.message}", e)
            return false
        }
    }

    /**
     * Collects all configuration properties from the Spring {@link ConfigurableEnvironment}
     * by iterating its {@link EnumerablePropertySource} instances. Property values are
     * resolved through the environment to ensure placeholders are expanded and
     * the correct precedence order is applied.
     *
     * @param environment the Spring environment
     * @return a sorted map of property names to their resolved values
     */
    Map<String, String> collectProperties(ConfigurableEnvironment environment) {
        Map<String, String> sorted = new TreeMap<String, String>()
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (propertySource instanceof EnumerablePropertySource) {
                EnumerablePropertySource<?> enumerable = (EnumerablePropertySource<?>) propertySource
                for (String propertyName : enumerable.getPropertyNames()) {
                    if (!sorted.containsKey(propertyName)) {
                        try {
                            String value = environment.getProperty(propertyName)
                            if (value != null) {
                                sorted.put(propertyName, value)
                            }
                        }
                        catch (Exception e) {
                            log.debug('Could not resolve property {}: {}', propertyName, e.message)
                        }
                    }
                }
            }
        }
        sorted
    }

    /**
     * Writes the configuration properties as an AsciiDoc file grouped by top-level namespace.
     *
     * @param sorted the sorted configuration properties
     * @param reportFile the file to write the report to
     */
    void writeReport(Map<String, String> runtimeProperties, File reportFile) {
        MetadataResult metadataResult = loadPropertyMetadata()
        List<ConfigPropertyMetadata> metadata = metadataResult.properties
        Map<String, String> groupDescriptions = metadataResult.groupDescriptions
        Map<String, List<ConfigPropertyMetadata>> categories = new LinkedHashMap<String, List<ConfigPropertyMetadata>>()
        for (ConfigPropertyMetadata property : metadata) {
            String category = groupDescriptions.get(property.group) ?: property.group
            if (!categories.containsKey(category)) {
                categories.put(category, new ArrayList<ConfigPropertyMetadata>())
            }
            categories.get(category).add(property)
        }

        Set<String> knownKeys = metadata.collect { ConfigPropertyMetadata property -> property.name }.toSet()
        Map<String, String> environmentProperties = collectEnvironmentProperties(runtimeProperties)
        Map<String, String> otherProperties = new TreeMap<String, String>()
        runtimeProperties.each { String key, String value ->
            if (!knownKeys.contains(key) && !environmentProperties.containsKey(key)) {
                otherProperties.put(key, value)
            }
        }

        reportFile.withWriter('UTF-8') { BufferedWriter writer ->
            writer.writeLine('= Grails Application Configuration Report')
            writer.writeLine(':toc: left')
            writer.writeLine(':toclevels: 2')
            writer.writeLine(':source-highlighter: coderay')
            writer.writeLine('')

            categories.each { String categoryName, List<ConfigPropertyMetadata> categoryProperties ->
                writer.writeLine("== ${categoryName}")
                writer.writeLine('')
                writer.writeLine('[cols="2,5,2", options="header"]')
                writer.writeLine('|===')
                writer.writeLine('| Property | Description | Default')
                writer.writeLine('')

                categoryProperties.each { ConfigPropertyMetadata property ->
                    String key = property.name
                    String description = property.description
                    String defaultValue = formatDefaultValue(property.defaultValue)
                    String resolvedValue
                    if (runtimeProperties.containsKey(key)) {
                        resolvedValue = "`${escapeAsciidoc(runtimeProperties.get(key))}`"
                    }
                    else {
                        resolvedValue = escapeAsciidoc(defaultValue)
                    }
                    writer.writeLine("| `${key}`")
                    writer.writeLine("| ${escapeAsciidoc(description)}")
                    writer.writeLine("| ${resolvedValue}")
                    writer.writeLine('')
                }
                writer.writeLine('|===')
                writer.writeLine('')
            }

            if (!otherProperties.isEmpty()) {
                writer.writeLine('== Other Properties')
                writer.writeLine('')
                writer.writeLine('[cols="2,3", options="header"]')
                writer.writeLine('|===')
                writer.writeLine('| Property | Default')
                writer.writeLine('')
                otherProperties.each { String key, String value ->
                    writer.writeLine("| `${key}`")
                    writer.writeLine("| `${escapeAsciidoc(value)}`")
                    writer.writeLine('')
                }
                writer.writeLine('|===')
            }

            if (!environmentProperties.isEmpty()) {
                if (!otherProperties.isEmpty()) {
                    writer.writeLine('')
                }
                writer.writeLine('== Environment Properties')
                writer.writeLine('')
                writer.writeLine('[cols="2,3", options="header"]')
                writer.writeLine('|===')
                writer.writeLine('| Property | Default')
                writer.writeLine('')
                environmentProperties.each { String key, String value ->
                    writer.writeLine("| `${key}`")
                    writer.writeLine("| `${escapeAsciidoc(value)}`")
                    writer.writeLine('')
                }
                writer.writeLine('|===')
            }
        }
    }

    MetadataResult loadPropertyMetadata() {
        Enumeration<URL> resources = ConfigReportCommand.classLoader.getResources('META-INF/spring-configuration-metadata.json')
        List<ConfigPropertyMetadata> metadata = new ArrayList<ConfigPropertyMetadata>()
        Map<String, String> groupDescriptions = new LinkedHashMap<String, String>()
        JsonSlurper slurper = new JsonSlurper()
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement()
            InputStream stream = resource.openStream()
            Map<String, Object> jsonData
            try {
                jsonData = (Map<String, Object>) slurper.parse(stream)
            }
            finally {
                stream.close()
            }
            if (!(jsonData instanceof Map)) {
                continue
            }
            groupDescriptions.putAll(loadGroupDescriptions(jsonData.get('groups')))
            Object propertiesObject = jsonData.get('properties')
            if (!(propertiesObject instanceof List)) {
                continue
            }
            for (Object propertyObject : (List<Object>) propertiesObject) {
                if (!(propertyObject instanceof Map)) {
                    continue
                }
                Map<String, Object> propertyMap = (Map<String, Object>) propertyObject
                Object nameObject = propertyMap.get('name')
                if (!(nameObject instanceof String)) {
                    continue
                }
                String name = (String) nameObject
                if (!isGrailsProperty(name)) {
                    continue
                }
                String description = propertyMap.get('description') instanceof String ? (String) propertyMap.get('description') : ''
                String type = propertyMap.get('type') instanceof String ? (String) propertyMap.get('type') : 'java.lang.String'
                Object defaultValue = propertyMap.get('defaultValue')
                String group = propertyMap.get('group') instanceof String ? (String) propertyMap.get('group') : resolveGroup(name, groupDescriptions.keySet())
                metadata.add(new ConfigPropertyMetadata(name, type, description, defaultValue, group))
            }
        }
        metadata.sort { ConfigPropertyMetadata left, ConfigPropertyMetadata right -> left.name <=> right.name }
        new MetadataResult(metadata, groupDescriptions)
    }

    Map<String, String> loadGroupDescriptions(Object groupsObject) {
        if (!(groupsObject instanceof List)) {
            return new LinkedHashMap<String, String>()
        }
        Map<String, String> descriptions = new LinkedHashMap<String, String>()
        for (Object groupObject : (List<Object>) groupsObject) {
            if (!(groupObject instanceof Map)) {
                continue
            }
            Map<String, Object> groupMap = (Map<String, Object>) groupObject
            Object nameObject = groupMap.get('name')
            if (!(nameObject instanceof String)) {
                continue
            }
            String name = (String) nameObject
            Object descriptionObject = groupMap.get('description')
            if (descriptionObject instanceof String) {
                descriptions.put(name, (String) descriptionObject)
            }
        }
        descriptions
    }

    Map<String, String> collectEnvironmentProperties(Map<String, String> runtimeProperties) {
        Map<String, String> environmentProperties = new TreeMap<String, String>()
        Set<String> normalizedKeys = new LinkedHashSet<String>()
        for (String envKey : System.getenv().keySet()) {
            String lowerKey = envKey.toLowerCase(Locale.ENGLISH)
            normalizedKeys.add(lowerKey.replace('_', '.'))
            normalizedKeys.add(lowerKey.replace('_', '-'))
        }
        runtimeProperties.each { String key, String value ->
            if (normalizedKeys.contains(key.toLowerCase(Locale.ENGLISH))) {
                environmentProperties.put(key, value)
            }
        }
        environmentProperties
    }

    String resolveGroup(String name, Set<String> groupNames) {
        if (groupNames == null || groupNames.isEmpty()) {
            return fallbackGroup(name)
        }
        String match = groupNames.findAll { String groupName ->
            name == groupName || name.startsWith("${groupName}.")
        }.sort { String left, String right -> right.length() <=> left.length() }
            .find { String groupName -> groupName }
        match ?: fallbackGroup(name)
    }

    String fallbackGroup(String name) {
        int delimiter = name.lastIndexOf('.')
        if (delimiter <= 0) {
            return name
        }
        name.substring(0, delimiter)
    }

    boolean isGrailsProperty(String name) {
        name.startsWith('grails.') || name.startsWith('dataSource.') || name.startsWith('hibernate.')
    }

    String formatDefaultValue(Object defaultValue) {
        if (defaultValue == null) {
            return ''
        }
        if (defaultValue instanceof List) {
            List<Object> values = (List<Object>) defaultValue
            String joined = values.collect { Object item -> "\"${item?.toString()}\"" }.join(', ')
            return "[${joined}]"
        }
        if (defaultValue instanceof Map) {
            Map<Object, Object> mapValue = (Map<Object, Object>) defaultValue
            if (mapValue.isEmpty()) {
                return '{}'
            }
            return mapValue.toString()
        }
        defaultValue.toString()
    }

    /**
     * Escapes special AsciiDoc characters in a value string.
     *
     * @param value the raw value
     * @return the escaped value safe for AsciiDoc table cells
     */
    static String escapeAsciidoc(String value) {
        if (!value) {
            return value
        }
        value.replace('|', '\\|')
    }

    static class ConfigPropertyMetadata {
        final String name
        final String type
        final String description
        final Object defaultValue
        final String group

        ConfigPropertyMetadata(String name, String type, String description, Object defaultValue, String group) {
            this.name = name
            this.type = type
            this.description = description
            this.defaultValue = defaultValue
            this.group = group
        }
    }

    static class MetadataResult {
        final List<ConfigPropertyMetadata> properties
        final Map<String, String> groupDescriptions

        MetadataResult(List<ConfigPropertyMetadata> properties, Map<String, String> groupDescriptions) {
            this.properties = properties
            this.groupDescriptions = groupDescriptions
        }
    }

}
