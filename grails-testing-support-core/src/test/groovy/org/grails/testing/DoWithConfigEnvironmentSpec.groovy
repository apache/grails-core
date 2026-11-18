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
package org.grails.testing

import org.springframework.beans.factory.annotation.Value

import spock.lang.Specification

/**
 * What {@code doWithConfig} sets reaches the Spring {@code Environment}, which property conditions
 * read, before the test's configuration is, and not only {@code grailsApplication.config}.
 */
class DoWithConfigEnvironmentSpec extends Specification implements GrailsUnitTest {

    Closure doWithConfig() {
        { config -> config.'probe.feature.enabled' = 'true' }
    }

    def beans = {
        bean('probeFeature', String).conditionalOnProperty('probe.feature.enabled', havingValue: 'true') { 'on' }
        bean('probeValue', String) { @Value('${probe.feature.enabled:unset}') String value -> value }
    }

    void "doWithConfig reaches the environment"() {
        expect:
        config.getProperty('probe.feature.enabled') == 'true'
        applicationContext.environment.getProperty('probe.feature.enabled') == 'true'
    }

    void "a property condition in the beans block sees doWithConfig"() {
        expect:
        applicationContext.getBean('probeFeature') == 'on'
        applicationContext.getBean('probeValue') == 'true'
    }
}
