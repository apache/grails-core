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

package org.grails.plugins.web.mapping

import java.util.function.Supplier

import grails.config.Settings
import grails.web.mapping.UrlMappings
import grails.web.mapping.cors.GrailsCorsConfiguration
import grails.web.mapping.cors.GrailsCorsFilter

import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter

import org.grails.web.mapping.mvc.UrlMappingsInfoHandlerAdapter
import org.grails.web.mapping.servlet.UrlMappingsErrorPageCustomizer

import spock.lang.Specification

class UrlMappingsAutoConfigurationSpec extends Specification {

    private WebApplicationContextRunner contextRunner() {
        // grailsLinkGenerator autowires the grailsUrlMappingsHolder bean and
        // urlMappingsInfoHandlerAdapter autowires UrlMappings; one mock satisfies both.
        UrlMappings urlMappings = Mock(UrlMappings)
        Supplier<UrlMappings> urlMappingsSupplier = () -> urlMappings
        new WebApplicationContextRunner()
                .withBean('grailsUrlMappingsHolder', UrlMappings, urlMappingsSupplier)
                .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration, UrlMappingsAutoConfiguration))
    }

    void 'the Grails url-mappings beans register by default'() {
        expect:
        contextRunner().run { context ->
            assert context.containsBean('grailsCorsFilter')
            assert context.containsBean('urlMappingsErrorPageCustomizer')
            assert context.containsBean('urlMappingsInfoHandlerAdapter')
        }
    }

    void 'the CORS filter is not registered when disabled by property'() {
        expect:
        contextRunner()
                .withPropertyValues("${Settings.SETTING_CORS_FILTER}=false")
                .run { context ->
                    assert !context.containsBean('grailsCorsFilter')
                }
    }

    void 'a user-defined GrailsCorsFilter bean makes the auto-configured one back off'() {
        given:
        GrailsCorsFilter userCorsFilter = new GrailsCorsFilter(new GrailsCorsConfiguration())
        Supplier<GrailsCorsFilter> userCorsFilterSupplier = () -> userCorsFilter

        expect:
        contextRunner()
                .withBean(GrailsCorsFilter, userCorsFilterSupplier)
                .run { context ->
                    def names = context.getBeanNamesForType(GrailsCorsFilter)
                    assert names.length == 1
                    assert context.getBean(names[0]).is(userCorsFilter)
                }
    }

    void 'a user-defined plain CorsFilter bean also makes the auto-configured GrailsCorsFilter back off'() {
        given: 'CORS handling replaced with a plain Spring CorsFilter'
        CorsFilter userCorsFilter = new CorsFilter(new UrlBasedCorsConfigurationSource())
        Supplier<CorsFilter> userCorsFilterSupplier = () -> userCorsFilter

        expect: 'only one CORS filter exists and it is the user one'
        contextRunner()
                .withBean(CorsFilter, userCorsFilterSupplier)
                .run { context ->
                    assert context.getBeanNamesForType(GrailsCorsFilter).length == 0
                    def names = context.getBeanNamesForType(CorsFilter)
                    assert names.length == 1
                    assert context.getBean(names[0]).is(userCorsFilter)
                }
    }

    void 'a user-defined UrlMappingsErrorPageCustomizer bean makes the auto-configured one back off'() {
        given:
        UrlMappingsErrorPageCustomizer userCustomizer = new UrlMappingsErrorPageCustomizer()
        Supplier<UrlMappingsErrorPageCustomizer> userCustomizerSupplier = () -> userCustomizer

        expect:
        contextRunner()
                .withBean(UrlMappingsErrorPageCustomizer, userCustomizerSupplier)
                .run { context ->
                    def names = context.getBeanNamesForType(UrlMappingsErrorPageCustomizer)
                    assert names.length == 1
                    assert context.getBean(names[0]).is(userCustomizer)
                }
    }

    void 'a user-defined UrlMappingsInfoHandlerAdapter bean makes the auto-configured one back off'() {
        given:
        UrlMappingsInfoHandlerAdapter userAdapter = new UrlMappingsInfoHandlerAdapter()
        Supplier<UrlMappingsInfoHandlerAdapter> userAdapterSupplier = () -> userAdapter

        expect:
        contextRunner()
                .withBean(UrlMappingsInfoHandlerAdapter, userAdapterSupplier)
                .run { context ->
                    def names = context.getBeanNamesForType(UrlMappingsInfoHandlerAdapter)
                    assert names.length == 1
                    assert context.getBean(names[0]).is(userAdapter)
                }
    }
}
