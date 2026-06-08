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
package simple.spec

import spec.SecurityUISpec

import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserCache

import grails.testing.mixin.integration.Integration

@Integration
class DefaultSecurityInfoSpec extends SecurityUISpec {

	UserCache userCache

	void testConfig() {
		when:
		go('securityInfo/config')

		then:
		with(pageSource) {
			contains('adh.ajaxErrorPage')
			contains('/login/ajaxDenied')
			contains('Showing 1 to 10 of ')
		}
	}

	void testMappings() {
		when:
		go('securityInfo/mappings')

		then:
		assertContentContainsOne(
				'ROLE_RUN_AS, IS_AUTHENTICATED_FULLY',
				'IS_AUTHENTICATED_FULLY, ROLE_RUN_AS'
		)
		with(pageSource) {
			contains('/j_spring_security_switch_user')
			contains('/secure/**')
			contains('ROLE_ADMIN')
		}
	}

	void testCurrentAuth() {
		when:
		go('securityInfo/currentAuth')

		then:
		with(pageSource) {
			contains('WebAuthenticationDetails')
			contains('__grails.anonymous.user__')
		}
	}

	void testUsercache() {
		given:
		userCache.putUserInCache(new User('testuser', 'pw', []))

		when:
		go('securityInfo/usercache')

		then:
		with(pageSource) {
			contains('UserCache class: org.ehcache.jsr107.Eh107Cache')
			contains('testuser')
		}

		cleanup:
		userCache.removeUserFromCache('testuser')
	}

	void testEmptyUsercache() {
		when:
		go('securityInfo/usercache')

		then:
		with(pageSource) {
			contains('UserCache class: org.ehcache.jsr107.Eh107Cache')
			!contains('testuser')
		}
	}

	void testFilterChains() {
		when:
		go('securityInfo/filterChains')

		then:
		with(pageSource) {
			contains('/assets/**')
			contains('/**/js/**')
			contains('/**/css/**')
			contains('/**/images/**')
			contains('/**/favicon.ico')
			contains('/**')
			contains('grails.plugin.springsecurity.web.SecurityRequestHolderFilter')
			contains('org.springframework.security.web.access.channel.ChannelProcessingFilter')
			contains('org.springframework.security.web.context.SecurityContextPersistenceFilter')
			contains('org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter')
			contains('grails.plugin.springsecurity.web.filter.GrailsRememberMeAuthenticationFilter')
			contains('grails.plugin.springsecurity.web.filter.GrailsAnonymousAuthenticationFilter')
			contains('org.springframework.security.web.access.intercept.FilterSecurityInterceptor')
			contains('grails.plugin.springsecurity.web.authentication.logout.MutableLogoutFilter')
			contains('grails.plugin.springsecurity.web.authentication.GrailsUsernamePasswordAuthenticationFilter')
			contains('org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter')
			contains('grails.plugin.springsecurity.web.filter.GrailsRememberMeAuthenticationFilter')
			contains('grails.plugin.springsecurity.web.filter.GrailsAnonymousAuthenticationFilter')
			contains('org.springframework.security.web.access.intercept.FilterSecurityInterceptor')
			contains('org.springframework.security.web.authentication.switchuser.SwitchUserFilter')
		}
	}

	void testLogoutHandlers() {
		when:
		go('securityInfo/logoutHandlers')

		then:
		with(pageSource) {
			contains('org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices')
			contains('org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler')
		}
	}

	void testVoters() {
		when:
		go('securityInfo/voters')

		then:
		with(pageSource) {
			contains('org.springframework.security.access.vote.AuthenticatedVoter')
			contains('org.springframework.security.access.vote.RoleHierarchyVoter')
			contains('grails.plugin.springsecurity.web.access.expression.WebExpressionVoter')
		}
	}

	void testProviders() {
		when:
		go('securityInfo/providers')

		then:
		with(pageSource) {
			contains('org.springframework.security.authentication.dao.DaoAuthenticationProvider')
			contains('grails.plugin.springsecurity.authentication.GrailsAnonymousAuthenticationProvider')
			contains('org.springframework.security.authentication.RememberMeAuthenticationProvider')
		}
	}
}
