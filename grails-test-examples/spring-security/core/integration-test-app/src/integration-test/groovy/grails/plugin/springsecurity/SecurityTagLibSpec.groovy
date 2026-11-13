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

import org.grails.buffer.GrailsPrintWriter
import org.grails.gsp.GroovyPagesTemplateEngine
import org.grails.plugins.testing.GrailsMockHttpServletRequest
import org.grails.plugins.testing.GrailsMockHttpServletResponse
import org.grails.web.servlet.DefaultGrailsApplicationAttributes
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.security.authentication.AuthenticationDetailsSource
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder as SCH
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsChecker
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.security.web.authentication.switchuser.SwitchUserFilter
import org.springframework.web.context.request.RequestContextHolder
import spock.lang.PendingFeature
import spock.lang.Shared

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletContext
import java.security.Principal

/**
 * Integration tests for <code>SecurityTagLib</code>.
 *
 * @author Burt Beckwith
 */
class SecurityTagLibSpec extends AbstractIntegrationSpec {

	@Shared
	private Expando user = new Expando()

	private GrailsMockHttpServletRequest request = new GrailsMockHttpServletRequest()
	private GrailsMockHttpServletResponse response = new GrailsMockHttpServletResponse()

	GroovyPagesTemplateEngine groovyPagesTemplateEngine
	ServletContext servletContext

	void 'ifAllGranted'() {
		given:
		String body = 'the_content'

		when:
		authenticate 'role1'

		then:
		assertOutputEquals '', "<sec:ifAllGranted roles='ROLE_role1,ROLE_role2'>$body</sec:ifAllGranted>"

		when:
		authenticate 'role2', 'role1'

		then:
		assertOutputEquals body, "<sec:ifAllGranted roles='ROLE_role1,ROLE_role2'>$body</sec:ifAllGranted>"
	}

	void 'ifNotGranted'() {
		given:
		String body = 'the_content'

		when:
		authenticate 'role1'

		then:
		assertOutputEquals '', "<sec:ifNotGranted roles='ROLE_role1,ROLE_role2'>$body</sec:ifNotGranted>"

		when:
		authenticate 'role3'

		then:
		assertOutputEquals body, "<sec:ifNotGranted roles='ROLE_role1,ROLE_role2'>$body</sec:ifNotGranted>"
	}

	void 'ifAnyGranted'() {
		given:
		String body = 'the_content'

		when:
		authenticate 'role3'

		then:
		assertOutputEquals '', "<sec:ifAnyGranted roles='ROLE_role1,ROLE_role2'>$body</sec:ifAnyGranted>"

		when:
		authenticate 'role2'

		then:
		assertOutputEquals body, "<sec:ifAnyGranted roles='ROLE_role1,ROLE_role2'>$body</sec:ifAnyGranted>"
	}

	void 'ifLoggedIn'() {
		when:
		String body = 'the_content'

		then:
		assertOutputEquals '', "<sec:ifLoggedIn roles='role1,role2'>$body</sec:ifLoggedIn>"

		when:
		authenticate 'role1'

		then:
		assertOutputEquals body, "<sec:ifLoggedIn roles='role1,role2'>$body</sec:ifLoggedIn>"
	}

	void 'ifNotLoggedIn'() {
		when:
		String body = 'the_content'

		then:
		assertOutputEquals body, "<sec:ifNotLoggedIn roles='role1,role2'>$body</sec:ifNotLoggedIn>"

		when:
		authenticate 'role1'

		then:
		assertOutputEquals '', "<sec:ifNotLoggedIn roles='role1,role2'>$body</sec:ifNotLoggedIn>"
	}

	void "loggedInUserInfo() for a principal that has a 'domainClass' property"() {
		given:
		String fullName = 'First Last'

		when:
		user.fullName = fullName

		then:
		assertOutputEquals '', "<sec:loggedInUserInfo field='fullName'/>"

		when:
		def principal = new HasDomainClass('username', fullName, 'role1', user)
		authenticate principal, 'role1'

		then:
		assertOutputEquals fullName, "<sec:loggedInUserInfo field='fullName'/>"
	}

