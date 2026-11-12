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

import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment

import spock.lang.Specification

class GrailsWebResourcesEnvironmentPostProcessorSpec extends Specification {

    private static final String PROPERTY = 'spring.web.resources.add-mappings'

    private final GrailsWebResourcesEnvironmentPostProcessor processor = new GrailsWebResourcesEnvironmentPostProcessor()

    void 'the processor defaults spring.web.resources.add-mappings to false'() {
        given:
        def environment = new StandardEnvironment()

        when:
        processor.postProcessEnvironment(environment, null)

        then:
        environment.getProperty(PROPERTY) == 'false'
    }

    void 'an application value overrides the contributed default'() {
        given: 'a higher-precedence source already sets the property'
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('app', [(PROPERTY): 'true']))

        when:
        processor.postProcessEnvironment(environment, null)

        then: 'the application value wins because the default is contributed at the lowest precedence'
        environment.getProperty(PROPERTY) == 'true'
    }
}
