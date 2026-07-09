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
package grails.plugin.springsecurity

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import org.springframework.core.env.Environment

/**
 * Tests for {@link SecurityAutoConfigurationExcluder}.
 *
 * Verifies that Spring Boot 4 servlet security auto-configuration classes that
 * conflict with the Grails Spring Security plugin are filtered out during the
 * auto-configuration discovery phase.
 */
class SecurityAutoConfigurationExcluderSpec extends Specification {

    @Subject
    SecurityAutoConfigurationExcluder excluder = new SecurityAutoConfigurationExcluder()

    @Unroll
    def "match excludes conflicting auto-configuration: #className"() {
        given:
        def autoConfigs = [className] as String[]

        when:
        def results = excluder.match(autoConfigs, null)

        then: 'the conflicting auto-configuration is excluded (false = filtered out)'
        !results[0]

        where:
        className << [
                'org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration',
                'org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration',
                'org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration',
                'org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration',
                'org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration',
                'org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration',
                'org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration',
                'org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration',
                'org.springframework.boot.security.saml2.autoconfigure.Saml2RelyingPartyAutoConfiguration',
                'org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerAutoConfiguration',
                'org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerJwtAutoConfiguration',
        ]
    }

    @Unroll
    def "match preserves non-security auto-configuration: #className"() {
        given:
        def autoConfigs = [className] as String[]

        when:
        def results = excluder.match(autoConfigs, null)

        then: 'non-security auto-configurations pass through (true = included)'
        results[0]

        where:
        className << [
                'org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration',
                'org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration',
                'org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration',
                'org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration',
                'org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration',
        ]
    }

    @Unroll
    def "match preserves Spring Boot 3 (pre-move) security auto-configuration class names: #className"() {
        given: 'these legacy class names are no longer registered as auto-configurations in Spring Boot 4'
        def autoConfigs = [className] as String[]

        when:
        def results = excluder.match(autoConfigs, null)

        then: 'the filter is conservative and only excludes the verified Spring Boot 4 names'
        results[0]

        where:
        className << [
                'org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration',
                'org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration',
                'org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration',
                'org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration',
                'org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration',
                'org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration',
                'org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration',
        ]
    }

    @Unroll
    def "match preserves reactive Spring Boot 4 security auto-configuration: #className"() {
        given: 'reactive variants are not excluded; they are guarded by ConditionalOnWebApplication(REACTIVE)'
        def autoConfigs = [className] as String[]

        when:
        def results = excluder.match(autoConfigs, null)

        then: 'the filter is servlet-only and lets reactive variants pass through'
        results[0]

        where:
        className << [
                'org.springframework.boot.security.autoconfigure.ReactiveUserDetailsServiceAutoConfiguration',
                'org.springframework.boot.security.autoconfigure.web.reactive.ReactiveWebSecurityAutoConfiguration',
                'org.springframework.boot.security.autoconfigure.actuate.web.reactive.ReactiveManagementWebSecurityAutoConfiguration',
                'org.springframework.boot.security.autoconfigure.rsocket.RSocketSecurityAutoConfiguration',
                'org.springframework.boot.security.oauth2.client.autoconfigure.reactive.ReactiveOAuth2ClientAutoConfiguration',
                'org.springframework.boot.security.oauth2.client.autoconfigure.reactive.ReactiveOAuth2ClientWebSecurityAutoConfiguration',
                'org.springframework.boot.security.oauth2.server.resource.autoconfigure.reactive.ReactiveOAuth2ResourceServerAutoConfiguration',
        ]
    }

    def "match handles mixed array of included and excluded auto-configurations"() {
        given:
        def autoConfigs = [
                'org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration',
                'org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration',
                'org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration',
                'org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration',
                'org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration',
        ] as String[]

        when:
        def results = excluder.match(autoConfigs, null)

        then:
        results[0]  // DataSource - included
        !results[1] // SecurityAutoConfiguration - excluded
        results[2]  // Jackson - included
        !results[3] // SecurityFilterAutoConfiguration - excluded
        results[4]  // DispatcherServlet - included
    }

    def "match handles empty array"() {
        given:
        def autoConfigs = [] as String[]

        when:
        def results = excluder.match(autoConfigs, null)

        then:
        results.length == 0
    }

    def "match handles null metadata parameter gracefully"() {
        given: 'autoConfigurationMetadata is null (not used by this filter)'
        def autoConfigs = [
                'org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration',
        ] as String[]

        when:
        def results = excluder.match(autoConfigs, null)

        then: 'still works correctly'
        !results[0]
    }

    def "getExcludedAutoConfigurations returns all 11 known conflicting classes"() {
        when:
        def excluded = SecurityAutoConfigurationExcluder.excludedAutoConfigurations

        then:
        excluded.size() == 11
        excluded.contains('org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration')
        excluded.contains('org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration')
        excluded.contains('org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration')
        excluded.contains('org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration')
        excluded.contains('org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration')
        excluded.contains('org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration')
        excluded.contains('org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration')
        excluded.contains('org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration')
        excluded.contains('org.springframework.boot.security.saml2.autoconfigure.Saml2RelyingPartyAutoConfiguration')
        excluded.contains('org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerAutoConfiguration')
        excluded.contains('org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerJwtAutoConfiguration')
    }

    def "getExcludedAutoConfigurations returns unmodifiable set"() {
        when:
        def excluded = SecurityAutoConfigurationExcluder.excludedAutoConfigurations
        excluded.add('some.new.AutoConfiguration')

        then:
        thrown(UnsupportedOperationException)
    }

    def "match allows all auto-configurations when disabled via environment property"() {
        given:
        def env = Mock(Environment)
        env.getProperty(SecurityAutoConfigurationExcluder.ENABLED_PROPERTY, Boolean, true) >> false
        excluder.environment = env

        and:
        def autoConfigs = [
                'org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration',
                'org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration',
                'org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration',
        ] as String[]

        when:
        def results = excluder.match(autoConfigs, null)

        then: 'all auto-configurations pass through when filter is disabled'
        results[0]
        results[1]
        results[2]
    }

    def "match excludes by default when environment has the property set to true"() {
        given:
        def env = Mock(Environment)
        env.getProperty(SecurityAutoConfigurationExcluder.ENABLED_PROPERTY, Boolean, true) >> true
        excluder.environment = env

        and:
        def autoConfigs = [
                'org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration',
        ] as String[]

        when:
        def results = excluder.match(autoConfigs, null)

        then: 'exclusion is active by default'
        !results[0]
    }

    def "match excludes by default when no environment is set"() {
        given: 'excluder without environment (e.g. unit test usage)'
        def autoConfigs = [
                'org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration',
        ] as String[]

        when:
        def results = excluder.match(autoConfigs, null)

        then: 'exclusion is active by default'
        !results[0]
    }

    def "spring.factories registers the filter correctly"() {
        when: 'enumerating all spring.factories resources on the classpath'
        def resources = getClass().classLoader.getResources('META-INF/spring.factories')
        def allContents = resources.collect { it.text }

        then: 'at least one spring.factories exists'
        !allContents.isEmpty()

        and: 'one of them registers SecurityAutoConfigurationExcluder as an AutoConfigurationImportFilter'
        allContents.any { content ->
            content.contains('org.springframework.boot.autoconfigure.AutoConfigurationImportFilter') &&
                content.contains('grails.plugin.springsecurity.SecurityAutoConfigurationExcluder')
        }
    }
}
