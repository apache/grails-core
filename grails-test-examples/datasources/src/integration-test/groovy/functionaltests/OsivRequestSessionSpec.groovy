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
import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport
import org.apache.grails.testing.http.client.TestHttpResponse
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

/**
 * The session the open session in view interceptor opens for the secondary datasource is used by an include within
 * the request, and closed when the request goes asynchronous, so that it is not left bound to the thread.
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

    void 'a request that goes asynchronous closes its session of the secondary datasource'() {
        given:
        RequestSessions.release = new CountDownLatch(1)
        RequestSessions.asyncSecondarySession = null

        when: 'the request goes asynchronous and waits'
        CompletableFuture<TestHttpResponse> asyncResponse = CompletableFuture.supplyAsync({ http('/requestSession/promise') } as Supplier<TestHttpResponse>)

        then: 'the session the action ran with is closed once the request has gone asynchronous'
        new PollingConditions(timeout: 10).eventually {
            assert RequestSessions.asyncSecondarySession != null
            assert !RequestSessions.asyncSecondarySession.open
        }

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

        cleanup:
        RequestSessions.release.countDown()
    }

    private Map<String, String> pageLines(String path) {
        http(path).assertStatus(200).body().readLines()*.trim().findAll().collectEntries { String line ->
            final parts = line.split(':', 2)
            [(parts[0]): parts[1]]
        }
    }
}
