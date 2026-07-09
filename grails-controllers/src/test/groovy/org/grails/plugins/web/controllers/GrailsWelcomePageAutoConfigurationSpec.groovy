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

import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration

import spock.lang.Specification

class GrailsWelcomePageAutoConfigurationSpec extends Specification {

    void 'Boot WebMvcAutoConfiguration contributes welcome-page mappings when the Grails auto-config is absent'() {
        expect: 'the contrast case proves the removal assertions below are meaningful'
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration))
                .run { context ->
                    assert context.containsBean('welcomePageHandlerMapping')
                    assert context.containsBean('welcomePageNotAcceptableHandlerMapping')
                }
    }

    void 'the welcome-page mappings are removed by default'() {
        expect:
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration, GrailsWelcomePageAutoConfiguration))
                .run { context ->
                    assert !context.containsBean('welcomePageHandlerMapping')
                    assert !context.containsBean('welcomePageNotAcceptableHandlerMapping')
                }
    }

    void 'the welcome-page mappings are kept when grails.web.removeWelcomePageMapping=false'() {
        expect:
        new WebApplicationContextRunner()
                .withPropertyValues("${GrailsWelcomePageAutoConfiguration.REMOVE_PROPERTY}=false")
                .withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration, GrailsWelcomePageAutoConfiguration))
                .run { context ->
                    assert context.containsBean('welcomePageHandlerMapping')
                    assert context.containsBean('welcomePageNotAcceptableHandlerMapping')
                }
    }
}
