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
package grails.plugin.springsecurity

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.web.savedrequest.RequestCache
import org.springframework.security.web.util.matcher.AnyRequestMatcher
import spock.lang.Specification

import grails.testing.web.GrailsWebUnitTest
import grails.web.CamelCaseUrlConverter

/**
 * What the plugin's {@code requestCache} bean saves, as {@code requestCache.ignoredPatterns} and
 * {@code requestMatcherBeanClass} configure it.
 */
abstract class AbstractRequestCacheConfigurationSpec extends Specification implements GrailsWebUnitTest {

    Set<String> getIncludePlugins() {
        ['core', 'springSecurityCore'] as Set
    }

    Closure doWithSpring() {
        { ->
            grailsUrlConverter(CamelCaseUrlConverter)
        }
    }

    Closure doWithConfig() {
        { config ->
            config.grails.plugin.springsecurity.securityConfigType = 'InterceptUrlMap'
            configure(config.grails.plugin.springsecurity)
        }
    }

    protected abstract void configure(Object securityConfig)

    void cleanupSpec() {
        SpringSecurityUtils.resetSecurityConfig()
        ReflectionUtils.application = null
        SpringSecurityUtils.application = null
    }

    protected boolean saves(String path) {
        def requestCache = applicationContext.getBean('requestCache', RequestCache)
        def request = new MockHttpServletRequest('GET', path)
        def response = new MockHttpServletResponse()
        requestCache.saveRequest(request, response)
        requestCache.getRequest(request, response) != null
    }
}

class DefaultRequestCacheConfigurationSpec extends AbstractRequestCacheConfigurationSpec {

    protected void configure(Object securityConfig) {
    }

    void 'by default the requests a browser sends on its own are not saved'() {
        expect:
        !saves('/manifest.json')
        !saves('/.well-known/appspecific/com.chrome.devtools.json')
        saves('/book/show')
    }
}

class CustomIgnoredPatternsConfigurationSpec extends AbstractRequestCacheConfigurationSpec {

    protected void configure(Object securityConfig) {
        securityConfig.requestCache.ignoredPatterns = ['/poll/**']
    }

    void 'the patterns an application configures replace the default ones'() {
        expect:
        !saves('/poll/status')
        saves('/manifest.json')
    }
}

class NoIgnoredPatternsConfigurationSpec extends AbstractRequestCacheConfigurationSpec {

    protected void configure(Object securityConfig) {
        securityConfig.requestCache.ignoredPatterns = []
    }

    void 'no patterns save every request'() {
        expect:
        saves('/manifest.json')
        saves('/book/show')
    }
}

class EmptyStringIgnoredPatternsConfigurationSpec extends AbstractRequestCacheConfigurationSpec {

    protected void configure(Object securityConfig) {
        securityConfig.requestCache.ignoredPatterns = ''
    }

    void 'an empty value, which an empty YAML list flattens to, saves every request'() {
        expect:
        saves('/manifest.json')
    }
}

class RequestMatcherBeanClassConfigurationSpec extends AbstractRequestCacheConfigurationSpec {

    protected void configure(Object securityConfig) {
        securityConfig.requestMatcherBeanClass = AnyRequestMatcher
    }

    void 'a class configured in place of the default matcher is created without the patterns'() {
        expect:
        applicationContext.getBean('requestMatcher') instanceof AnyRequestMatcher
        saves('/manifest.json')
    }
}
