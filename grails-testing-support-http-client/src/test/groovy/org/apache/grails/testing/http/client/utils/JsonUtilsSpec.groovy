/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.testing.http.client.utils

import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse

import javax.net.ssl.SSLSession

import groovy.json.JsonException
import groovy.json.JsonParserType

import org.opentest4j.AssertionFailedError
import spock.lang.Specification

class JsonUtilsSpec extends Specification {

    void 'JsonSlurperConfig applies supported JsonSlurper options'() {
        when:
        def slurper = new JsonUtils.SlurperConfig(
                parserType: JsonParserType.LAX,
                checkDates: true,
                chop: true,
                lazyChop: true,
                maxSizeForInMemory: 64
        ).newSlurper()

        then:
        slurper.type == JsonParserType.LAX
        slurper.checkDates
        slurper.chop
        slurper.lazyChop
        slurper.maxSizeForInMemory == 64
    }

    void 'parseText uses the default strict parser unless configured otherwise'() {
        when:
        JsonUtils.parseText("{name:'Widget'}")

        then:
        thrown(JsonException)

        when:
        def json = JsonUtils.parseText("{name:'Widget'}", new JsonUtils.SlurperConfig(parserType: JsonParserType.LAX))

        then:
        json.name == 'Widget'
    }

    void 'verifyJsonTree ignores object key order for map and json string expectations'() {
        given:
        def response = mockResponse('{"b":2,"a":1,"nested":{"d":4,"c":3}}')

        expect:
        JsonUtils.verifyJsonTree(response, [a: 1, b: 2, nested: [c: 3, d: 4]])
        JsonUtils.verifyJsonTree(response, '{"nested":{"c":3,"d":4},"a":1,"b":2}')
    }

    void 'verifyJsonTree accepts lax json only when configured'() {
        given:
        def response = mockResponse("{name:'Widget', nested:{ok:true}}")
        def config = new JsonUtils.SlurperConfig(parserType: JsonParserType.LAX)

        when:
        JsonUtils.verifyJsonTree(response, "{name:'Widget', nested:{ok:true}}")

        then:
        thrown(JsonException)

        when:
        JsonUtils.verifyJsonTree(response, "{nested:{ok:true}, name:'Widget'}", config)

        then:
        noExceptionThrown()
    }

    void 'verifyJsonTreeContains accepts lax json subsets only when configured'() {
        given:
        def response = mockResponse("{name:'Widget', nested:{ok:true}, tags:['a','b']}")
        def config = new JsonUtils.SlurperConfig(parserType: JsonParserType.LAX)

        when:
        JsonUtils.verifyJsonTreeContains(response, "{nested:{ok:true}}")

        then:
        thrown(JsonException)

        when:
        JsonUtils.verifyJsonTreeContains(response, "{nested:{ok:true}, tags:['a']}", config)

        then:
        noExceptionThrown()
    }

    void 'verifyJsonTreeContains reports mismatch paths in assertion message'() {
        given:
        def response = mockResponse('{"nested":{"ok":false},"items":[1],"name":"Widget"}')

        when:
        JsonUtils.verifyJsonTreeContains(response, [nested: [ok: true], items: [1, 2], missing: 1])

        then:
        def error = thrown(AssertionError)
        error.message.contains('JSON does not contain expected subset')
        error.message.contains('$.nested.ok expected true but was false')
        error.message.contains('$.items expected array size >= 2 but was 1')
        error.message.contains('$ missing key \'missing\'')
    }

    void 'verifyJsonTree failures include canonicalized expected and actual payloads'() {
        given:
        def response = mockResponse('{"b":2,"a":1}')

        when:
        JsonUtils.verifyJsonTree(response, '{"a":1,"b":3}')

        then:
        def error = thrown(AssertionFailedError)
        error.expected.value.contains('"a": 1')
        error.expected.value.contains('"b": 3')
        error.actual.value.contains('"a": 1')
        error.actual.value.contains('"b": 2')
    }

    private static HttpResponse<String> mockResponse(String bodyText) {
        HttpHeaders httpHeaders = HttpHeaders.of(Collections.emptyMap(), { n, v -> true })
        [
                statusCode: { -> 200 },
                body: { -> bodyText },
                headers: { -> httpHeaders },
                request: { -> null as HttpRequest },
                previousResponse: { -> Optional.empty() as Optional<HttpResponse<String>> },
                sslSession: { -> Optional.empty() as Optional<SSLSession> },
                uri: { -> URI.create('http://localhost') },
                version: { -> null }
        ] as HttpResponse<String>
    }
}




