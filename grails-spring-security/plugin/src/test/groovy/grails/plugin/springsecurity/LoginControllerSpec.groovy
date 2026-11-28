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

import spock.lang.Issue
import spock.lang.Specification

import org.springframework.security.authentication.AuthenticationTrustResolverImpl
import org.springframework.security.authentication.RememberMeAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextHolder

import grails.testing.web.controllers.ControllerUnitTest

class LoginControllerSpec extends Specification implements ControllerUnitTest<LoginController> {

    void setup() {
        ConfigObject conf = new ConfigObject()
        conf.successHandler.defaultTargetUrl = '/'
        conf.apf.filterProcessesUrl = '/login/authenticate'
        conf.rememberMe.parameter = 'remember-me'
        SpringSecurityUtils.securityConfig = conf
        controller.authenticationTrustResolver = new AuthenticationTrustResolverImpl()
    }

    void cleanup() {
        SecurityContextHolder.clearContext()
        SpringSecurityUtils.resetSecurityConfig()
    }

    @Issue('https://github.com/apache/grails-core/issues/15877')
    void 'auth sends a user who only has a remember-me cookie to the full login page'() {
        given: 'a remembered user, sent to the login form because a page needs a full login'
        controller.springSecurityService = Stub(SpringSecurityService) { isLoggedIn() >> true }
        SecurityContextHolder.context.authentication =
                new RememberMeAuthenticationToken('key', 'remembered', AuthorityUtils.createAuthorityList('ROLE_USER'))

        when:
        controller.auth()

        then: 'the user has to log in again, instead of being sent on to the target page, which may need that login too'
        response.redirectedUrl == '/login/full'
    }

    void 'auth sends a fully authenticated user on to the default target'() {
        given:
        controller.springSecurityService = Stub(SpringSecurityService) { isLoggedIn() >> true }
        SecurityContextHolder.context.authentication =
                new UsernamePasswordAuthenticationToken('user', 'password', AuthorityUtils.createAuthorityList('ROLE_USER'))

        when:
        controller.auth()

        then:
        response.redirectedUrl == '/'
    }

    void 'auth shows the login form to a user who is not logged in'() {
        given:
        controller.springSecurityService = Stub(SpringSecurityService) { isLoggedIn() >> false }

        when:
        controller.auth()

        then:
        response.redirectedUrl == null
        view == '/login/auth'
        model.postUrl.endsWith('/login/authenticate')
    }
}
