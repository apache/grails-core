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
package functionaltests

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

import datasources.RequestSessions
import ds1.Shelf
import ds1.Volume
import ds2.Book as SecondaryBook
import ds2.Chapter
import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport
import org.apache.grails.testing.http.client.TestHttpResponse
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

/**
 * The session the open session in view interceptor opens for the secondary datasource is used by an include within
 * the request. When the request goes asynchronous, the sessions are taken off the thread, so that the thread's next
 * request does not use them, and closed when the asynchronous request completes, so that the work it continues with
 * can still use them.
 */
@Integration
class OsivRequestSessionSpec extends Specification implements HttpClientSupport {

    void 'an include uses the session of the secondary datasource of the request and leaves it open'() {
        when:
        final page = pageLines('/requestSession/page?include=true')

        then:
        page.before.endsWith('/open')
        page.included == page.before
        page.after == page.before
    }

    void 'a request that goes asynchronous closes its session of the secondary datasource once it completes'() {
        given:
        RequestSessions.release = new CountDownLatch(1)
        RequestSessions.asyncSecondarySession = null

        when: 'the request goes asynchronous and waits'
        CompletableFuture<TestHttpResponse> asyncResponse = CompletableFuture.supplyAsync({ http('/requestSession/promise') } as Supplier<TestHttpResponse>)

        then: 'the session the action ran with stays open for the asynchronous work'
        new PollingConditions(timeout: 10).eventually {
            assert RequestSessions.asyncSecondarySession != null
        }
        RequestSessions.asyncSecondarySession.open

        when: 'another request runs while the first one waits'
        TestHttpResponse later = http('/requestSession/current')

        then: 'it gets an open session of its own'
        later.assertStatus(200)
        later.body() ==~ /\d+\/open/
        !later.body().startsWith("${System.identityHashCode(RequestSessions.asyncSecondarySession)}/")

        when: 'the first request is released'
        RequestSessions.release.countDown()

        then: 'it completes successfully'
        asyncResponse.get(30, TimeUnit.SECONDS).assertStatus(200)

        and: 'the session the action ran with is closed once the asynchronous request has completed'
        new PollingConditions(timeout: 10).eventually {
            assert !RequestSessions.asyncSecondarySession.open
        }

        cleanup:
        RequestSessions.release.countDown()
    }

    void 'a task a request returns lazily loads associations of the entities its action loaded'() {
        given:
        Shelf.withTransaction {
            new Shelf(name: 'asynchronous')
                    .addToVolumes(new Volume(title: 'first'))
                    .addToVolumes(new Volume(title: 'second'))
                    .save(flush: true, failOnError: true)
        }
        SecondaryBook.withTransaction {
            new SecondaryBook(title: 'Asynchronous chapters')
                    .addToChapters(new Chapter(title: 'one'))
                    .addToChapters(new Chapter(title: 'two'))
                    .save(flush: true, failOnError: true)
        }

        expect: 'the task loads them after the request has gone asynchronous'
        http('/requestSession/lazyVolumes?name=asynchronous').assertEquals(200, 'first,second')
        http('/requestSession/lazyShelf?title=first').assertEquals(200, 'asynchronous')
        http('/requestSession/lazyChapters?title=Asynchronous%20chapters').assertEquals(200, 'one,two')

        cleanup:
        Shelf.withTransaction {
            Shelf.findByName('asynchronous')?.delete(flush: true)
        }
        SecondaryBook.withTransaction {
            SecondaryBook.findByTitle('Asynchronous chapters')?.delete(flush: true)
        }
    }

    void 'a request that goes asynchronous and times out keeps its sessions open until the timeout and has them closed after it'() {
        given:
        RequestSessions.asyncDefaultSession = null
        RequestSessions.asyncSecondarySession = null

        when: 'the request goes asynchronous, never completes, and waits past the handoff'
        final asyncResponse = CompletableFuture.supplyAsync({ http('/asyncRequestSession/timesOut') } as Supplier<TestHttpResponse>)
        new PollingConditions(timeout: 10).eventually {
            assert RequestSessions.asyncDefaultSession != null
            assert RequestSessions.asyncSecondarySession != null
        }
        sleep(500)

        then: 'its sessions are still open'
        RequestSessions.asyncDefaultSession.open
        RequestSessions.asyncSecondarySession.open

        when: 'the container times it out'
        asyncResponse.get(30, TimeUnit.SECONDS)

        then: 'both sessions are closed'
        new PollingConditions(timeout: 10).eventually {
            assert !RequestSessions.asyncDefaultSession.open
            assert !RequestSessions.asyncSecondarySession.open
        }
    }

    private Map<String, String> pageLines(String path) {
        http(path).assertStatus(200).body().readLines()*.trim().findAll().collectEntries { String line ->
            final parts = line.split(':', 2)
            [(parts[0]): parts[1]]
        }
    }
}
