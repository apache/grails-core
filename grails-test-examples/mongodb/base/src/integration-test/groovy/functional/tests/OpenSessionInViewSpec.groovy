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

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import spock.lang.Specification

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport
import org.apache.grails.testing.http.client.TestHttpResponse

/**
 * An application whose only datastore is MongoDB keeps a session open for each request, so an
 * action without a transaction can read what its queries return. Without that session, the one a
 * query opens for itself closes before the action reads the results: MongoDB returns the first 101
 * documents with the query and they are decoded at once, but decoding a later batch fails with
 * "No datastore session found".
 */
@Integration(applicationClass = Application)
class OpenSessionInViewSpec extends Specification implements HttpClientSupport {

    @Autowired
    ApplicationContext applicationContext

    String teamId

    void setup() {
        Player.withNewSession { session ->
            Team team = new Team(name: 'Rovers').save()
            102.times { int i -> new Player(name: "Player $i", team: team).save() }
            session.flush()
            teamId = team.id.toString()
        }
    }

    void cleanup() {
        Player.collection.drop()
        Team.collection.drop()
    }

    void "the open session in view interceptor is registered"() {
        expect:
        applicationContext.containsBean('mongoOpenSessionInViewInterceptor')
    }

    void "an action reads results beyond the first batch"() {
        expect: 'the 102nd player arrives in the second batch'
        http('/player/count').assertStatus(200).body() == '102'
    }

    void "an action's save is written without a flush or a transaction"() {
        when:
        String playerId = postForm('/player/save', "name=Late+Signing&teamId=$teamId").assertStatus(200).body()

        then:
        Player.withNewSession { Player.get(playerId)?.name } == 'Late Signing'
    }

    void "an action's change to an instance it did not save is not written"() {
        given:
        String playerId = Player.withNewSession { Player.findByName('Player 0').id.toString() }

        when:
        postForm("/player/rename/$playerId", 'name=Renamed').assertStatus(200)

        then:
        Player.withNewSession { Player.get(playerId).name } == 'Player 0'
    }

    private TestHttpResponse postForm(String path, String form) {
        httpPost(path, form, 'application/x-www-form-urlencoded')
    }
}
