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

package grails.plugin.springsecurity


import org.springframework.security.authentication.AccountExpiredException
import org.springframework.security.authentication.CredentialsExpiredException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.authentication.LockedException
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsChecker
import spock.lang.Unroll

class PrePostAuthenticationCheckSpec extends AbstractIntegrationSpec {

    UserDetailsChecker preAuthenticationChecks
    UserDetailsChecker postAuthenticationChecks

    @Unroll
    def 'pre-authentication exception uses i18n message - #test'() {
        given:
        def userDetails = Mock(UserDetails) {
            isAccountNonLocked() >> { test != 'locked' }
            isEnabled() >> { test != 'disabled' }
            isAccountNonExpired() >> { test != 'expired'}
        }

        when:
        preAuthenticationChecks.check(userDetails)

        then:
        Exception exception = thrown(type)
        exception.message == expectMessage

        where:
        test       | type                    | expectMessage
        'locked'   | LockedException         | 'Custom user account is locked.'
        'disabled' | DisabledException       | 'Custom user account is disabled.'
        'expired'  | AccountExpiredException | 'Custom user account is expired.'
    }

    def 'post-authentication exception uses i18n message - credentials expired'() {
        given:
        def userDetails = Mock(UserDetails) {
            isCredentialsNonExpired() >> false
        }

        when:
        postAuthenticationChecks.check(userDetails)

        then:
        Exception exception = thrown(CredentialsExpiredException)
        exception.message == 'Custom user credentials are expired.'
    }
}
