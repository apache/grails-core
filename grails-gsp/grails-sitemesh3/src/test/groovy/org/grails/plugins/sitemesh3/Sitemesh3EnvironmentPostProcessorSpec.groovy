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

import org.springframework.boot.SpringApplication
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.MutablePropertySources
import org.springframework.mock.env.MockEnvironment

import spock.lang.Specification

class Sitemesh3EnvironmentPostProcessorSpec extends Specification {

    Sitemesh3EnvironmentPostProcessor pp = new Sitemesh3EnvironmentPostProcessor()

    void "seeds sitemesh defaults when nothing is configured"() {
        given:
        MockEnvironment env = new MockEnvironment()

        when:
        pp.postProcessEnvironment(env, new SpringApplication())

        then:
        env.getProperty('sitemesh.integration') == 'view-resolver'
        env.getProperty('sitemesh.viewResolver.wrapMode') == 'bean-instance'
        env.getProperty('sitemesh.viewResolver.targetBeanName') == 'jspViewResolver'
    }

    void "respects existing user values"() {
        given:
        MockEnvironment env = new MockEnvironment()
        env.setProperty('sitemesh.integration', 'filter')
        env.setProperty('sitemesh.viewResolver.wrapMode', 'bean-definition')
        env.setProperty('sitemesh.viewResolver.targetBeanName', 'myResolver')

        when:
        pp.postProcessEnvironment(env, new SpringApplication())

        then: 'user values win'
        env.getProperty('sitemesh.integration') == 'filter'
        env.getProperty('sitemesh.viewResolver.wrapMode') == 'bean-definition'
        env.getProperty('sitemesh.viewResolver.targetBeanName') == 'myResolver'
    }

    void "registered property source has the expected name"() {
        given:
        MockEnvironment env = new MockEnvironment()

        when:
        pp.postProcessEnvironment(env, new SpringApplication())

        then:
        env.getPropertySources().contains(Sitemesh3EnvironmentPostProcessor.PROPERTY_SOURCE_NAME)
    }
}
