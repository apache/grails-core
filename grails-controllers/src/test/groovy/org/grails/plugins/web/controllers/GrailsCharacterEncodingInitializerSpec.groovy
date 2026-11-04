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
package org.grails.plugins.web.controllers

import ch.qos.logback.classic.Level

import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.env.DefaultPropertiesPropertySource
import org.springframework.boot.servlet.autoconfigure.HttpEncodingAutoConfiguration
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.web.filter.CharacterEncodingFilter

import spock.lang.Specification

import org.apache.grails.core.testing.support.LogCapture

class GrailsCharacterEncodingInitializerSpec extends Specification {

    private LogCapture logCapture

    void setup() {
        logCapture = new LogCapture(GrailsCharacterEncodingInitializer)
    }

    void cleanup() {
        logCapture.close()
    }

    void 'translates the deprecated Grails properties into spring.servlet.encoding'() {
        given: 'an application that still sets the Grails properties'
        def environment = environmentWith('grails.filter.encoding': 'ISO-8859-1', 'grails.filter.forceEncoding': 'true')

        when: 'the application context is refreshed'
        refresh(environment)

        then: 'Spring Boot reads the same settings from its own properties'
        environment.getProperty('spring.servlet.encoding.charset') == 'ISO-8859-1'
        environment.getProperty('spring.servlet.encoding.force') == 'true'

        and: 'each deprecated property is named with its replacement'
        warnings() == [
                "'grails.filter.encoding' is deprecated and will be removed. Spring Boot registers the character " +
                        "encoding filter now, so use 'spring.servlet.encoding.charset=ISO-8859-1' instead.",
                "'grails.filter.forceEncoding' is deprecated and will be removed. Spring Boot registers the character " +
                        "encoding filter now, so use 'spring.servlet.encoding.force=true' instead."
        ]
    }

    void 'lets a spring.servlet.encoding setting of the application win over a translated one'() {
        given: 'an application that sets both the Grails and the Spring Boot property'
        def environment = environmentWith('grails.filter.encoding': 'ISO-8859-1', 'spring.servlet.encoding.charset': 'UTF-16')

        when: 'the application context is refreshed'
        refresh(environment)

        then: 'the translated value is contributed at the lowest precedence'
        environment.getProperty('spring.servlet.encoding.charset') == 'UTF-16'

        and: 'the deprecated property is reported as ignored'
        warnings() == ["'grails.filter.encoding' is deprecated and will be removed. It is ignored, because " +
                "'spring.servlet.encoding.charset' is set."]
    }

    void 'keeps a spring.servlet.encoding default property of the application'() {
        given: 'an application that sets the Spring Boot property as a default property and still has the Grails one'
        def environment = environmentWith('grails.filter.encoding': 'ISO-8859-1')
        DefaultPropertiesPropertySource.addOrMerge(['spring.servlet.encoding.charset': 'UTF-16'], environment.propertySources)

        when: 'the application context is refreshed'
        refresh(environment)

        then: 'the deprecated value does not replace it'
        environment.getProperty('spring.servlet.encoding.charset') == 'UTF-16'
    }

    void 'translates each deprecated property on its own'() {
        given: 'an application that sets spring.servlet.encoding.force as a default property, and both Grails properties'
        def environment = environmentWith('grails.filter.encoding': 'ISO-8859-1', 'grails.filter.forceEncoding': 'true')
        DefaultPropertiesPropertySource.addOrMerge(['spring.servlet.encoding.force': 'false'], environment.propertySources)

        when: 'the application context is refreshed'
        refresh(environment)

        then: 'the encoding is translated and the force setting of the application is kept'
        environment.getProperty('spring.servlet.encoding.charset') == 'ISO-8859-1'
        environment.getProperty('spring.servlet.encoding.force') == 'false'

        and: 'only grails.filter.forceEncoding is reported as ignored'
        warnings() == [
                "'grails.filter.encoding' is deprecated and will be removed. Spring Boot registers the character " +
                        "encoding filter now, so use 'spring.servlet.encoding.charset=ISO-8859-1' instead.",
                "'grails.filter.forceEncoding' is deprecated and will be removed. It is ignored, because " +
                        "'spring.servlet.encoding.force' is set."
        ]
    }

    void 'translates the deprecated properties that a @PropertySource of the application sets'() {
        expect: 'the encoding filter Spring Boot registers takes the settings of the @PropertySource'
        new WebApplicationContextRunner()
                .withInitializer(new GrailsCharacterEncodingInitializer())
                .withUserConfiguration(LegacyEncodingPropertySource)
                .withConfiguration(AutoConfigurations.of(HttpEncodingAutoConfiguration))
                .run { context ->
                    def encodingFilter = context.getBean(CharacterEncodingFilter)
                    assert encodingFilter.encoding == 'ISO-8859-1'
                    assert !encodingFilter.forceRequestEncoding
                }
    }

    void 'contributes nothing when the deprecated properties are not set'() {
        given: 'an application that sets neither Grails property'
        def environment = new StandardEnvironment()

        when: 'the application context is refreshed'
        refresh(environment)

        then: 'Spring Boot defaults apply'
        !environment.propertySources.contains(DefaultPropertiesPropertySource.NAME)
        environment.getProperty('spring.servlet.encoding.charset') == null

        and: 'nothing is logged'
        warnings().empty
    }

    private List<String> warnings() {
        logCapture.events.findAll { it.level == Level.WARN }*.formattedMessage
    }

    private static void refresh(StandardEnvironment environment) {
        def context = new GenericApplicationContext()
        context.environment = environment
        new GrailsCharacterEncodingInitializer().initialize(context)
        context.refresh()
        context.close()
    }

    private static StandardEnvironment environmentWith(Map<String, Object> properties) {
        new StandardEnvironment().tap {
            propertySources.addFirst(new MapPropertySource('application', properties))
        }
    }

    @Configuration(proxyBeanMethods = false)
    @PropertySource('classpath:org/grails/plugins/web/controllers/legacy-encoding.properties')
    static class LegacyEncodingPropertySource {
    }
}
