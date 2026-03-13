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
import spock.lang.AutoCleanup
import spock.lang.Retry
import spock.lang.Specification

import org.springframework.beans.factory.annotation.Autowired

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

@Integration
class MicronautErsatzRoundtripSpec extends Specification implements HttpClientSupport {

    @Autowired ExternalApiService externalApiService

    @AutoCleanup
    ErsatzServer ersatz = new ErsatzServer({ ServerConfig cfg ->
        cfg.httpPort(19876)
    })

    // --- Service layer: Grails service -> Micronaut @Client -> ersatz ---

    void "service fetches list via Micronaut client from ersatz mock"() {
        given: 'ersatz mocks the external API index endpoint'
        ersatz.expectations({ expect ->
            expect.GET('/micronaut-test', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(200)
                    res.body('[{"id":"1","name":"alpha"},{"id":"2","name":"beta"}]', ContentType.APPLICATION_JSON)
                })
            })
        })

        when: 'the Grails service calls fetchAll()'
        def result = externalApiService.fetchAll()

        then: 'the mocked JSON array is returned through the Micronaut client'
        result != null
        result.contains('alpha')
        result.contains('beta')

        and: 'ersatz received exactly one GET'
        ersatz.verify()
    }

    void "service fetches single resource by ID from ersatz mock"() {
        given: 'ersatz mocks a specific resource endpoint'
        ersatz.expectations({ expect ->
            expect.GET('/micronaut-test/7', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(200)
                    res.body('{"id":"7","name":"gamma","active":true}', ContentType.APPLICATION_JSON)
                })
            })
        })

        when: 'the Grails service calls fetchById("7")'
        def result = externalApiService.fetchById('7')

        then: 'the mocked resource is returned'
        result != null
        result.contains('"id":"7"')
        result.contains('gamma')

        and: 'ersatz received exactly one GET'
        ersatz.verify()
    }

    void "service creates resource via Micronaut client through ersatz mock"() {
        given: 'ersatz mocks a 201 Created response'
        ersatz.expectations({ expect ->
            expect.POST('/micronaut-test', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(201)
                    res.body('{"id":"99","name":"new-item"}', ContentType.APPLICATION_JSON)
                })
            })
        })

        when: 'the Grails service calls createResource()'
        def result = externalApiService.createResource('{"name":"new-item"}')

        then: 'the mocked creation response is returned'
        result != null
        result.contains('"id":"99"')

        and: 'ersatz received exactly one POST'
        ersatz.verify()
    }

    void "service updates resource via Micronaut client through ersatz mock"() {
        given: 'ersatz mocks a 200 OK response for the update'
        ersatz.expectations({ expect ->
            expect.PUT('/micronaut-test/99', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(200)
                    res.body('{"id":"99","name":"updated-item"}', ContentType.APPLICATION_JSON)
                })
            })
        })

        when: 'the Grails service calls updateResource()'
        def result = externalApiService.updateResource('99', '{"name":"updated-item"}')

        then: 'the mocked update response is returned'
        result != null
        result.contains('updated-item')

        and: 'ersatz received exactly one PUT'
        ersatz.verify()
    }

    void "service deletes resource via Micronaut client through ersatz mock"() {
        given: 'ersatz mocks a 204 No Content response for deletion'
        ersatz.expectations({ expect ->
            expect.DELETE('/micronaut-test/99', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(204)
                })
            })
        })

        when: 'the Grails service calls deleteResource()'
        def statusCode = externalApiService.deleteResource('99')

        then: 'the 204 status code is returned'
        statusCode == 204

        and: 'ersatz received exactly one DELETE'
        ersatz.verify()
    }

    void "service handles 500 error from external API via ersatz mock"() {
        given: 'ersatz mocks a 500 Internal Server Error'
        ersatz.expectations({ expect ->
            expect.GET('/micronaut-test/broken', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(500)
                    res.body('{"error":"internal failure"}', ContentType.APPLICATION_JSON)
                })
            })
        })

        when: 'the Grails service calls fetchWithErrorHandling() for a failing endpoint'
        def result = externalApiService.fetchWithErrorHandling('broken')

        then: 'the error is caught and returned as a map'
        result.success == false
        result.status == 500

        and: 'ersatz received exactly one GET'
        ersatz.verify()
    }

    void "service handles 503 Service Unavailable from external API"() {
        given: 'ersatz mocks a 503 Service Unavailable'
        ersatz.expectations({ expect ->
            expect.GET('/micronaut-test/unavailable', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(503)
                    res.body('{"error":"service unavailable"}', ContentType.APPLICATION_JSON)
                })
            })
        })

        when: 'the Grails service calls fetchWithErrorHandling()'
        def result = externalApiService.fetchWithErrorHandling('unavailable')

        then: 'the error is caught with the correct status'
        result.success == false
        result.status == 503

        and: 'ersatz received exactly one GET'
        ersatz.verify()
    }

    // --- Full HTTP roundtrip: HTTP client -> Grails controller -> service -> Micronaut @Client -> ersatz ---

    void "full roundtrip: GET /external-api returns data from ersatz mock"() {
        given: 'ersatz mocks the external API with a JSON list'
        ersatz.expectations({ expect ->
            expect.GET('/micronaut-test', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(200)
                    res.body('[{"id":"1","name":"from-ersatz"}]', ContentType.APPLICATION_JSON)
                })
            })
        })

        when: 'hitting the Grails controller endpoint'
        def response = http('/external-api', 'Accept': 'application/json')

        then: 'the Grails controller returns data that originated from the ersatz mock'
        response.expectContains(200, 'micronaut-client')
                .expectContains('from-ersatz')

        and: 'ersatz confirms it served the mocked response'
        ersatz.verify()
    }

    void "full roundtrip: GET /external-api/{id} returns specific resource from ersatz mock"() {
        given: 'ersatz mocks a specific resource by ID'
        ersatz.expectations({ expect ->
            expect.GET('/micronaut-test/42', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(200)
                    res.body('{"id":"42","name":"the-answer"}', ContentType.APPLICATION_JSON)
                })
            })
        })

        when: 'hitting the Grails controller show endpoint'
        def response = http('/external-api/42', 'Accept': 'application/json')

        then: 'the response contains the ersatz-mocked resource data'
        response.expectContains(200, 'the-answer')

        and: 'ersatz confirms it served the mocked response'
        ersatz.verify()
    }

    void "full roundtrip: POST /external-api creates resource via ersatz mock"() {
        given: 'ersatz mocks a 201 Created response'
        ersatz.expectations({ expect ->
            expect.POST('/micronaut-test', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(201)
                    res.body('{"id":"new-1","name":"created-via-roundtrip"}', ContentType.APPLICATION_JSON)
                })
            })
        })

        when: 'POSTing to the Grails controller'
        def response = httpPostJson(
                '/external-api',
                '{"name":"test"}',
                'Accept': 'application/json'
        )

        then: 'the response includes the ersatz-mocked creation result'
        response.expectContains(201, 'created-via-roundtrip')

        and: 'ersatz confirms it received the POST'
        ersatz.verify()
    }

    void "full roundtrip: PUT /external-api/{id} updates resource via ersatz mock"() {
        given: 'ersatz mocks a 200 OK response for the update'
        ersatz.expectations({ expect ->
            expect.PUT('/micronaut-test/42', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(200)
                    res.body('{"id":"42","name":"updated-via-roundtrip"}', ContentType.APPLICATION_JSON)
                })
            })
        })

        when: 'PUTting to the Grails controller'
        def response = httpPutJson('/external-api/42', '{"name":"updated"}')

        then: 'the response includes the ersatz-mocked update result'
        response.expectContains(200, 'updated-via-roundtrip')

        and: 'ersatz confirms it received the PUT'
        ersatz.verify()
    }

    void "full roundtrip: DELETE /external-api/{id} deletes resource via ersatz mock"() {
        given: 'ersatz mocks a 204 No Content response'
        ersatz.expectations({ expect ->
            expect.DELETE('/micronaut-test/42', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(204)
                })
            })
        })

        when: 'DELETEing via the Grails controller'
        def response = httpDelete('/external-api/42', 'Accept': 'application/json')

        then: 'the Grails controller returns 204'
        response.expectStatus(204)

        and: 'ersatz confirms it received the DELETE'
        ersatz.verify()
    }

    void "full roundtrip: error from ersatz propagates through Grails controller"() {
        given: 'ersatz mocks a 500 Internal Server Error'
        ersatz.expectations({ expect ->
            expect.GET('/micronaut-test/fail', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(500)
                    res.body('{"error":"boom"}', ContentType.APPLICATION_JSON)
                })
            })
        })

        when: 'hitting the error-handling endpoint that catches upstream errors'
        def response = http('/external-api/safe/fail', 'Accept': 'application/json')

        then: 'the Grails controller propagates the error status from the ersatz mock'
        response.expectStatus(500)

        and: 'ersatz confirms it served the error response'
        ersatz.verify()
    }

    void "full roundtrip: ersatz mocks different responses for sequential calls"() {
        given: 'ersatz mocks two different resources'
        ersatz.expectations({ expect ->
            expect.GET('/micronaut-test/1', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(200)
                    res.body('{"id":"1","name":"first"}', ContentType.APPLICATION_JSON)
                })
            })
            expect.GET('/micronaut-test/2', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(200)
                    res.body('{"id":"2","name":"second"}', ContentType.APPLICATION_JSON)
                })
            })
        })

        when: 'fetching two different resources through the service'
        def first = externalApiService.fetchById('1')
        def second = externalApiService.fetchById('2')

        then: 'each returns the correct mocked data from ersatz'
        first.contains('first')
        second.contains('second')
        !first.contains('second')
        !second.contains('first')

        and: 'ersatz received both requests'
        ersatz.verify()
    }

    @Retry(count = 2, delay = 200, exceptions = [io.micronaut.http.client.exceptions.HttpClientException])
    void "full roundtrip: ersatz mocks empty response body"() {
        given: 'ersatz mocks an endpoint returning 200 with an empty JSON object'
        ersatz.expectations({ expect ->
            expect.GET('/micronaut-test/empty', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(200)
                    res.body('{}', ContentType.APPLICATION_JSON)
                })
            })
        })

        when: 'fetching the empty resource'
        def result = externalApiService.fetchById('empty')

        then: 'the empty JSON is returned'
        result == '{}'

        and: 'ersatz received the request'
        ersatz.verify()
    }

    void "full roundtrip: ersatz mocks large JSON response"() {
        given: 'ersatz mocks an endpoint returning a large JSON array'
        def largeArray = (1..50).collect { """{"id":"$it","name":"item-$it"}""" }.join(',')
        def largeBody = "[$largeArray]"
        ersatz.expectations({ expect ->
            expect.GET('/micronaut-test', { req ->
                req.called(1)
                req.responder({ res ->
                    res.code(200)
                    res.body(largeBody, ContentType.APPLICATION_JSON)
                })
            })
        })

        when: 'fetching the large response through the service'
        def result = externalApiService.fetchAll()

        then: 'all 50 items are present'
        result != null
        result.contains('"id":"1"')
        result.contains('"id":"50"')
        result.contains('item-25')

        and: 'ersatz received the request'
        ersatz.verify()
    }
}
