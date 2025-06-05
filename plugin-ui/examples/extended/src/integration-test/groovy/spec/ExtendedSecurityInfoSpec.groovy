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

package spec

import grails.testing.mixin.integration.Integration
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserCache

@Integration
class ExtendedSecurityInfoSpec extends AbstractSecuritySpec {

	UserCache userCache

	void testConfig() {
		when:
		browser.go('securityInfo/config')

		then:
		assertContentContains('adh.ajaxErrorPage /login/ajaxDenied')
		assertContentContains('Showing 1 to 10 of ')
	}

	void testMappings() {
		when:
		browser.go('securityInfo/mappings')

		then:
		assertContentContainsOne(
				'ROLE_RUN_AS, IS_AUTHENTICATED_FULLY',
				'IS_AUTHENTICATED_FULLY, ROLE_RUN_AS'
		)

		assertContentContains('/j_spring_security_switch_user')
		assertContentContains('/secure/**')
		assertContentContains('ROLE_ADMIN')
	}

	void testCurrentAuth() {
		when:
		browser.go('securityInfo/currentAuth')

		then:
		assertContentContains('Details WebAuthenticationDetails')
		assertContentContains('__grails.anonymous.user__')
	}

	void testUserCache() {
		given:
		userCache.putUserInCache(new User('testuser', 'pw', []))

		when:
		browser.go('securityInfo/usercache')

		then:
		assertContentContains('UserCache class: org.ehcache.jsr107.Eh107Cache')
		assertContentContains('testuser')

		cleanup:
		userCache.removeUserFromCache('testuser')
	}

	void testEmptyUserCache() {
		when:
		browser.go('securityInfo/usercache')

		then:
		assertContentContains('UserCache class: org.ehcache.jsr107.Eh107Cache')
		assertContentDoesNotContain('testuser')
	}

	void testFilterChains() {
		when:
		browser.go('securityInfo/filterChains')

		then:
		assertContentContains('/assets/**')
		assertContentContains('/**/js/**')
		assertContentContains('/**/css/**')
		assertContentContains('/**/images/**')
		assertContentContains('/**/favicon.ico')
		assertContentContains('/**')
		assertContentContains('grails.plugin.springsecurity.web.SecurityRequestHolderFilter')
		assertContentContains('org.springframework.security.web.access.channel.ChannelProcessingFilter')
		assertContentContains('org.springframework.security.web.context.SecurityContextPersistenceFilter')
		assertContentContains('org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter')
		assertContentContains('grails.plugin.springsecurity.web.filter.GrailsRememberMeAuthenticationFilter')
		assertContentContains('grails.plugin.springsecurity.web.filter.GrailsAnonymousAuthenticationFilter')

		assertContentContains('org.springframework.security.web.access.intercept.FilterSecurityInterceptor')
		assertContentContains('grails.plugin.springsecurity.web.authentication.logout.MutableLogoutFilter')
		assertContentContains('grails.plugin.springsecurity.web.authentication.GrailsUsernamePasswordAuthenticationFilter')
		assertContentContains('org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter')
		assertContentContains('grails.plugin.springsecurity.web.filter.GrailsRememberMeAuthenticationFilter')
		assertContentContains('grails.plugin.springsecurity.web.filter.GrailsAnonymousAuthenticationFilter')
		assertContentContains('org.springframework.security.web.access.intercept.FilterSecurityInterceptor')
		assertContentContains('org.springframework.security.web.authentication.switchuser.SwitchUserFilter')
	}

	void testLogoutHandlers() {
		when:
		browser.go('securityInfo/logoutHandlers')

		then:
		assertContentContains('org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices')
		assertContentContains('org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler')
	}

	void testVoters() {
		when:
		browser.go('securityInfo/voters')

		then:
		assertContentContains('org.springframework.security.access.vote.AuthenticatedVoter')
		assertContentContains('org.springframework.security.access.vote.RoleHierarchyVoter')
		assertContentContains('grails.plugin.springsecurity.web.access.expression.WebExpressionVoter')
	}

	void testProviders() {
		when:
		browser.go('securityInfo/providers')

		then:
		assertContentContains('org.springframework.security.authentication.dao.DaoAuthenticationProvider')
		assertContentContains('grails.plugin.springsecurity.authentication.GrailsAnonymousAuthenticationProvider')
		assertContentContains('org.springframework.security.authentication.RememberMeAuthenticationProvider')
	}
}
