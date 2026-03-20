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
package micronaut

import io.github.cjstehno.ersatz.GroovyErsatzServer
import io.github.cjstehno.ersatz.cfg.ServerConfig
import io.micronaut.context.ApplicationContext as MicronautApplicationContext
import io.micronaut.http.client.exceptions.HttpClientResponseException
import micronaut.client.MicronautTestClient
import spock.lang.AutoCleanup
import spock.lang.Specification

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value

import grails.testing.mixin.integration.Integration

@Integration
class MicronautDeclarativeClientSpec extends Specification {

    @Autowired
    MicronautApplicationContext micronautContext

    @Value('${local.server.port}')
    Integer serverPort

    @AutoCleanup
    GroovyErsatzServer server = new GroovyErsatzServer({ ServerConfig cfg ->
        cfg.httpPort(19876)
    })

    void "declarative @Client interface is registered as a bean in Micronaut context"() {
        when: 'looking up the declarative client bean'
        def client = micronautContext.getBean(MicronautTestClient)

        then: 'the bean is found and implements the interface'
        client != null
        client instanceof MicronautTestClient
    }

    void "declarative @Client invokes endpoint through load balancing path"() {
        given: 'an ersatz server mocking the expected endpoint response'
        server.expectations {
            GET('/micronaut-test') {
                called(1)
                responder {
                    code(200)
                    body('{"javaMessage":"hello","factoryName":"test-factory"}', 'application/json')
                }
            }
        }

        and: 'the declarative client from the Micronaut context'
        def client = micronautContext.getBean(MicronautTestClient)

        when: 'invoking the client method which goes through service discovery and load balancing'
        def response = client.index()

        then: 'the response contains the expected JSON from the ersatz mock'
        response != null
        response.contains('javaMessage')
        response.contains('factoryName')

        and: 'the ersatz server received exactly one request'
        server.verify()
    }

    void "declarative @Client sends POST and receives mock response"() {
        given: 'an ersatz server mocking a 201 Created response for POST'
        server.expectations {
            POST('/micronaut-test') {
                called(1)
                responder {
                    code(201)
                    body('{"id":"1","title":"test-item"}', 'application/json')
                }
            }
        }

        and: 'the declarative client from the Micronaut context'
        def client = micronautContext.getBean(MicronautTestClient)

        when: 'creating a resource through the client'
        def response = client.create('{"title":"test-item"}')

        then: 'the response contains the mocked JSON from ersatz'
        response != null
        response.contains('"id"')
        response.contains('"title"')

        and: 'the ersatz server received exactly one POST request'
        server.verify()
    }

    void "declarative @Client sends PUT and receives mock response"() {
        given: 'an ersatz server mocking a 200 OK response for PUT'
        server.expectations {
            PUT('/micronaut-test/42') {
                called(1)
                responder {
                    code(200)
                    body('{"id":"42","title":"updated"}', 'application/json')
                }
            }
        }

        and: 'the declarative client from the Micronaut context'
        def client = micronautContext.getBean(MicronautTestClient)

        when: 'updating a resource through the client'
        def response = client.update('42', '{"title":"updated"}')

        then: 'the response contains the mocked JSON from ersatz'
        response != null
        response.contains('"id"')
        response.contains('"updated"')

        and: 'the ersatz server received exactly one PUT request'
        server.verify()
    }

    void "declarative @Client sends DELETE with path variable"() {
        given: 'an ersatz server expecting a DELETE'
        server.expectations {
            DELETE('/micronaut-test/42') {
                called(1)
                responder {
                    code(204)
                }
            }
        }

        and: 'the declarative client from the Micronaut context'
        def client = micronautContext.getBean(MicronautTestClient)

        when: 'deleting a resource through the client'
        def response = client.delete('42')

        then: 'the response status indicates no content'
        response.status.code == 204

        and: 'the ersatz server received exactly one request'
        server.verify()
    }

    void "declarative @Client sends GET with path variable"() {
        given: 'an ersatz server expecting a GET with a path variable'
        server.expectations {
            GET('/micronaut-test/42') {
                called(1)
                responder {
                    code(200)
                    body('{"id":"42","title":"test-item"}', 'application/json')
                }
            }
        }

        and: 'the declarative client from the Micronaut context'
        def client = micronautContext.getBean(MicronautTestClient)

        when: 'retrieving a resource through the client'
        def response = client.show('42')

        then: 'the response contains the expected JSON'
        response == '{"id":"42","title":"test-item"}'

        and: 'the ersatz server received exactly one request'
        server.verify()
    }

    void "declarative @Client handles 404 response from ersatz mock"() {
        given: 'an ersatz server returning 404 for a missing resource'
        server.expectations {
            GET('/micronaut-test/999') {
                called(1)
                responder {
                    code(404)
                    body('{"error":"not found"}', 'application/json')
                }
            }
        }

        and: 'the declarative client from the Micronaut context'
        def client = micronautContext.getBean(MicronautTestClient)

        when: 'requesting a missing resource'
        String response = null
        HttpClientResponseException caught = null
        try {
            response = client.show('999')
        } catch (HttpClientResponseException ex) {
            caught = ex
        }

        then: 'either an exception is thrown or null is returned'
        caught != null || response == null

        and: 'if exception was thrown, status is 404'
        caught == null || caught.status.code == 404

        and: 'the ersatz server received exactly one request'
        server.verify()
    }

    void "declarative @Client surfaces 500 responses"() {
        given: 'an ersatz server returning 500 for an error response'
        server.expectations {
            GET('/micronaut-test/error') {
                called(1)
                responder {
                    code(500)
                    body('{"error":"server error"}', 'application/json')
                }
            }
        }

        and: 'the declarative client from the Micronaut context'
        def client = micronautContext.getBean(MicronautTestClient)

        when: 'requesting the error endpoint'
        client.show('error')

        then: 'an HttpClientResponseException is thrown'
        def ex = thrown(HttpClientResponseException)
        ex.status.code == 500

        and: 'the ersatz server received exactly one request'
        server.verify()
    }
}