	void 'loggedInUserInfo() with a nested property'() {
		given:
		String fullName = 'First Last'

		when:
		user.foo = [bar: [fullName: fullName]]

		then:
		assertOutputEquals '', "<sec:loggedInUserInfo field='foo.bar.fullName'/>"

		when:
		def principal = new HasDomainClass('username', 'fullName', 'role1', user)
		authenticate principal, 'role1'

		then:
		assertOutputEquals fullName, "<sec:loggedInUserInfo field='foo.bar.fullName'/>"
		assertOutputEquals '', "<sec:loggedInUserInfo field='foo.fullName'/>"
	}

	void "Test loggedInUserInfo() for a principal that doesn't have a 'domainClass' property"() {
		given:
		String fullName = 'First Last'

		when:
		user.fullName = fullName

		then:
		assertOutputEquals '', "<sec:loggedInUserInfo field='fullName'/>"

		when:
		def principal = new NoDomainClass('username', fullName, 'role1')
		authenticate principal, 'role1'

		then:
		assertOutputEquals fullName, "<sec:loggedInUserInfo field='fullName'/>"
	}

	void '<sec:username/>'() {
		expect:
		assertOutputEquals '', '<sec:username/>'

		when:
		authenticate 'role1'

		then:
		assertOutputEquals 'username1', '<sec:username/>'
	}

	void '<sec:ifSwitched> and <sec:ifNotSwitched>'() {
		when:
		String body = 'the_content'

		then:
		assertOutputEquals body, "<sec:ifNotSwitched>$body</sec:ifNotSwitched>"
		assertOutputEquals '', "<sec:ifSwitched>$body</sec:ifSwitched>"

		when:
		authenticate 'role1'

		then:
		assertOutputEquals body, "<sec:ifNotSwitched>$body</sec:ifNotSwitched>"
		assertOutputEquals '', "<sec:ifSwitched>$body</sec:ifSwitched>"

		when:
		switchUser()

		then:
		assertOutputEquals body, "<sec:ifSwitched>$body</sec:ifSwitched>"
		assertOutputEquals '', "<sec:ifNotSwitched>$body</sec:ifNotSwitched>"
	}

	void '<sec:switchedUserOriginalUsername/>'() {
		expect:
		assertOutputEquals '', '<sec:switchedUserOriginalUsername/>'

		when:
		authenticate 'role1'

		then:
		assertOutputEquals '', '<sec:switchedUserOriginalUsername/>'

		when:
		switchUser()

		then:
		assertOutputEquals 'username1', '<sec:switchedUserOriginalUsername/>'
	}

	void '<sec:access>'() {
		when:
		String body = 'the_content'
		authenticate ''

		then:
		assertOutputEquals '', """<sec:access expression="hasRole('role1')">$body</sec:access>"""
		assertOutputEquals body, """<sec:noAccess expression="hasRole('role1')">$body</sec:noAccess>"""

		when:
		authenticate 'role1'

		then:
		assertOutputEquals body, """<sec:access expression="hasRole('role1')">$body</sec:access>"""
		assertOutputEquals '', """<sec:noAccess expression="hasRole('role1')">$body</sec:noAccess>"""
	}

	void '<sec:link> via expression'() {
		when:
		String body = 'Test link'

		then:
		assertOutputEquals '', """<sec:link controller="testController" action="testAction" expression="hasRole('role1')">$body</sec:link>"""

		when:
		authenticate 'role1'

		then:
		assertOutputEquals 'test', """<sec:access expression="hasRole('role1')">test</sec:access>"""
		assertOutputEquals """<a href="/testController/testAction">$body</a>""",
		                   """<sec:link controller="testController" action="testAction" expression="hasRole('role1')">$body</sec:link>"""
	}

	void '<sec:link fallback="true"> via expression'() {
		when:
		String body = 'Test link'

		then:
		assertOutputEquals "", """<sec:link controller="testController" action="testAction" expression="hasRole('role1')" fallback="false">$body</sec:link>"""

		then:
		assertOutputEquals body, """<sec:link controller="testController" action="testAction" expression="hasRole('role1')" fallback="true">$body</sec:link>"""

		when:
		authenticate 'role1'

		then:
		assertOutputEquals 'test', """<sec:access expression="hasRole('role1')">test</sec:access>"""
		assertOutputEquals """<a href="/testController/testAction">$body</a>""",
				"""<sec:link controller="testController" action="testAction" expression="hasRole('role1')" fallback="true">$body</sec:link>"""
	}

