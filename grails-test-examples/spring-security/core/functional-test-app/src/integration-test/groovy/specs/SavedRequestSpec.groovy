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

import java.net.http.HttpClient

import org.apache.grails.testing.http.client.HttpClientSupport
import org.apache.grails.testing.http.client.TestHttpResponse
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification

import com.testapp.TestDataService
import grails.testing.mixin.integration.Integration

/**
 * A login redirects to the page whose request was denied, but not to a request the browser sent on its own. It runs in
 * a configuration that adds no other specification's data to the roles and users the browser specifications count.
 */
@IgnoreIf({ System.getProperty('TESTCONFIG') != 'oncePerRequest' })
@Integration(applicationClass = functional.test.app.Application)
class SavedRequestSpec extends Specification implements HttpClientSupport {

    private static final String USERNAME = 'saved_request_user'

    @Autowired
    TestDataService testDataService

    @Shared
    private boolean userAdded

    void setup() {
        if (!userAdded) {
            testDataService.addTestUser(USERNAME)
            userAdded = true
        }
    }

    void 'a login redirects to the page whose request was denied'() {
        when:
        def response = loginAfterRequestOf('/secureAnnotated')

        then:
        response.statusCode() == 302
        response.headerValue('Location').contains('/secureAnnotated')
    }

    void 'a login does not redirect to #path, which a browser requests on its own'() {
        when:
        def response = loginAfterRequestOf(path)

        then:
        response.statusCode() == 302
        response.headerValue('Location').endsWith('/')
        !response.headerValue('Location').contains(path)

        where:
        path << [
                '/apple-touch-icon.png',
                '/apple-touch-icon-152x152.png',
                '/manifest.json',
                '/manifest.webmanifest',
                '/browserconfig.xml',
                '/.well-known/appspecific/com.chrome.devtools.json'
        ]
    }

    private TestHttpResponse loginAfterRequestOf(String path) {
        def client = newHttpClientWith {
            followRedirects(HttpClient.Redirect.NEVER)
            cookieHandler(new CookieManager())
        }
        def denied = http([:], path, client)
        assert denied.statusCode() == 302
        assert denied.headerValue('Location').endsWith('/login/auth')
        httpPostForm([:], '/login/authenticate', [username: USERNAME, password: 'password'], client)
    }
}
