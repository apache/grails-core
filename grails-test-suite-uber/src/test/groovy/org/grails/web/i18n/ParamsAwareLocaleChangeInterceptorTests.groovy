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
package org.grails.web.i18n

import grails.util.GrailsWebMockUtil
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.servlet.DispatcherServlet
import org.springframework.web.servlet.i18n.SessionLocaleResolver

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotEquals

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ParamsAwareLocaleChangeInterceptorTests {

    @AfterEach
    protected void tearDown() {
        RequestContextHolder.resetRequestAttributes()
    }

    @Test
    void testSwitchLocaleWithStringArrayParamsObject() {

        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        def request = webRequest.getCurrentRequest()
        def response = webRequest.getCurrentResponse()

        SessionLocaleResolver localeResolver = new SessionLocaleResolver()

        request.setAttribute(DispatcherServlet.LOCALE_RESOLVER_ATTRIBUTE,localeResolver)

        def localeChangeInterceptor = new ParamsAwareLocaleChangeInterceptor()
        localeChangeInterceptor.paramName = "lang"

        def locale = localeResolver.resolveLocale(request)
        assert localeChangeInterceptor.preHandle(request, response, null)

        assertEquals locale, localeResolver.resolveLocale(request)

        webRequest.params.lang = ["de_DE", "en_GB"] as String[]

        assert localeChangeInterceptor.preHandle(request, response, null)

        assertNotEquals locale, localeResolver.resolveLocale(request)

        locale = localeResolver.resolveLocale(request)

        assertEquals "de", locale.getLanguage()
        assertEquals "DE", locale.getCountry()
    }

    @Test
    void testSwitchLocaleWithParamsObject() {

        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        def request = webRequest.getCurrentRequest()
        def response = webRequest.getCurrentResponse()

        SessionLocaleResolver localeResolver = new SessionLocaleResolver()

        request.setAttribute(DispatcherServlet.LOCALE_RESOLVER_ATTRIBUTE,localeResolver)

        def localeChangeInterceptor = new ParamsAwareLocaleChangeInterceptor()
        localeChangeInterceptor.paramName = "lang"

        def locale = localeResolver.resolveLocale(request)
        assert localeChangeInterceptor.preHandle(request, response, null)

        assertEquals locale, localeResolver.resolveLocale(request)

        webRequest.params.lang = "de_DE"

        assert localeChangeInterceptor.preHandle(request, response, null)

        assertNotEquals locale, localeResolver.resolveLocale(request)

        locale = localeResolver.resolveLocale(request)

        assertEquals "de", locale.getLanguage()
        assertEquals "DE", locale.getCountry()
    }

    @Test
    void testSwitchLocaleWithRequestParameter() {

        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        MockHttpServletRequest request = webRequest.getCurrentRequest()
        def response = webRequest.getCurrentResponse()

        SessionLocaleResolver localeResolver = new SessionLocaleResolver()

        request.setAttribute(DispatcherServlet.LOCALE_RESOLVER_ATTRIBUTE,localeResolver)

        def localeChangeInterceptor = new ParamsAwareLocaleChangeInterceptor()
        localeChangeInterceptor.paramName = "lang"

        def locale = localeResolver.resolveLocale(request)
        assert localeChangeInterceptor.preHandle(request, response, null)

        assertEquals locale, localeResolver.resolveLocale(request)

        request.addParameter "lang", "de_DE"

        assert localeChangeInterceptor.preHandle(request, response, null)

        assertNotEquals locale, localeResolver.resolveLocale(request)

        locale = localeResolver.resolveLocale(request)

        assertEquals "de", locale.getLanguage()
        assertEquals "DE", locale.getCountry()
    }
}
