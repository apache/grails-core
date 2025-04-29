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
import grails.plugin.json.view.test.JsonViewTest
import spock.lang.Shared
import spock.lang.Specification

class NullRenderingSpec extends Specification implements JsonViewTest {

    @Shared
    ObjectMapper objectMapper = new ObjectMapper()

    void 'test rendering nulls with a domain'() {
        given:
        def templateText = '''
            import grails.plugin.json.view.*
            
            model {
                Player player
            }
            
            json g.render(player)
        '''

        when:
        mappingContext.addPersistentEntity(Player)
        def renderResult = render(templateText, [player: new Player()])

        then: 'No fields are rendered because they are null'
        renderResult.jsonText == '{}'
    }

    void 'test rendering nulls with a domain (renderNulls = true)'() {
        given:
        def templateText = '''
            import grails.plugin.json.view.*
            
            model {
                Player player
            }
            
            json g.render(player, [renderNulls: true])
        '''

        when:
        mappingContext.addPersistentEntity(Player)
        def renderResult = render(templateText, [player: new Player()])

        then: 'No fields are rendered because they are null'
        objectMapper.readTree(renderResult.jsonText) == objectMapper.readTree('{ "team": null, "name": null}')
    }

    void 'test rendering nulls with a map'() {
        given:
        def templateText = '''
            model {
                Map map
            }
            
            json g.render(map)
        '''

        when:
        mappingContext.addPersistentEntity(Player)
        def renderResult = render(templateText, [map: [foo: null, bar: null]])

        then: 'Maps with nulls are rendered by default'
        objectMapper.readTree(renderResult.jsonText) == objectMapper.readTree('{"foo": null,"bar": null}')
    }

    void 'test rendering nulls with a pogo'() {
        given:
        def templateText = '''
            model {
                Object obj
            }
            
            json g.render(obj)
        '''

        when:
        mappingContext.addPersistentEntity(Player)
        def renderResult = render(templateText, [obj: new Child2()])

        then: 'No fields are rendered because they are null'
        renderResult.jsonText == '{}'
    }

    void 'test rendering nulls with a pogo (renderNulls = true)'() {
        given:
        def templateText = '''
            model {
                Object obj
            }
            
            json g.render(obj, [renderNulls: true])
        '''

        when:
        mappingContext.addPersistentEntity(Player)
        def renderResult = render(templateText, [obj: new Child2()])

        then:
        objectMapper.readTree(renderResult.jsonText) == objectMapper.readTree('{"name": null, "parent": null}')
    }
}