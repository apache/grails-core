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
package org.grails.plugins.xml

import groovy.xml.XmlSlurper

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.xml.JacksonXmlHttpMessageConverter
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice

import grails.artefact.Artefact
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

/**
 * With Spring XML rendering, respond writes a bean in XML as Spring MVC writes the value a {@code @ResponseBody} handler
 * method of the controller returns, so the application's {@code ResponseBodyAdvice} beans that apply to the controller
 * apply to it, as they do to the JSON respond writes through Spring MVC.
 */
class XmlResponseBodyAdviceSpec extends Specification implements ControllerUnitTest<XmlAdvisedController> {

    Closure doWithConfig() {
        { config -> config['grails.web.rendering.xml.spring'] = true }
    }

    @Configuration
    static class AdviceConfiguration {

        @Bean
        XmlTitleAdvice xmlTitleAdvice() {
            new XmlTitleAdvice()
        }

        @Bean
        XmlOtherControllerAdvice xmlOtherControllerAdvice() {
            new XmlOtherControllerAdvice()
        }
    }

    void setup() {
        response.format = 'xml'
    }

    void 'the advice that applies to the controller is given a bean respond writes with Spring XML first'() {
        when:
        controller.respond(new XmlAdvisedBody(title: 'grails'))

        then: 'with the converter that writes it, and what it returns written in the type respond negotiated'
        response.status == 200
        response.getHeader('X-Advised') == JacksonXmlHttpMessageConverter.name
        response.contentType == MediaType.TEXT_XML_VALUE
        new XmlSlurper().parseText(response.contentAsString).title.text() == 'GRAILS'

        and: 'the advice for another controller is not'
        !response.containsHeader('X-Other')
    }

    void 'a value the legacy XML converter writes is not given to the advice'() {
        when:
        controller.respond([title: 'grails'])

        then:
        response.status == 200
        !response.containsHeader('X-Advised')
        response.contentAsString.contains('grails')
    }
}

@Artefact('Controller')
class XmlAdvisedController { }

@Artefact('Controller')
class XmlOtherController { }

class XmlAdvisedBody {
    String title
}

/** Upper-cases the title of a body and names the converter that writes it, as an application's advice may. */
@ControllerAdvice(assignableTypes = XmlAdvisedController)
class XmlTitleAdvice implements ResponseBodyAdvice<Object> {

    @Override
    boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        true
    }

    @Override
    Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                           Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
                           ServerHttpResponse response) {
        response.headers.set('X-Advised', selectedConverterType.name)
        body instanceof XmlAdvisedBody ? new XmlAdvisedBody(title: ((XmlAdvisedBody) body).title.toUpperCase()) : body
    }
}

@ControllerAdvice(assignableTypes = XmlOtherController)
class XmlOtherControllerAdvice implements ResponseBodyAdvice<Object> {

    @Override
    boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        true
    }

    @Override
    Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                           Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
                           ServerHttpResponse response) {
        response.headers.set('X-Other', 'applied')
        body
    }
}
