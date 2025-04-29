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

import com.fasterxml.jackson.databind.ObjectMapper
import grails.testing.mixin.integration.Integration
import grails.testing.spock.RunOnce
import grails.web.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import org.junit.jupiter.api.BeforeEach
import spock.lang.IgnoreIf
import spock.lang.Shared

@Integration(applicationClass = Application)
class TeamSpec extends HttpClientSpec {

    @RunOnce
    @BeforeEach
    void init() {
        super.init()
    }

    @Shared
    String lang

    @Shared
    ObjectMapper objectMapper

    void setup() {
        objectMapper = new ObjectMapper()
    }

    void setupSpec() {
        this.lang = "${System.properties.getProperty('user.language')}_${System.properties.getProperty('user.country')}"
    }

    void 'Test association template rendering'() {
        when:
        HttpRequest request = HttpRequest.GET('/teams/1')
        HttpResponse<String> resp = client.toBlocking().exchange(request, String)

        then: 'The response is correct'
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'

        // Note current behaviour is that the captain is not rendered twice
        objectMapper.readTree(resp.body()) == objectMapper.readTree('''
            {
                "id": 1,
                "name": "Barcelona",
                "players": [
                    { "id": 1},
                    { "id": 2}
                ],
                "captain": { "id":1 },
                "sport": "football"
            }
        ''')
    }

    void 'Test deep association template rendering'() {
        when:
        HttpRequest request = HttpRequest.GET('/teams/deep/1')
        HttpResponse<String> resp = client.toBlocking().exchange(request, String)

        then: 'The response is correct'
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        objectMapper.readTree(resp.body()) == objectMapper.readTree('''
            {
                "id": 1,
                "name": "Barcelona",
                "players": [
                    { "id": 1, "name": "Iniesta", "sport": "football" },
                    { "id": 2, "name": "Messi", "sport": "football" }
                ],
                "captain": { "id": 1, "name": "Iniesta", "sport": "football" },
                "sport": "football"
            }
        ''')
    }

    @IgnoreIf({ System.getenv('GITHUB_REF') })
    void 'Test HAL rendering'() {
        when:
        HttpRequest request = HttpRequest.GET('/teams/hal/1')
        HttpResponse<String> resp = client.toBlocking().exchange(request, String)

        then: 'The response is correct'
        resp.status == HttpStatus.OK

        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/hal+json;charset=UTF-8'
        objectMapper.readTree(resp.body()) == objectMapper.readTree("""
            {
                \"_embedded\": {
                    \"players\": [
                        {
                            \"_links\": {
                                \"self\": {
                                    \"href\": \"http://localhost:$serverPort/player/show/1\",
                                    \"hreflang\": \"$lang\",
                                    \"type\": \"application/hal+json\"
                                }
                            },
                            \"name\": \"Iniesta\",
                            \"version\": 0
                        },
                        {
                            \"_links\": {
                                \"self\": {
                                    \"href\": \"http://localhost:$serverPort/player/show/2\",
                                    \"hreflang\": \"$lang\",
                                    \"type\": \"application/hal+json\"
                                }
                            },
                            \"name\": \"Messi\",
                            \"version\": 0
                        }
                    ],
                    \"captain\": {
                        \"_links\": {
                            \"self\": {
                                \"href\": \"http://localhost:$serverPort/player/show/1\",
                                \"hreflang\": \"$lang\",
                                \"type\": \"application/hal+json\"
                            }
                        },
                        \"name\": \"Iniesta\",
                        \"version\": 0
                    }
                },
                \"_links\": {
                        \"self\": {
                            \"href\": \"http://localhost:$serverPort/teams/1\",
                            \"hreflang\": \"$lang\",
                            \"type\": \"application/hal+json\"
                        }
                },
                \"id\": 1,
                \"name\": \"Barcelona\",
                \"sport\": \"football\",
                \"another\": {
                    \"foo\": \"bar\"
                }
            }
        """)
    }

    void 'Test composite ID rendering'() {
        given:
        Composite.withNewSession {
            Composite.withNewTransaction {
                new Composite(name: 'foo', team: Team.load(1), player: Player.load(2)).save(flush: true, failOnError: true)
            }
        }

        when:
        HttpRequest request = HttpRequest.GET('/team/composite')
        HttpResponse<String> resp = client.toBlocking().exchange(request, String)

        then: 'The response is correct'
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        objectMapper.readTree(resp.body()) == objectMapper.readTree('''
            {
                "player": {
                    "id": 2,
                    "name": "Messi",
                    "sport": "football"
                },
                "team": {
                    "id": 1,
                    "name": "Barcelona",
                    "captain": { "id": 1 },
                    "sport": "football"
                },
                "name":"foo"
            }
        ''')
    }
}
