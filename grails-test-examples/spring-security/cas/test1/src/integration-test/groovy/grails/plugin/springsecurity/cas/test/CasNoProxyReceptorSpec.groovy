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

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.security.cas.web.CasAuthenticationFilter
import spock.lang.IgnoreIf

import java.net.http.HttpResponse

/**
 * Regression coverage for leaving {@code cas.proxyReceptorUrl} unset, which is the default.
 *
 * <p>The plugin used to assign it unconditionally. Under Spring Security 7.1 that fails outright
 * because the request matcher rejects a null pattern, so this spec cannot even reach its first
 * feature method without the guard - the application context does not start. Before that it
 * produced a matcher for the literal path {@code /**null}, which quietly made an unconfigured app
 * serve a live proxy receptor.</p>
 */
@IgnoreIf({ !CasTestConfig.configured || CasTestConfig.proxyEnabled })
class CasNoProxyReceptorSpec extends AbstractCasSpec {

    @Autowired
    ApplicationContext applicationContext

    void 'the application starts and wires the CAS filter with no proxy receptor configured'() {
        expect: 'reaching this point at all means the context started'
        applicationContext.getBean('casAuthenticationFilter', CasAuthenticationFilter)
    }

    void 'the receptor path is left to the application when no proxy receptor is configured'() {
        given: 'an authenticated session, so the response cannot simply be a login redirect'
        login('/secure/users', 'user', 'user')

        when:
        HttpResponse<String> response = followRedirects(
                get(appClient, appBaseUrl + CasTestConfig.PROXY_RECEPTOR_URL))

        then: 'the request reaches the application instead of being swallowed by the CAS filter'
        response.statusCode() == 404
    }

    void 'a normal CAS login still works without proxy settings'() {
        when:
        HttpResponse<String> response = login('/secure/users', 'user', 'user')

        then:
        response.statusCode() == 200
        response.body().contains('Logged in with ROLE_USER')
    }

    void 'no proxy ticket can be obtained when the receptor is not configured'() {
        given:
        login('/secure/users', 'user', 'user')

        when:
        HttpResponse<String> response = followRedirects(get(appClient, "${appBaseUrl}/secure/proxyStatus"))

        then:
        response.statusCode() == 200
        response.body().trim() == 'NO_PROXY_TICKET'
    }
}
