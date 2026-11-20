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
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Shared
import spock.lang.Specification

import com.testapp.TestDataService

/**
 * Base class for the specs that drive a security configuration through the JDK HTTP client rather than a browser.
 * It seeds the test users once per specification, and offers Basic Auth credentials and a client that does not
 * follow redirects, so that a redirect to the login page can be asserted.
 */
abstract class AbstractHttpClientSecuritySpec extends Specification implements HttpClientSupport {

    @Autowired
    TestDataService testDataService

    @Shared
    private boolean usersAdded

    @Shared
    private HttpClient clientWithoutRedirects

    void setup() {
        if (!usersAdded) {
            testDataService.addTestUsers()
            usersAdded = true
        }
    }

    /**
     * @return a client that does not follow redirects, shared by the features of the specification
     */
    protected HttpClient withoutRedirects() {
        if (!clientWithoutRedirects) {
            clientWithoutRedirects = newHttpClientWith { followRedirects(HttpClient.Redirect.NEVER) }
        }
        clientWithoutRedirects
    }

    protected static String basic(String username, String password) {
        'Basic ' + "$username:$password".bytes.encodeBase64()
    }
}
