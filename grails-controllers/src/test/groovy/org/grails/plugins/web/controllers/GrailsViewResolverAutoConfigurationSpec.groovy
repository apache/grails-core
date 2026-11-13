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

class GrailsViewResolverAutoConfigurationSpec extends Specification {

    void 'Boot WebMvcAutoConfiguration contributes a defaultViewResolver when the Grails auto-config is absent'() {
        expect: 'the contrast case proves the removal assertions below are meaningful'
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration))
                .run { context ->
                    assert context.containsBean('defaultViewResolver')
                }
    }

    void 'the defaultViewResolver is removed by default'() {
        expect:
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration, GrailsViewResolverAutoConfiguration))
                .run { context ->
                    assert !context.containsBean('defaultViewResolver')
                }
    }

    void 'the defaultViewResolver is kept when grails.web.removeDefaultViewResolverBean=false'() {
        expect:
        new WebApplicationContextRunner()
                .withPropertyValues("${GrailsViewResolverAutoConfiguration.REMOVE_PROPERTY}=false")
                .withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration, GrailsViewResolverAutoConfiguration))
                .run { context ->
                    assert context.containsBean('defaultViewResolver')
                }
    }

    void 'the deprecated spring.gsp.removeDefaultViewResolverBean property is still honoured'() {
        expect:
        new WebApplicationContextRunner()
                .withPropertyValues("${GrailsViewResolverAutoConfiguration.LEGACY_REMOVE_PROPERTY}=false")
                .withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration, GrailsViewResolverAutoConfiguration))
                .run { context ->
                    assert context.containsBean('defaultViewResolver')
                }
    }

    void 'the new property takes precedence over the deprecated one'() {
        expect:
        new WebApplicationContextRunner()
                .withPropertyValues(
                        "${GrailsViewResolverAutoConfiguration.REMOVE_PROPERTY}=true",
                        "${GrailsViewResolverAutoConfiguration.LEGACY_REMOVE_PROPERTY}=false")
                .withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration, GrailsViewResolverAutoConfiguration))
                .run { context ->
                    assert !context.containsBean('defaultViewResolver')
                }
    }
}
