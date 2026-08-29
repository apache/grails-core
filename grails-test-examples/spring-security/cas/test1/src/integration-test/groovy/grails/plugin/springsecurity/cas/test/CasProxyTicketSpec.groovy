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

package grails.plugin.springsecurity.cas.test

import spock.lang.IgnoreIf

import java.net.http.HttpResponse

/**
 * Covers the proxy-ticket path: with {@code proxyCallbackUrl} and {@code proxyReceptorUrl} both
 * configured, CAS calls back into the application while the service ticket is being validated, and
 * the proxy-granting ticket it delivers is stored and retrievable.
 */
@IgnoreIf({ !CasTestConfig.proxyEnabled })
class CasProxyTicketSpec extends AbstractCasSpec {

    void 'a proxy ticket can be obtained for the logged-in user'() {
        given:
        login('/secure/users', 'user', 'user')

        when:
        HttpResponse<String> response = followRedirects(get(appClient, "${appBaseUrl}/secure/proxyStatus"))

        then:
        response.statusCode() == 200
        response.body().startsWith('PROXY_TICKET:PT-')
    }

    void 'the receptor path is consumed by the CAS filter rather than the application'() {
        when: 'the receptor is requested without the parameters CAS would send'
        HttpResponse<String> response = get(appClient, appBaseUrl + CasTestConfig.PROXY_RECEPTOR_URL)

        then: 'the CAS filter handles it instead of letting it fall through to a 404'
        response.statusCode() == 200
        !response.body()
    }

    void 'a normal CAS login still works with proxy settings configured'() {
        when:
        HttpResponse<String> response = login('/secure/users', 'user', 'user')

        then:
        response.statusCode() == 200
        response.body().contains('Logged in with ROLE_USER')
    }
}
