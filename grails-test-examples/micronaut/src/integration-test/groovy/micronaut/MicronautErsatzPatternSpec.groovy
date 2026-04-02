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
import micronaut.client.MicronautFilteredClient
import micronaut.client.MicronautPathClient
import micronaut.client.MicronautReactiveClient
import micronaut.client.MicronautRetryableClient
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Tag

import org.springframework.beans.factory.annotation.Autowired

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

@Integration
@Tag('http-client')
class MicronautErsatzPatternSpec extends Specification implements HttpClientSupport {

    @Autowired ExternalApiService externalApiService
    @Autowired MicronautApplicationContext micronautContext

    @AutoCleanup
    GroovyErsatzServer ersatz = new GroovyErsatzServer({ ServerConfig cfg ->
        cfg.httpPort(19876)
    })

    // --- CompletableFuture return types ---

    void "CompletableFuture GET resolves with ersatz-mocked data"() {
        given: 'ersatz mocks the async endpoint'
        ersatz.expectations {
            GET('/micronaut-test/async') {
                called(1)
                responder {
                    code(200)
                    body('{"async":"get-result"}', 'application/json')
                }
            }
        }

        and: 'the reactive client'
        def client = micronautContext.getBean(MicronautReactiveClient)

        when: 'calling getAsync'
        def future = client.getAsync()
        def result = future.get()

        then: 'the future resolves with the mocked data'
        result != null
        result.contains('async')
        result.contains('get-result')

        and: 'ersatz verifies the call'
        ersatz.verify()
    }

    void "CompletableFuture POST resolves with ersatz-mocked creation response"() {
        given: 'ersatz mocks the async POST endpoint'
        ersatz.expectations {
            POST('/micronaut-test/async') {
                called(1)
                responder {
                    code(201)
                    body('{"id":"async-1","created":true}', 'application/json')
                }
            }
        }

        and: 'the reactive client'
        def client = micronautContext.getBean(MicronautReactiveClient)

        when: 'calling createAsync'
        def future = client.createAsync('{"name":"async-item"}')
        def result = future.get()

        then: 'the future resolves with the created resource'
        result != null
        result.contains('async-1')
        result.contains('created')

        and: 'ersatz verifies the call'
        ersatz.verify()
    }

    void "CompletableFuture DELETE resolves with HttpResponse from ersatz"() {
        given: 'ersatz mocks the async DELETE endpoint'
        ersatz.expectations {
            DELETE('/micronaut-test/async/77') {
                called(1)
                responder {
                    code(204)
                }
            }
        }

        and: 'the reactive client'
        def client = micronautContext.getBean(MicronautReactiveClient)

        when: 'calling deleteAsync'
        def future = client.deleteAsync('77')
        def response = future.get()

        then: 'the future resolves with the HttpResponse'
        response.status.code == 204

        and: 'ersatz verifies the call'
        ersatz.verify()
    }

    void "void return type completes without error and ersatz verifies the call"() {
        given: 'ersatz mocks the fire-and-forget endpoint'
        ersatz.expectations {
            POST('/micronaut-test/fire-and-forget') {
                called(1)
                responder {
                    code(202)
                }
            }
        }

        and: 'the reactive client'
        def client = micronautContext.getBean(MicronautReactiveClient)

        when: 'calling fireAndForget'
        client.fireAndForget('{"event":"fired"}')

        then: 'no exception is thrown'
        noExceptionThrown()

        and: 'ersatz verifies the call was made'
        ersatz.verify()
    }

    // --- @Client with base path ---

    void "@Client path prepends base path to GET request verified by ersatz"() {
        given: 'ersatz mocks the versioned API endpoint'
        ersatz.expectations {
            GET('/api/v1/items') {
                called(1)
                responder {
                    code(200)
                    body('[{"id":"1","name":"path-item"}]', 'application/json')
                }
            }
        }

        and: 'the path client'
        def client = micronautContext.getBean(MicronautPathClient)

        when: 'calling listItems'
        def result = client.listItems()

        then: 'the response contains the mocked data'
        result != null
        result.contains('path-item')

        and: 'ersatz verifies the call to the base-path-prefixed URL'
        ersatz.verify()
    }

    void "@Client path prepends base path to GET with @PathVariable verified by ersatz"() {
        given: 'ersatz mocks the versioned API endpoint with id'
        ersatz.expectations {
            GET('/api/v1/items/55') {
                called(1)
                responder {
                    code(200)
                    body('{"id":"55","name":"versioned-item"}', 'application/json')
                }
            }
        }

        and: 'the path client'
        def client = micronautContext.getBean(MicronautPathClient)

        when: 'calling getItem'
        def result = client.getItem('55')

        then: 'the response contains the mocked item'
        result != null
        result.contains('"id":"55"')
        result.contains('versioned-item')

        and: 'ersatz verifies the call'
        ersatz.verify()
    }

    void "@Client path prepends base path to POST request verified by ersatz"() {
        given: 'ersatz mocks the versioned API POST endpoint'
        ersatz.expectations {
            POST('/api/v1/items') {
                called(1)
                responder {
                    code(201)
                    body('{"id":"new","name":"path-created"}', 'application/json')
                }
            }
        }

        and: 'the path client'
        def client = micronautContext.getBean(MicronautPathClient)

        when: 'calling createItem'
        def result = client.createItem('{"name":"path-created"}')

        then: 'the response contains the created item'
        result != null
        result.contains('path-created')

        and: 'ersatz verifies the call'
        ersatz.verify()
    }

    // --- @ClientFilter auto-injects headers ---

