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
package grails.plugin.json.view

import com.fasterxml.jackson.databind.ObjectMapper
import grails.views.ViewException
import grails.views.json.test.JsonViewUnitTest
import spock.lang.Shared
import spock.lang.Specification

class IterableRenderSpec extends Specification implements JsonViewUnitTest {

    @Shared
    ObjectMapper objectMapper

    def setupSpec() {
        objectMapper = new ObjectMapper()
    }

    void 'Test render a collection type'() {
        given: 'A collection'
        def players = [new Player(name: 'Cantona')]

        when: 'A collection type is rendered'
        def renderResult = render('''
            import groovy.transform.*
            import grails.plugin.json.view.*
            
            @Field Collection<Player> players
            
            json g.render(players)
        ''', [players: players])

        then: 'The result is an array'
        renderResult.jsonText == '[{"name":"Cantona"}]'
    }

    void 'Test render a collection type with HAL'() {
        given: 'A collection'
        def players = [new Player(name: 'Cantona')]

        when: 'A collection type is rendered'
        def renderResult = render('''
            import groovy.transform.*
            import grails.plugin.json.view.*
            
            @Field Collection<Player> players
            
            json hal.render(players)
        ''', [players: players])

        then: 'The result is an array'
        objectMapper.readTree(renderResult.jsonText) == objectMapper.readTree('''
            {
                "_links": {
                    "self": {
                        "href": "http://localhost:8080/player/index",
                        "hreflang": "en",
                        "type": "application/hal+json"
                    }
                },
                "_embedded": [
                    {
                        "_links": {
                            "self": {
                                "href": "http://localhost:8080/player/index",
                                "hreflang": "en",
                                "type": "application/hal+json"
                            }
                        },
                        "name": "Cantona"
                    }
                ]
            }
        ''')

    }

    void 'Test render a single element collection type with JSON API'() {
        given: 'A collection'
        mappingContext.addPersistentEntities(Player, Team)
        Player player = new Player(name: 'Cantona')
        player.id = 1
        def players = [player]

        when: 'A collection type is rendered'
        def renderResult = render('''
            import groovy.transform.*
            import grails.plugin.json.view.*
            
            @Field Collection<Player> players
            
            json jsonapi.render(players)
        ''', [players: players]) {
            uri = '/foo'
        }

        then: 'The result is an array'
        objectMapper.readTree(renderResult.jsonText) == objectMapper.readTree('''
            {
                "data": [
                    {
                        "type": "player",
                        "id": "1",
                        "attributes": {
                            "name": "Cantona"
                        },
                        "relationships": {
                            "team": {
                                "data": null
                            }
                        }
                    }
                ],
                "links": {
                    "self": "/foo"
                }
            }
        ''')
    }

    void 'Test render a collection type with JSON API'() {
        given: 'A collection'
        mappingContext.addPersistentEntities(Player, Team)
        Player player = new Player(name: 'Cantona')
        player.id = 1
        Player player2 = new Player(name: 'Louis')
        player2.id = 2
        def players = [player, player2]

        when: 'A collection type is rendered'
        def renderResult = render('''
            import groovy.transform.*
            import grails.plugin.json.view.*
            
            @Field Collection<Player> players
            
            json jsonapi.render(players)
        ''', [players: players]) {
            uri = '/foo'
        }

        then: 'The result is an array'
        objectMapper.readTree(renderResult.jsonText) == objectMapper.readTree('''
            {
                "data": [
                    {
                        "type": "player",
                        "id": "1",
                        "attributes": {
                            "name": "Cantona"
                        },
                        "relationships": {
                            "team": {
                                "data": null
                            }
                        }
                    },
                    {
                        "type": "player",
                        "id": "2",
                        "attributes": {
                            "name": "Louis"
                        },
                        "relationships": {
                            "team": {
                                "data": null
                            }
                        }
                    }
                ],
                "links": {
                    "self": "/foo"
                }
            }
        ''')
    }

