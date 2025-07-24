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

import groovy.transform.CompileStatic

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.propertyeditors.LocaleEditor
import org.springframework.web.servlet.DispatcherServlet
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor
import org.springframework.web.servlet.support.RequestContextUtils

import org.grails.web.servlet.mvc.GrailsWebRequest

/**
 * A LocaleChangeInterceptor instance that is aware of the Grails params object.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class ParamsAwareLocaleChangeInterceptor extends LocaleChangeInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(ParamsAwareLocaleChangeInterceptor)

    String paramName = DEFAULT_PARAM_NAME

    LocaleResolver localeResolver

    void setParamName(String name) {
        paramName = name
        super.setParamName name
    }

    @Autowired(required = false)
    void setLocaleResolver(LocaleResolver localeResolver) {
        this.localeResolver = localeResolver
    }

    @Override
    boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        GrailsWebRequest webRequest = GrailsWebRequest.lookup(request)

        def params = webRequest.params

        def localeParam = params?.get(paramName)
        if (!localeParam) {
            return super.preHandle(request, response, handler)
        }

        try {
            // choose first if multiple specified
            if (localeParam.getClass().isArray()) {
                localeParam = ((Object[])localeParam)[0]
            }
            def localeResolver = RequestContextUtils.getLocaleResolver(request)
            if(localeResolver == null) {
                localeResolver = this.localeResolver
                request.setAttribute(DispatcherServlet.LOCALE_RESOLVER_ATTRIBUTE, localeResolver)
            }
            def localeEditor = new LocaleEditor()
            localeEditor.setAsText localeParam?.toString()
            localeResolver?.setLocale request, response, (Locale)localeEditor.value
            return true
        }
        catch (Exception e) {
            LOG.error("Error intercepting locale change: ${e.message}", e)
            return true
        }
    }
}
