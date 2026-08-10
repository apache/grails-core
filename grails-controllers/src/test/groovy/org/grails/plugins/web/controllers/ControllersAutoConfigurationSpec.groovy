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

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication

import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.servlet.autoconfigure.MultipartAutoConfiguration
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.boot.web.servlet.AbstractFilterRegistrationBean
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.boot.web.servlet.ServletContextInitializerBeans
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletRegistrationBean
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.support.StaticWebApplicationContext
import org.springframework.web.filter.RequestContextFilter
import org.springframework.web.servlet.handler.SimpleMappingExceptionResolver

import org.grails.web.config.http.GrailsFilters
import org.grails.web.errors.GrailsExceptionResolver
import org.grails.web.servlet.mvc.GrailsWebRequestFilter

import spock.lang.Specification

class ControllersAutoConfigurationSpec extends Specification {

    def applicationContext = Mock(ApplicationContext) {
        getBeansOfType(_) >> [:]
    }

    def autoConfiguration = new ControllersAutoConfiguration()

    void 'grailsWebRequest filter is a RequestContextFilter so Boot WebMvcAutoConfiguration backs off its own RequestContextFilter'() {
        when: 'the Grails request-binding filter bean is created'
        def filter = autoConfiguration.grailsWebRequest(applicationContext)

        then: 'it is exposed as a RequestContextFilter, the type Boot @ConditionalOnMissingBean keys on'
        filter != null
        filter instanceof RequestContextFilter
    }

    void 'grailsWebRequestFilter registers the GrailsWebRequestFilter with the Grails request-filter order'() {
        given: 'the Grails request-binding filter'
        def filter = autoConfiguration.grailsWebRequest(applicationContext)

        when: 'it is wrapped in a registration bean'
        def registrationBean = autoConfiguration.grailsWebRequestFilter(filter)

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
        expect: 'Boot does not contribute its OrderedRequestContextFilter, leaving GrailsWebRequest bound'
        new WebApplicationContextRunner()
                .withBean(GrailsApplication, grailsApplicationSupplier())
                .withConfiguration(AutoConfigurations.of(ControllersAutoConfiguration, WebMvcAutoConfiguration))
                .run { context ->
                    assert !context.containsBean('requestContextFilter')
                    assert context.getBeanNamesForType(GrailsWebRequestFilter).length == 1
                    assert grailsWebRequestFilterRegistrations(context) == 1
                }
    }

    void 'a user-defined grailsWebRequestFilter registration bean makes the auto-configured one back off'() {
        given: 'a user-defined registration bean under the auto-configured bean name'
        def userRegistration = new FilterRegistrationBean<>()
        def userRegistrationSupplier = () -> userRegistration

        expect: 'the user bean wins and the framework filter backs off entirely — no second, Boot-adapted copy on the chain'
        new WebApplicationContextRunner()
                .withBean(GrailsApplication, grailsApplicationSupplier())
                .withBean('grailsWebRequestFilter', FilterRegistrationBean, userRegistrationSupplier)
                .withConfiguration(AutoConfigurations.of(ControllersAutoConfiguration, WebMvcAutoConfiguration))
                .run { context ->
                    assert context.getBean('grailsWebRequestFilter').is(userRegistration)
                    assert context.getBeanNamesForType(GrailsWebRequestFilter).length == 0
                    assert grailsWebRequestFilterRegistrations(context) == 0
                }
    }

    void 'Boot multipart configuration reaches the dispatcher servlet registration'() {
        expect:
        new WebApplicationContextRunner()
                .withBean(GrailsApplication, grailsApplicationSupplier())
                .withPropertyValues('spring.servlet.multipart.maxFileSize=1MB')
                .withConfiguration(AutoConfigurations.of(
                        ControllersAutoConfiguration,
                        WebMvcAutoConfiguration,
                        MultipartAutoConfiguration))
                .run { context ->
                    assert context.getBean(DispatcherServletRegistrationBean).multipartConfig.maxFileSize == 1024 * 1024
                }
    }

    void 'disabled Boot multipart configuration is not added to the dispatcher servlet registration'() {
        expect:
        new WebApplicationContextRunner()
                .withBean(GrailsApplication, grailsApplicationSupplier())
                .withPropertyValues('spring.servlet.multipart.enabled=false')
                .withConfiguration(AutoConfigurations.of(
                        ControllersAutoConfiguration,
                        WebMvcAutoConfiguration,
                        MultipartAutoConfiguration))
                .run { context ->
                    assert context.getBean(DispatcherServletRegistrationBean).multipartConfig == null
                }
    }

