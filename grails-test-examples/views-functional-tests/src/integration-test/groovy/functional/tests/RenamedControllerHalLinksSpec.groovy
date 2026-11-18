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
package functional.tests

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport
import org.apache.grails.testing.http.client.TestHttpResponse

/**
 * Pins the HAL links rendered for domain classes served by controllers not named after them, through
 * JSON views and through the HAL renderers.
 */
@Integration
@Tag('http-client')
class RenamedControllerHalLinksSpec extends Specification implements HttpClientSupport {

    private static final Map<String, String> HAL = [Accept: 'application/hal+json']

    @Shared
    Map<String, Long> ids = [:]

    void setup() {
        if (!ids) {
            Publisher.withNewTransaction {
                Publisher publisher = new Publisher(name: 'Penguin').save(flush: true, failOnError: true)
                ids.publisher = publisher.id
                ids.magazine = new Magazine(title: 'Wired', publisher: publisher).save(flush: true, failOnError: true).id
                ids.journal = new Journal(title: 'Nature', publisher: publisher).save(flush: true, failOnError: true).id
            }
        }
    }

    void "a JSON view of an instance links it to the controller serving it"() {
        when:
        Map json = hal("/periodicals/${ids.magazine}")

        then:
        path(json._links.self.href) == "/periodicals/${ids.magazine}"
    }

    void "a JSON view of a collection links it, and each element, to the controller serving them"() {
        when:
        Map json = hal('/periodicals')

        then:
        path(json._links.self.href) == '/periodicals'
        embedded(json)*._links*.self*.href.collect { path(it) } == ["/periodicals/${ids.magazine}"]
    }

    void "the HAL renderer links an instance and its lazy association to the controllers serving them"() {
        when:
        Map json = hal("/papers/${ids.journal}")

        then:
        path(json._links.self.href) == "/papers/${ids.journal}"
        path(json._links.publisher.href) == "/publishers/${ids.publisher}"
    }

    void "the HAL collection renderer links a collection, and each element, to the controller serving them"() {
        when:
        Map json = hal('/papers')

        then:
        path(json._links.self.href) == '/papers'
        embedded(json)*._links*.self*.href.collect { path(it) } == ["/papers/${ids.journal}"]
    }

    private Map hal(String path) {
        TestHttpResponse response = http(HAL, path)
        assert response.statusCode() == 200
        response.json()
    }

    private static List<Map> embedded(Map json) {
        def embedded = json._embedded
        (embedded instanceof Map ? ((Map) embedded).values().flatten() : embedded) as List<Map>
    }

    private static String path(Object href) {
        href == null ? null : new URI(href.toString()).path
    }
}
