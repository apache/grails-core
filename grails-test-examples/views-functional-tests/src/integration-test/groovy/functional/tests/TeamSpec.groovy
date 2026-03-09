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

import spock.lang.IgnoreIf
import spock.lang.Specification

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClient
import org.springframework.beans.factory.annotation.Autowired

@Integration
class TeamSpec extends Specification {

    @Autowired HttpClient http

    void 'Test association template rendering'() {
        when:
        def response = http.get('/teams/1')

        then: 'The response is correct'
        response.expectJson(200, 'Content-Type': 'application/json;charset=UTF-8', '''
            {
                "id": 1,
                "name": "Barcelona",
                "players": [
                    { "id": 1 },
                    { "id": 2 }
                ],
                "captain": { "id": 1 },
                "sport": "football"
            }
        ''')
    }

    void 'Test deep association template rendering'() {
        when:
        def response = http.get('/teams/deep/1')

        then: 'The response is correct'
        response.expectJson(200, 'Content-Type': 'application/json;charset=UTF-8', '''
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
        def response = http.get('/teams/hal/1')
        def lang = "${System.properties.getProperty('user.language')}_${System.properties.getProperty('user.country')}"

        then: 'The response is correct'
        response.expectJson(200, 'Content-Type': 'application/hal+json;charset=UTF-8', """
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
        def response = http.get('/team/composite')

        then: 'The response is correct'
        response.expectJson(200, 'Content-Type': 'application/json;charset=UTF-8', '''
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