    void 'the dispatcher servlet registration starts without the legacy multipart property'() {
        expect:
        new WebApplicationContextRunner()
                .withBean(GrailsApplication, grailsApplicationSupplier())
                .withConfiguration(AutoConfigurations.of(
                        ControllersAutoConfiguration,
                        WebMvcAutoConfiguration,
                        MultipartAutoConfiguration))
                .run { context ->
                    assert context.startupFailure == null
                }
    }

    void 'a user-defined GrailsWebMvcConfigurer bean makes the auto-configured webMvcConfig back off'() {
        given: 'a user-defined web MVC configurer'
        def userConfigurer = new ControllersAutoConfiguration.GrailsWebMvcConfigurer(0, false, '/custom/**')
        def userConfigurerSupplier = () -> userConfigurer

        expect: 'the user bean wins and only one GrailsWebMvcConfigurer exists'
        new WebApplicationContextRunner()
                .withBean(GrailsApplication, grailsApplicationSupplier())
                .withBean(ControllersAutoConfiguration.GrailsWebMvcConfigurer, userConfigurerSupplier)
                .withConfiguration(AutoConfigurations.of(ControllersAutoConfiguration, WebMvcAutoConfiguration))
                .run { context ->
                    def names = context.getBeanNamesForType(ControllersAutoConfiguration.GrailsWebMvcConfigurer)
                    assert names.length == 1
                    assert context.getBean(names[0]).is(userConfigurer)
                }
    }

    void 'the default exceptionHandler maps exceptions to the error view'() {
        given: 'the auto-configured exception resolver, wired the way the runtime does'
        def exceptionResolver = autoConfiguration.exceptionHandler().tap {
            grailsApplication = new DefaultGrailsApplication()
            servletContext = servletContextWithWebApplicationContext()
        }

        when:
        def modelAndView = exceptionResolver.resolveException(
                new MockHttpServletRequest(), new MockHttpServletResponse(), null, new Exception('boom'))

        then:
        modelAndView.viewName == '/error'
    }

    void 'the exceptionHandler default is auto-configured when no user bean exists'() {
        expect:
        new WebApplicationContextRunner()
                .withBean(GrailsApplication, grailsApplicationSupplier())
                .withConfiguration(AutoConfigurations.of(ControllersAutoConfiguration, WebMvcAutoConfiguration))
                .run { context ->
                    assert context.getBean('exceptionHandler') instanceof GrailsExceptionResolver
                }
    }

    void 'a user-defined exceptionHandler bean makes the auto-configured default back off'() {
        given: 'a user-defined exception resolver under the auto-configured bean name'
        def userResolver = new SimpleMappingExceptionResolver()
        def userResolverSupplier = () -> userResolver

        expect: 'the user bean wins and the framework default is never registered'
        new WebApplicationContextRunner()
                .withBean('exceptionHandler', SimpleMappingExceptionResolver, userResolverSupplier)
                .withBean(GrailsApplication, grailsApplicationSupplier())
                .withConfiguration(AutoConfigurations.of(ControllersAutoConfiguration, WebMvcAutoConfiguration))
                .run { context ->
                    assert context.getBean('exceptionHandler').is(userResolver)
                    assert context.getBeanNamesForType(GrailsExceptionResolver).length == 0
                }
    }

    private static MockServletContext servletContextWithWebApplicationContext() {
        def servletContext = new MockServletContext()
        servletContext.setAttribute(
                WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE,
                new StaticWebApplicationContext().tap {
                    it.servletContext = servletContext
                    refresh()
                    beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
                }
        )
        servletContext
    }

    private Supplier<GrailsApplication> grailsApplicationSupplier() {
        () -> Mock(GrailsApplication) {
            getClassLoader() >> getClass().classLoader
        }
    }

    // Reconstructs the servlet filter chain the way Boot assembles it at container start, so the specs
    // assert on the real chain — including any raw filter bean Boot auto-adapts onto "/*" — rather than
    // mere bean presence in the context.
    private static int grailsWebRequestFilterRegistrations(ConfigurableApplicationContext context) {
        new ServletContextInitializerBeans(context.beanFactory).count { initializer ->
            initializer instanceof AbstractFilterRegistrationBean &&
                    ((AbstractFilterRegistrationBean) initializer).filter instanceof GrailsWebRequestFilter
        }
    }
}
