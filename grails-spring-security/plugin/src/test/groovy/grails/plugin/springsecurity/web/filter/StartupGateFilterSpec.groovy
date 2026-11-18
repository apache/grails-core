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
package grails.plugin.springsecurity.web.filter

import java.time.Duration

import org.springframework.boot.SpringApplication
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.support.StaticApplicationContext
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import spock.lang.Specification

class StartupGateFilterSpec extends Specification {

    private final StartupGateFilter filter = new StartupGateFilter()
    private final MockHttpServletRequest request = new MockHttpServletRequest('GET', '/secured')
    private final MockHttpServletResponse response = new MockHttpServletResponse()
    private final MockFilterChain chain = new MockFilterChain()

    void 'rejects requests with 503 until opened'() {
        when:
        filter.doFilter(request, response, chain)

        then:
        !filter.open
        response.status == 503
        chain.request == null
    }

    void 'sets the status instead of sending an error'() {
        when:
        filter.doFilter(request, response, chain)

        then:
        response.errorMessage == null
        response.forwardedUrl == null
    }

    void 'stays closed until the application has started'() {
        when:
        filter.markInitialized()
        filter.doFilter(request, response, chain)

        then:
        !filter.open
        response.status == 503
        chain.request == null
    }

    void 'stays closed until the plugin has initialized'() {
        when:
        filter.onApplicationEvent(applicationStarted())
        filter.doFilter(request, response, chain)

        then:
        !filter.open
        response.status == 503
        chain.request == null
    }

    void 'lets requests through once the plugin has initialized and the application has started'() {
        when:
        filter.markInitialized()
        filter.onApplicationEvent(applicationStarted())
        filter.doFilter(request, response, chain)

        then:
        filter.open
        response.status == 200
        chain.request.is(request)
        chain.response.is(response)
    }

    void 'opens when the plugin initializes after the application has started'() {
        when:
        filter.onApplicationEvent(applicationStarted())
        filter.markInitialized()
        filter.doFilter(request, response, chain)

        then:
        filter.open
        chain.request.is(request)
    }

    private static ApplicationStartedEvent applicationStarted() {
        new ApplicationStartedEvent(new SpringApplication(), new String[0], new StaticApplicationContext(), Duration.ZERO)
    }
}
