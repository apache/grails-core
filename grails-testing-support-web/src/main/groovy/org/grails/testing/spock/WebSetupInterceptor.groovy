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

package org.grails.testing.spock

import groovy.transform.CompileStatic

import jakarta.servlet.ServletContext

import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation

import org.springframework.web.servlet.DispatcherServlet

import grails.testing.web.GrailsWebUnitTest
import grails.util.GrailsWebMockUtil
import org.grails.plugins.testing.GrailsMockHttpServletRequest
import org.grails.plugins.testing.GrailsMockHttpServletResponse
import org.grails.web.servlet.mvc.GrailsWebRequest

@CompileStatic
class WebSetupInterceptor implements IMethodInterceptor {
    @Override
    void intercept(IMethodInvocation invocation) throws Throwable {
        GrailsWebUnitTest test = (GrailsWebUnitTest)invocation.instance
        setup(test)
        invocation.proceed()
    }

    void setup(GrailsWebUnitTest test) {
        def applicationContext = test.applicationContext
        GrailsMockHttpServletRequest request = new GrailsMockHttpServletRequest((ServletContext)test.servletContext)
        request.setAttribute(DispatcherServlet.LOCALE_RESOLVER_ATTRIBUTE, applicationContext.getBean('localeResolver'))
        request.method = 'GET'
        GrailsMockHttpServletResponse response = new GrailsMockHttpServletResponse()
        GrailsWebRequest webRequest = GrailsWebMockUtil.bindMockWebRequest(applicationContext, request, response)
        test.webRequest = webRequest
    }


}
