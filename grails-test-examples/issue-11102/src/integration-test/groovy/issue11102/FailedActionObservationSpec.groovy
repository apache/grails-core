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
    private static final String REQUEST_HEADER = 'X-Failing-Request'
    private static final Map<String, List<String>> NOTIFIED = new ConcurrentHashMap<>()
    private static final Map<String, String> STOPPED_ON_ROUTE = new ConcurrentHashMap<>()

    @Autowired
    MeterRegistry meterRegistry

    @Autowired
    ObservationRegistry observationRegistry

    void setup() {
        if (LISTENING.compareAndSet(false, true)) {
            observationRegistry.observationConfig().observationHandler(new ErrorListener())
        }
    }

    void 'a failed action answered by #handler is reported once with its own exception and route'() {
        when:
        def response = http([(REQUEST_HEADER): path], path)

        then: 'the error page answered, so the exception did not reach the filter'
        response.assertContains(500, page)

        and: 'the observation stopped on the route that failed, and its metrics say what failed'
        new PollingConditions(timeout: 5).eventually {
            assert STOPPED_ON_ROUTE[path] == route
            assert errorTags(route) == [exception]
        }

        and: 'the handlers that mark a span, all told of the failure before the observation stopped, heard of it once'
        NOTIFIED[path] == [exception]

        where:
        handler                                            | path                                              | route                                             | page                               || exception
        'the error view'                                   | '/failing/answeredByView'                         | '/failing/answeredByView'                         | 'An error has occurred'            || 'IllegalStateException'
        'an error controller'                              | '/failing/answeredByController'                   | '/failing/answeredByController'                   | 'Answered by the error controller' || 'UnsupportedOperationException'
        'the error view, asynchronously'                   | '/failing/answeredByViewAsynchronously'           | '/failing/answeredByViewAsynchronously'           | 'An error has occurred'            || 'IllegalStateException'
        'an error controller, asynchronously'              | '/failing/answeredByControllerAsynchronously'     | '/failing/answeredByControllerAsynchronously'     | 'Answered by the error controller' || 'UnsupportedOperationException'
        'the error view, after starting async'             | '/failing/answeredByViewAfterStartingAsync'       | '/failing/answeredByViewAfterStartingAsync'       | 'An error has occurred'            || 'IllegalStateException'
        'an error controller, after starting async'        | '/failing/answeredByControllerAfterStartingAsync' | '/failing/answeredByControllerAfterStartingAsync' | 'Answered by the error controller' || 'UnsupportedOperationException'
        'the error view, after a forward'                  | '/failing/forwardedToView'                        | '/failing/answeredByView'                         | 'An error has occurred'            || 'IllegalStateException'
        'an error controller, after a forward'             | '/failing/forwardedToController'                  | '/failing/answeredByController'                   | 'Answered by the error controller' || 'UnsupportedOperationException'
        'an error controller, after an interceptor failed' | '/failing/failsInInterceptor'                     | '/failing/failsInInterceptor'                     | 'Answered by the error controller' || 'UnsupportedOperationException'
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
            NOTIFIED.computeIfAbsent(requestOf(context)) { new CopyOnWriteArrayList<>() } << context.error.class.simpleName
        }

        @Override
        void onStop(Observation.Context context) {
            STOPPED_ON_ROUTE[requestOf(context)] = ((ServerRequestObservationContext) context).pathPattern ?: 'none'
        }

        private static String requestOf(Observation.Context context) {
            ((ServerRequestObservationContext) context).carrier.getHeader(REQUEST_HEADER) ?: 'none'
        }
    }
}
