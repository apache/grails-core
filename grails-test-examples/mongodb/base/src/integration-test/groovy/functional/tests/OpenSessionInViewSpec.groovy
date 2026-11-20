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

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport
import org.apache.grails.testing.http.client.TestHttpResponse

/**
 * An application whose only datastore is MongoDB keeps a session open for each request, so an
 * action without a transaction can read what its queries return. Without that session, the one a
 * query opens for itself closes before the action reads the results: MongoDB returns the first 101
 * documents with the query and they are decoded at once, but decoding a later batch fails with
 * "No datastore session found". An include uses the session of its request, and a request that
 * goes asynchronous closes its session, so that it is not left bound to the thread.
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

    void "an action included from a view has its save written"() {
        when: 'the view, rendered after the outer action has flushed, includes an action that saves without a flush'
        String body = http('/player/includeSave').assertStatus(200).body()

        then:
        body.contains('saved')
        Player.withNewSession { Player.countByName('From View') } == 1
    }

    void "an include uses the session of the request and leaves it open for the rest of the page"() {
        when:
        final page = pageLines('/requestSession/page?include=true')

        then: 'the include reads through the session of the page'
        page.included == page.before

        and: 'the page keeps its session after the include, and reads beyond the first batch'
        page.after == page.before
        page.read == '102'
    }

    void "an action that goes asynchronous with #how closes its session as the request goes asynchronous"() {
        given:
        RequestSessions.release = new CountDownLatch(1)
        RequestSessions.asyncSession = null

        when: 'the request goes asynchronous and waits'
        CompletableFuture<TestHttpResponse> asyncResponse = CompletableFuture.supplyAsync({ http(path) } as Supplier<TestHttpResponse>)

        then: 'the session the action ran with is closed once the request has gone asynchronous'
        new PollingConditions(timeout: 10).eventually {
            assert RequestSessions.asyncSession != null
            assert !RequestSessions.asyncSession.connected
        }

        and: 'what the action saved was flushed then, as when an action returns'
        Player.withNewSession { Player.findByName('Saved before going asynchronous') } != null

        when: 'another request runs while the first one waits'
        TestHttpResponse later = http('/requestSession/current')

        then: 'it gets an open session of its own'
        later.assertStatus(200)
        later.body() ==~ /\d+\/open/
        !later.body().startsWith("${System.identityHashCode(RequestSessions.asyncSession)}/")

        when: 'the first request is released'
        RequestSessions.release.countDown()

        then: 'it completes successfully'
        asyncResponse.get(30, TimeUnit.SECONDS).assertStatus(200)

        cleanup:
        RequestSessions.release.countDown()

        where:
        how                            | path
        'a promise'                    | '/requestSession/promise'
        'AsyncController.startAsync()' | '/asyncRequestSession/start'
    }

    private Map<String, String> pageLines(String path) {
        http(path).assertStatus(200).body().readLines()*.trim().findAll().collectEntries { String line ->
            final parts = line.split(':', 2)
            [(parts[0]): parts[1]]
        }
    }

    private TestHttpResponse postForm(String path, String form) {
        httpPost(path, form, 'application/x-www-form-urlencoded')
    }
}
