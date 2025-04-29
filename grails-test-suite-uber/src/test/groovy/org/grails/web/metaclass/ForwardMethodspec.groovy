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
package org.grails.web.metaclass

import grails.artefact.Controller
import grails.web.mapping.LinkGenerator
import org.springframework.web.context.request.RequestContextHolder

import jakarta.servlet.RequestDispatcher
import jakarta.servlet.ServletContext
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
 
import grails.web.UrlConverter
 
import org.grails.web.util.GrailsApplicationAttributes
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.context.ApplicationContext
 
import spock.lang.Specification
 
class ForwardMethodSpec extends Specification {
 
    ForwardMethodTest forwardMethod
    ApplicationContext appContext
    ServletContext servletContext
    HttpServletRequest request
    HttpServletResponse response
    RequestDispatcher dispatcher
    GrailsWebRequest webRequest
    UrlConverter urlConverter
    
    def setup() {
        forwardMethod = new ForwardMethodTest()
        
        appContext = Mock(ApplicationContext)
        servletContext = Mock(ServletContext)
        request = Mock(HttpServletRequest)
        response = Mock(HttpServletResponse)
        dispatcher = Mock(RequestDispatcher)
        urlConverter = Mock(UrlConverter)

        def linkGenerator = Mock(LinkGenerator)
        linkGenerator.link(_) >> { args ->
            def map = args[0]
            "/$map.controller/$map.action"
        }
        appContext.getBean(LinkGenerator) >> linkGenerator
 
        webRequest = new GrailsWebRequest(request, response, servletContext, appContext)
        RequestContextHolder.setRequestAttributes(webRequest)
            
        dispatcher.forward(_,_) >> { }
        request.getAttribute(GrailsApplicationAttributes.ACTION_NAME_ATTRIBUTE) >> { 'fooBar' }     
        request.getAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE) >> { 'foo' }
        request.getAttribute(GrailsApplicationAttributes.WEB_REQUEST) >> { webRequest }
        request.getRequestDispatcher(_) >> { dispatcher }
        def parameters = [:]
        request.getParameterMap() >> { parameters }
    }

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }
    
    def 'Test forward request with controller and action params and url converter'() {
        setup:
            Map params = [controller : 'foo', action : 'fooBar', model : [param1 : 1, param2 : 2]]
            urlConverter.toUrlElement(_) >> { it[0]?.toLowerCase() }
            forwardMethod.urlConverter = urlConverter
        when:
            def forwardUri = forwardMethod.forward(params)
        then:
            forwardUri == '/foo/foobar'
    }
    
    def 'Test forward request with controller and action params and url converter in app context'() {
        setup:
            Map params = [controller : 'foo', action : 'fooBar', model : [param1 : 1, param2 : 2]]
            urlConverter.toUrlElement(_) >> { it[0]?.toLowerCase() }
            forwardMethod.urlConverter = urlConverter
        when:
            def forwardUri = forwardMethod.forward(params)
        then:
            forwardUri == '/foo/foobar'
    }
    
    def 'Test forward request with controller and action params without an url converter'() {
        setup:
            Map params = [controller : 'foo', action : 'fooBar', model : [param1 : 1, param2 : 2]]
            appContext.getBean("grailsUrlConverter", UrlConverter) >> { null }
            forwardMethod.urlConverter = null
        when:
            def forwardUri = forwardMethod.forward(params)
        then:
            forwardUri == '/foo/fooBar'
    }   
}
class ForwardMethodTest implements Controller {}