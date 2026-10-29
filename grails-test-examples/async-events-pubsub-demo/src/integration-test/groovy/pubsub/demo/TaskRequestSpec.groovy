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
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

@Integration
@Tag('http-client')
class TaskRequestSpec extends Specification implements HttpClientSupport {

    @Issue('https://github.com/apache/grails-core/issues/15851')
    @Unroll
    void 'tasks started before and during a forward of #request read their requests as they were once the request had been processed'() {
        when: 'an action starts a task, forwards to one that starts another, and sets an attribute once the forward returns'
        def response = http(headers, "/taskRequest/forwarding?run=$run")

        then:
        response.assertEquals(200, 'forwarded')

        and: 'once the request has left the application, each task reads its own request, with what was set last'
        new PollingConditions(timeout: 10).eventually {
            Map seen = http('/taskRequest/seen').json()
            assert seen[run + '-request'] == [late: 'set after the forward', forward: false,
                                              forwardURI: '/taskRequest/forwarding', uri: '/taskRequest/forwarding']
            assert seen[run + '-forward'] == [late: 'set after the forward', forward: true,
                                              forwardURI: '/taskRequest/forwarding', uri: '/taskRequest/forwarded']
        }

        where:
        request                          | run         | headers
        'the request the container gave' | 'unwrapped' | [:]
        'a request a filter wrapped'     | 'wrapped'   | [(RequestCompletedFilter.WRAPPED): 'true']
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    @Unroll
    void 'a task started during a forward of #request reads what the request that forwarded set once the forward had returned'() {
        when:
        def response = http(headers, "/taskRequest/forwardingOnly?run=$run")

        then:
        response.assertEquals(200, 'forwarded')

        and:
        new PollingConditions(timeout: 10).eventually {
            Map seen = http('/taskRequest/seen').json()
            assert seen[run + '-forwardOnly'] == [late: 'set after the forward', forward: true,
                                                  forwardURI: '/taskRequest/forwardingOnly', uri: '/taskRequest/forwardedOnly']
        }

        where:
        request                          | run         | headers
        'the request the container gave' | 'unwrapped' | [:]
        'a request a filter wrapped'     | 'wrapped'   | [(RequestCompletedFilter.WRAPPED): 'true']
    }
}
