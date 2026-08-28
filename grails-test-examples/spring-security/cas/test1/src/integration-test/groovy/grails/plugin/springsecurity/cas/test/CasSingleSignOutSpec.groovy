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
 * Covers single sign-out, which the app opts into via {@code cas.useSingleSignout}. The plugin then
 * registers an {@code org.apereo.cas.client.session.SingleSignOutFilter} ahead of every other
 * filter. {@code CasNoSingleSignOutSpec} covers the default, where it is off.
 *
 * <p>The logout request is posted here rather than triggered from the CAS server. What belongs to
 * the plugin is <em>handling</em> the request - mapping the service ticket to the HTTP session and
 * invalidating it - and posting the same message CAS would send exercises exactly that, without
 * depending on how the CAS server is configured to emit it.</p>
 */
@IgnoreIf({ !CasTestConfig.configured || !CasTestConfig.singleSignoutEnabled })
class CasSingleSignOutSpec extends AbstractCasSpec {

    void 'a CAS logout request invalidates the session that the service ticket authenticated'() {
        given: 'an authenticated session established with a service ticket'
        HttpResponse<String> loggedIn = login('/secure/users', 'user', 'user')

        expect:
        loggedIn.statusCode() == 200
        loggedIn.body().contains('Logged in with ROLE_USER')
        lastServiceTicket

        when: 'CAS posts a back-channel logout request naming that ticket'
        HttpResponse<String> logoutResponse = postForm(backChannelClient,
                appBaseUrl + '/login/cas', [logoutRequest: logoutRequest(lastServiceTicket)])

        then: 'the single sign-out filter consumes it'
        logoutResponse.statusCode() == 200

        and: 'the session no longer authenticates, so the next request goes back to CAS'
        HttpResponse<String> afterLogout = get(appClient, "${appBaseUrl}/secure/users")
        afterLogout.statusCode() == 302
        location(afterLogout).startsWith("${casBaseUrl}/login")
    }

    void 'a logout request for an unrelated ticket leaves the session alone'() {
        given:
        login('/secure/users', 'user', 'user')

        when:
        postForm(backChannelClient, appBaseUrl + '/login/cas',
                [logoutRequest: logoutRequest('ST-does-not-exist')])

        then: 'the established session is untouched'
        HttpResponse<String> stillIn = followRedirects(get(appClient, "${appBaseUrl}/secure/users"))
        stillIn.statusCode() == 200
        stillIn.body().contains('Logged in with ROLE_USER')
    }
}
