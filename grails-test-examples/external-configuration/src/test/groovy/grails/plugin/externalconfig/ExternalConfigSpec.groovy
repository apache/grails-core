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

package grails.plugin.externalconfig

import grails.config.external.ExternalConfigRunListener
import grails.web.servlet.context.support.GrailsEnvironment
import org.grails.config.NavigableMap
import org.grails.config.NavigableMapPropertySource
import org.grails.testing.GrailsUnitTest
import org.springframework.core.env.ConfigurableEnvironment
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

class ExternalConfigSpec extends Specification implements GrailsUnitTest {

    ConfigurableEnvironment environment = new GrailsEnvironment(grailsApplication)
    ExternalConfigRunListener listener = new ExternalConfigRunListener(null, null)

    def cleanupSpec() {
        cleanupGrailsApplication()
    }

    def "when getting config without grails.config.location set, the config does not change"() {
        when:
        listener.environmentPrepared(null, environment)

        then:
        environment.properties == old(environment.properties)
    }

    def "when getting config with configs does not exist, noting changes"() {
        given:
        addToEnvironment('grails.config.locations': ['boguslocation', '/otherboguslocation', 'classpath:bogusclasspath', '~/bogus', 'file://bogusfile', 'http://bogus.server'])

        when:
        listener.environmentPrepared(null, environment)

        then:
        environment.properties == old(environment.properties)
    }

    def "getting configuration from environment specific location"() {
        given:
        addToEnvironment('environments.test.grails.config.locations': ["classpath:externalConfig.yml"])

        when:
        listener.environmentPrepared(null, environment)

        then:
        getConfigProperty("yml.config") == 'yml-expected-value'
    }

    def "when getting config with config class, expect the config to be loaded"() {
        given:
        addToEnvironment('grails.config.locations': [ConfigWithoutEnvironmentBlock])

        when:
        listener.environmentPrepared(null, environment)

        then:
        getConfigProperty('test.external.config') == 'expected-value'
    }

    def "when getting config with config class and environment block, expect the config to be loaded"() {
        given:
        addToEnvironment('grails.config.locations': [ConfigWithEnvironmentBlock])

        when:
        listener.environmentPrepared(null, environment)

        then:
        getConfigProperty('test.external.config') == 'expected-value-test'
    }

    def "when getting config with config class that has a canonical config, expect the config to be loaded"() {
        given:
        addToEnvironment(
                'global.config': 'global',
                'grails.config.locations': [ConfigWithCanonicalParameter])


        when:
        listener.environmentPrepared(null, environment)

        then:
        getConfigProperty('test.external.config') == 'global-value'
    }

    def "when getting config with config class that has a two levels of canonical config, expect the config to be loaded"() {
        given:
        addToEnvironment(
                'global.config': 'global',
                'grails.config.locations': [ConfigWithCanonicalParameter, ConfigWithSecondLevelCanonicalParameter])


        when:
        listener.environmentPrepared(null, environment)

        then:
        getConfigProperty('test.external.config') == 'global-value'
        getConfigProperty('second.external.config') == 'value-of-global-value'
    }

    def "when getting config with file in user.home"() {
        given: "The home directory of the user"
        def dir = new File("${System.getProperty('user.home')}/.grails")
        dir.mkdirs()

        and: "a new external configuration file"
        def file = new File(dir, 'external-config-temp-config.groovy')
        file.text = """\
            config.value = 'expected-value'
            nested { config { value = 'nested-value' } }
            """.stripIndent()

        and:
        addToEnvironment('grails.config.locations': ['~/.grails/external-config-temp-config.groovy'])

        when:
        listener.environmentPrepared(null, environment)

        then:
        getConfigProperty('config.value') == 'expected-value'
        getConfigProperty('nested.config.value') == 'nested-value'

        cleanup:
        file.delete()
    }

    def "when getting config with file in system property user.home"() {
        given: "The home directory of the user"
        def dir = new File("${System.getProperty('user.home')}/.grails")
        dir.mkdirs()

        and: "a new external configuration file"
        def file = new File(dir, 'external-config-temp-config2.groovy')
        file.text = """\
            config.value = 'expected-value'
            nested { config { value = 'nested-value' } }
            """.stripIndent()

        and:
        addToEnvironment('grails.config.locations': ['file:${user.home}/.grails/external-config-temp-config2.groovy'])

        when:
        listener.environmentPrepared(null, environment)

        then:
        getConfigProperty('config.value') == 'expected-value'
        getConfigProperty('nested.config.value') == 'nested-value'

        cleanup:
        file.delete()
    }

