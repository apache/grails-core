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

import io.github.cjstehno.ersatz.ErsatzServer
import io.github.cjstehno.ersatz.cfg.ContentType
import io.micronaut.context.ApplicationContext as MicronautApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
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
    ErsatzServer ersatz = new ErsatzServer({ cfg ->
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
        ersatz.expectations({ expect ->
            expect.GET('/micronaut-test', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(200)
                    res.body(
                            '{"javaMessage":"hello","factoryName":"test-factory"}',
                            ContentType.APPLICATION_JSON
                    )
                })
            })
        })

        and: 'the declarative client from the Micronaut context'
        def client = micronautContext.getBean(MicronautTestClient)

        when: 'invoking the client method which goes through service discovery and load balancing'
        def response = client.index()

        then: 'the response contains the expected JSON from the ersatz mock'
        response != null
        response.contains('javaMessage')
        response.contains('factoryName')

        and: 'the ersatz server received exactly one request'
        ersatz.verify()
    }

    void "Micronaut HttpClient can reach the running Grails application"() {
        given: 'a Micronaut HTTP client targeting the running server'
        def client = HttpClient.create("http://localhost:$serverPort".toURL())

        when: 'calling the root endpoint'
        def response = client.toBlocking().exchange('/')

        then: 'the server responds successfully'
        response.status.code == 200

        cleanup:
        client.close()
    }

    void "declarative @Client sends POST and receives mock response"() {
        given: 'an ersatz server mocking a 201 Created response for POST'
        ersatz.expectations({ expect ->
            expect.POST('/micronaut-test', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(201)
                    res.body('{"id":"1","title":"test-item"}', ContentType.APPLICATION_JSON)
                })
            })
        })

        and: 'the declarative client from the Micronaut context'
        def client = micronautContext.getBean(MicronautTestClient)

        when: 'creating a resource through the client'
        def response = client.create('{"title":"test-item"}')

        then: 'the response contains the mocked JSON from ersatz'
        response != null
        response.contains('"id"')
        response.contains('"title"')

        and: 'the ersatz server received exactly one POST request'
        ersatz.verify()
    }

    void "declarative @Client sends PUT and receives mock response"() {
        given: 'an ersatz server mocking a 200 OK response for PUT'
        ersatz.expectations({ expect ->
            expect.PUT('/micronaut-test/42', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(200)
                    res.body('{"id":"42","title":"updated"}', ContentType.APPLICATION_JSON)
                })
            })
        })

        and: 'the declarative client from the Micronaut context'
        def client = micronautContext.getBean(MicronautTestClient)

        when: 'updating a resource through the client'
        def response = client.update('42', '{"title":"updated"}')

        then: 'the response contains the mocked JSON from ersatz'
        response != null
        response.contains('"id"')
        response.contains('"updated"')

        and: 'the ersatz server received exactly one PUT request'
        ersatz.verify()
    }

    void "declarative @Client sends DELETE with path variable"() {
        given: 'an ersatz server expecting a DELETE'
        ersatz.expectations({ expect ->
            expect.DELETE('/micronaut-test/42', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(204)
                })
            })
        })

        and: 'the declarative client from the Micronaut context'
        def client = micronautContext.getBean(MicronautTestClient)

        when: 'deleting a resource through the client'
        def response = client.delete('42')

        then: 'the response status indicates no content'
        response.status.code == 204

        and: 'the ersatz server received exactly one request'
        ersatz.verify()
    }

    void "declarative @Client sends GET with path variable"() {
        given: 'an ersatz server expecting a GET with a path variable'
        ersatz.expectations({ expect ->
            expect.GET('/micronaut-test/42', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(200)
                    res.body('{"id":"42","title":"test-item"}', ContentType.APPLICATION_JSON)
                })
            })
        })

        and: 'the declarative client from the Micronaut context'
        def client = micronautContext.getBean(MicronautTestClient)

        when: 'retrieving a resource through the client'
        def response = client.show('42')

        then: 'the response contains the expected JSON'
        response == '{"id":"42","title":"test-item"}'

        and: 'the ersatz server received exactly one request'
        ersatz.verify()
    }

    void "declarative @Client handles 404 response from ersatz mock"() {
        given: 'an ersatz server returning 404 for a missing resource'
        ersatz.expectations({ expect ->
            expect.GET('/micronaut-test/999', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(404)
                    res.body('{"error":"not found"}', ContentType.APPLICATION_JSON)
                })
            })
        })

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
        ersatz.verify()
    }

    void "declarative @Client surfaces 500 responses"() {
        given: 'an ersatz server returning 500 for an error response'
        ersatz.expectations({ expect ->
            expect.GET('/micronaut-test/error', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(500)
                    res.body('{"error":"server error"}', ContentType.APPLICATION_JSON)
                })
            })
        })

        and: 'the declarative client from the Micronaut context'
        def client = micronautContext.getBean(MicronautTestClient)

        when: 'requesting the error endpoint'
        client.show('error')

        then: 'an HttpClientResponseException is thrown'
        def ex = thrown(HttpClientResponseException)
        ex.status.code == 500

        and: 'the ersatz server received exactly one request'
        ersatz.verify()
    }

    void "Micronaut HttpClient can set Accept header"() {
        given: 'an ersatz server expecting an Accept header'
        ersatz.expectations({ expect ->
            expect.GET('/micronaut-test', { req ->
                req.called(1)
                req.header('Accept', MediaType.APPLICATION_JSON)
                req.responder({ res ->
                    res.code(200)
                    res.body('{"status":"ok"}', ContentType.APPLICATION_JSON)
                })
            })
        })

        and: 'a Micronaut HTTP client targeting the ersatz server'
        def client = HttpClient.create('http://localhost:19876'.toURL())

        when: 'calling the endpoint with an Accept header'
        def response = client.toBlocking().exchange(
                HttpRequest.GET('/micronaut-test').accept(MediaType.APPLICATION_JSON),
                String
        )

        then: 'the response contains the expected JSON'
        response.status.code == 200
        response.body() == '{"status":"ok"}'

        and: 'the ersatz server received exactly one request'
        ersatz.verify()

        cleanup:
        client.close()
    }
}
