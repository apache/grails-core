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
import io.github.cjstehno.ersatz.cfg.ServerConfig
import io.micronaut.context.ApplicationContext as MicronautApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import micronaut.client.MicronautAdvancedClient
import micronaut.client.MicronautHeaderClient
import micronaut.client.MicronautTestClient
import spock.lang.AutoCleanup
import spock.lang.Specification

import org.springframework.beans.factory.annotation.Autowired

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.httpclient.HttpClientSupport

@Integration
class MicronautErsatzAdvancedSpec extends Specification implements HttpClientSupport {

    @Autowired MicronautApplicationContext micronautContext
    @Autowired ExternalApiService externalApiService

    @AutoCleanup
    ErsatzServer ersatz = new ErsatzServer({ ServerConfig cfg ->
        cfg.httpPort(19876)
    })

    void "PATCH request via declarative client through ersatz mock"() {
        given: 'ersatz mocks the PATCH endpoint'
        ersatz.expectations({ expect ->
            expect.PATCH('/micronaut-test/42', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(200)
                    res.body('{"id":"42","name":"patched"}', ContentType.APPLICATION_JSON)
                })
            })
        })

        and: 'the declarative advanced client'
        def client = micronautContext.getBean(MicronautAdvancedClient)

        when: 'patching the resource'
        def response = client.patch('42', '{"name":"patched"}')

        then: 'the response contains patched data'
        response != null
        response.contains('"id":"42"')
        response.contains('patched')

        and: 'ersatz verifies the call'
        ersatz.verify()
    }

    void "HEAD request via declarative client returns only headers from ersatz"() {
        given: 'ersatz mocks the HEAD endpoint with headers'
        ersatz.expectations({ expect ->
            expect.HEAD('/micronaut-test', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(200)
                    res.header('X-Total-Count', '42')
                })
            })
        })

        and: 'the declarative advanced client'
        def client = micronautContext.getBean(MicronautAdvancedClient)

        when: 'calling the head endpoint'
        def response = client.headCheck()

        then: 'the response contains the header'
        response.status.code == 200
        response.header('X-Total-Count') == '42'

        and: 'ersatz verifies the call'
        ersatz.verify()
    }

    void "OPTIONS request via declarative client through ersatz mock"() {
        given: 'ersatz mocks the OPTIONS endpoint'
        ersatz.expectations({ expect ->
            expect.OPTIONS('/micronaut-test', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(200)
                    res.header('Allow', 'GET, POST, PUT, DELETE, PATCH')
                })
            })
        })

        and: 'the declarative advanced client'
        def client = micronautContext.getBean(MicronautAdvancedClient)

        when: 'calling the options endpoint'
        def response = client.optionsCheck()

        then: 'the response contains the allow header'
        response.status.code == 200
        response.header('Allow') == 'GET, POST, PUT, DELETE, PATCH'

        and: 'ersatz verifies the call'
        ersatz.verify()
    }

    void "declarative client sends query parameters matched by ersatz"() {
        given: 'ersatz expects query parameters'
        ersatz.expectations({ expect ->
            expect.GET('/micronaut-test/search', { req ->
                req.query('q', 'grails')
                req.query('page', '1')
                req.called(1)
                req.responder({ res ->
                    res.code(200)
                    res.body('{"results":["grails"]}', ContentType.APPLICATION_JSON)
                })
            })
        })

        and: 'the declarative advanced client'
        def client = micronautContext.getBean(MicronautAdvancedClient)

        when: 'searching with query parameters'
        def response = client.search('grails', '1')

        then: 'the response contains the mocked results'
        response != null
        response.contains('grails')

        and: 'ersatz verifies the call'
        ersatz.verify()
    }

    void "ersatz matches query parameters in full roundtrip through Grails stack"() {
        given: 'ersatz expects query parameters'
        ersatz.expectations({ expect ->
            expect.GET('/micronaut-test/search', { req ->
                req.query('q', 'grails')
                req.query('page', '2')
                req.called(1)
                req.responder({ res ->
                    res.code(200)
                    res.body('{"results":["grails","roundtrip"]}', ContentType.APPLICATION_JSON)
                })
            })
        })

        when: 'calling the Grails controller endpoint'
        def response = httpClient.exchange(
                HttpRequest.GET('/external-api/search?q=grails&page=2').accept(MediaType.APPLICATION_JSON),
                String
        )

        then: 'the response contains the mocked data'
        response.status.code == 200
        response.body().contains('roundtrip')

        and: 'ersatz verifies the call'
        ersatz.verify()
    }

    void "declarative client sends Authorization header matched by ersatz"() {
        given: 'ersatz expects an authorization header'
        ersatz.expectations({ expect ->
            expect.GET('/micronaut-test/secure', { req ->
                req.header('Authorization', 'Bearer test-token-123')
                req.called(1)
                req.responder({ res ->
                    res.code(200)
                    res.body('{"secure":true}', ContentType.APPLICATION_JSON)
                })
            })
        })

        and: 'the declarative advanced client'
        def client = micronautContext.getBean(MicronautAdvancedClient)

        when: 'calling the secure endpoint'
        def response = client.secureEndpoint('Bearer test-token-123')

        then: 'the response contains the secure payload'
        response != null
        response.contains('secure')

        and: 'ersatz verifies the call'
        ersatz.verify()
    }

    void "declarative client sends custom X-Api-Key header matched by ersatz"() {
        given: 'ersatz expects an api key header'
        ersatz.expectations({ expect ->
            expect.GET('/micronaut-test/api-resource', { req ->
                req.header('X-Api-Key', 'my-secret-key')
                req.called(1)
                req.responder({ res ->
                    res.code(200)
                    res.body('{"api":"ok"}', ContentType.APPLICATION_JSON)
                })
            })
        })

        and: 'the declarative advanced client'
        def client = micronautContext.getBean(MicronautAdvancedClient)

        when: 'calling the endpoint with api key'
        def response = client.withApiKey('my-secret-key')

        then: 'the response contains the api payload'
        response != null
        response.contains('ok')

        and: 'ersatz verifies the call'
        ersatz.verify()
    }

    void "class-level @Header annotation sends header on every request through ersatz"() {
        given: 'ersatz expects the class-level header'
        ersatz.expectations({ expect ->
            expect.GET('/micronaut-test', { req ->
                req.header('X-App-Version', '2.0')
                req.called(1)
                req.responder({ res ->
                    res.code(200)
                    res.body('{"version":"2.0"}', ContentType.APPLICATION_JSON)
                })
            })
        })

        and: 'the header client'
        def client = micronautContext.getBean(MicronautHeaderClient)

        when: 'calling the index endpoint'
        def response = client.index()

        then: 'the response contains the expected version'
        response != null
        response.contains('2.0')

        and: 'ersatz verifies the call'
        ersatz.verify()
    }

    void "full roundtrip: Authorization header passes through Grails stack to ersatz"() {
        given: 'ersatz expects an authorization header'
        ersatz.expectations({ expect ->
            expect.GET('/micronaut-test/secure', { req ->
                req.header('Authorization', 'Bearer roundtrip-token')
                req.called(1)
                req.responder({ res ->
                    res.code(200)
                    res.body('{"secure":"roundtrip"}', ContentType.APPLICATION_JSON)
                })
            })
        })

        when: 'calling the Grails controller endpoint'
        def response = httpClient.exchange(
                HttpRequest.GET('/external-api/secure')
                        .header('Authorization', 'Bearer roundtrip-token')
                        .accept(MediaType.APPLICATION_JSON),
                String
        )

        then: 'the response contains the secured payload'
        response.status.code == 200
        response.body().contains('roundtrip')

        and: 'ersatz verifies the call'
        ersatz.verify()
    }

    void "declarative client sends cookie matched by ersatz"() {
        given: 'ersatz expects a session cookie'
        ersatz.expectations({ expect ->
            expect.GET('/micronaut-test/with-cookie', { req ->
                req.cookie('session', 'abc-session-123')
                req.called(1)
                req.responder({ res ->
                    res.code(200)
                    res.body('{"cookie":"ok"}', ContentType.APPLICATION_JSON)
                })
            })
        })

        and: 'the declarative advanced client'
        def client = micronautContext.getBean(MicronautAdvancedClient)

        when: 'calling the endpoint with cookie'
        def response = client.withCookie('abc-session-123')

        then: 'the response contains the cookie payload'
        response != null
        response.contains('cookie')

        and: 'ersatz verifies the call'
        ersatz.verify()
    }

    void "ersatz returns plain text response consumed by declarative client"() {
        given: 'ersatz returns plain text'
        ersatz.expectations({ expect ->
            expect.GET('/micronaut-test/text', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(200)
                    res.body('Hello, Grails!', ContentType.TEXT_PLAIN)
                })
            })
        })

        and: 'the declarative advanced client'
        def client = micronautContext.getBean(MicronautAdvancedClient)

        when: 'calling the text endpoint'
        def response = client.getPlainText()

        then: 'the response matches the plain text'
        response == 'Hello, Grails!'

        and: 'ersatz verifies the call'
        ersatz.verify()
    }

    void "ersatz verifies exact call count for multiple requests to same endpoint"() {
        given: 'ersatz expects multiple calls'
        ersatz.expectations({ expect ->
            expect.GET('/micronaut-test', { req ->
                req.called(3)
                req.responder({ res ->
                    res.code(200)
                    res.body('{"status":"ok"}', ContentType.APPLICATION_JSON)
                })
            })
        })

        and: 'the declarative test client'
        def client = micronautContext.getBean(MicronautTestClient)

        when: 'calling multiple times'
        client.index()
        client.index()
        client.index()

        then: 'ersatz verifies the call count'
        ersatz.verify()
    }

    void "Micronaut client handles 401 Unauthorized from ersatz"() {
        given: 'ersatz responds with 401'
        ersatz.expectations({ expect ->
            expect.GET('/micronaut-test/unauthorized', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(401)
                })
            })
        })

        and: 'the declarative test client'
        def client = micronautContext.getBean(MicronautTestClient)

        when: 'requesting an unauthorized resource'
        client.show('unauthorized')

        then: 'a 401 exception is thrown'
        def ex = thrown(HttpClientResponseException)
        ex.status.code == 401

        and: 'ersatz verifies the call'
        ersatz.verify()
    }

    void "Micronaut client handles 403 Forbidden from ersatz"() {
        given: 'ersatz responds with 403'
        ersatz.expectations({ expect ->
            expect.GET('/micronaut-test/forbidden', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(403)
                })
            })
        })

        and: 'the declarative test client'
        def client = micronautContext.getBean(MicronautTestClient)

        when: 'requesting a forbidden resource'
        client.show('forbidden')

        then: 'a 403 exception is thrown'
        def ex = thrown(HttpClientResponseException)
        ex.status.code == 403

        and: 'ersatz verifies the call'
        ersatz.verify()
    }

    void "Micronaut client handles 429 Too Many Requests from ersatz"() {
        given: 'ersatz responds with 429'
        ersatz.expectations({ expect ->
            expect.GET('/micronaut-test/rate-limited', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(429)
                })
            })
        })

        and: 'the declarative test client'
        def client = micronautContext.getBean(MicronautTestClient)

        when: 'requesting a rate limited resource'
        client.show('rate-limited')

        then: 'a 429 exception is thrown'
        def ex = thrown(HttpClientResponseException)
        ex.status.code == 429

        and: 'ersatz verifies the call'
        ersatz.verify()
    }

    void "multiple @Client interfaces coexist and resolve in Micronaut context"() {
        when: 'resolving all client beans'
        def testClient = micronautContext.getBean(MicronautTestClient)
        def advancedClient = micronautContext.getBean(MicronautAdvancedClient)
        def headerClient = micronautContext.getBean(MicronautHeaderClient)

        then: 'all client beans are available'
        testClient != null
        advancedClient != null
        headerClient != null
        testClient instanceof MicronautTestClient
        advancedClient instanceof MicronautAdvancedClient
        headerClient instanceof MicronautHeaderClient
    }

    void "service orchestrates multiple ersatz-backed API calls in single operation"() {
        given: 'ersatz mocks multiple endpoints'
        ersatz.expectations({ expect ->
            expect.GET('/micronaut-test/10', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(200)
                    res.body('{"id":"10","name":"first"}', ContentType.APPLICATION_JSON)
                })
            })
            expect.GET('/micronaut-test/20', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(200)
                    res.body('{"id":"20","name":"second"}', ContentType.APPLICATION_JSON)
                })
            })
        })

        when: 'the service orchestrates multiple calls'
        def result = externalApiService.orchestrateMultiple('10', '20')

        then: 'the result map contains both responses'
        result != null
        result.first.contains('"id":"10"')
        result.second.contains('"id":"20"')

        and: 'ersatz verifies both calls'
        ersatz.verify()
    }

    void "full roundtrip: PATCH through Grails controller to ersatz mock"() {
        given: 'ersatz mocks the PATCH endpoint'
        ersatz.expectations({ expect ->
            expect.PATCH('/micronaut-test/99', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(200)
                    res.body('{"id":"99","name":"patched"}', ContentType.APPLICATION_JSON)
                })
            })
        })

        when: 'sending a PATCH request to Grails'
        def response = httpClient.exchange(
                HttpRequest.PATCH('/external-api/99', '{"name":"patched"}')
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON),
                String
        )

        then: 'the response contains the patched payload'
        response.status.code == 200
        response.body().contains('patched')

        and: 'ersatz verifies the call'
        ersatz.verify()
    }

    void "full roundtrip: search with query params through Grails controller to ersatz"() {
        given: 'ersatz expects query parameters'
        ersatz.expectations({ expect ->
            expect.GET('/micronaut-test/search', { req ->
                req.query('q', 'test-query')
                req.query('page', '3')
                req.called(1)
                req.responder({ res ->
                    res.code(200)
                    res.body('{"results":["test-query"]}', ContentType.APPLICATION_JSON)
                })
            })
        })

        when: 'calling the search endpoint'
        def response = httpClient.exchange(
                HttpRequest.GET('/external-api/search?q=test-query&page=3').accept(MediaType.APPLICATION_JSON),
                String
        )

        then: 'the response contains the mocked data'
        response.status.code == 200
        response.body().contains('test-query')

        and: 'ersatz verifies the call'
        ersatz.verify()
    }

    void "Grails service error handling wraps Micronaut client error from ersatz"() {
        given: 'ersatz responds with a 502 error'
        ersatz.expectations({ expect ->
            expect.GET('/micronaut-test/service-error', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(502)
                })
            })
        })

        when: 'the service handles the error'
        def result = externalApiService.fetchWithErrorHandling('service-error')

        then: 'the error is captured'
        result.success == false
        result.status == 502

        and: 'ersatz verifies the call'
        ersatz.verify()
    }
}
