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
package configreport

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ConfigurableApplicationContext

import org.apache.grails.core.cli.ConfigReportTool
import org.apache.grails.core.cli.ExecutionContext
import grails.testing.mixin.integration.Integration
import org.grails.build.parsing.CommandLine
import spock.lang.Narrative
import spock.lang.Specification


/**
 * Integration tests for {@link ConfigReportTool} that verify the command
 * correctly reports configuration from multiple sources:
 * <ul>
 *   <li>{@code application.yml} - YAML-based configuration</li>
 *   <li>{@code application.groovy} - Groovy-based configuration</li>
 *   <li>{@code @ConfigurationProperties} - Type-safe configuration beans</li>
 * </ul>
 *
 * <p>The hybrid report uses curated property metadata (from {@code spring-configuration-metadata.json})
 * to produce a 3-column AsciiDoc table (Property | Description | Default) for known
 * Grails properties, with runtime values overriding static defaults. Properties not
 * found in the metadata appear in a separate "Other Properties" section.
 */
@Integration
@Narrative('Verifies that ConfigReportTool generates a hybrid AsciiDoc report merging static property metadata with runtime-collected values')
class ConfigReportToolIntegrationSpec extends Specification {

    @Autowired
    ConfigurableApplicationContext applicationContext

    @Autowired
    AppProperties appProperties


    private ConfigReportTool createCommand() {
        ConfigReportTool command = new ConfigReportTool()
        command.applicationContext = applicationContext
        return command
    }

    def "ConfigReportTool generates a report file with hybrid format"() {
        given: 'a ConfigReportTool wired to the live application context'
        ConfigReportTool command = createCommand()

        and: 'an execution context pointing to a temporary directory'
        ExecutionContext executionContext = new ExecutionContext(Mock(CommandLine))
        File reportFile = new File(executionContext.baseDir, ConfigReportTool.DEFAULT_REPORT_FILE)

        when: 'the command is executed'
        boolean result = command.handle(executionContext)

        then: 'the command succeeds'
        result

        and: 'the report file is created'
        reportFile.exists()
        reportFile.length() > 0

        and: 'the report has valid AsciiDoc structure with 3-column format'
        String content = reportFile.text
        content.startsWith('= Grails Application Configuration Report')
        content.contains(':toc: left')
        content.contains('[cols="2,5,2", options="header"]')
        content.contains('| Property | Description | Default')

        cleanup:
        reportFile?.delete()
    }

    def "report shows known Grails properties in metadata categories"() {
        given: 'a ConfigReportTool wired to the live application context'
        ConfigReportTool command = createCommand()
        ExecutionContext executionContext = new ExecutionContext(Mock(CommandLine))
        File reportFile = new File(executionContext.baseDir, ConfigReportTool.DEFAULT_REPORT_FILE)

        when: 'the command is executed'
        command.handle(executionContext)
        String content = reportFile.text

        then: 'known metadata categories are present as section headers'
        content.contains('== Core Properties')
        content.contains('== Web & Controllers')
        content.contains('== DataSource')

        and: 'grails.codegen.defaultPackage appears in the Core Properties section with its description'
        content.contains('`grails.codegen.defaultPackage`')

        and: 'runtime value overrides the static default for grails.codegen.defaultPackage'
        content.contains('`configreport`')

        cleanup:
        reportFile?.delete()
    }

    def "report puts custom application properties in Other Properties section"() {
        given: 'a ConfigReportTool wired to the live application context'
        ConfigReportTool command = createCommand()
        ExecutionContext executionContext = new ExecutionContext(Mock(CommandLine))
        File reportFile = new File(executionContext.baseDir, ConfigReportTool.DEFAULT_REPORT_FILE)

        when: 'the command is executed'
        command.handle(executionContext)
        String content = reportFile.text

        then: 'YAML-defined custom properties appear in Other Properties'
        content.contains('== Other Properties')
        content.contains('`myapp.yaml.greeting`')
        content.contains('`Hello from YAML`')

        and: 'YAML numeric properties are in Other Properties'
        content.contains('`myapp.yaml.maxRetries`')
        content.contains('`5`')

        and: 'YAML nested properties are in Other Properties'
        content.contains('`myapp.yaml.feature.enabled`')
        content.contains('`myapp.yaml.feature.timeout`')

        cleanup:
        reportFile?.delete()
    }

