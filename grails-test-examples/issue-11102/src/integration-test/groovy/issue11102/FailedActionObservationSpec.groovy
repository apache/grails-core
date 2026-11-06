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

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Autowired
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

    @Autowired
    MeterRegistry meterRegistry

    void 'a failed action answered by #handler is tagged with its own exception and route'() {
        when:
        def response = http("/failing/$action")

        then: 'the error page answered, so the exception did not reach the filter'
        response.assertContains(500, page)

        and:
        new PollingConditions(timeout: 5).eventually {
            assert errorTags("/failing/$action") == [exception]
        }

        where:
        handler               | action                 | page                               | exception
        'the error view'      | 'answeredByView'       | 'An error has occurred'            | 'IllegalStateException'
        'an error controller' | 'answeredByController' | 'Answered by the error controller' | 'UnsupportedOperationException'
    }

    private List<String> errorTags(String route) {
        meterRegistry.find('http.server.requests').tag('status', '500').tag('uri', route).timers()*.id*.getTag('error')
    }
}
