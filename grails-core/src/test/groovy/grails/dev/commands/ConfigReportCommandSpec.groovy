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

import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.MutablePropertySources

import org.grails.build.parsing.CommandLine
import spock.lang.Specification
import spock.lang.TempDir

class ConfigReportCommandSpec extends Specification {

    @TempDir
    File tempDir

    ConfigReportCommand command

    ConfigurableApplicationContext applicationContext

    ConfigurableEnvironment environment

    MutablePropertySources propertySources

    def setup() {
        propertySources = new MutablePropertySources()
        environment = Mock(ConfigurableEnvironment)
        environment.getPropertySources() >> propertySources
        applicationContext = Mock(ConfigurableApplicationContext)
        applicationContext.getEnvironment() >> environment

        command = new ConfigReportCommand()
        command.applicationContext = applicationContext
    }

    def "command name is derived from class name"() {
        expect:
        command.name == 'config-report'
    }

    def "command has a description"() {
        expect:
        command.description == 'Generates an AsciiDoc report of the application configuration'
    }

    def "handle generates AsciiDoc report file"() {
        given:
        Map<String, Object> props = [
            'grails.profile': 'web',
            'grails.codegen.defaultPackage': 'myapp',
            'server.port': '8080',
            'spring.main.banner-mode': 'off'
        ]
        propertySources.addFirst(new MapPropertySource('test', props))
        props.each { String key, Object value ->
            environment.getProperty(key) >> value.toString()
        }

        ExecutionContext executionContext = new ExecutionContext(Mock(CommandLine))

        when:
        boolean result = command.handle(executionContext)

        then:
        result

        and: "report file is written to the base directory"
        File reportFile = new File(executionContext.baseDir, ConfigReportCommand.DEFAULT_REPORT_FILE)
        reportFile.exists()

        and:
        String content = reportFile.text
        content.contains('= Grails Application Configuration Report')
        content.contains('== grails')
        content.contains('== server')
        content.contains('== spring')
        content.contains('`grails.profile`')
        content.contains('`web`')
        content.contains('`server.port`')
        content.contains('`8080`')

        cleanup:
        reportFile?.delete()
    }

    def "handle returns false when an error occurs"() {
        given:
        ConfigurableApplicationContext failingContext = Mock(ConfigurableApplicationContext)
        failingContext.getEnvironment() >> { throw new RuntimeException('test error') }
        ConfigReportCommand failingCommand = new ConfigReportCommand()
        failingCommand.applicationContext = failingContext
        ExecutionContext executionContext = new ExecutionContext(Mock(CommandLine))

        when:
        boolean result = failingCommand.handle(executionContext)

        then:
        !result
    }

    def "collectProperties gathers from all enumerable property sources"() {
        given:
        Map<String, Object> yamlProps = ['myapp.yaml.greeting': 'Hello']
        Map<String, Object> groovyProps = ['myapp.groovy.name': 'TestApp']
        propertySources.addLast(new MapPropertySource('yaml', yamlProps))
        propertySources.addLast(new MapPropertySource('groovy', groovyProps))
        environment.getProperty('myapp.yaml.greeting') >> 'Hello'
        environment.getProperty('myapp.groovy.name') >> 'TestApp'

        when:
        Map<String, String> result = command.collectProperties(environment)

        then:
        result['myapp.yaml.greeting'] == 'Hello'
        result['myapp.groovy.name'] == 'TestApp'
    }

    def "collectProperties respects property source precedence"() {
        given: 'two sources with the same key, higher-priority source listed first'
        Map<String, Object> overrideProps = ['app.name': 'Override']
        Map<String, Object> defaultProps = ['app.name': 'Default']
        propertySources.addLast(new MapPropertySource('override', overrideProps))
        propertySources.addLast(new MapPropertySource('default', defaultProps))
        environment.getProperty('app.name') >> 'Override'

        when:
        Map<String, String> result = command.collectProperties(environment)

        then: 'the higher-priority value wins'
        result['app.name'] == 'Override'
    }

    def "collectProperties skips properties that resolve to null"() {
        given:
        Map<String, Object> props = ['app.present': 'value', 'app.missing': 'placeholder']
        propertySources.addFirst(new MapPropertySource('test', props))
        environment.getProperty('app.present') >> 'value'
        environment.getProperty('app.missing') >> null

        when:
        Map<String, String> result = command.collectProperties(environment)

        then:
        result.containsKey('app.present')
        !result.containsKey('app.missing')
    }

    def "collectProperties handles resolution errors gracefully"() {
        given:
        Map<String, Object> props = ['app.good': 'value', 'app.bad': '${unresolved}']
        propertySources.addFirst(new MapPropertySource('test', props))
        environment.getProperty('app.good') >> 'value'
        environment.getProperty('app.bad') >> { throw new IllegalArgumentException('unresolved placeholder') }

        when:
        Map<String, String> result = command.collectProperties(environment)

        then: 'the good property is collected and the bad one is skipped'
        result['app.good'] == 'value'
        !result.containsKey('app.bad')
    }

    def "writeReport groups properties by top-level namespace"() {
        given:
        Map<String, String> sorted = new TreeMap<String, String>()
        sorted.put('grails.controllers.defaultScope', 'singleton')
        sorted.put('grails.profile', 'web')
        sorted.put('server.port', '8080')

        File reportFile = new File(tempDir, 'test-report.adoc')

        when:
        command.writeReport(sorted, reportFile)

        then:
        String content = reportFile.text

        and: "report has correct AsciiDoc structure"
        content.startsWith('= Grails Application Configuration Report')
        content.contains(':toc: left')
        content.contains('[cols="2,3", options="header"]')
        content.contains('| Property | Value')

        and: "properties are grouped by namespace"
        content.contains('== grails')
        content.contains('== server')

        and: "grails section appears before server section (alphabetical)"
        content.indexOf('== grails') < content.indexOf('== server')

        and: "properties are listed under correct sections"
        content.contains('`grails.controllers.defaultScope`')
        content.contains('`singleton`')
        content.contains('`server.port`')
        content.contains('`8080`')
    }

    def "writeReport escapes pipe characters in values"() {
        given:
        Map<String, String> sorted = new TreeMap<String, String>()
        sorted.put('test.key', 'value|with|pipes')

        File reportFile = new File(tempDir, 'escape-test.adoc')

        when:
        command.writeReport(sorted, reportFile)

        then:
        String content = reportFile.text
        content.contains('value\\|with\\|pipes')
        !content.contains('value|with|pipes')
    }

    def "writeReport handles empty configuration"() {
        given:
        Map<String, String> sorted = new TreeMap<String, String>()
        File reportFile = new File(tempDir, 'empty-report.adoc')

        when:
        command.writeReport(sorted, reportFile)

        then:
        reportFile.exists()
        String content = reportFile.text
        content.contains('= Grails Application Configuration Report')
        !content.contains('|===')
    }

    def "escapeAsciidoc handles null and empty strings"() {
        expect:
        ConfigReportCommand.escapeAsciidoc(null) == null
        ConfigReportCommand.escapeAsciidoc('') == ''
        ConfigReportCommand.escapeAsciidoc('simple') == 'simple'
        ConfigReportCommand.escapeAsciidoc('a|b') == 'a\\|b'
    }

}
