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
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.ErrorResponse
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter

import grails.testing.web.controllers.ControllerUnitTest
import org.grails.plugins.web.rest.render.SpringMessageConverters
import spock.lang.Specification

/**
 * A web unit test has Spring MVC's handler adapter, built as Spring MVC builds it, so the {@code ResponseBodyAdvice} and
 * {@code ErrorResponse.Interceptor} beans a test declares apply to a problem {@code respond} writes, as in the
 * application.
 */
class ControllerProblemHandlerAdapterSpec extends Specification implements ControllerUnitTest<ProblemAdviceController> {

    @Configuration
    static class AdviceConfiguration {

        @Bean
        ProblemBodyAdvice problemBodyAdvice() {
            new ProblemBodyAdvice()
        }

        @Bean
        WebMvcConfigurer interceptingConfigurer() {
            new WebMvcConfigurer() {
                @Override
                void addErrorResponseInterceptors(List<ErrorResponse.Interceptor> interceptors) {
                    interceptors.add(new RecordingInterceptor())
                }
            }
        }
    }

    void 'the advice and interceptors a test declares apply to a problem respond writes'() {
        when:
        controller.respond(ProblemDetail.forStatus(HttpStatus.CONFLICT))

        then:
        response.status == 409
        response.contentType == 'application/problem+json'
        response.json.advised == ProblemAdviceController.name
        response.json.intercepted == true
    }

    void 'the handler adapter writes with the converters the test is given'() {
        expect:
        applicationContext.getBean('requestMappingHandlerAdapter', RequestMappingHandlerAdapter).messageConverters ==
                applicationContext.getBean(SpringMessageConverters).converters
    }
}
