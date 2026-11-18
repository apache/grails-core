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
package undertowapp

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

/**
 * An {@code Error} thrown by a servlet filter never passes through the {@code DispatcherServlet}, so nothing
 * wraps it. Undertow stores it in {@code jakarta.servlet.error.exception} and makes an ERROR dispatch to the
 * {@code "500"} mapping, where interceptors run. An error page that copies that attribute into its model
 * puts the bare {@code Error} into the {@code exception} request attribute that {@code afterView} reads.
 */
@Integration
class UndertowErrorPageInterceptorSpec extends Specification implements HttpClientSupport {

    PollingConditions conditions = new PollingConditions(timeout: 5)

    def setup() {
        ErrorPageInterceptor.OBSERVED.clear()
    }

    def 'an Error thrown by a servlet filter reaches afterView unwrapped through the container error page'() {
        when:
        def response = http('/staticInitFailure/application.css')

        then: 'Undertow hands the bare Error to the error page, which puts it in its model'
        response.assertContains(500, 'Handled java.lang.ExceptionInInitializerError')

        and: 'afterView still runs and sees the Error itself'
        def observed = afterViewThrowable()
        observed instanceof ExceptionInInitializerError
        observed.cause instanceof StaticInitFailure
    }

    def 'an Error thrown by an action reaches afterView wrapped in an Exception'() {
        when:
        def response = http('/errorPage/actionError')

        then:
        response.assertStatus(500)

        and: 'the Error is reached through the cause chain'
        def observed = afterViewThrowable()
        observed instanceof Exception
        causes(observed).any { it instanceof ExceptionInInitializerError }
    }

    private Throwable afterViewThrowable() {
        conditions.eventually {
            assert ErrorPageInterceptor.OBSERVED.size() == 1
        }
        ErrorPageInterceptor.OBSERVED.first()
    }

    private static List<Throwable> causes(Throwable throwable) {
        List<Throwable> chain = []
        for (Throwable t = throwable; t != null && !chain.contains(t); t = t.cause) {
            chain << t
        }
        chain
    }
}
