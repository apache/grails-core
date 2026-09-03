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

package org.grails.web.databinding.bindingsource

import grails.databinding.CollectionDataBindingSource
import grails.databinding.DataBindingSource
import grails.databinding.SimpleMapDataBindingSource
import grails.web.mime.MimeType
import grails.web.servlet.mvc.GrailsParameterMap
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import org.grails.web.util.HiddenHttpMethod
import org.springframework.http.HttpMethod
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import jakarta.servlet.ServletContext

/**
 * Created by Jim on 8/22/2016.
 */
class AbstractRequestBodyDataBindingSourceCreatorSpec extends Specification {

    @Shared
    AbstractRequestBodyDataBindingSourceCreator bindingSourceCreator

    @Shared
    ServletContext servletContext = new MockServletContext()

    void setupSpec() {
        bindingSourceCreator = new AbstractRequestBodyDataBindingSourceCreator() {

            @Override
            protected DataBindingSource createBindingSource(Reader reader) {
                return new SimpleMapDataBindingSource([id: "request"])
            }

            @Override
            protected CollectionDataBindingSource createCollectionBindingSource(Reader reader) {
                String body = reader.text
                return { -> [new SimpleMapDataBindingSource([id: body])] } as CollectionDataBindingSource
            }
        }
    }

    MockHttpServletRequest build(String method, String content) {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(method, new URI("")).param("id", "url")
        if (content != null) {
            builder.content(content)
        }
        MockHttpServletRequest request = builder.buildRequest(servletContext)
        request.setAttribute(GrailsApplicationAttributes.WEB_REQUEST, new GrailsWebRequest(request, new MockHttpServletResponse(), servletContext))
        request
    }

    @Unroll
    void "test binding request #request.method with content length #request.contentLength"() {
        given:
        MimeType mimeType = MimeType.ALL
        DataBindingSource source

        when:
        source = bindingSourceCreator.createDataBindingSource(mimeType, Object, request)

        then:
        source.identifierValue == expectedSource

        where:
        expectedSource | request
        "url"          | build("GET", null)
        "url"          | build("GET", "")
        "url"          | build("GET", "x")
        "url"          | build("DELETE", null)
        "url"          | build("DELETE", "")
        "url"          | build("DELETE", "x")
        "request"      | build("POST", null)
        "url"          | build("POST", "")
        "request"      | build("POST", "x")
        "request"      | build("PUT", null)
        "url"          | build("PUT", "")
        "request"      | build("PUT", "x")
    }

    @Issue('https://github.com/apache/grails-core/issues/16280')
    void "test a collection binding source built from a GrailsParameterMap reads the request body"() {
        given: 'a parameter map over a request whose body carries the binding source'
        MockHttpServletRequest request = build('POST', 'from the body')
        GrailsParameterMap params = new GrailsParameterMap(request)

        when: 'the creator resolves the underlying request through request()'
        CollectionDataBindingSource source = bindingSourceCreator
                .createCollectionDataBindingSource(MimeType.ALL, Object, params)

        then:
        source.dataBindingSources*.identifierValue == ['from the body']
    }

    void "the body is bound or not according to the method the request routed as"() {
        given: "a POST the dispatcher resolved as DELETE, which is a method with no body to bind"
        MockHttpServletRequest request = build("POST", "x")
        request.setAttribute(HiddenHttpMethod.OVERRIDDEN_METHOD_ATTRIBUTE, "DELETE")

        when:
        DataBindingSource source = bindingSourceCreator.createDataBindingSource(MimeType.ALL, Object, request)

        then: "the body is left alone, as it is when a servlet filter rewrote the method instead"
        source.identifierValue == "url"
    }

    void "a POST that asked for nothing still binds its body"() {
        given:
        MockHttpServletRequest request = build("POST", "x")

        when:
        DataBindingSource source = bindingSourceCreator.createDataBindingSource(MimeType.ALL, Object, request)

        then:
        source.identifierValue == "request"
    }
}
