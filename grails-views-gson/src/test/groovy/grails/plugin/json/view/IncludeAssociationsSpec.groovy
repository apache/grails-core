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

import grails.plugin.json.view.test.JsonViewTest
import org.grails.testing.GrailsUnitTest
import spock.lang.Specification

class IncludeAssociationsSpec extends Specification implements JsonViewTest, GrailsUnitTest {

    void "test includeAssociations with json api"() {
        given: "A collection"
        mappingContext.addPersistentEntities(Player, Team)
        Player player = new Player(name: "Cantona")
        player.id = 1
        def players = [player]

        when: "A collection type is rendered"
        def renderResult = render('''
import groovy.transform.*
import grails.plugin.json.view.*

@Field Collection<Player> players

json jsonapi.render(players, [associations: false])
''', [players: players]) {
            uri = "/foo"
        }

        then: "The result is an array"
        renderResult.jsonText == '{"data":[{"type":"player","id":"1","attributes":{"name":"Cantona"}}],"links":{"self":"/foo"}}'

    }
}