    def "report contains properties from application.groovy in Other Properties"() {
        given: 'a ConfigReportTool wired to the live application context'
        ConfigReportTool command = createCommand()
        ExecutionContext executionContext = new ExecutionContext(Mock(CommandLine))
        File reportFile = new File(executionContext.baseDir, ConfigReportTool.DEFAULT_REPORT_FILE)

        when: 'the command is executed'
        command.handle(executionContext)
        String content = reportFile.text

        then: 'Groovy config properties are present in Other Properties'
        content.contains('`myapp.groovy.appName`')
        content.contains('`Config Report Test App`')

        and: 'Groovy config version property is present'
        content.contains('`myapp.groovy.version`')
        content.contains('`1.2.3`')

        cleanup:
        reportFile?.delete()
    }

    def "report escapes pipe characters from application.groovy values"() {
        given: 'a ConfigReportTool wired to the live application context'
        ConfigReportTool command = createCommand()
        ExecutionContext executionContext = new ExecutionContext(Mock(CommandLine))
        File reportFile = new File(executionContext.baseDir, ConfigReportTool.DEFAULT_REPORT_FILE)

        when: 'the command is executed'
        command.handle(executionContext)
        String content = reportFile.text

        then: 'pipe characters are escaped for valid AsciiDoc'
        content.contains('`myapp.groovy.delimitedValue`')
        content.contains('value1\\|value2\\|value3')
        !content.contains('value1|value2|value3')

        cleanup:
        reportFile?.delete()
    }

    def "report contains properties bound via @ConfigurationProperties in Other Properties"() {
        given: 'a ConfigReportTool wired to the live application context'
        ConfigReportTool command = createCommand()
        ExecutionContext executionContext = new ExecutionContext(Mock(CommandLine))
        File reportFile = new File(executionContext.baseDir, ConfigReportTool.DEFAULT_REPORT_FILE)

        when: 'the command is executed'
        command.handle(executionContext)
        String content = reportFile.text

        then: 'the @ConfigurationProperties bean was correctly populated'
        appProperties.name == 'Configured App'
        appProperties.pageSize == 50
        appProperties.debugEnabled == true

        and: 'the typed properties appear in Other Properties'
        content.contains('`myapp.typed.name`')
        content.contains('`Configured App`')
        content.contains('`myapp.typed.pageSize`')
        content.contains('`50`')
        content.contains('`myapp.typed.debugEnabled`')
        content.contains('`true`')

        cleanup:
        reportFile?.delete()
    }

    def "report separates known metadata properties from custom properties"() {
        given: 'a ConfigReportTool wired to the live application context'
        ConfigReportTool command = createCommand()
        ExecutionContext executionContext = new ExecutionContext(Mock(CommandLine))
        File reportFile = new File(executionContext.baseDir, ConfigReportTool.DEFAULT_REPORT_FILE)

        when: 'the command is executed'
        command.handle(executionContext)
        String content = reportFile.text

        then: 'known Grails properties appear in categorized sections before Other Properties'
        int coreIdx = content.indexOf('== Core Properties')
        int otherIdx = content.indexOf('== Other Properties')
        coreIdx >= 0
        otherIdx >= 0
        coreIdx < otherIdx

        and: 'grails.codegen.defaultPackage is in the Core Properties section (not Other Properties)'
        String otherSection = content.substring(otherIdx)
        !otherSection.contains('`grails.codegen.defaultPackage`')

        and: 'custom myapp properties are in Other Properties (not in categorized sections)'
        String beforeOther = content.substring(0, otherIdx)
        !beforeOther.contains('`myapp.yaml.greeting`')
        !beforeOther.contains('`myapp.groovy.appName`')

        cleanup:
        reportFile?.delete()
    }

    def "report contains properties from all three config sources"() {
        given: 'a ConfigReportTool wired to the live application context'
        ConfigReportTool command = createCommand()
        ExecutionContext executionContext = new ExecutionContext(Mock(CommandLine))
        File reportFile = new File(executionContext.baseDir, ConfigReportTool.DEFAULT_REPORT_FILE)

        when: 'the command is executed'
        command.handle(executionContext)
        String content = reportFile.text

        then: 'YAML properties are present'
        content.contains('`myapp.yaml.greeting`')

        and: 'Groovy properties are present'
        content.contains('`myapp.groovy.appName`')

        and: 'typed @ConfigurationProperties are present'
        content.contains('`myapp.typed.name`')

        and: 'Other Properties section uses 2-column format'
        int otherIdx = content.indexOf('== Other Properties')
        String otherSection = content.substring(otherIdx)
        otherSection.contains('[cols="2,3", options="header"]')
        otherSection.contains('| Property | Default')

        cleanup:
        reportFile?.delete()
    }

}
