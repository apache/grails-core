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

package springjson

import spock.lang.Specification
import spock.lang.Tag

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

/**
 * An application that sets grails.web.rendering.json.spring, grails.converters.json.legacy and
 * grails.databinding.json.jackson, the defaults later releases change to, as application.yml sets them: respond writes JSON
 * through Spring MVC's converter with the Grails mapper, binds JSON bodies with Jackson, and Spring MVC writes the
 * problems it responds with, with the application's advice. With grails.exceptionresolver.springHandlers, the exception
 * handlers of the application and of spring.mvc.problemdetails.enabled answer the exceptions an action throws. What an
 * action returns, where Spring MVC writes what a handler method returns as the response, such as a ResponseEntity, Spring
 * MVC writes, and with grails.controllers.responseBody, so is the value of an action annotated @ResponseBody.
 */
@Integration(applicationClass = Application)
@Tag('http-client')
class SpringJsonRespondSpec extends Specification implements HttpClientSupport {

    Long bookId

    Long authorId

    void setup() {
        Book.withNewTransaction {
            def author = new Author(name: 'Graeme').save(failOnError: true)
            authorId = author.id
            bookId = new Book(title: 'The Definitive Guide', isbn: '978-1', author: author).save(failOnError: true, flush: true).id
        }
    }

    void cleanup() {
        Book.withNewTransaction {
            Book.list()*.delete()
            Author.list()*.delete(flush: true)
        }
    }

    void 'respond writes a domain class instance with the Grails mapper, an association as a reference of its id'() {
        when:
        def response = http("/book/show/$bookId")

        then: 'as Spring MVC writes a response body, with the advice of the application'
        response.assertStatus(200)
        response.contentType == 'application/json'
        response.headerValue('X-Advised-By') == 'ProblemAdvice'
        response.json() == [id: bookId, title: 'The Definitive Guide', isbn: '978-1', author: [id: authorId]]
    }

    void 'the excludes respond is given leave a property out'() {
        when:
        def response = http("/book/summary/$bookId")

        then:
        response.assertStatus(200)
        response.json() == [id: bookId, title: 'The Definitive Guide', author: [id: authorId]]
    }

    void 'the @JsonView of the action selects the properties of a domain class instance respond writes'() {
        when:
        def response = http("/book/brief/$bookId")

        then:
        response.assertStatus(200)
        response.headerValue('X-Advised-By') == 'ProblemAdvice'
        response.json() == [title: 'The Definitive Guide']
    }

    void 'a JSON body Jackson binds is saved and written back'() {
        when:
        def response = httpPostJson('/book/save', '{"title":"Grails in Action","isbn":"978-2"}')

        then:
        response.assertStatus(201)
        response.json().title == 'Grails in Action'
        response.json().isbn == '978-2'
    }

    void 'the validation errors of a bound body are a problem Spring MVC writes, with the advice of the application'() {
        when:
        def response = httpPostJson('/book/save', '{"title":""}')

        then:
        response.assertStatus(422)
        response.contentType == 'application/problem+json'
        response.json().title == 'Validation failed'
        response.json().errors*.field == ['title']
        !response.json().errors.first().containsKey('rejectedValue')
        response.json().advisedBy == 'ProblemAdvice'
    }

    void 'a ProblemDetail respond is given is written by Spring MVC, with the advice of the application'() {
        when:
        def response = http('/book/conflict')

        then:
        response.assertStatus(409)
        response.contentType == 'application/problem+json'
        response.json().detail == 'The title is taken'
        response.json().instance == '/book/conflict'
        response.json().advisedBy == 'ProblemAdvice'
    }

    void 'a client that asks for a problem in XML is answered in JSON, as Spring MVC answers it without jackson-dataformat-xml'() {
        when:
        def response = http('/book/conflict', Accept: 'application/problem+xml')

        then:
        response.assertStatus(409)
        response.contentType == 'application/problem+json'
    }

    void 'a client whose Accept header Spring MVC cannot read is answered with the status of the problem alone'() {
        when:
        def response = http('/book/conflict', Accept: 'application/json;q=2')

        then:
        response.assertStatus(409)
        response.body() == ''
    }

    void 'an ErrorResponse respond is given is answered with its status and headers'() {
        when:
        def response = http('/book/unavailable')

        then:
        response.assertStatus(503)
        response.contentType == 'application/problem+json'
        response.headerValue('Retry-After') == '30'
        response.json().status == 503
        response.json().advisedBy == 'ProblemAdvice'
    }

    void 'an exception an action throws is answered by the exception handler the application advises the controller with'() {
        when:
        def response = http('/book/missing/42')

        then:
        response.assertStatus(404)
        response.contentType == 'application/problem+json'
        response.json().detail == 'Book 42 does not exist'
        response.json().bookId == 42
    }

    void 'an exception no exception handler answers is resolved as without the setting'() {
        when:
        def response = http('/book/broken')

        then:
        response.assertStatus(500)
    }

    void 'a ResponseEntity an action returns is written by Spring MVC, with the advice of the application'() {
        when:
        def response = http("/book/entity/$bookId")

        then:
        response.assertStatus(200)
        response.contentType == 'application/json'
        response.headerValue('X-Book') == 'The Definitive Guide'
        response.headerValue('X-Advised-By') == 'ProblemAdvice'
        response.json() == [id: bookId, title: 'The Definitive Guide']

        when:
        response = http('/book/entity/0')

        then:
        response.assertStatus(404)
        response.body() == ''
    }

    void 'a ProblemDetail an action returns is written by Spring MVC, with the advice of the application'() {
        when:
        def response = http('/book/retired')

        then:
        response.assertStatus(410)
        response.contentType == 'application/problem+json'
        response.json().detail == 'The edition is retired'
        response.json().advisedBy == 'ProblemAdvice'
    }

    void 'the value of an action annotated @ResponseBody is written as the body, with the status of its @ResponseStatus'() {
        when:
        def response = http('/book/titles')

        then:
        response.assertStatus(202)
        response.contentType == 'application/json'
        response.headerValue('X-Advised-By') == 'ProblemAdvice'
        response.jsonList() == ['The Definitive Guide']
    }

    void 'the value a CompletableFuture of an action annotated @ResponseBody completes with is written as the body'() {
        when:
        def response = http('/book/later')

        then:
        response.assertStatus(200)
        response.contentType == 'application/json'
        response.json() == [title: 'Written later']
    }

    void 'a ResponseEntity of a StreamingResponseBody an action returns is streamed'() {
        when:
        def response = http('/book/download')

        then:
        response.assertStatus(200)
        response.contentType.startsWith('text/plain')
        response.body() == 'Streamed'
    }

    void 'an exception of Spring MVC an action throws is answered by the handler of spring.mvc.problemdetails.enabled'() {
        when:
        def response = http('/book/search')

        then: "Spring Boot's handler answers it with a problem, where Spring MVC's other resolvers send the error alone"
        response.assertStatus(400)
        response.contentType == 'application/problem+json'
        response.json().detail == "Required parameter 'title' is not present."
    }
}
