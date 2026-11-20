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

package org.grails.gorm.graphql.plugin.testing

import io.github.cjstehno.ersatz.GroovyErsatzServer
import io.github.cjstehno.ersatz.encdec.Decoders
import io.github.cjstehno.ersatz.encdec.JsonDecoder
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestClient
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * Mock-HTTP-server coverage for {@code GraphQLSpec.GraphQLRequestHelper}.
 *
 * The trait exchanges JSON with the running application using Spring's
 * {@link RestClient}. These tests stand up a local HTTP server and assert that
 * the request bodies are serialized, and the responses deserialized, by the
 * RestClient Jackson message converter - not by hand - and that the correct
 * content types are sent for both the raw {@code application/graphql} and the
 * structured {@code application/json} flavours.
 */
class GraphQLSpecSpec extends Specification {

    @AutoCleanup
    GroovyErsatzServer server = new GroovyErsatzServer({})

    private static GraphQLSpec.GraphQLRequestHelper helperFor(String baseUrl) {
        new GraphQLSpec.GraphQLRequestHelper(rest: RestClient.builder().baseUrl(baseUrl).build())
    }

    void 'graphql(String) posts an application/graphql body and parses the JSON response into a Map'() {
        given:
        server.expectations {
            POST('/graphql') {
                called(1)
                decoder('application/graphql', Decoders.utf8String)
                body('{ bookList { id } }', 'application/graphql')
                responder {
                    code(200)
                    body('{"data":{"bookList":[{"id":"1"}]}}', 'application/json')
                }
            }
        }

        and:
        def helper = helperFor(server.httpUrl)

        when:
        ResponseEntity<Map> response = helper.graphql('{ bookList { id } }')

        then: 'the response was deserialized by the Jackson message converter into a Map'
        response.statusCode.value() == 200
        response.body instanceof Map
        response.body.data.bookList[0].id == '1'

        and:
        server.verify()
    }

    void 'json(...) serializes the request body with the RestClient Jackson converter'() {
        given:
        server.expectations {
            POST('/graphql') {
                called(1)
                decoder('application/json', new JsonDecoder())
                body([query: 'query Q { x }', operationName: 'Q', variables: [a: 'one', b: 'two']], 'application/json')
                responder {
                    code(200)
                    body('{"data":{"x":true}}', 'application/json')
                }
            }
        }

        and:
        def helper = helperFor(server.httpUrl)

        when: 'a Map payload is handed to RestClient, which encodes it as JSON'
        ResponseEntity<Map> response = helper.json('query Q { x }', [a: 'one', b: 'two'], 'Q')

        then: 'the server received the Jackson-encoded body and the parsed response comes back as a Map'
        response.statusCode.value() == 200
        response.body.data.x == true

        and:
        server.verify()
    }

    void 'get(...) issues a GET to /graphql with the variables JSON-encoded as a query parameter'() {
        given:
        server.expectations {
            GET('/graphql') {
                called(1)
                query('query', 'query Q { x }')
                query('operationName', 'Q')
                query('variables', '{"a":"one","b":"two"}')
                responder {
                    code(200)
                    body('{"data":{"x":true}}', 'application/json')
                }
            }
        }

        and:
        def helper = helperFor(server.httpUrl)

        when:
        ResponseEntity<Map> response = helper.get('query Q { x }', [a: 'one', b: 'two'], 'Q')

        then: 'the request hits the graphql endpoint and the response is parsed into a Map'
        response.statusCode.value() == 200
        response.body.data.x == true

        and:
        server.verify()
    }

    void 'graphql(String, Class) returns the unparsed String body'() {
        given:
        server.expectations {
            POST('/graphql') {
                called(1)
                decoder('application/graphql', Decoders.utf8String)
                body('{ ping }', 'application/graphql')
                responder {
                    code(200)
                    body('{"data":{"ping":"pong"}}', 'application/json')
                }
            }
        }

        and:
        def helper = helperFor(server.httpUrl)

        when:
        ResponseEntity<String> response = helper.graphql('{ ping }', String)

        then:
        response.statusCode.value() == 200
        response.body == '{"data":{"ping":"pong"}}'

        and:
        server.verify()
    }
}