	@PendingFeature(reason = 'Was previously ignored')
	void '<sec:link fallback="true"> via url'() {
		when:
		String body = 'Test link'

		then:
		assertOutputEquals '', """<sec:link controller="testController" action="testAction" fallback="false">$body</sec:link>"""

		then:
		assertOutputEquals body, """<sec:link controller="testController" action="testAction" fallback="true">$body</sec:link>"""

		when:
		authenticate 'roleInMap'

		then:
		assertOutputEquals """<a href="/testController/testAction">$body</a>""",
				"""<sec:link controller="testController" action="testAction" fallback="true">$body</sec:link>"""
	}

	@PendingFeature(reason = 'Was previously ignored')
	void '<sec:link> via url'() {
		when:
		String body = 'Test link'

		then:
		assertOutputEquals '', """<sec:link controller="testController" action="testAction"></sec:link>"""

		when:
		// role 'roleInMap' mapped to controller via interceptUrlMap in Config.groovy
		authenticate 'roleInMap'

		then:
		assertOutputEquals """<a href="/testController/testAction">$body</a>""",
		                   """<sec:link controller="testController" action="testAction">$body</sec:link>"""
	}

	private void switchUser() {
		def filter = new SwitchUserFilter(switchUserUrl: '/login/impersonate', exitUserUrl: '/logout/impersonate')
		request.method = 'POST'
		request.requestURI = '/login/impersonate'
		request.addParameter 'username', 'somebody'

		boolean chainCalled = false
		boolean onAuthenticationSuccessCalled = false
		def chain = [doFilter: { req, res -> chainCalled = true }] as FilterChain
		def onAuthenticationSuccess = { req, res, targetUser -> onAuthenticationSuccessCalled = true }
		filter.successHandler = [onAuthenticationSuccess: onAuthenticationSuccess] as AuthenticationSuccessHandler

		def user = new User('somebody', 'password', true, true, true, true, [new SimpleGrantedAuthority('ROLE_USER')])
		filter.userDetailsService = [loadUserByUsername: { String username -> user }] as UserDetailsService
		filter.userDetailsChecker = [check: { details -> }] as UserDetailsChecker
		filter.authenticationDetailsSource = [buildDetails: { req -> '' }] as AuthenticationDetailsSource

		filter.doFilter request, response, chain

		assert !chainCalled
		assert onAuthenticationSuccessCalled
	}

	private void authenticate(String... roles) {
		authenticate new SimplePrincipal(name: 'username1', domainClass: user), roles
	}

	private void authenticate(Principal principal, String... roles) {
		Authentication authentication = new TestingAuthenticationToken(
				  principal, null, roles.collect { new SimpleGrantedAuthority('ROLE_' + it) })
		authentication.authenticated = true
		SCH.context.authentication = authentication
	}

	private void assertOutputEquals(String expected, String template) {
		def sw = new StringWriter()
		def out = new GrailsPrintWriter(sw)

		GrailsWebRequest grailsWebRequest = new GrailsWebRequest(request, response,
				  new DefaultGrailsApplicationAttributes(servletContext))
		grailsWebRequest.out = out
		RequestContextHolder.requestAttributes = grailsWebRequest

		groovyPagesTemplateEngine.createTemplate(template, 'test_' + UUID.randomUUID()).make([:]).writeTo out

		assert expected == sw.toString()
	}

	void cleanup() {
		SCH.clearContext()
		RequestContextHolder.resetRequestAttributes()
	}
}

class NoDomainClass extends User implements Principal {

	final String fullName

	NoDomainClass(String username, String name, String roles) {
		super(username, 'password', true, true, true, true, SpringSecurityUtils.parseAuthoritiesString(roles))
		fullName = name
	}

	String getName() { username }
}

class HasDomainClass extends User implements Principal {

	final String fullName
	final domainClass

	HasDomainClass(String username, String name, String roles, dc) {
		super(username, 'password', true, true, true, true, SpringSecurityUtils.parseAuthoritiesString(roles))
		fullName = name
		domainClass = dc
	}

	String getName() { username }
}

class SimplePrincipal implements Principal {
	String name
	def domainClass
}
