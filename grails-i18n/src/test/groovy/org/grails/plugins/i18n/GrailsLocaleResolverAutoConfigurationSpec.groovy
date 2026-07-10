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

package org.grails.plugins.i18n

import java.util.function.Supplier

import grails.config.Settings
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.plugins.GrailsPlugin
import grails.plugins.GrailsPluginManager

import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver
import org.springframework.web.servlet.i18n.FixedLocaleResolver
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor
import org.springframework.web.servlet.i18n.SessionLocaleResolver

import org.grails.web.i18n.ParamsAwareLocaleChangeInterceptor

import spock.lang.Specification

class GrailsLocaleResolverAutoConfigurationSpec extends Specification {

    private WebApplicationContextRunner contextRunner() {
        GrailsApplication grailsApplication = new DefaultGrailsApplication()
        GrailsPluginManager pluginManager = Mock(GrailsPluginManager) {
            getAllPlugins() >> ([] as GrailsPlugin[])
        }
        Supplier<GrailsApplication> grailsApplicationSupplier = () -> grailsApplication
        Supplier<GrailsPluginManager> pluginManagerSupplier = () -> pluginManager
        new WebApplicationContextRunner()
                .withBean(GrailsApplication, grailsApplicationSupplier)
                .withBean(GrailsPluginManager, pluginManagerSupplier)
                .withConfiguration(AutoConfigurations.of(
                        PropertyPlaceholderAutoConfiguration,
                        GrailsLocaleResolverAutoConfiguration,
                        I18nAutoConfiguration))
    }

    void 'under @EnableWebMvc the WebMvcConfigurationSupport localeResolver is removed and the Grails session resolver wins'() {
        expect:
        contextRunner()
                .withUserConfiguration(EnableWebMvcConfig)
                .run { context ->
                    assert context.getBean('localeResolver') instanceof SessionLocaleResolver
                    assert context.getBeanNamesForType(AcceptHeaderLocaleResolver).length == 0
                    assert context.getBean(LocaleChangeInterceptor) instanceof ParamsAwareLocaleChangeInterceptor
                }
    }

    void 'grails.i18n.localeResolver=acceptHeader under @EnableWebMvc yields an AcceptHeaderLocaleResolver'() {
        expect:
        contextRunner()
                .withPropertyValues("${Settings.I18N_LOCALE_RESOLVER}=acceptHeader")
                .withUserConfiguration(EnableWebMvcConfig)
                .run { context ->
                    assert context.getBean('localeResolver') instanceof AcceptHeaderLocaleResolver
                    // the ?lang= interceptor is still registered; it no-ops for a read-only resolver
                    assert context.getBean(LocaleChangeInterceptor) instanceof ParamsAwareLocaleChangeInterceptor
                }
    }

    void 'an application-defined localeResolver bean is not removed'() {
        expect: 'a localeResolver contributed by the application (not WebMvcConfigurationSupport) survives'
        contextRunner()
                .withUserConfiguration(UserLocaleResolverConfig)
                .run { context ->
                    def resolver = context.getBean('localeResolver')
                    assert resolver instanceof FixedLocaleResolver
                    assert resolver.resolveLocale(null) == Locale.CANADA
                }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class EnableWebMvcConfig {
    }

    @Configuration(proxyBeanMethods = false)
    static class UserLocaleResolverConfig {

        @Bean
        LocaleResolver localeResolver() {
            new FixedLocaleResolver(Locale.CANADA)
        }
    }
}
