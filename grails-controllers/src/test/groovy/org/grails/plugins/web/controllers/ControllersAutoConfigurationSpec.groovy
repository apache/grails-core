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

import java.util.function.Supplier

import grails.core.GrailsApplication

import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.web.filter.RequestContextFilter

import org.grails.web.config.http.GrailsFilters
import org.grails.web.servlet.mvc.GrailsWebRequestFilter

import spock.lang.Specification

class ControllersAutoConfigurationSpec extends Specification {

    ApplicationContext applicationContext = Mock(ApplicationContext) {
        getBeansOfType(_) >> [:]
    }

    ControllersAutoConfiguration autoConfiguration = new ControllersAutoConfiguration()

    void 'grailsWebRequest filter is a RequestContextFilter so Boot WebMvcAutoConfiguration backs off its own RequestContextFilter'() {
        when: 'the Grails request-binding filter bean is created'
        GrailsWebRequestFilter filter = autoConfiguration.grailsWebRequest(applicationContext)

        then: 'it is exposed as a RequestContextFilter, the type Boot @ConditionalOnMissingBean keys on'
        filter != null
        filter instanceof RequestContextFilter
    }

    void 'grailsWebRequestFilter registers the GrailsWebRequestFilter with the Grails request-filter order'() {
        given: 'the Grails request-binding filter'
        GrailsWebRequestFilter filter = autoConfiguration.grailsWebRequest(applicationContext)

        when: 'it is wrapped in a registration bean'
        FilterRegistrationBean<GrailsWebRequestFilter> registrationBean = autoConfiguration.grailsWebRequestFilter(filter)

        then: 'the same filter instance is registered ahead of the Spring Security chain'
        registrationBean.filter.is(filter)
        registrationBean.order == GrailsFilters.GRAILS_WEB_REQUEST_FILTER.order
    }

    void 'Boot WebMvcAutoConfiguration registers its own requestContextFilter when the Grails controllers auto-config is absent'() {
        expect: 'the contrast case proves the backoff assertion below is meaningful'
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration))
                .run { context ->
                    assert context.containsBean('requestContextFilter')
                }
    }

    void 'Grails controllers auto-config makes Boot WebMvcAutoConfiguration back off its requestContextFilter'() {
        given: 'a GrailsApplication, required by the controllers auto-config'
        GrailsApplication grailsApplication = Mock(GrailsApplication) {
            getClassLoader() >> getClass().classLoader
        }
        Supplier<GrailsApplication> grailsApplicationSupplier = () -> grailsApplication

        expect: 'Boot does not contribute its OrderedRequestContextFilter, leaving GrailsWebRequest bound'
        new WebApplicationContextRunner()
                .withBean(GrailsApplication, grailsApplicationSupplier)
                .withConfiguration(AutoConfigurations.of(ControllersAutoConfiguration, WebMvcAutoConfiguration))
                .run { context ->
                    assert !context.containsBean('requestContextFilter')
                    assert context.getBeanNamesForType(GrailsWebRequestFilter).length == 1
                }
    }
}
