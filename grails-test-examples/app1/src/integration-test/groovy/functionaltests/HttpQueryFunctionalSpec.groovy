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
package functionaltests

import java.net.http.HttpRequest

import org.junit.jupiter.api.Tag
import spock.lang.Specification

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport
import org.apache.grails.testing.http.client.TestHttpResponse

@Integration
@Tag('http-client')
class HttpQueryFunctionalSpec extends Specification implements HttpClientSupport {

    private static final String JSON_TYPE = 'application/json'
    private static final String FORM_TYPE = 'application/x-www-form-urlencoded'

    void 'the query prefix maps a URL to the QUERY method, and a JSON body is bound to the command object'() {
        when:
        def response = send('QUERY', '/http-query/books', '{"title":"The Stand","pages":1153}', JSON_TYPE)

        then:
        response.assertStatus(200)
        with(response.json()) {
            method == 'QUERY'
            title == 'The Stand'
            pages == 1153
        }
    }

    void 'a form-encoded body is bound to the command object'() {
        when:
        def response = send('QUERY', '/http-query/books', 'title=The%20Stand&pages=1153', FORM_TYPE)

        then:
        response.assertStatus(200)
        with(response.json()) {
            method == 'QUERY'
            title == 'The Stand'
            pages == 1153
        }
    }

    void 'a mapping for the QUERY method does not match another method'() {
        when:
        def response = send('POST', '/http-query/books', '{"title":"The Stand"}', JSON_TYPE)

        then:
        response.statusCode() == 404
    }

    void 'allowedMethods restricts an action to the QUERY method'() {
        expect:
        send('QUERY', '/httpQuery/restricted', '{}', JSON_TYPE).statusCode() == 200
        send('POST', '/httpQuery/restricted', '{}', JSON_TYPE).statusCode() == 405
    }

    void 'a domain class is looked up by its identifier and not bound to the body'() {
        given:
        Long id = Book.withNewTransaction { new Book(title: 'Original').save(failOnError: true, flush: true).id }

        when:
        def response = send('QUERY', "/http-query/lookup/$id", '{"title":"Changed"}', JSON_TYPE)

        then:
        response.assertStatus(200)
        with(response.json()) {
            title == 'Original'
            changed == false
        }

        cleanup:
        Book.withNewTransaction { Book.get(id)?.delete(flush: true) }
    }

    private TestHttpResponse send(String httpMethod, String path, String body, String contentType) {
        sendHttpRequest(newHttpRequestWith(path) {
            header('Content-Type', contentType)
            method(httpMethod, HttpRequest.BodyPublishers.ofString(body))
        })
    }
}
