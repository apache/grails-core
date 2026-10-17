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

import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.validation.BeanPropertyBindingResult

import grails.artefact.Artefact
import grails.rest.render.errors.ValidationProblemDetailFactory
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

/**
 * An application's own {@code ValidationProblemDetailFactory}, whatever its bean name, is the one {@code respond} uses
 * for validation errors and the one a controller that declares the factory gets.
 */
class ValidationProblemDetailFactoryBeanSpec extends Specification implements ControllerUnitTest<ProblemFactoryController> {

    Closure doWithConfig() {
        { config -> config['grails.web.rendering.json.spring'] = true }
    }

    @Configuration
    static class FactoryConfiguration {

        @Bean
        ValidationProblemDetailFactory includingRejectedValues(MessageSource messageSource) {
            new ValidationProblemDetailFactory(true, messageSource)
        }
    }

    void setup() {
        response.format = 'json'
    }

    void 'respond writes validation errors with the application factory'() {
        when:
        controller.respond(errors())

        then:
        response.status == 422
        response.json.errors.first().rejectedValue == 'secret'
    }

    void 'a controller that declares the factory gets the application factory'() {
        when:
        controller.respond(controller.validationProblemDetailFactory.create(errors()))

        then:
        response.status == 422
        response.contentType == 'application/problem+json;charset=UTF-8'
        response.json.errors.first().rejectedValue == 'secret'
    }

    static BeanPropertyBindingResult errors() {
        def errors = new BeanPropertyBindingResult(new ProblemFactoryBody(name: 'secret'), 'body')
        errors.rejectValue('name', 'invalid', 'Name is invalid')
        errors
    }
}

/**
 * An application {@code @Bean} method of the name the plugin registers its own factory under replaces it.
 */
class ValidationProblemDetailFactoryConventionalNameSpec extends Specification implements ControllerUnitTest<ProblemFactoryController> {

    Closure doWithConfig() {
        { config -> config['grails.web.rendering.json.spring'] = true }
    }

    @Configuration
    static class FactoryConfiguration {

        @Bean
        ValidationProblemDetailFactory validationProblemDetailFactory(MessageSource messageSource) {
            new ValidationProblemDetailFactory(true, messageSource)
        }
    }

    void setup() {
        response.format = 'json'
    }

    void 'respond and a controller that declares the factory both get the application factory'() {
        when:
        controller.respond(ValidationProblemDetailFactoryBeanSpec.errors())

        then:
        response.json.errors.first().rejectedValue == 'secret'
        controller.validationProblemDetailFactory.create(ValidationProblemDetailFactoryBeanSpec.errors()).properties.errors
                .first().rejectedValue == 'secret'
    }
}

@Artefact('Controller')
class ProblemFactoryController {
    ValidationProblemDetailFactory validationProblemDetailFactory
}

class ProblemFactoryBody {
    String name
}
