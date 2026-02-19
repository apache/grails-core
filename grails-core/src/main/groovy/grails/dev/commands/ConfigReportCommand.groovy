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
    void writeReport(Map<String, String> sorted, File reportFile) {
        reportFile.withWriter('UTF-8') { BufferedWriter writer ->
            writer.writeLine('= Grails Application Configuration Report')
            writer.writeLine(':toc: left')
            writer.writeLine(':toclevels: 2')
            writer.writeLine(':source-highlighter: coderay')
            writer.writeLine('')

            String currentSection = ''
            sorted.each { String key, String value ->
                String section = key.contains('.') ? key.substring(0, key.indexOf('.')) : key
                if (section != currentSection) {
                    if (currentSection) {
                        writer.writeLine('|===')
                        writer.writeLine('')
                    }
                    currentSection = section
                    writer.writeLine("== ${section}")
                    writer.writeLine('')
                    writer.writeLine('[cols="2,3", options="header"]')
                    writer.writeLine('|===')
                    writer.writeLine('| Property | Value')
                    writer.writeLine('')
                }
                writer.writeLine("| `${key}`")
                writer.writeLine("| `${escapeAsciidoc(value)}`")
                writer.writeLine('')
            }
            if (currentSection) {
                writer.writeLine('|===')
            }
        }
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
