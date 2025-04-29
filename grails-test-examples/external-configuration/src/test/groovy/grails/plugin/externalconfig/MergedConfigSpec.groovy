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
import spock.lang.Specification

class MergedConfigSpec extends Specification implements GrailsUnitTest {

    ConfigurableEnvironment environment = new GrailsEnvironment(grailsApplication)
    ExternalConfigRunListener listener = new ExternalConfigRunListener(null, null)

    def cleanupSpec() {
        cleanupGrailsApplication()
    }


    def "when merging multiple configs the expected values are in the final result"() {
        given:
        addToEnvironment('grails.config.locations': [
                'classpath:/mergeExternalConfig.yml',
                'classpath:/mergeExternalConfig.groovy',
                'classpath:/mergeExternalConfig.properties'
        ])
        when:
        listener.environmentPrepared(null, environment)

        then:
        getConfigProperty('base.config.yml') == 'yml-expected-value'
        getConfigProperty('base.config.groovy') == 'groovy-expected-value'
        getConfigProperty('base.config.properties') == 'properties-expected-value'
    }

    def "when merging multiple groovy configs the expected values are in the final result"() {
        given:
        addToEnvironment('base.config.global': 'global-expected-value',
                'grails.config.locations': [
                        'classpath:/mergeExternalConfig.groovy',
                        'classpath:/mergeExternalConfig2.groovy',
                ])
        when:
        listener.environmentPrepared(null, environment)

        then:
        getConfigProperty('base.config.global') == 'global-expected-value'
        getConfigProperty('base.config.groovy') == 'groovy-expected-value'
        getConfigProperty('base.config.groovy2A') == 'groovy2-expected-value-A'
        getConfigProperty('base.config.groovy2') == 'groovy2-expected-value'
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
