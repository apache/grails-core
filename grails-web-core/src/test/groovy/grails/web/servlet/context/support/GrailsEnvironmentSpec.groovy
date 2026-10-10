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
package grails.web.servlet.context.support

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import org.grails.config.PropertySourcesConfig
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import spock.lang.Specification

/**
 * The environment is used from dynamic Groovy (Spock specs, scripts, the external configuration
 * plugin), where {@code environment.getProperty('a.b')} must be the Spring
 * {@link org.springframework.core.env.PropertyResolver} lookup and never the Groovy property
 * lookup, which would throw a {@link MissingPropertyException} for a dotted config key.
 *
 * <p>The lookups are plain dynamic method calls assigned in {@code when:} blocks on purpose: a call
 * written inside a Spock condition is dispatched differently and does not exercise the same
 * Groovy call-site path as application code or a helper method.</p>
 */
class GrailsEnvironmentSpec extends Specification {

    private final GrailsApplication grailsApplication = new DefaultGrailsApplication()

    ConfigurableEnvironment environment

    void setup() {
        grailsApplication.config = new PropertySourcesConfig(['yml.config': 'yml-expected-value', 'nested.config.value': 'nested-value'])
        environment = new GrailsEnvironment(grailsApplication)
    }

    void "a dotted config property is resolved through the Spring property resolver"() {
        when:
        String top = lookup('yml.config')
        String nested = lookup('nested.config.value')
        String missing = lookup('missing.config')
        String defaulted = environment.getProperty('missing.config', 'fallback')

        then:
        top == 'yml-expected-value'
        nested == 'nested-value'
        missing == null
        defaulted == 'fallback'
    }

    void "a property source added at runtime takes precedence over the application config"() {
        given:
        environment.propertySources.addFirst(new MapPropertySource('added', ['yml.config': 'overridden', 'added.only': 'added-value']))

        when:
        String overridden = lookup('yml.config')
        String added = lookup('added.only')
        String nested = lookup('nested.config.value')

        then:
        overridden == 'overridden'
        added == 'added-value'
        nested == 'nested-value'
    }

    void "the application config is exposed as a named property source"() {
        when:
        def configSource = environment.propertySources.get('grailsApplication')
        def value = configSource.getProperty('yml.config')

        then:
        configSource != null
        value == 'yml-expected-value'
        environment.propertySources.get('systemProperties') != null
    }

    void "the current Grails environment name is an active profile"() {
        expect:
        environment.activeProfiles.contains(grails.util.Environment.current.name)
    }

    private String lookup(String key) {
        environment.getProperty(key)
    }

}
