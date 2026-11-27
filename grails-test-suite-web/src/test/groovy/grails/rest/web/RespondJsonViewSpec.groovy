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

import com.fasterxml.jackson.annotation.JsonView

import org.springframework.validation.BeanPropertyBindingResult

import grails.artefact.Artefact
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

/**
 * With Spring JSON rendering, respond writes JSON as Spring MVC writes the value of the action's handler method, so the
 * {@code @JsonView} the action is annotated with selects the properties written, as for a Spring MVC controller method.
 */
class RespondJsonViewSpec extends Specification implements ControllerUnitTest<JsonViewController> {

    Closure doWithConfig() {
        { config -> config['grails.web.rendering.json.spring'] = true }
    }

    void setup() {
        response.format = 'json'
    }

    void 'the @JsonView of the action selects the properties respond writes'() {
        when:
        controller.summary()

        then:
        response.status == 200
        response.json == [title: 'Grails']
    }

    void 'the @JsonView of an action that takes parameters selects the properties respond writes'() {
        given:
        params.id = '7'

        when:
        controller.show()

        then:
        response.json == [title: 'Book 7']
    }

    void 'an action without @JsonView has all of the properties written'() {
        when:
        controller.full()

        then:
        response.json == [isbn: '978-1', title: 'Grails']
    }

    void 'the validation errors of an action with @JsonView are a problem written with all of its properties'() {
        when:
        controller.invalid()

        then:
        response.status == 422
        response.contentType == 'application/problem+json'
        response.json.title
        response.json.errors*.field == ['title']
    }

    void 'a named configuration, which selects the properties it writes, is not given the view of the action'() {
        when:
        controller.named()

        then:
        response.json == [isbn: '978-1', title: 'Grails']
    }

    void 'respond called outside of an action, as a test may call it, writes with no view'() {
        when:
        controller.respond(new JsonViewBook(title: 'Grails', isbn: '978-1'))

        then:
        response.json == [isbn: '978-1', title: 'Grails']
    }
}

@Artefact('Controller')
class JsonViewController {

    @JsonView(JsonViewBook.Summary)
    def summary() {
        respond new JsonViewBook(title: 'Grails', isbn: '978-1')
    }

    @JsonView(JsonViewBook.Summary)
    def show(Long id) {
        respond new JsonViewBook(title: "Book $id".toString(), isbn: '978-1')
    }

    def full() {
        respond new JsonViewBook(title: 'Grails', isbn: '978-1')
    }

    @JsonView(JsonViewBook.Summary)
    def invalid() {
        def errors = new BeanPropertyBindingResult(new JsonViewBook(title: '', isbn: '978-1'), 'book')
        errors.rejectValue('title', 'blank', 'Title is blank')
        respond errors
    }

    @JsonView(JsonViewBook.Summary)
    def named() {
        respond new JsonViewBook(title: 'Grails', isbn: '978-1'), jsonConfiguration: 'deep'
    }
}

class JsonViewBook {

    interface Summary {
    }

    interface Detail {
    }

    @JsonView(Summary)
    String title

    @JsonView(Detail)
    String isbn
}
