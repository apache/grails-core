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
 * Exercises the once-per-request authorization of the filter security interceptor in a configuration with no
 * rule for {@code /error}: the ERROR dispatch that renders an error page passes through the interceptor without
 * being authorized again, while a direct request to {@code /error} is still authorized against its own rule.
 */
@IgnoreIf({ System.getProperty('TESTCONFIG') != 'oncePerRequest' })
@Issue('https://github.com/apache/grails-core/issues/16739')
@Integration(applicationClass = functional.test.app.Application)
class OncePerRequestSpec extends AbstractHttpClientSecuritySpec {

    void 'the error page of a public action that answers 404 is served to a caller without credentials'() {
        when:
        def response = http([:], '/sendError/notFound', withoutRedirects())

        then:
        response.statusCode() == 404
        response.body().contains('Error 404 Page Not Found')
    }

    void 'the error page of a request denied for a missing role is served to the caller'() {
        when:
        def response = http([Authorization: basic('testuser', 'password')], '/secureClassAnnotated', withoutRedirects())

        then:
        response.statusCode() == 403
        response.body().contains('Error 403 Forbidden')
    }

    void 'a direct request to the error page is authorized against its own rule'() {
        when:
        def response = http([:], '/error', withoutRedirects())

        then:
        response.statusCode() == 302
        response.headerValue('Location').endsWith('/login/auth')
    }

    void 'a secured URL is authorized on its first dispatch'() {
        when:
        def response = http([:], '/secureAnnotated', withoutRedirects())

        then:
        response.statusCode() == 302
        response.headerValue('Location').endsWith('/login/auth')
    }
}
