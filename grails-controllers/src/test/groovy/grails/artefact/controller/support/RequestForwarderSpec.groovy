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
package grails.artefact.controller.support

import grails.util.GrailsWebMockUtil
import grails.web.mapping.LinkGenerator
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.servlet.mvc.ParameterCreationListener
import org.grails.web.util.GrailsApplicationAttributes
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockRequestDispatcher
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.servlet.ModelAndView
import spock.lang.Specification

import jakarta.servlet.RequestDispatcher
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse

/**
 * Created by graemerocher on 21/02/2017.
 */
class RequestForwarderSpec extends Specification {

    void "test request forward cleans up request attributes after forward"() {

        setup:
        def applicationContext = Mock(WebApplicationContext)
        def linkGenerator = Mock(LinkGenerator)
        linkGenerator.link(_) >> "/test"
        applicationContext.getBean(LinkGenerator) >> linkGenerator
        applicationContext.getBeansOfType(ParameterCreationListener) >> [:]
        MockRequestDispatcher mockRequestDispatcher = new MockRequestDispatcher("test") {
            @Override
            void forward(ServletRequest request, ServletResponse response) {
                request.setAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW, new ModelAndView())
                super.forward(request, response)
            }
        };

        GrailsWebRequest webRequest = GrailsWebMockUtil.bindMockWebRequest(applicationContext, new MockHttpServletRequest() {
            @Override
            RequestDispatcher getRequestDispatcher(String path) {
                return mockRequestDispatcher
            }
        }, new MockHttpServletResponse())

        when:"A forward is issued that populates the model"
        TestForwarder forwarder = new TestForwarder()

        forwarder.doForward()

        then:"The model and view attribute is cleared"
        webRequest.request.getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW) == null


        cleanup:
        RequestContextHolder.setRequestAttributes(null)
    }
}

class TestForwarder implements RequestForwarder {
    void doForward() {
        forward(controller:"blah")
    }
}
