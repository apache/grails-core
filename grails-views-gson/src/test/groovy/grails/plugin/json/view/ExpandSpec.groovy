package grails.plugin.json.view

import com.fasterxml.jackson.databind.ObjectMapper
import grails.plugin.json.view.test.JsonRenderResult
import grails.plugin.json.view.test.JsonViewTest
import org.grails.datastore.mapping.core.Session
import org.grails.testing.GrailsUnitTest
import spock.lang.Shared
import spock.lang.Specification

class ExpandSpec extends Specification implements JsonViewTest, GrailsUnitTest {

    @Shared
    ObjectMapper objectMapper = new ObjectMapper()

    void setup() {
        mappingContext.addPersistentEntities(Team, Player)
    }

    void 'Test expand parameter allows expansion of child associations'() {
        given: 'An entity with a proxy association'
        def mockSession = Mock(Session)
        mockSession.getMappingContext() >> mappingContext
        mockSession.retrieve(Team, 1L) >> new Team(name: 'Manchester United')
        def teamProxy = mappingContext.proxyFactory.createProxy(mockSession, Team, 1L)

        def player = new Player(name: 'Cantona', team: teamProxy)

        def templateText = '''
            import grails.plugin.json.view.*
            
            @Field Player player
            
            json g.render(player)
        '''

        when: 'The domain is rendered'
        def result = render(templateText, [player: player])

        then: 'The result does not include the proxied association'
        objectMapper.readTree(result.jsonText) == objectMapper.readTree('''
            {
                "team": { "id": 1 },
                "name": "Cantona"
            }
        ''')

        when: 'The domain is rendered with expand parameters'
        result = render(templateText, [player:player]) {
            params(expand: 'team')
        }

        then: 'The association is expanded'
        objectMapper.readTree(result.jsonText) == objectMapper.readTree('''
            {
                "team": {
                    "id": 1,
                    "name": "Manchester United"
                },
                "name": "Cantona"
            }
        ''')
    }

    void 'Test expand parameter on nested property'() {
        given: 'An entity with a proxy association'
        def mockSession = Mock(Session)
        mockSession.getMappingContext() >> mappingContext
        mockSession.retrieve(Team, 1L) >> new Team(name: 'Manchester United')
        def teamProxy = mappingContext.proxyFactory.createProxy(mockSession, Team, 1L)

        def player = new Player(name: 'Cantona', team: teamProxy)
        def templateText = '''
            import grails.plugin.json.view.*
            
            @Field Map map
            
            json g.render(map)
        '''

        when: 'The domain is rendered with expand parameters'
        def result = render(templateText, [map: [player:player]]) {
            params(expand: 'player.team')
        }

        then: 'The association is expanded'
        objectMapper.readTree(result.jsonText) == objectMapper.readTree('''
            {
                "player": {
                    "team": {
                        "id": 1,
                        "name": "Manchester United"
                    },
                    "name": "Cantona"
                }
            }
        ''')
    }

    void 'Test expand parameter allows expansion of child associations with HAL'() {
        given: 'An entity with a proxy association'
        def mockSession = Mock(Session)
        mockSession.getMappingContext() >> mappingContext
        mockSession.retrieve(Team, 1L) >> new Team(name: 'Manchester United')
        def teamProxy = mappingContext.proxyFactory.createProxy(mockSession, Team, 1L)

        def player = new Player(name: 'Cantona', team: teamProxy)

        def templateText = '''
            import grails.plugin.json.view.*
            model {
                Player player
            }
            json hal.render(player)
        '''

        when: 'The domain is rendered'
        def result = render(templateText, [player: player])

        then: 'The result does not include the proxied association'
        objectMapper.readTree(result.jsonText) == objectMapper.readTree('''
            {
                "_links": {
                    "self": {
                        "href": "http://localhost:8080/player",
                        "hreflang": "en",
                        "type": "application/hal+json"
                    }
                },
                "name": "Cantona"
            }
        ''')

        when: 'The domain is rendered with expand parameters'
        result = render(templateText, [player: player]) {
            params(expand: 'team')
        }

        then: 'The association is expanded'
        objectMapper.readTree(result.jsonText) == objectMapper.readTree('''
            {
                "_embedded": {
                    "team": {
                        "_links": {
                            "self": {
                                "href": "http://localhost:8080/team/1",
                                "hreflang": "en",
                                "type": "application/hal+json"
                            }
                        },
                        "name": "Manchester United"
                    }
                },
                "_links": {
                    "self": {
                        "href": "http://localhost:8080/player",
                        "hreflang": "en",
                        "type": "application/hal+json"
                    }
                },
                "name": "Cantona"
            }
        ''')
    }

    void 'Test expand parameter allows expansion of child associations with JSON API'() {
        given: 'An entity with a proxy association'
        def mockSession = Mock(Session)
        mockSession.getMappingContext() >> mappingContext
        mockSession.retrieve(Team, 9L) >> new Team(name: 'Manchester United')
        def teamProxy = mappingContext.proxyFactory.createProxy(mockSession, Team, 9L)
        def player = new Player(name: 'Cantona', team: teamProxy)
        player.id = 3

        when: 'The domain is rendered with expand parameters'
        JsonRenderResult result = render('''
            import grails.plugin.json.view.*
            model {
                Player player
            }
            
            json jsonapi.render(player, [expand: 'team'])
        ''', [player: player])

        then: 'The JSON relationships are in place'
        objectMapper.readTree(result.jsonText) == objectMapper.readTree('''
            {
                "data": {
                    "type": "player",
                    "id": "3",
                    "attributes": {
                        "name": "Cantona"
                    },
                    "relationships": {
                        "team": {
                            "links": {
                                "self": "/team/9"
                            },
                            "data": {
                                "type": "team",
                                "id": "9"
                            }
                        }
                    }
                },
                "links": {
                    "self": "/player/3"
                },
                "included": [
                    {
                        "type":"team",
                        "id": "9",
                        "attributes": {
                            "titles": null,
                            "name": "Manchester United"
                        },
                        "relationships": {
                            "players": {
                                "data":[]
                            },
                            "captain": {
                                "data":null
                            }
                        },
                        "links": {
                            "self": "/team/9"
                        }
                    }
                ]
            }
        ''')
    }
}
