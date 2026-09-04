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
			pageSource.contains('adh.ajaxErrorPage')
			pageSource.contains('/login/ajaxDenied')


		and: 'the full config table is rendered (no client-side pagination)'
			pageSource.contains('securityConfigType')
			pageSource.contains('userLookup.userDomainClassName')

	}

	void testMappings() {
		when:
		go('securityInfo/mappings')

		then:
		assertContentContainsOne(
				'ROLE_RUN_AS, IS_AUTHENTICATED_FULLY',
				'IS_AUTHENTICATED_FULLY, ROLE_RUN_AS'
		)
			pageSource.contains('/j_spring_security_switch_user')
			pageSource.contains('/secure/**')
			pageSource.contains('ROLE_ADMIN')

	}

	void testCurrentAuth() {
		when:
		go('securityInfo/currentAuth')

		then:
			pageSource.contains('WebAuthenticationDetails')
			pageSource.contains('__grails.anonymous.user__')

	}

	void testUsercache() {
		given:
		userCache.putUserInCache(new User('testuser', 'pw', []))

		when:
		go('securityInfo/usercache')

		then:
			pageSource.contains('UserCache class: org.ehcache.jsr107.Eh107Cache')
			pageSource.contains('testuser')


		cleanup:
		userCache.removeUserFromCache('testuser')
	}

	void testEmptyUsercache() {
		when:
		go('securityInfo/usercache')

		then:
			pageSource.contains('UserCache class: org.ehcache.jsr107.Eh107Cache')
			!pageSource.contains('testuser')

	}

	void testFilterChains() {
		when:
		go('securityInfo/filterChains')

		then:
			pageSource.contains('/assets/**')
			pageSource.contains('/**/js/**')
			pageSource.contains('/**/css/**')
			pageSource.contains('/**/images/**')
			pageSource.contains('/**/favicon.ico')
			pageSource.contains('/**')
			pageSource.contains('grails.plugin.springsecurity.web.SecurityRequestHolderFilter')
			pageSource.contains('org.springframework.security.web.access.channel.ChannelProcessingFilter')
			pageSource.contains('org.springframework.security.web.context.SecurityContextPersistenceFilter')
			pageSource.contains('org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter')
			pageSource.contains('grails.plugin.springsecurity.web.filter.GrailsRememberMeAuthenticationFilter')
			pageSource.contains('grails.plugin.springsecurity.web.filter.GrailsAnonymousAuthenticationFilter')
			pageSource.contains('org.springframework.security.web.access.intercept.FilterSecurityInterceptor')
			pageSource.contains('grails.plugin.springsecurity.web.authentication.logout.MutableLogoutFilter')
			pageSource.contains('grails.plugin.springsecurity.web.authentication.GrailsUsernamePasswordAuthenticationFilter')
			pageSource.contains('org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter')
			pageSource.contains('grails.plugin.springsecurity.web.filter.GrailsRememberMeAuthenticationFilter')
			pageSource.contains('grails.plugin.springsecurity.web.filter.GrailsAnonymousAuthenticationFilter')
			pageSource.contains('org.springframework.security.web.access.intercept.FilterSecurityInterceptor')
			pageSource.contains('org.springframework.security.web.authentication.switchuser.SwitchUserFilter')

	}

	void testLogoutHandlers() {
		when:
		go('securityInfo/logoutHandlers')

		then:
			pageSource.contains('org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices')
			pageSource.contains('org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler')

	}

	void testVoters() {
		when:
		go('securityInfo/voters')

		then:
			pageSource.contains('org.springframework.security.access.vote.AuthenticatedVoter')
			pageSource.contains('org.springframework.security.access.vote.RoleHierarchyVoter')
			pageSource.contains('grails.plugin.springsecurity.web.access.expression.WebExpressionVoter')

	}

	void testProviders() {
		when:
		go('securityInfo/providers')

		then:
			pageSource.contains('org.springframework.security.authentication.dao.DaoAuthenticationProvider')
			pageSource.contains('grails.plugin.springsecurity.authentication.GrailsAnonymousAuthenticationProvider')
			pageSource.contains('org.springframework.security.authentication.RememberMeAuthenticationProvider')

	}
}
