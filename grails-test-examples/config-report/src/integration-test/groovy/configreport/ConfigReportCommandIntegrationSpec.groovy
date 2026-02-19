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

import grails.dev.commands.ConfigReportCommand
import grails.dev.commands.ExecutionContext
import grails.testing.mixin.integration.Integration
import org.grails.build.parsing.CommandLine
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.TempDir

/**
 * Integration tests for {@link ConfigReportCommand} that verify the command
 * correctly reports configuration from multiple sources:
 * <ul>
 *   <li>{@code application.yml} - YAML-based configuration</li>
 *   <li>{@code application.groovy} - Groovy-based configuration</li>
 *   <li>{@code @ConfigurationProperties} - Type-safe configuration beans</li>
 * </ul>
 */
@Integration
@Narrative('Verifies that ConfigReportCommand generates an AsciiDoc report containing properties from application.yml, application.groovy, and @ConfigurationProperties sources')
class ConfigReportCommandIntegrationSpec extends Specification {

    @Autowired
    ConfigurableApplicationContext applicationContext

    @Autowired
    AppProperties appProperties

    @TempDir
    File tempDir

    private ConfigReportCommand createCommand() {
        ConfigReportCommand command = new ConfigReportCommand()
        command.applicationContext = applicationContext
        return command
    }

    private File executeCommand(ConfigReportCommand command) {
        ExecutionContext executionContext = new ExecutionContext(Mock(CommandLine))
        File reportFile = new File(executionContext.baseDir, ConfigReportCommand.DEFAULT_REPORT_FILE)
        command.handle(executionContext)
        return reportFile
    }

    def "ConfigReportCommand generates a report file"() {
        given: 'a ConfigReportCommand wired to the live application context'
        ConfigReportCommand command = createCommand()

        and: 'an execution context pointing to a temporary directory'
        ExecutionContext executionContext = new ExecutionContext(Mock(CommandLine))
        File reportFile = new File(executionContext.baseDir, ConfigReportCommand.DEFAULT_REPORT_FILE)

        when: 'the command is executed'
        boolean result = command.handle(executionContext)

        then: 'the command succeeds'
        result

        and: 'the report file is created'
        reportFile.exists()
        reportFile.length() > 0

        and: 'the report has valid AsciiDoc structure'
        String content = reportFile.text
        content.startsWith('= Grails Application Configuration Report')
        content.contains(':toc: left')
        content.contains('[cols="2,3", options="header"]')
        content.contains('| Property | Value')

        cleanup:
        reportFile?.delete()
    }

    def "report contains properties from application.yml"() {
        given: 'a ConfigReportCommand wired to the live application context'
        ConfigReportCommand command = createCommand()
        ExecutionContext executionContext = new ExecutionContext(Mock(CommandLine))
        File reportFile = new File(executionContext.baseDir, ConfigReportCommand.DEFAULT_REPORT_FILE)

        when: 'the command is executed'
        command.handle(executionContext)
        String content = reportFile.text

        then: 'YAML-defined properties are present in the report'
        content.contains('`myapp.yaml.greeting`')
        content.contains('`Hello from YAML`')

        and: 'YAML numeric properties are present'
        content.contains('`myapp.yaml.maxRetries`')
        content.contains('`5`')

        and: 'YAML nested properties are present'
        content.contains('`myapp.yaml.feature.enabled`')
        content.contains('`true`')
        content.contains('`myapp.yaml.feature.timeout`')
        content.contains('`30000`')

        and: 'standard Grails YAML properties are present'
        content.contains('`grails.profile`')
        content.contains('`web`')

        cleanup:
        reportFile?.delete()
    }

