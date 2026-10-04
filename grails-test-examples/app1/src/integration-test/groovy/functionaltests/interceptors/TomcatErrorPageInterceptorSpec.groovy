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
package functionaltests.interceptors

import jakarta.servlet.ServletException

import spock.lang.Specification
import spock.lang.Tag
import spock.util.concurrent.PollingConditions

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

/**
 * What {@code afterView} sees when an error page or an action puts something under the {@code exception}
 * model entry, which the view exposes as the {@code exception} request attribute. Unlike Undertow, Tomcat
 * wraps an {@code Error} thrown by a servlet filter in a {@code ServletException} before it stores it in
 * {@code jakarta.servlet.error.exception}, so the error page receives an {@code Exception}.
 */
@Integration
@Tag('http-client')
class TomcatErrorPageInterceptorSpec extends Specification implements HttpClientSupport {

    PollingConditions conditions = new PollingConditions(timeout: 5)

    def setup() {
        ErrorPageInterceptor.OBSERVED.clear()
    }

    def 'Tomcat wraps an Error thrown by a servlet filter before handing it to the container error page'() {
        when:
        def response = http('/staticInitFailure/application.css')

        then: 'the error page receives the ServletException that Tomcat wraps the Error in'
        response.assertContains(500, 'Handled jakarta.servlet.ServletException')

        and: 'afterView sees that ServletException, with the Error as its cause'
        def observed = afterViewThrowable()
        observed instanceof ServletException
        observed.cause instanceof ExceptionInInitializerError
    }

    def 'afterView runs when the model holds an exception entry that is not a Throwable'() {
        when:
        def response = http('/errorPage/renderMessage')

        then:
        response.assertContains(200, 'Handled java.lang.String')

        and: 'the String is not exposed as the throwable'
        afterViewThrowable() == null
    }

    private Throwable afterViewThrowable() {
        conditions.eventually {
            assert ErrorPageInterceptor.OBSERVED.size() == 1
        }
        ErrorPageInterceptor.OBSERVED.first()
    }
}
