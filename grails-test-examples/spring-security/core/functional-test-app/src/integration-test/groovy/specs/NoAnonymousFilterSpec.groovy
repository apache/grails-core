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

package specs

import spock.lang.IgnoreIf
import spock.lang.Issue

import grails.testing.mixin.integration.Integration

/**
 * Exercises filter chains that drop {@code anonymousAuthenticationFilter}: a request without credentials then
 * reaches the filter security interceptor with no {@code Authentication} in the security context, and the chain's
 * exception translation filter must still answer it through the configured entry point.
 */
@IgnoreIf({ System.getProperty('TESTCONFIG') != 'noAnonymous' })
@Issue('https://github.com/apache/grails-core/issues/16632')
@Integration(applicationClass = functional.test.app.Application)
class NoAnonymousFilterSpec extends AbstractHttpClientSecuritySpec {

    private static final String BASIC_CHALLENGE = 'Basic realm="Grails Spring Security Basic Test Realm"'

    void 'a request without credentials to the Basic Auth chain is challenged with 401'() {
        when:
        def response = http('/secureClassAnnotated')

        then:
        response.statusCode() == 401
        response.headerValue('WWW-Authenticate').startsWith(BASIC_CHALLENGE)
    }

    void 'a request without credentials to the form login chain is redirected to the login page'() {
        when:
        def response = http([:], '/secureAnnotated', withoutRedirects())

        then:
        response.statusCode() == 302
        response.headerValue('Location').endsWith('/login/auth')
    }

    void 'Basic Auth credentials with the required role are served'() {
        when:
        def response = http([Authorization: basic('admin', 'password')], '/secureClassAnnotated')

        then:
        response.statusCode() == 200
        response.body() == 'index: you have ROLE_ADMIN'
    }

    void 'Basic Auth credentials without the required role are refused with 403'() {
        when:
        def response = http([Authorization: basic('testuser', 'password')], '/secureClassAnnotated')

        then:
        response.statusCode() == 403
    }

    void 'a chain that keeps the anonymous filter still serves public URLs'() {
        when:
        def response = http('/')

        then:
        response.statusCode() == 200
    }
}
