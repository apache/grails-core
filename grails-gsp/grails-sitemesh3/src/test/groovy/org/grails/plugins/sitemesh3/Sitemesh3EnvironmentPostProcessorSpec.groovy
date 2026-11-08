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
package org.grails.plugins.sitemesh3

import org.springframework.core.env.MapPropertySource
import org.springframework.mock.env.MockEnvironment

import org.grails.web.util.WebUtils

import spock.lang.Specification

class Sitemesh3EnvironmentPostProcessorSpec extends Specification {

    Sitemesh3EnvironmentPostProcessor postProcessor = new Sitemesh3EnvironmentPostProcessor()

    void "contributes the SiteMesh 3 defaults with lowest precedence"() {
        given:
        MockEnvironment environment = new MockEnvironment()

        when:
        postProcessor.postProcessEnvironment(environment, null)

        then:
        environment.getProperty('sitemesh.decorator.metaTag') == 'layout'
        environment.getProperty('sitemesh.decorator.attribute') == WebUtils.LAYOUT_ATTRIBUTE
        environment.getProperty('sitemesh.decorator.prefix') == '/layouts/'
        environment.getProperty('sitemesh.decorator.default') == null
        environment.propertySources.iterator().toList().last().name == Sitemesh3EnvironmentPostProcessor.PROPERTY_SOURCE_NAME
    }

    void "keys already configured by the application are not overridden"() {
        given:
        MockEnvironment environment = new MockEnvironment()
        environment.setProperty('sitemesh.decorator.prefix', '/custom-layouts/')

        when:
        postProcessor.postProcessEnvironment(environment, null)

        then:
        environment.getProperty('sitemesh.decorator.prefix') == '/custom-layouts/'
        MapPropertySource defaults =
                environment.propertySources.get(Sitemesh3EnvironmentPostProcessor.PROPERTY_SOURCE_NAME) as MapPropertySource
        !defaults.containsProperty('sitemesh.decorator.prefix')
        defaults.containsProperty('sitemesh.decorator.metaTag')
    }

    void "the default layout comes from grails.sitemesh.default.layout, falling back to the legacy grails.views.layout.default key"() {
        given:
        MockEnvironment environment = new MockEnvironment()
        if (sitemesh3Key) {
            environment.setProperty('grails.sitemesh.default.layout', sitemesh3Key)
        }
        if (sitemesh2Key) {
            environment.setProperty('grails.views.layout.default', sitemesh2Key)
        }

        when:
        postProcessor.postProcessEnvironment(environment, null)

        then:
        environment.getProperty('sitemesh.decorator.default') == expected

        where:
        sitemesh3Key | sitemesh2Key | expected
        'main'       | null         | 'main'
        null         | 'legacy'     | 'legacy'
        'main'       | 'legacy'     | 'main'
        null         | null         | null
    }

    void "no property source is added when every key is already configured"() {
        given:
        MockEnvironment environment = new MockEnvironment()
        environment.setProperty('sitemesh.decorator.metaTag', 'decorator')
        environment.setProperty('sitemesh.decorator.attribute', 'custom')
        environment.setProperty('sitemesh.decorator.prefix', '/decorators/')

        when:
        postProcessor.postProcessEnvironment(environment, null)

        then:
        environment.propertySources.get(Sitemesh3EnvironmentPostProcessor.PROPERTY_SOURCE_NAME) == null
    }
}