    def "when getting config with file in specific folder"() {
        given:
        def file = File.createTempFile("other-external-config-temp-config", '.groovy')
        file.text = """\
            config.value = 'expected-value'
            nested { config { value = 'nested-value' } }
            """.stripIndent()

        and:
        addToEnvironment('grails.config.locations': ["file:${file.absolutePath}"])

        when:
        listener.environmentPrepared(null, environment)

        then:
        getConfigProperty('config.value') == 'expected-value'
        getConfigProperty('nested.config.value') == 'nested-value'

        cleanup:
        file.delete()
    }

    @Unroll("when getting #configExtension config with file in classpath")
    def "when getting config with file in classpath"() {
        given:
        addToEnvironment('grails.config.locations': ["classpath:/externalConfig.${configExtension}"])

        when:
        listener.environmentPrepared(null, environment)

        then:
        getConfigProperty("${configExtension}.config") == expectedValue

        where:
        configExtension | expectedValue
        'yml'           | 'yml-expected-value'
        'properties'    | 'properties-expected-value'
        'groovy'        | 'groovy-expected-value'
    }

    @Unroll("when getting referenced #configExtension config with file in classpath")
    def "when getting referenced config with file in classpath"() {
        given:
        addToEnvironment(
                'global.config': 'global-value',
                'grails.config.locations': ["classpath:/externalConfigWithReferencedValue.${configExtension}"])

        when:
        listener.environmentPrepared(null, environment)

        then:
        getConfigProperty("external.config") == expectedValue
        getConfigProperty("external.javaHome") == 'test-home-value'

        where:
        configExtension | expectedValue
        'yml'           | 'yml-global-value'
        'properties'    | 'properties-global-value'
        'groovy'        | 'groovy-global-value'
    }


    def "when getting yml config with file in classpath and with environments block "() {
        given:
        addToEnvironment('grails.config.locations': ['classpath:/externalConfigEnvironments.yml'])

        when:
        listener.environmentPrepared(null, environment)

        then:
        getConfigProperty('yml.config') == 'expected-value-test'
    }

    @Issue('https://github.com/sbglasius/external-config/issues/24')
    def "when getting config with wildcard files from tmp"() {
        given: "Three files in tmp, where two matches the pattern"
        def tmp = new File(System.getProperty('java.io.tmpdir'))
        def file1 = new File(tmp, "file-a_name-config.groovy")
        def file2 = new File(tmp, "file-b_name-config.groovy")
        def file3 = new File(tmp, "not-a-match-file-c_name-config.groovy")
        file1.text = "config.value1 = 'from-a'"
        file2.text = "config.value2 = 'from-b'"
        file3.text = "config.value3 = 'from-c'"

        and: "Matching files in tmp"
        addToEnvironment('grails.config.locations': ["file:${tmp}/file-*-config.groovy"])

        when:
        listener.environmentPrepared(null, environment)

        then: "Two values are set from two config files"
        getConfigProperty('config.value1') == 'from-a'
        getConfigProperty('config.value2') == 'from-b'

        and: "Value in file3 is never read"
        !getConfigProperty('config.value3')

        cleanup:
        [file1, file2, file3]*.delete()
    }

    @Issue('https://github.com/sbglasius/external-config/issues/24')
    def "when getting config with wildcard files from user home"() {
        given: "Three files in home, where two matches the pattern"
        def home = new File(System.getProperty('user.home'))
        def file1 = new File(home, "file-a_name-config.groovy")
        def file2 = new File(home, "file-b_name-config.groovy")
        def file3 = new File(home, "not-a-match-file-c_name-config.groovy")
        file1.text = "config.value1 = 'from-a'"
        file2.text = "config.value2 = 'from-b'"
        file3.text = "config.value3 = 'from-c'"

        and: "a pattern from user home"
        addToEnvironment('grails.config.locations': ["~/file-*-config.groovy"])

        when:
        listener.environmentPrepared(null, environment)

        then: "Two values are set from two config files"
        getConfigProperty('config.value1') == 'from-a'
        getConfigProperty('config.value2') == 'from-b'

        and: "Value in file3 is never read"
        !getConfigProperty('config.value3')

        cleanup:
        [file1, file2, file3]*.delete()
    }

    def "getting configuration from yml with multiple documents"() {
        given:
        addToEnvironment('environments.test.grails.config.locations': ["classpath:/externalConfigMultipleDocs.yml"])

        when:
        listener.environmentPrepared(null, environment)

        then:
        getConfigProperty("yml.config") == 'yml-expected-value'
        getConfigProperty("yml.second") == 'yml-second-value'
    }


    private void addToEnvironment(Map properties = [:]) {
        NavigableMap navigableMap = new NavigableMap()
        navigableMap.merge(properties, true)

        environment.propertySources.addFirst(new NavigableMapPropertySource("Basic config", navigableMap))
    }

    private String getConfigProperty(String key) {
        environment.getProperty(key)
    }
}
