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

import grails.plugin.springsecurity.annotation.Secured
import org.apereo.cas.client.authentication.AttributePrincipal
import org.springframework.security.cas.authentication.CasAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder

class SecureController {

    @Secured('ROLE_ADMIN')
    def admins() {
        render 'Logged in with ROLE_ADMIN'
    }

    @Secured('ROLE_USER')
    def users() {
        render 'Logged in with ROLE_USER'
    }

    /**
     * Asks CAS for a proxy ticket on behalf of the logged-in user. This only succeeds when the
     * proxy receptor is configured, because CAS delivers the proxy-granting ticket by calling back
     * to the receptor URL while the service ticket is being validated.
     */
    @Secured('ROLE_USER')
    def proxyStatus() {
        Authentication authentication = SecurityContextHolder.context.authentication
        if (!(authentication instanceof CasAuthenticationToken)) {
            render 'NOT_A_CAS_AUTHENTICATION'
            return
        }

        AttributePrincipal principal = ((CasAuthenticationToken) authentication).assertion.principal
        String targetService = params.targetService ?: 'http://localhost/proxied-service'
        String proxyTicket = principal.getProxyTicketFor(targetService)
        render proxyTicket ? "PROXY_TICKET:${proxyTicket}" : 'NO_PROXY_TICKET'
    }
}
