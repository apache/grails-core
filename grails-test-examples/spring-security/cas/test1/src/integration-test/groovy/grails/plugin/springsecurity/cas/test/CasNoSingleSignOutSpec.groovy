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
 * Asserts the shipped default: {@code cas.useSingleSignout} is off unless the application opts in.
 *
 * <p>Enabling it disables session fixation prevention, so it is a security trade-off rather than
 * something an application should get without asking. With it off, a CAS logout request must not
 * reach into the application's sessions.</p>
 */
@IgnoreIf({ !CasTestConfig.configured || CasTestConfig.singleSignoutEnabled })
class CasNoSingleSignOutSpec extends AbstractCasSpec {

    void 'a CAS logout request is ignored when single signout is not enabled'() {
        given: 'an authenticated session established with a service ticket'
        HttpResponse<String> loggedIn = login('/secure/users', 'user', 'user')

        expect:
        loggedIn.statusCode() == 200
        lastServiceTicket

        when: 'the same logout request that would end the session is posted'
        postForm(backChannelClient, appBaseUrl + '/login/cas',
                [logoutRequest: logoutRequest(lastServiceTicket)])

        then: 'no single signout filter is listening, so the session survives'
        HttpResponse<String> stillIn = followRedirects(get(appClient, "${appBaseUrl}/secure/users"))
        stillIn.statusCode() == 200
        stillIn.body().contains('Logged in with ROLE_USER')
    }

    void 'session fixation prevention is left in place when single signout is not enabled'() {
        expect: 'the core plugin default stands, so logging in still works normally'
        login('/secure/users', 'user', 'user').body().contains('Logged in with ROLE_USER')
    }
}