    void "@ClientFilter auto-injects X-Auth-Token header verified by ersatz"() {
        given: 'ersatz expects the auto-injected header'
        ersatz.expectations {
            GET('/micronaut-test/filtered/data') {
                header('X-Auth-Token', 'auto-injected-token-123')
                called(1)
                responder {
                    code(200)
                    body('{"filtered":"data"}', 'application/json')
                }
            }
        }

        and: 'the filtered client'
        def client = micronautContext.getBean(MicronautFilteredClient)

        when: 'calling getFilteredData'
        def result = client.getFilteredData()

        then: 'the response contains the filtered data'
        result != null
        result.contains('filtered')

        and: 'ersatz verifies the header was present'
        ersatz.verify()
    }

    void "@ClientFilter applies to nested paths with @PathVariable"() {
        given: 'ersatz expects the auto-injected header on nested path'
        ersatz.expectations {
            GET('/micronaut-test/filtered/details/42') {
                header('X-Auth-Token', 'auto-injected-token-123')
                called(1)
                responder {
                    code(200)
                    body('{"id":"42","detail":"filtered"}', 'application/json')
                }
            }
        }

        and: 'the filtered client'
        def client = micronautContext.getBean(MicronautFilteredClient)

        when: 'calling getFilteredDetails with path variable'
        def result = client.getFilteredDetails('42')

        then: 'the response contains the filtered detail'
        result != null
        result.contains('"id":"42"')

        and: 'ersatz verifies the header was present on the nested path'
        ersatz.verify()
    }

    // --- @Retryable ---

    void "@Retryable client succeeds on first attempt through ersatz"() {
        given: 'ersatz mocks a successful response'
        ersatz.expectations {
            GET('/micronaut-test/retryable') {
                called(1)
                responder {
                    code(200)
                    body('{"retry":"not-needed"}', 'application/json')
                }
            }
        }

        and: 'the retryable client'
        def client = micronautContext.getBean(MicronautRetryableClient)

        when: 'calling getWithRetry'
        def result = client.getWithRetry()

        then: 'the result is returned on first attempt'
        result != null
        result.contains('not-needed')

        and: 'ersatz verifies only one call was made'
        ersatz.verify()
    }

    void "@Retryable retries on 503 errors and succeeds on third attempt via ersatz sequential responses"() {
        given: 'ersatz responds with 503 twice then 200'
        ersatz.expectations {
            GET('/micronaut-test/retryable') {
                called(3)
                responder {
                    code(503)
                    body('unavailable', 'text/plain')
                }
                responder {
                    code(503)
                    body('still unavailable', 'text/plain')
                }
                responder {
                    code(200)
                    body('{"retry":"succeeded"}', 'application/json')
                }
            }
        }

        and: 'the retryable client'
        def client = micronautContext.getBean(MicronautRetryableClient)

        when: 'calling getWithRetry'
        def result = client.getWithRetry()

        then: 'the result is returned after retries'
        result != null
        result.contains('succeeded')

        and: 'ersatz verifies all three calls were made'
        ersatz.verify()
    }

    // --- Bean resolution ---

    void "all Phase 3 client beans resolve in Micronaut context"() {
        when: 'resolving all new client beans'
        def reactiveClient = micronautContext.getBean(MicronautReactiveClient)
        def pathClient = micronautContext.getBean(MicronautPathClient)
        def filteredClient = micronautContext.getBean(MicronautFilteredClient)
        def retryableClient = micronautContext.getBean(MicronautRetryableClient)

        then: 'all beans are available and correctly typed'
        reactiveClient != null
        pathClient != null
        filteredClient != null
        retryableClient != null
        reactiveClient instanceof MicronautReactiveClient
        pathClient instanceof MicronautPathClient
        filteredClient instanceof MicronautFilteredClient
        retryableClient instanceof MicronautRetryableClient
    }

    // --- Full roundtrip tests ---

    void "full roundtrip: CompletableFuture through Grails controller to ersatz"() {
        given: 'ersatz mocks the async endpoint'
        ersatz.expectations {
            GET('/micronaut-test/async') {
                called(1)
                responder {
                    code(200)
                    body('{"async":"roundtrip-data"}', 'application/json')
                }
            }
        }

        when: 'calling the Grails async controller endpoint'
        def response = http(
                '/external-api/async',
                'Accept': 'application/json'
        )

        then: 'the response contains the ersatz-mocked async data'
        response.assertContains(200, 'roundtrip-data')

        and: 'ersatz verifies the call'
        ersatz.verify()
    }

    void "full roundtrip: path client through Grails controller to ersatz"() {
        given: 'ersatz mocks the versioned API endpoint'
        ersatz.expectations {
            GET('/api/v1/items/88') {
                called(1)
                responder {
                    code(200)
                    body('{"id":"88","name":"path-roundtrip"}', 'application/json')
                }
            }
        }

        when: 'calling the Grails path controller endpoint'
        def response = http('/external-api/path/88', 'Accept': 'application/json')

        then: 'the response contains the ersatz-mocked path data'
        response.assertContains(200, 'path-roundtrip')

        and: 'ersatz verifies the call'
        ersatz.verify()
    }

    void "full roundtrip: filtered client through Grails controller to ersatz with auto-injected header"() {
        given: 'ersatz expects the auto-injected header on the filtered path'
        ersatz.expectations {
            GET('/micronaut-test/filtered/data') {
                header('X-Auth-Token', 'auto-injected-token-123')
                called(1)
                responder {
                    code(200)
                    body('{"filtered":"roundtrip-data"}', 'application/json')
                }
            }
        }

        when: 'calling the Grails filtered controller endpoint'
        def response = http('/external-api/filtered', 'Accept': 'application/json')

        then: 'the response contains the filtered data'
        response.assertContains(200, 'roundtrip-data')

        and: 'ersatz verifies the header was auto-injected'
        ersatz.verify()
    }
}
