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
package pubsub.demo

import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Tag
import spock.util.concurrent.PollingConditions

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

@Integration
@Tag('http-client')
class TaskRequestSpec extends Specification implements HttpClientSupport {

    private static final String COMPLETED = 'The request has completed, so a task that outlives it can no longer use its'

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a task that outlives its request runs outside a request once the container is done with it'() {
        when:
        def response = http('/taskRequest/outlive?run=outlive')

        then:
        response.assertEquals(200, 'started')

        and: 'what the task took from the request fails clearly, and binding a domain class works as outside a request'
        new PollingConditions(timeout: 30).eventually {
            Map seen = http('/taskRequest/seen').json()
            assert seen.outlive == [live         : 'books', detached: true,
                                    request      : "$COMPLETED request".toString(),
                                    response     : "$COMPLETED response".toString(),
                                    title        : 'Grails', bindingErrors: 0]
        }
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a task the request waits for reads the live request'() {
        when:
        def response = http('/taskRequest/live?run=live')

        then:
        response.assertEquals(200, 'read')

        and:
        new PollingConditions(timeout: 10).eventually {
            Map seen = http('/taskRequest/seen').json()
            assert seen.live == [model: 'books', late: 'set after the task started', uri: '/taskRequest/live']
        }
    }
}
