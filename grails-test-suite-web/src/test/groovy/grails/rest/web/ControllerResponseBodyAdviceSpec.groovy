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
package grails.rest.web

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice

import grails.artefact.Artefact
import grails.converters.json.NamedJsonConfigurationRegistry
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

/**
 * With Spring JSON rendering, respond writes JSON as Spring MVC writes the value a {@code @ResponseBody} handler method
 * of the controller returns, so the application's {@code ResponseBodyAdvice} beans apply to it: with the Grails mapper,
 * with a projection, with a named configuration, and with a converter of the application's.
 */
class ControllerResponseBodyAdviceSpec extends Specification implements ControllerUnitTest<AdvisedBodyController> {

    Closure doWithConfig() {
        { config -> config['grails.web.rendering.json.spring'] = true }
    }

    @Configuration
    static class AdviceConfiguration {

        @Bean
        HeaderBodyAdvice headerBodyAdvice() {
            new HeaderBodyAdvice()
        }

        @Bean
        EnvelopeBodyAdvice envelopeBodyAdvice() {
            new EnvelopeBodyAdvice()
        }

        @Bean
        OtherControllerBodyAdvice otherControllerBodyAdvice() {
            new OtherControllerBodyAdvice()
        }
    }

    void setup() {
        response.format = 'json'
    }

    void 'the advice of the application applies to a value respond writes with the Grails mapper'() {
        when:
        controller.respond(new AdvisedBody(title: 'Grails', secret: 'hidden'))

        then:
        response.status == 200
        response.getHeader('X-Advised') == JacksonJsonHttpMessageConverter.name
        response.json == [secret: 'hidden', title: 'Grails']
        !response.containsHeader('X-Other')
    }

    void 'the advice applies to a value respond writes with a projection, which the Grails mapper still applies'() {
        when:
        controller.respond(new AdvisedBody(title: 'Grails', secret: 'hidden'), excludes: ['secret'])

        then:
        response.getHeader('X-Advised') != null
        response.json == [title: 'Grails']
    }

    void 'the advice applies to a value respond writes with a named configuration'() {
        given:
        applicationContext.getBean(NamedJsonConfigurationRegistry).register('plain') { }

        when:
        controller.respond(new AdvisedBody(title: 'Grails', secret: 'hidden'), jsonConfiguration: 'plain')

        then:
        response.getHeader('X-Advised') != null
        response.json.title == 'Grails'
    }

    void 'the body an advice replaces the value with is the one written'() {
        when:
        controller.respond(new EnvelopedBody(title: 'Grails'))

        then:
        response.json == [data: [title: 'Grails']]
    }
}

@Artefact('Controller')
class AdvisedBodyController {
}

@Artefact('Controller')
class OtherBodyController {
}

class AdvisedBody {
    String title
    String secret
}

class EnvelopedBody {
    String title
}

/** Says which converter writes the body, as an application's advice may. */
@ControllerAdvice(assignableTypes = AdvisedBodyController)
class HeaderBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        true
    }

    @Override
    Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                           Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
                           ServerHttpResponse response) {
        response.headers.set('X-Advised', JacksonJsonHttpMessageConverter.isAssignableFrom(selectedConverterType) ?
                JacksonJsonHttpMessageConverter.name : selectedConverterType.name)
        body
    }
}

/** Puts a body of one class in an envelope, as an application's advice may. */
@ControllerAdvice(assignableTypes = AdvisedBodyController)
class EnvelopeBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        true
    }

    @Override
    Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                           Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
                           ServerHttpResponse response) {
        body instanceof EnvelopedBody ? [data: body] : body
    }
}

/** Advice for another controller, which the controller under test does not get. */
@ControllerAdvice(assignableTypes = OtherBodyController)
class OtherControllerBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        true
    }

    @Override
    Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                           Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
                           ServerHttpResponse response) {
        response.headers.set('X-Other', 'true')
        body
    }
}