    def "report contains properties from application.groovy"() {
        given: 'a ConfigReportCommand wired to the live application context'
        ConfigReportCommand command = createCommand()
        ExecutionContext executionContext = new ExecutionContext(Mock(CommandLine))
        File reportFile = new File(executionContext.baseDir, ConfigReportCommand.DEFAULT_REPORT_FILE)

        when: 'the command is executed'
        command.handle(executionContext)
        String content = reportFile.text

        then: 'Groovy config properties are present in the report'
        content.contains('`myapp.groovy.appName`')
        content.contains('`Config Report Test App`')

        and: 'Groovy config version property is present'
        content.contains('`myapp.groovy.version`')
        content.contains('`1.2.3`')

        cleanup:
        reportFile?.delete()
    }

    def "report escapes pipe characters from application.groovy values"() {
        given: 'a ConfigReportCommand wired to the live application context'
        ConfigReportCommand command = createCommand()
        ExecutionContext executionContext = new ExecutionContext(Mock(CommandLine))
        File reportFile = new File(executionContext.baseDir, ConfigReportCommand.DEFAULT_REPORT_FILE)

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

    def "report contains properties bound via @ConfigurationProperties"() {
        given: 'a ConfigReportCommand wired to the live application context'
        ConfigReportCommand command = createCommand()
        ExecutionContext executionContext = new ExecutionContext(Mock(CommandLine))
        File reportFile = new File(executionContext.baseDir, ConfigReportCommand.DEFAULT_REPORT_FILE)

        when: 'the command is executed'
        command.handle(executionContext)
        String content = reportFile.text

        then: 'the @ConfigurationProperties bean was correctly populated'
        appProperties.name == 'Configured App'
        appProperties.pageSize == 50
        appProperties.debugEnabled == true

        and: 'the typed properties appear in the config report'
        content.contains('`myapp.typed.name`')
        content.contains('`Configured App`')
        content.contains('`myapp.typed.pageSize`')
        content.contains('`50`')
        content.contains('`myapp.typed.debugEnabled`')
        content.contains('`true`')

        cleanup:
        reportFile?.delete()
    }

    def "report groups properties by top-level namespace"() {
        given: 'a ConfigReportCommand wired to the live application context'
        ConfigReportCommand command = createCommand()
        ExecutionContext executionContext = new ExecutionContext(Mock(CommandLine))
        File reportFile = new File(executionContext.baseDir, ConfigReportCommand.DEFAULT_REPORT_FILE)

        when: 'the command is executed'
        command.handle(executionContext)
        String content = reportFile.text

        then: 'properties are organized into namespace sections'
        content.contains('== grails')
        content.contains('== myapp')
        content.contains('== dataSource')

        and: 'sections are in alphabetical order'
        content.indexOf('== dataSource') < content.indexOf('== grails')
        content.indexOf('== grails') < content.indexOf('== myapp')

        cleanup:
        reportFile?.delete()
    }

    def "report contains properties from all three config sources simultaneously"() {
        given: 'a ConfigReportCommand wired to the live application context'
        ConfigReportCommand command = createCommand()
        ExecutionContext executionContext = new ExecutionContext(Mock(CommandLine))
        File reportFile = new File(executionContext.baseDir, ConfigReportCommand.DEFAULT_REPORT_FILE)

        when: 'the command is executed'
        command.handle(executionContext)
        String content = reportFile.text

        then: 'YAML properties are present'
        content.contains('`myapp.yaml.greeting`')

        and: 'Groovy properties are present'
        content.contains('`myapp.groovy.appName`')

        and: 'typed @ConfigurationProperties are present'
        content.contains('`myapp.typed.name`')

        and: 'all properties are in the same myapp section'
        int myappSectionIndex = content.indexOf('== myapp')
        myappSectionIndex >= 0

        and: 'each table row has the correct AsciiDoc format'
        content.contains('| `myapp.yaml.greeting`')
        content.contains('| `Hello from YAML`')
        content.contains('| `myapp.groovy.appName`')
        content.contains('| `Config Report Test App`')
        content.contains('| `myapp.typed.name`')
        content.contains('| `Configured App`')

        cleanup:
        reportFile?.delete()
    }

}
