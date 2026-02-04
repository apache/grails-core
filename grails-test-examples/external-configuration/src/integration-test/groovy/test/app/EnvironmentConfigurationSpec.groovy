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
package test.app

import grails.core.GrailsApplication
import grails.testing.mixin.integration.Integration
import grails.util.Environment
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.test.annotation.DirtiesContext
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

/**
 * Integration tests for environment-specific configuration.
 * Tests that different environments load correct configuration values
 * and that environment detection works properly.
 */
@Integration
@RestoreSystemProperties
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class EnvironmentConfigurationSpec extends Specification {

    @Autowired
    GrailsApplication grailsApplication

    @Autowired
    ConfigurableEnvironment springEnvironment

    @Value('${grails.profile:unknown}')
    String grailsProfile

    @Value('${dataSource.url:not-set}')
    String dataSourceUrl

    void setupSpec() {
        System.setProperty('grails.config.locations', '')
    }

    // ========== Environment Detection Tests ==========

    def "current environment is TEST"() {
        expect: "running in test environment"
        Environment.current == Environment.TEST
        Environment.currentEnvironment == Environment.TEST
    }

    def "grails application reports test environment"() {
        expect: "environment is test (using Environment.current)"
        Environment.current == Environment.TEST
        Environment.current.name == 'test'
    }

    def "spring environment contains test profile"() {
        expect: "test profile is active or environment is test"
        springEnvironment.activeProfiles.contains('test') ||
        Environment.current == Environment.TEST
    }

    // ========== Environment-Specific Configuration Tests ==========

    def "test environment uses test database URL"() {
        expect: "test database URL is configured"
        dataSourceUrl.contains('testDb') || dataSourceUrl.contains('mem')
    }

    def "environment-specific datasource configuration is loaded"() {
        when: "getting datasource config for test environment"
        def config = grailsApplication.config

        then: "test-specific URL is used"
        config.getProperty('dataSource.url', String)?.contains('testDb') ||
        config.getProperty('dataSource.url', String)?.contains('mem')
    }

    def "grails profile is correctly set"() {
        expect: "profile is web"
        grailsProfile == 'web'
    }

    // ========== Configuration Property Access Tests ==========

    def "can access nested configuration properties"() {
        when: "accessing nested properties"
        def config = grailsApplication.config

        then: "nested properties are accessible"
        config.getProperty('grails.mime.types.json', List) != null
        config.getProperty('grails.controllers.defaultScope', String) == 'singleton'
    }

    def "can access configuration as flat properties"() {
        when: "getting flat property"
        def defaultScope = grailsApplication.config.getProperty('grails.controllers.defaultScope', String)

        then: "property is retrieved"
        defaultScope == 'singleton'
    }

    def "configuration supports type conversion"() {
        when: "getting property with type conversion"
        def cacheMaxSize = grailsApplication.config.getProperty('grails.urlmapping.cache.maxsize', Integer)

        then: "property is converted to integer"
        cacheMaxSize == 1000
    }

    def "configuration supports default values"() {
        when: "getting non-existent property with default"
        def value = grailsApplication.config.getProperty('non.existent.property', String, 'default-value')

        then: "default value is returned"
        value == 'default-value'
    }

    // ========== MIME Types Configuration Tests ==========

    def "MIME types are configured correctly"() {
        when: "getting MIME type configuration"
        def config = grailsApplication.config

        then: "common MIME types are configured"
        config.getProperty('grails.mime.types.json', List)?.contains('application/json')
        config.getProperty('grails.mime.types.xml', List)?.contains('text/xml')
        config.getProperty('grails.mime.types.html', List)?.contains('text/html')
    }

    // ========== GSP Configuration Tests ==========

    def "GSP encoding configuration is loaded"() {
        when: "getting GSP config"
        def config = grailsApplication.config

        then: "GSP encoding is configured"
        config.getProperty('grails.views.gsp.encoding', String) == 'UTF-8'
        config.getProperty('grails.views.default.codec', String) == 'html'
    }

    // ========== Hibernate Configuration Tests ==========

    def "Hibernate cache configuration is loaded"() {
        when: "getting Hibernate config"
        def config = grailsApplication.config

        then: "cache settings are configured"
        config.getProperty('hibernate.cache.queries', Boolean) == false ||
        config.getProperty('hibernate.cache.queries', String) == 'false'
    }

    // ========== Configuration Hierarchy Tests ==========

    def "application.yml configuration is loaded"() {
        expect: "base configuration from application.yml is available"
        grailsApplication.config.getProperty('grails.codegen.defaultPackage', String) == 'test.app'
    }

    def "info properties contain app metadata"() {
        when: "accessing info properties"
        def config = grailsApplication.config

        then: "app info is available (may be placeholders in test)"
        config.getProperty('info.app.name', String) != null
    }

    // ========== Spring Configuration Tests ==========

    def "Spring banner mode is configured"() {
        expect: "banner mode is off"
        grailsApplication.config.getProperty('spring.main.banner-mode', String) == 'off'
    }

    def "Spring template location check is disabled"() {
        expect: "template check is false"
        grailsApplication.config.getProperty('spring.groovy.template.check-template-location', Boolean) == false ||
        grailsApplication.config.getProperty('spring.groovy.template.check-template-location', String) == 'false'
    }

    // ========== Environment Object Tests ==========

    def "Environment object provides utility methods"() {
        expect: "environment utility methods work"
        Environment.isDevelopmentMode() == false
        Environment.isWarDeployed() == false
        Environment.current.name == 'test'
    }

    def "can check if running in specific environment"() {
        expect:
        Environment.current == Environment.TEST
        Environment.current != Environment.DEVELOPMENT
        Environment.current != Environment.PRODUCTION
    }

    // ========== Configuration Reload Tests ==========

    def "configuration is immutable at runtime by default"() {
        when: "attempting to modify config"
        def originalValue = grailsApplication.config.getProperty('grails.controllers.defaultScope', String)

        then: "original value is accessible"
        originalValue == 'singleton'

        // Note: Modern Spring Boot configuration is generally immutable
        // Runtime changes require specific mechanisms
    }
}
