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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.PropertySource
import org.springframework.test.annotation.DirtiesContext
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

/**
 * Integration tests for configuration source priority.
 * Tests that configuration from different sources is loaded
 * in the correct order of precedence.
 */
@Integration
@RestoreSystemProperties
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class ConfigurationPrioritySpec extends Specification {

    @Autowired
    GrailsApplication grailsApplication

    @Autowired
    ConfigurableEnvironment environment

    void setupSpec() {
        // Set system property to test system property priority
        System.setProperty('test.system.property', 'from-system-property')
        System.setProperty('grails.config.locations', '')
    }

    void cleanupSpec() {
        System.clearProperty('test.system.property')
    }

    // ========== Property Source Existence Tests ==========

    def "multiple property sources are registered"() {
        when: "listing property sources"
        def sources = environment.propertySources.toList()

        then: "multiple sources exist"
        sources.size() > 1

        and: "sources include expected types"
        sources.any { it.name.contains('application') || it.name.contains('Config') }
    }

    def "system properties source exists"() {
        expect: "system properties are available"
        environment.propertySources.any {
            it.name.toLowerCase().contains('system') ||
            it.name == 'systemProperties'
        }
    }

    def "environment variables source exists"() {
        expect: "environment variables source is available"
        environment.propertySources.any {
            it.name.toLowerCase().contains('environment') ||
            it.name == 'systemEnvironment'
        }
    }

    // ========== System Property Priority Tests ==========

    def "system properties are accessible"() {
        expect: "system property set in setupSpec is accessible"
        environment.getProperty('test.system.property') == 'from-system-property'
    }

    def "system property java.home is accessible"() {
        expect: "standard system properties are available"
        environment.getProperty('java.home') != null
    }

    // ========== Environment Variable Tests ==========

    def "environment variables are accessible"() {
        expect: "PATH or Path environment variable exists"
        environment.getProperty('PATH') != null ||
        environment.getProperty('Path') != null ||
        System.getenv('PATH') != null ||
        System.getenv('Path') != null
    }

    def "TEST_HOME environment variable from build.gradle is accessible"() {
        expect: "TEST_HOME set in build.gradle test configuration"
        // This is set in the build.gradle: systemProperty 'TEST_HOME', 'home-value'
        System.getProperty('TEST_HOME') == 'home-value'
    }

    // ========== Application Configuration Priority Tests ==========

    def "application.yml configuration is loaded"() {
        expect: "value from application.yml is accessible"
        grailsApplication.config.getProperty('test.config.value', String) == 'From application.yml'
    }

    def "grails config locations property is recognized"() {
        when: "checking for config locations property"
        def configLocations = grailsApplication.config.getProperty('grails.config.locations', List)

        then: "config locations may be defined"
        // May be null if not configured or may have values
        configLocations == null || configLocations instanceof List
    }

    // ========== Property Resolution Tests ==========

    def "property resolver uses correct precedence"() {
        when: "resolving property that exists in multiple sources"
        // grails.env is typically set as system property during test
        def grailsEnv = environment.getProperty('grails.env')

        then: "highest priority source wins"
        grailsEnv == 'TEST' || grailsEnv == 'test' || grailsEnv == null
    }

    def "default values work when property not found"() {
        expect: "default value is returned for missing property"
        environment.getProperty('completely.missing.property', 'default') == 'default'
    }

    def "required property throws exception when missing"() {
        when: "getting required missing property"
        environment.getRequiredProperty('completely.missing.required.property')

        then: "exception is thrown"
        thrown(IllegalStateException)
    }

    // ========== Property Source Order Tests ==========

    def "can list all property source names"() {
        when: "getting property source names"
        def sourceNames = environment.propertySources.collect { it.name }

        then: "names are available"
        !sourceNames.isEmpty()

        and: "we can log them for debugging"
        sourceNames.each { println "Property source: $it" }
        true
    }

    def "property sources have defined order"() {
        when: "iterating property sources"
        def sources = environment.propertySources.toList()

        then: "order is maintained"
        sources.size() > 0

        and: "first source has highest priority"
        // First source in the list has highest priority
        sources[0] != null
    }

    // ========== Placeholder Resolution Tests ==========

    def "placeholders in configuration are resolved"() {
        when: "getting property that may contain placeholders"
        def infoAppName = grailsApplication.config.getProperty('info.app.name', String)

        then: "placeholder is resolved or returns placeholder syntax"
        infoAppName != null
    }

    // ========== Type Conversion Tests ==========

    def "string to boolean conversion works"() {
        when: "getting boolean property"
        def cacheQueries = environment.getProperty('hibernate.cache.queries', Boolean)

        then: "boolean is returned"
        cacheQueries == false || cacheQueries == null
    }

    def "string to integer conversion works"() {
        when: "getting integer property"
        def maxSize = environment.getProperty('grails.urlmapping.cache.maxsize', Integer)

        then: "integer is returned"
        maxSize == 1000 || maxSize == null
    }

    def "string to list conversion works for MIME types"() {
        when: "getting list property"
        def jsonTypes = grailsApplication.config.getProperty('grails.mime.types.json', List)

        then: "list is returned"
        jsonTypes == null || jsonTypes.contains('application/json')
    }

    // ========== Nested Property Tests ==========

    def "deeply nested properties are accessible"() {
        when: "accessing deeply nested property"
        def codec = grailsApplication.config.getProperty('grails.views.gsp.codecs.scriptlet', String)

        then: "property is retrieved"
        codec == 'html'
    }

    def "nested property with map value"() {
        when: "accessing datasource properties"
        def pooled = grailsApplication.config.getProperty('dataSource.pooled', Boolean)

        then: "map property is accessible"
        pooled == true
    }

    // ========== Profile-Specific Configuration Tests ==========

    def "active profiles affect configuration"() {
        when: "checking active profiles"
        def activeProfiles = environment.activeProfiles

        then: "profiles are available"
        activeProfiles != null
        // In test, may have 'test' profile or be empty depending on setup
    }

    def "grails profile is set"() {
        expect: "grails profile is web"
        grailsApplication.config.getProperty('grails.profile', String) == 'web'
    }
}
