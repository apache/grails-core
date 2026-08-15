/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.grails.benchmarks.report

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import groovy.json.JsonOutput
import spock.lang.Specification

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

class GitHubCommentsHttpSpec extends Specification {
    private HttpServer server
    private List<Map<String, String>> requests
    private List<List<Map<String, Object>>> pages

    def setup() {
        requests = []
        pages = [[]]
        server = HttpServer.create(new InetSocketAddress('localhost', 0), 0)
        server.createContext('/repos/') { HttpExchange exchange ->
            requests.add([
                    method: exchange.requestMethod,
                    path  : exchange.requestURI.rawPath,
                    query : exchange.requestURI.rawQuery,
                    auth  : exchange.requestHeaders.getFirst('Authorization'),
                    body  : new String(exchange.requestBody.readAllBytes(), StandardCharsets.UTF_8)
            ])
            String response = exchange.requestMethod == 'GET'
                    ? JsonOutput.toJson(pages[page(exchange) - 1])
                    : JsonOutput.toJson([id: 99])
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8)
            exchange.responseHeaders.set('Content-Type', 'application/json')
            exchange.sendResponseHeaders(200, responseBytes.length)
            exchange.responseBody.write(responseBytes)
            exchange.close()
        }
        server.start()
    }

    def cleanup() {
        server.stop(0)
    }

    def "unknown, missing, and non-map authors create a new comment"() {
        given:
        pages = [[[id: 12, body: 'old <!-- grails-jmh-benchmark -->', user: author]]]

        when:
        comments().post('report', 'apache/grails-core', '42', 'token')

        then:
        requests.size() == 2
        assertRequest(0, 'GET', '/repos/apache/grails-core/issues/42/comments', 'per_page=100&page=1', '')
        assertRequest(1, 'POST', '/repos/apache/grails-core/issues/42/comments', null, JsonOutput.toJson([body: 'report\n\n<!-- grails-jmh-benchmark -->']))

        where:
        author << [null, [:], [login: 'alice'], 'github-actions[bot]']
    }

    def "a full first comment page patches the newest page-two bot marker"() {
        given:
        pages = [(0..<100).collect { int id -> [id: id, body: 'unrelated', user: [login: 'github-actions[bot]']] },
                 [[id: 101, body: 'old <!-- grails-jmh-benchmark -->', user: [login: 'github-actions[bot]']],
                  [id: 102, body: 'human <!-- grails-jmh-benchmark -->', user: [login: 'alice']],
                  [id: 103, body: 'new <!-- grails-jmh-benchmark -->', user: [login: 'github-actions[bot]']]]]

        when:
        comments().post('report', 'apache/grails-core', '42', 'token')

        then:
        requests.size() == 3
        assertRequest(0, 'GET', '/repos/apache/grails-core/issues/42/comments', 'per_page=100&page=1', '')
        assertRequest(1, 'GET', '/repos/apache/grails-core/issues/42/comments', 'per_page=100&page=2', '')
        assertRequest(2, 'PATCH', '/repos/apache/grails-core/issues/comments/103', null, JsonOutput.toJson([body: 'report\n\n<!-- grails-jmh-benchmark -->']))
    }

    def "the newest GitHub Actions marker is patched"() {
        given:
        pages = [[[id: 10, body: 'old <!-- grails-jmh-benchmark -->', user: [login: 'github-actions[bot]']],
                  [id: 11, body: 'human <!-- grails-jmh-benchmark -->', user: [login: 'alice']],
                  [id: 12, body: 'new <!-- grails-jmh-benchmark -->', user: [login: 'github-actions[bot]']]]]

        when:
        comments().post('report', 'apache/grails-core', '42', 'token')

        then:
        requests.size() == 2
        assertRequest(0, 'GET', '/repos/apache/grails-core/issues/42/comments', 'per_page=100&page=1', '')
        assertRequest(1, 'PATCH', '/repos/apache/grails-core/issues/comments/12', null, JsonOutput.toJson([body: 'report\n\n<!-- grails-jmh-benchmark -->']))
    }

    private GitHubComments comments() {
        new GitHubComments("http://localhost:${server.address.port}")
    }

    private void assertRequest(int index, String method, String path, String query, String body) {
        assert requests[index] == [method: method, path: path, query: query, auth: 'Bearer token', body: body]
    }

    private static int page(HttpExchange exchange) {
        Integer.parseInt(exchange.requestURI.rawQuery.substring(exchange.requestURI.rawQuery.lastIndexOf('page=') + 5))
    }
}
