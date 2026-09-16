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

import jakarta.servlet.Filter

import grails.config.Settings

import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.boot.web.servlet.AbstractFilterRegistrationBean
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.boot.web.servlet.ServletContextInitializerBeans
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.web.filter.HiddenHttpMethodFilter as SpringHiddenHttpMethodFilter

import org.grails.web.config.http.GrailsFilters
import org.grails.web.filters.HiddenHttpMethodFilter
import org.grails.web.util.HiddenHttpMethod

import spock.lang.Specification

/**
 * No servlet filter rewrites the request method by default as of Grails 8 -- the override is resolved inside
 * the dispatcher instead. A filter is contributed only when an application asks for one, through either the
 * Grails property or Spring Boot's.
 *
 * The invariant these specs protect is the one {@link HiddenHttpMethod#isServletFilterMode} relies on:
 * whenever either property is set, a filter really is on the chain.
 */
class GrailsHiddenHttpMethodFilterAutoConfigurationSpec extends Specification {

    void 'no filter is registered by default'() {
        expect: 'the override is resolved in the dispatcher, so no filter is needed'
        contextRunner()
                .run { context ->
                    assert grailsFilterRegistrations(context) == 0
                    assert context.getBeanNamesForType(SpringHiddenHttpMethodFilter).length == 0
                }
    }

    void 'the Grails property contributes the Grails filter, at the Grails filter order'() {
        expect:
        contextRunner()
                .withPropertyValues("${Settings.WEB_HIDDEN_METHOD_FILTER_ENABLED}=true")
                .run { context ->
                    assert grailsFilterRegistrations(context) == 1
                    assert context.getBean('hiddenHttpMethodFilter').order ==
                            GrailsFilters.HIDDEN_HTTP_METHOD_FILTER.order
                }
    }

    void 'the Spring Boot property contributes the Spring filter, and Grails backs off'() {
        expect: 'both would otherwise define a bean named hiddenHttpMethodFilter and fail startup'
        contextRunner()
                .withPropertyValues("${HiddenHttpMethod.SPRING_FILTER_ENABLED}=true")
                .run { context ->
                    assert context.startupFailure == null
                    assert context.getBeanNamesForType(SpringHiddenHttpMethodFilter).length == 1
                    assert grailsFilterRegistrations(context) == 0
                }
    }

    void 'setting both properties yields one filter, not a collision'() {
        expect: "Spring's is already registered by the time this auto-configuration is considered"
        contextRunner()
                .withPropertyValues("${Settings.WEB_HIDDEN_METHOD_FILTER_ENABLED}=true",
                        "${HiddenHttpMethod.SPRING_FILTER_ENABLED}=true")
                .run { context ->
                    assert context.startupFailure == null
                    assert context.getBeanNamesForType(SpringHiddenHttpMethodFilter).length == 1
                    assert grailsFilterRegistrations(context) == 0
                }
    }

    void 'Grails fills the gap when Boot cannot contribute a filter'() {
        given: "the @EnableWebMvc case: WebMvcAutoConfiguration backs off, taking Boot's filter with it"
        def runner = new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(GrailsHiddenHttpMethodFilterAutoConfiguration))

        expect: "asking for Boot's filter still produces a filter, rather than silently producing none"
        runner.withPropertyValues("${HiddenHttpMethod.SPRING_FILTER_ENABLED}=true")
                .run { context ->
                    assert context.getBeanNamesForType(SpringHiddenHttpMethodFilter).length == 0
                    assert grailsFilterRegistrations(context) == 1
                }

        and: 'as does asking for the Grails one'
        runner.withPropertyValues("${Settings.WEB_HIDDEN_METHOD_FILTER_ENABLED}=true")
                .run { context ->
                    assert grailsFilterRegistrations(context) == 1
                }

        and: 'while asking for neither still produces none'
        runner.run { context ->
            assert grailsFilterRegistrations(context) == 0
        }
    }

    void 'an application-defined filter wins'() {
        given:
        def userFilter = new HiddenHttpMethodFilter()
        Supplier<HiddenHttpMethodFilter> userFilterSupplier = () -> userFilter

        expect: 'no second, framework-supplied copy is added'
        contextRunner()
                .withPropertyValues("${Settings.WEB_HIDDEN_METHOD_FILTER_ENABLED}=true")
                .withBean('applicationHiddenHttpMethodFilter', HiddenHttpMethodFilter, userFilterSupplier)
                .run { context ->
                    assert grailsFilterRegistrations(context) == 1
                    assert !context.containsBean('hiddenHttpMethodFilter')
                }
    }

    private static WebApplicationContextRunner contextRunner() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        GrailsHiddenHttpMethodFilterAutoConfiguration, WebMvcAutoConfiguration))
    }

    // Counts what actually reaches the servlet filter chain, including any raw filter bean Boot adapts
    // onto "/*", rather than mere bean presence in the context.
    private static int grailsFilterRegistrations(ConfigurableApplicationContext context) {
        new ServletContextInitializerBeans(context.beanFactory).count { initializer ->
            initializer instanceof AbstractFilterRegistrationBean &&
                    ((AbstractFilterRegistrationBean) initializer).filter instanceof HiddenHttpMethodFilter
        }
    }
}