    void 'Test render a collection type with JSON API and pagination'() {
        given: 'A collection'
        mappingContext.addPersistentEntities(Player, Team)
        Player player = new Player(name: 'Cantona')
        player.id = 1
        Player player2 = new Player(name: 'Louis')
        player2.id = 2
        def players = [player, player2]

        when: 'A collection type is rendered total must be greater than max (10)'
        def renderResult = render('''
            import groovy.transform.*
            import grails.plugin.json.view.*
            
            @Field Collection<Player> players
            
            json jsonapi.render(players, [pagination: [resource: Player, total: 11]])
        ''', [players: players], {
            uri = '/foo'
        })

        then: 'The result is an array'
        objectMapper.readTree(renderResult.jsonText) == objectMapper.readTree('''
            {
                "data": [
                    {
                        "type": "player",
                        "id": "1",
                        "attributes": {
                            "name": "Cantona"
                        },
                        "relationships": {
                            "team": {
                                "data": null
                            }
                        }
                    },
                    {
                        "type": "player",
                        "id": "2",
                        "attributes": {
                            "name": "Louis"
                        },
                        "relationships": {
                            "team": {
                                "data": null
                            }
                        }
                    }
                ],
                "links": {
                    "self": "/foo",
                    "first": "http://localhost:8080/player/index?offset=0&max=10",
                    "next": "http://localhost:8080/player/index?offset=10&max=10",
                    "last": "http://localhost:8080/player/index?offset=10&max=10"
                }
            }
        ''')
    }

    void 'Test render a collection type with JSON API and pagination override max'() {
        given: 'A collection'
        mappingContext.addPersistentEntities(Player, Team)
        Player player = new Player(name: 'Cantona')
        player.id = 1
        Player player2 = new Player(name: 'Louis')
        player2.id = 2
        def players = [player, player2]

        when: 'A collection type is rendered total must be greater than max (10)'
        def renderResult = render('''
            import groovy.transform.*
            import grails.plugin.json.view.*
            
            @Field Collection<Player> players
            
            json jsonapi.render(players, [pagination: [resource: Player, total: 11, max: 5]])
        ''', [players: players]) {
            uri = '/foo'
        }

        then: 'The result is an array'
        objectMapper.readTree(renderResult.jsonText) == objectMapper.readTree('''
            {
                "data": [
                    {
                        "type": "player",
                        "id": "1",
                        "attributes": {
                            "name": "Cantona"
                        },
                        "relationships": {
                            "team": {
                                "data": null
                            }
                        }
                    },
                    {
                        "type": "player",
                        "id": "2",
                        "attributes": {
                            "name": "Louis"
                        },
                        "relationships": {
                            "team": {
                                "data": null
                            }
                        }
                    }
                ],
                "links": {
                    "self": "/foo",
                    "first": "http://localhost:8080/player/index?offset=0&max=5",
                    "next": "http://localhost:8080/player/index?offset=5&max=5",
                    "last": "http://localhost:8080/player/index?offset=10&max=5"
                }
            }
        ''')
    }

    void 'Test render a collection type with JSON API and pagination (incorrect arguments)'() {
        given: 'A collection'
        mappingContext.addPersistentEntities(Player, Team)
        Player player = new Player(name: 'Cantona')
        player.id = 1
        Player player2 = new Player(name: 'Louis')
        player2.id = 2
        def players = [player, player2]

        when: 'A collection type is rendered total must be greater than max (10)'
        render('''
            import groovy.transform.*
            import grails.plugin.json.view.*
            
            @Field Collection<Player> players
            
            json jsonapi.render(players, [pagination: [total: 11]])
        ''', [players: players]) {
            uri = '/foo'
        }

        then: 'An illegal argument exception is thrown'
        def ex = thrown(ViewException)
        ex.cause instanceof IllegalArgumentException
        ex.message == 'Error rendering view: JSON API pagination arguments must contain resource and total'
    }
}
