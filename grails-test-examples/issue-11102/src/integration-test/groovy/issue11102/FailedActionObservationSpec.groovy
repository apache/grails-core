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

package issue11102

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.server.observation.ServerRequestObservationContext
import spock.lang.Specification
import spock.lang.Tag
import spock.util.concurrent.PollingConditions

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

/**
 * An error page that renders hides the failure from the filter that observes the request: Spring MVC clears the
 * exception from the request once the error view has rendered, and a forward to an error controller replaces it, and
 * the route, with its own. Only a real dispatch does that, so this goes through one.
 */
@Integration
@Tag('http-client')
class FailedActionObservationSpec extends Specification implements HttpClientSupport {

    private static final AtomicBoolean LISTENING = new AtomicBoolean()
    private static final Map<String, List<String>> NOTIFIED = new ConcurrentHashMap<>()

    @Autowired
    MeterRegistry meterRegistry

    @Autowired
    ObservationRegistry observationRegistry

    void setup() {
        if (LISTENING.compareAndSet(false, true)) {
            observationRegistry.observationConfig().observationHandler(new ErrorListener())
        }
    }

    void 'a failed action answered by #handler is reported with its own exception and route'() {
        when:
        def response = http("/failing/$action")

        then: 'the error page answered, so the exception did not reach the filter'
        response.assertContains(500, page)

        and: 'its metrics say what failed and where, and the handlers that mark a span hear of it once'
        new PollingConditions(timeout: 5).eventually {
            assert errorTags("/failing/$action") == [exception]
            assert NOTIFIED["/failing/$action".toString()] == [exception]
        }

        where:
        handler               | action                 | page                               | exception
        'the error view'      | 'answeredByView'       | 'An error has occurred'            | 'IllegalStateException'
        'an error controller' | 'answeredByController' | 'Answered by the error controller' | 'UnsupportedOperationException'
    }

    private List<String> errorTags(String route) {
        meterRegistry.find('http.server.requests').tag('status', '500').tag('uri', route).timers()*.id*.getTag('error')
    }

    private static class ErrorListener implements ObservationHandler<Observation.Context> {

        @Override
        boolean supportsContext(Observation.Context context) {
            context instanceof ServerRequestObservationContext
        }

        @Override
        void onError(Observation.Context context) {
            def route = ((ServerRequestObservationContext) context).pathPattern
            NOTIFIED.computeIfAbsent(route) { new CopyOnWriteArrayList<>() } << context.error.class.simpleName
        }
    }
}
