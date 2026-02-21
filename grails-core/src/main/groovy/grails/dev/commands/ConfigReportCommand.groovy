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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.yaml.snakeyaml.Yaml

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
        Map<String, Map<String, String>> metadata = loadPropertyMetadata()
        Map<String, List<Map<String, String>>> categories = new LinkedHashMap<String, List<Map<String, String>>>()
        for (Map.Entry<String, Map<String, String>> entry : metadata.entrySet()) {
            Map<String, String> property = entry.value
            String category = property.get('category')
            if (!categories.containsKey(category)) {
                categories.put(category, new ArrayList<Map<String, String>>())
            }
            categories.get(category).add(property)
        }

        Set<String> knownKeys = metadata.keySet()
        Map<String, String> otherProperties = new TreeMap<String, String>()
        runtimeProperties.each { String key, String value ->
            if (!knownKeys.contains(key)) {
                otherProperties.put(key, value)
            }
        }

        reportFile.withWriter('UTF-8') { BufferedWriter writer ->
            writer.writeLine('= Grails Application Configuration Report')
            writer.writeLine(':toc: left')
            writer.writeLine(':toclevels: 2')
            writer.writeLine(':source-highlighter: coderay')
            writer.writeLine('')

            categories.each { String categoryName, List<Map<String, String>> categoryProperties ->
                writer.writeLine("== ${categoryName}")
                writer.writeLine('')
                writer.writeLine('[cols="2,5,2", options="header"]')
                writer.writeLine('|===')
                writer.writeLine('| Property | Description | Default')
                writer.writeLine('')

                categoryProperties.each { Map<String, String> property ->
                    String key = property.get('key')
                    String description = property.get('description')
                    String defaultValue = property.get('default')
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
        }
    }

    Map<String, Map<String, String>> loadPropertyMetadata() {
        InputStream stream = ConfigReportCommand.classLoader.getResourceAsStream('META-INF/grails/config-properties.yml')
        if (stream == null) {
            return new LinkedHashMap<String, Map<String, String>>()
        }
        Map<String, Map<String, String>> metadata = new LinkedHashMap<String, Map<String, String>>()
        Map<String, Object> yamlData
        try {
            yamlData = (Map<String, Object>) new Yaml().load(stream)
        }
        finally {
            stream.close()
        }
        Object categories = yamlData.get('categories')
        if (!(categories instanceof List)) {
            return metadata
        }
        for (Object categoryObject : (List<Object>) categories) {
            if (!(categoryObject instanceof Map)) {
                continue
            }
            Map<String, Object> categoryMap = (Map<String, Object>) categoryObject
            Object categoryNameObject = categoryMap.get('name')
            if (!(categoryNameObject instanceof String)) {
                continue
            }
            String categoryName = (String) categoryNameObject
            Object properties = categoryMap.get('properties')
            if (!(properties instanceof List)) {
                continue
            }
            for (Object propertyObject : (List<Object>) properties) {
                if (!(propertyObject instanceof Map)) {
                    continue
                }
                Map<String, Object> propertyMap = (Map<String, Object>) propertyObject
                Object keyObject = propertyMap.get('key')
                Object descriptionObject = propertyMap.get('description')
                Object defaultObject = propertyMap.get('default')
                if (!(keyObject instanceof String)) {
                    continue
                }
                String key = (String) keyObject
                String description = descriptionObject instanceof String ? (String) descriptionObject : ''
                String defaultValue = defaultObject instanceof String ? (String) defaultObject : ''
                Map<String, String> entry = new LinkedHashMap<String, String>()
                entry.put('key', key)
                entry.put('description', description)
                entry.put('default', defaultValue)
                entry.put('category', categoryName)
                metadata.put(key, entry)
            }
        }
        metadata
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

}
