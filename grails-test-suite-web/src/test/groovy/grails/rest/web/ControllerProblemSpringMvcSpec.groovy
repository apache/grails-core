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
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.core.MethodParameter
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.web.ErrorResponse
import org.springframework.web.ErrorResponseException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice

import grails.artefact.Artefact
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

/**
 * respond writes a problem with the handler Spring MVC's handler adapter writes a {@code ResponseEntity} with, so the
 * application's {@code ResponseBodyAdvice} and {@code ErrorResponse.Interceptor} beans apply to it as to a handler method
 * of the controller.
 */
class ControllerProblemSpringMvcSpec extends Specification implements ControllerUnitTest<ProblemAdviceController> {

    Closure doWithConfig() {
        { config -> config['grails.web.rendering.json.spring'] = true }
    }

    @Configuration
    static class MvcConfiguration {

        @Bean
        ProblemBodyAdvice problemBodyAdvice() {
            new ProblemBodyAdvice()
        }

        @Bean
        OtherControllerAdvice otherControllerAdvice() {
            new OtherControllerAdvice()
        }

        @Bean
        RequestMappingHandlerAdapter requestMappingHandlerAdapter() {
            new RequestMappingHandlerAdapter().tap {
                messageConverters = [new JacksonJsonHttpMessageConverter()] as List<HttpMessageConverter<?>>
                errorResponseInterceptors = [new RecordingInterceptor()] as List<ErrorResponse.Interceptor>
            }
        }
    }

    void 'the ResponseBodyAdvice of the application applies to a problem respond writes, as to a handler method of the controller'() {
        when:
        controller.respond(ProblemDetail.forStatus(HttpStatus.CONFLICT))

        then:
        response.status == 409
        response.contentType == 'application/problem+json'
        response.json.advised == ProblemAdviceController.name
    }

    void 'advice for other controllers does not apply to a problem respond writes'() {
        when:
        controller.respond(ProblemDetail.forStatus(HttpStatus.CONFLICT))

        then:
        !response.json.containsKey('other')
    }

    void 'the ErrorResponse interceptors of the handler adapter see a problem respond writes'() {
        when:
        controller.respond(ProblemDetail.forStatus(HttpStatus.CONFLICT))

        then:
        response.json.intercepted == true
    }

    void 'validation errors written as a problem pass through the same advice'() {
        given:
        response.format = 'json'
        def errors = new BeanPropertyBindingResult(new Object(), 'book')
        errors.reject('invalid', 'Book is invalid')

        when:
        controller.respond(errors)

        then:
        response.status == 422
        response.json.advised == ProblemAdviceController.name
        response.json.intercepted == true
    }

    void 'an ErrorResponse respond is given is answered with its problem, status and headers, as Spring MVC answers one a handler method returns'() {
        given:
        request.requestURI = '/books/1'
        def exception = new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE)
        exception.headers.set(HttpHeaders.RETRY_AFTER, '30')

        when:
        controller.respond(exception)

        then:
        response.status == 503
        response.contentType == 'application/problem+json'
        response.getHeader(HttpHeaders.RETRY_AFTER) == '30'
        response.json.status == 503
        response.json.instance == '/books/1'

        and: 'the interceptors are given the ErrorResponse'
        response.json.source == 'ErrorResponseException'
    }

    void "the message codes of an ErrorResponse respond is given are resolved with the application's messages"() {
        given:
        Locale locale = LocaleContextHolder.locale
        messageSource.addMessage(ErrorResponse.getDefaultTitleMessageCode(BookTakenException), locale, 'Book taken')
        messageSource.addMessage(ErrorResponse.getDefaultDetailMessageCode(BookTakenException, null), locale, 'The book {0} is taken')

        when:
        controller.respond(new BookTakenException('Grails'))

        then:
        response.status == 409
        response.json.title == 'Book taken'
        response.json.detail == 'The book Grails is taken'
    }

    void 'a status respond is given decides the status of an ErrorResponse, which keeps its headers'() {
        given:
        def exception = new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE)
        exception.headers.set(HttpHeaders.RETRY_AFTER, '30')

        when:
        controller.respond(exception, status: 429)

        then:
        response.status == 429
        response.getHeader(HttpHeaders.RETRY_AFTER) == '30'
        response.json.status == 503
    }
}

@Artefact('Controller')
class ProblemAdviceController {
}

@Artefact('Controller')
class OtherAdvisedController {
}

/** Advice for the controller under test, which adds the name of the class it is applied for. */
@ControllerAdvice(assignableTypes = ProblemAdviceController)
class ProblemBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        true
    }

    @Override
    Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                           Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
                           ServerHttpResponse response) {
        if (body instanceof ProblemDetail) {
            ((ProblemDetail) body).setProperty('advised', returnType.containingClass.name)
        }
        body
    }
}

/** Advice for another controller, which a problem the controller under test responds with does not get. */
@ControllerAdvice(assignableTypes = OtherAdvisedController)
class OtherControllerAdvice implements ResponseBodyAdvice<Object> {

    @Override
    boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        true
    }

    @Override
    Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                           Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
                           ServerHttpResponse response) {
        if (body instanceof ProblemDetail) {
            ((ProblemDetail) body).setProperty('other', true)
        }
        body
    }
}

class RecordingInterceptor implements ErrorResponse.Interceptor {

    @Override
    void handleError(ProblemDetail detail, ErrorResponse errorResponse) {
        detail.setProperty('intercepted', true)
        if (errorResponse != null) {
            detail.setProperty('source', errorResponse.getClass().simpleName)
        }
    }
}

/** An exception with a problem, whose detail message code takes the title of the book. */
class BookTakenException extends ErrorResponseException {

    BookTakenException(String title) {
        super(HttpStatus.CONFLICT, ProblemDetail.forStatus(HttpStatus.CONFLICT), null, null, [title] as Object[])
    }
}
