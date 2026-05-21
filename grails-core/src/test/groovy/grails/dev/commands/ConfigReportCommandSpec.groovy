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
            'spring.main.banner-mode': 'off',
            'my.custom.prop': 'value'
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

        and: "report has correct AsciiDoc structure"
        String content = reportFile.text
        content.contains('= Grails Application Configuration Report')

        and: "known Grails properties appear in their metadata category sections"
        content.contains('`grails.profile`')
        content.contains('`grails.codegen.defaultPackage`')

        and: "unknown runtime properties appear in the Other Properties section"
        content.contains('== Other Properties')
        content.contains('`server.port`')
        content.contains('`8080`')
        content.contains('`spring.main.banner-mode`')
        content.contains('`off`')
        content.contains('`my.custom.prop`')
        content.contains('`value`')

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

    def "writeReport uses 3-column format with metadata categories"() {
        given:
        Map<String, String> runtimeProperties = new TreeMap<String, String>()
        runtimeProperties.put('grails.gorm.autoFlush', 'true')
        runtimeProperties.put('grails.profile', 'web')
        runtimeProperties.put('server.port', '8080')

        File reportFile = new File(tempDir, 'test-report.adoc')

        when:
        command.writeReport(runtimeProperties, reportFile)

        then:
        String content = reportFile.text

        and: "report has correct AsciiDoc header"
        content.startsWith('= Grails Application Configuration Report')
        content.contains(':toc: left')

        and: "metadata categories are used as section headers"
        content.contains('== Core Properties')
        content.contains('== GORM')

        and: "3-column table format is used for known properties"
        content.contains('[cols="2,5,2", options="header"]')
        content.contains('| Property | Description | Default')

        and: "known properties appear with descriptions"
        content.contains('`grails.profile`')
        content.contains('`grails.gorm.autoFlush`')

        and: "runtime values override static defaults for known properties"
        content.contains('`web`')
        content.contains('`true`')

        and: "unknown runtime properties go to Other Properties section"
        content.contains('== Other Properties')
        content.contains('`server.port`')
        content.contains('`8080`')
    }

    def "writeReport shows static defaults when no runtime value exists"() {
        given: "no runtime properties provided"
        Map<String, String> runtimeProperties = new TreeMap<String, String>()
        File reportFile = new File(tempDir, 'defaults-report.adoc')

        when:
        command.writeReport(runtimeProperties, reportFile)

        then:
        String content = reportFile.text

        and: "metadata categories are still present with static defaults"
        content.contains('== Core Properties')
        content.contains('`grails.profile`')
        content.contains('Set by project template')
    }

    def "writeReport runtime values override static defaults"() {
        given:
        Map<String, String> runtimeProperties = new TreeMap<String, String>()
        runtimeProperties.put('grails.profile', 'rest-api')

        File reportFile = new File(tempDir, 'override-report.adoc')

        when:
        command.writeReport(runtimeProperties, reportFile)

        then:
        String content = reportFile.text

        and: "runtime value overrides the static default"
        content.contains('`rest-api`')
    }

    def "writeReport escapes pipe characters in values"() {
        given:
        Map<String, String> runtimeProperties = new TreeMap<String, String>()
        runtimeProperties.put('test.key', 'value|with|pipes')

        File reportFile = new File(tempDir, 'escape-test.adoc')

        when:
        command.writeReport(runtimeProperties, reportFile)

        then:
        String content = reportFile.text
        content.contains('value\\|with\\|pipes')
        !content.contains('value|with|pipes')
    }

    def "writeReport handles empty configuration with no Other Properties"() {
        given:
        Map<String, String> runtimeProperties = new TreeMap<String, String>()
        File reportFile = new File(tempDir, 'empty-report.adoc')

        when:
        command.writeReport(runtimeProperties, reportFile)

        then:
        reportFile.exists()
        String content = reportFile.text
        content.contains('= Grails Application Configuration Report')

        and: "metadata categories still appear from the metadata"
        content.contains('== Core Properties')

        and: "no Other Properties section when no unknown runtime properties"
        !content.contains('== Other Properties')
    }

    def "writeReport puts only unknown runtime properties in Other Properties"() {
        given:
        Map<String, String> runtimeProperties = new TreeMap<String, String>()
        runtimeProperties.put('custom.app.setting', 'myvalue')
        runtimeProperties.put('grails.profile', 'web')

        File reportFile = new File(tempDir, 'other-props-report.adoc')

        when:
        command.writeReport(runtimeProperties, reportFile)

        then:
        String content = reportFile.text

        and: "known property is in its category, not in Other Properties"
        content.contains('== Core Properties')
        content.contains('`grails.profile`')

        and: "unknown property appears in Other Properties"
        content.contains('== Other Properties')
        content.contains('`custom.app.setting`')
        content.contains('`myvalue`')

        and: "Other Properties uses 2-column format"
        int otherIdx = content.indexOf('== Other Properties')
        String otherSection = content.substring(otherIdx)
        otherSection.contains('[cols="2,3", options="header"]')
        otherSection.contains('| Property | Default')
    }

    def "writeReport moves environment-derived properties to environment section"() {
        given:
        Map<String, String> runtimeProperties = new TreeMap<String, String>()
        runtimeProperties.put('my.custom.value', 'custom')
        runtimeProperties.put('grails.profile', 'web')
        File reportFile = new File(tempDir, 'env-report.adoc')

        and:
        String envKey = 'MY_CUSTOM_VALUE'
        String originalValue = System.getenv(envKey)
        setEnvVar(envKey, 'from-env')

        when:
        command.writeReport(runtimeProperties, reportFile)

        then:
        String content = reportFile.text
        int envIndex = content.indexOf('== Environment Properties')
        envIndex > -1
        String envSection = content.substring(envIndex)
        envSection.contains('`my.custom.value`')
        envSection.contains('`custom`')

        and:
        int otherIndex = content.indexOf('== Other Properties')
        String otherSection = otherIndex > -1 ? content.substring(otherIndex, envIndex) : ''
        !otherSection.contains('`my.custom.value`')

        cleanup:
        if (originalValue != null) {
            setEnvVar(envKey, originalValue)
        }
        else {
            clearEnvVar(envKey)
        }
    }

    def "loadPropertyMetadata returns properties from classpath JSON metadata"() {
        when:
        ConfigReportCommand.MetadataResult metadataResult = command.loadPropertyMetadata()

        then: "metadata is loaded from spring-configuration-metadata.json on the classpath"
        !metadataResult.properties.isEmpty()
        metadataResult.properties.find { ConfigReportCommand.ConfigPropertyMetadata property -> property.name == 'grails.profile' }

        and: "each entry has the expected fields"
        ConfigReportCommand.ConfigPropertyMetadata profileEntry = metadataResult.properties.find { ConfigReportCommand.ConfigPropertyMetadata property -> property.name == 'grails.profile' }
        profileEntry.name == 'grails.profile'
        profileEntry.description != null
        profileEntry.description.length() > 0
        metadataResult.groupDescriptions.get('grails') == 'Core Properties'
    }

    def "escapeAsciidoc handles null and empty strings"() {
        expect:
        ConfigReportCommand.escapeAsciidoc(null) == null
        ConfigReportCommand.escapeAsciidoc('') == ''
        ConfigReportCommand.escapeAsciidoc('simple') == 'simple'
        ConfigReportCommand.escapeAsciidoc('a|b') == 'a\\|b'
    }

    private void setEnvVar(String key, String value) {
        setEnvironmentVariable(key, value)
    }

    private void clearEnvVar(String key) {
        setEnvironmentVariable(key, null)
    }

    private void setEnvironmentVariable(String key, String value) {
        Map<String, String> env = System.getenv()
        Class<?> envClass = env.getClass()
        try {
            java.lang.reflect.Field field = envClass.getDeclaredField('m')
            field.setAccessible(true)
            Map<String, String> writable = (Map<String, String>) field.get(env)
            if (value == null) {
                writable.remove(key)
            }
            else {
                writable.put(key, value)
            }
        }
        catch (NoSuchFieldException ignored) {
            java.lang.reflect.Field field = envClass.getDeclaredField('delegate')
            field.setAccessible(true)
            Map<String, String> writable = (Map<String, String>) field.get(env)
            if (value == null) {
                writable.remove(key)
            }
            else {
                writable.put(key, value)
            }
        }
    }

}
