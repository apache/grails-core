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
 * Covers the CAS handshake the plugin exists to perform: redirect to CAS, service ticket back,
 * ticket validated against the CAS server, and the resulting authentication carrying the roles
 * looked up in GORM.
 */
@IgnoreIf({ !CasTestConfig.configured })
class CasLoginSpec extends AbstractCasSpec {

    void 'an unauthenticated request is redirected to the CAS login page for this service'() {
        when:
        HttpResponse<String> response = get(appClient, "${appBaseUrl}/secure/users")

        then:
        response.statusCode() == 302

        and: 'the redirect targets the CAS server the container is running'
        location(response).startsWith("${casBaseUrl}/login")

        and: 'it asks CAS to send the ticket back to this application'
        location(response).contains('service=')
        location(response).contains(URLEncoder.encode(CasTestConfig.serviceUrl(serverPort), 'UTF-8'))
    }

    void 'a user authenticated at CAS reaches a ROLE_USER action'() {
        when:
        HttpResponse<String> response = login('/secure/users', 'user', 'user')

        then:
        response.statusCode() == 200
        response.body().contains('Logged in with ROLE_USER')
    }

    void 'an admin authenticated at CAS reaches a ROLE_ADMIN action'() {
        when:
        HttpResponse<String> response = login('/secure/admins', 'admin', 'admin')

        then:
        response.statusCode() == 200
        response.body().contains('Logged in with ROLE_ADMIN')
    }

    void 'a user without the role is denied a ROLE_ADMIN action'() {
        given:
        login('/secure/users', 'user', 'user')

        when:
        HttpResponse<String> response = followRedirects(get(appClient, "${appBaseUrl}/secure/admins"))

        then: 'access is refused rather than granted'
        response.statusCode() == 403 || !response.body().contains('Logged in with ROLE_ADMIN')
    }

    void 'bad credentials do not authenticate'() {
        when:
        HttpResponse<String> challenge = get(appClient, "${appBaseUrl}/secure/users")
        String loginUrl = location(challenge)
        HttpResponse<String> form = get(casClient, loginUrl)
        String execution = extractExecution(form.body())
        assert execution, 'CAS login form did not contain an execution token'
        HttpResponse<String> submitted = postForm(casClient, loginUrl,
                [username: 'user', password: 'wrong-password', execution: execution, _eventId: 'submit'])

        then: 'CAS re-renders the login form instead of issuing a ticket'
        submitted.statusCode() == 401 || submitted.statusCode() == 200
        !submitted.headers().firstValue('Location').isPresent()
    }
}
