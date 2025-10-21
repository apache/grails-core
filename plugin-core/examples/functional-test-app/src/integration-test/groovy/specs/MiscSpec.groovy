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

package specs

import geb.module.TextInput
import grails.testing.mixin.integration.Integration
import org.springframework.security.crypto.password.PasswordEncoder
import pages.IndexPage
import spock.lang.IgnoreIf
import spock.lang.Issue

@Integration
@IgnoreIf({ System.getProperty('TESTCONFIG') != 'misc' })
class MiscSpec extends AbstractHyphenatedSecuritySpec {

	void 'salted password'() {
		given:
		String username = 'testuser_books_and_movies'
		PasswordEncoder passwordEncoder = createSha256Encoder()

		when:
		String hashedPassword = getUserProperty(username, 'password')
		String notSalted = passwordEncoder.encode('password')

		then:
		notSalted != hashedPassword
	}

	void 'switch user'() {
		when:
		login 'admin'

		then:
		at IndexPage

		// verify logged in
		when:
		go 'secure-annotated'

		then:
		pageSource.contains('you have ROLE_ADMIN')

		when:
		String auth = getSessionValue('SPRING_SECURITY_CONTEXT')

		then:
		auth.contains 'Username=admin'
		auth.contains 'Authenticated=true'
		auth.contains 'ROLE_ADMIN'
		auth.contains 'ROLE_USER' // new, added since inferred from role hierarchy
		!auth.contains('ROLE_PREVIOUS_ADMINISTRATOR')

		// switch via GET
		when:
		go 'login/impersonate?username=testuser'

		then:
		pageSource.contains('Error 404 Page Not Found')

		// switch via POST
		when:
		go 'misc-test/test'
		def input = $("#username").module(TextInput)
		input.text = 'testuser'
		$("#switchUserFormSubmitButton").click()

		then:
		pageSource.contains('Available Controllers:')

		// verify logged in as testuser

		when:
		auth = getSessionValue('SPRING_SECURITY_CONTEXT')

		then:
		auth.contains 'Username=testuser'
		auth.contains 'Authenticated=true'
		auth.contains 'ROLE_USER'
		auth.contains 'ROLE_PREVIOUS_ADMINISTRATOR'

		when:
		go 'secure-annotated/user-action'

		then:
		pageSource.contains('you have ROLE_USER')

		// verify not logged in as admin
		when:
		go 'secure-annotated/admin-either'

		then:
		pageSource.contains('Sorry, you\'re not authorized to view this page.')

		// switch back via GET
		when:
		go 'logout/impersonate'

		then:
		pageSource.contains('Error 404 Page Not Found')

		// switch via POST
		when:
		go 'misc-test/test'
		$("#exitUserFormSubmitButton").click()

		then:
		pageSource.contains('Available Controllers:')

		// verify logged in as admin
		when:
		go 'secure-annotated/admin-either'

		then:
		pageSource.contains('you have ROLE_ADMIN')

		when:
		auth = getSessionValue('SPRING_SECURITY_CONTEXT')

		then:
		auth.contains 'Username=admin'
		auth.contains 'Authenticated=true'
		auth.contains 'ROLE_ADMIN'
		auth.contains 'ROLE_USER'
		!auth.contains('ROLE_PREVIOUS_ADMINISTRATOR')
	}

	void 'hierarchical roles'() {
		when:
		login 'admin'

		then:
		at IndexPage

		// verify logged in
		when:
		go 'secure-annotated'

		then:
		pageSource.contains('you have ROLE_ADMIN')

		when:
		String auth = getSessionValue('SPRING_SECURITY_CONTEXT')

		then:
		auth.contains 'Authenticated=true'
		auth.contains 'ROLE_USER'

		// now get an action that's ROLE_USER only
		when:
		go 'secure-annotated/user-action'

		then:
		pageSource.contains('you have ROLE_USER')
	}

	void 'taglibs unauthenticated'() {
		when:
		go 'misc-test/test'

		then:
		!pageSource.contains('user and admin')
		!pageSource.contains('user and admin and foo')
		pageSource.contains('not user and not admin')
		!pageSource.contains('user or admin')
		pageSource.contains('accountNonExpired: "not logged in"')
		pageSource.contains('id: "not logged in"')
		pageSource.contains('Username is ""')
		!pageSource.contains('logged in true')
		pageSource.contains('logged in false')
		!pageSource.contains('switched true')
		pageSource.contains('switched false')
		pageSource.contains('switched original username ""')

		!pageSource.contains('access with role user: true')
		!pageSource.contains('access with role admin: true')
		pageSource.contains('access with role user: false')
		pageSource.contains('access with role admin: false')

		pageSource.contains('Can access /login/auth')
		!pageSource.contains('Can access /secure-annotated')
		!pageSource.contains('Cannot access /login/auth')
		pageSource.contains('Cannot access /secure-annotated')

		pageSource.contains('anonymous access: true')
		pageSource.contains('Can access /misc-test/test')
		!pageSource.contains('anonymous access: false')
		!pageSource.contains('Cannot access /misc-test/test')
	}

	void 'taglibs user'() {
		when:
		login 'testuser'

		then:
		at IndexPage

		when:
		go 'misc-test/test'

		then:
		!pageSource.contains('user and admin')
		!pageSource.contains('user and admin and foo')
		!pageSource.contains('not user and not admin')
		pageSource.contains('user or admin')
		pageSource.contains('accountNonExpired: "true"')
		!pageSource.contains('id: "not logged in"') // can't test on exact id, don't know what it is)
		pageSource.contains('Username is "testuser"')
		pageSource.contains('logged in true')
		!pageSource.contains('logged in false')
		!pageSource.contains('switched true')
		pageSource.contains('switched false')
		pageSource.contains('switched original username ""')

		pageSource.contains('access with role user: true')
		!pageSource.contains('access with role admin: true')
		!pageSource.contains('access with role user: false')
		pageSource.contains('access with role admin: false')

		pageSource.contains('Can access /login/auth')
		!pageSource.contains('Can access /secure-annotated')
		!pageSource.contains('Cannot access /login/auth')
		pageSource.contains('Cannot access /secure-annotated')

		pageSource.contains('anonymous access: false')
		pageSource.contains('Can access /misc-test/test')
		!pageSource.contains('anonymous access: true')
	}

	void 'taglibs admin'() {
		when:
		login 'admin'

		then:
		at IndexPage

		when:
		go 'misc-test/test'

		then:
		pageSource.contains('user and admin')
		!pageSource.contains('user and admin and foo')
		!pageSource.contains('not user and not admin')
		pageSource.contains('user or admin')
		pageSource.contains('accountNonExpired: "true"')
		!pageSource.contains('id: "not logged in"') // can't test on exact id, don't know what it is)
		pageSource.contains('Username is "admin"')

		pageSource.contains('logged in true')
		!pageSource.contains('logged in false')
		!pageSource.contains('switched true')
		pageSource.contains('switched false')
		pageSource.contains('switched original username ""')

		pageSource.contains('access with role user: true')
		pageSource.contains('access with role admin: true')
		!pageSource.contains('access with role user: false')
		!pageSource.contains('access with role admin: false')

		pageSource.contains('Can access /login/auth')
		pageSource.contains('Can access /secure-annotated')
		!pageSource.contains('Cannot access /login/auth')
		!pageSource.contains('Cannot access /secure-annotated')

		pageSource.contains('anonymous access: false')
		pageSource.contains('Can access /misc-test/test')
		!pageSource.contains('anonymous access: true')
		!pageSource.contains('Cannot access /misc-test/test')
	}

	void 'controller methods unauthenticated'() {
		when:
		go 'misc-test/test-controller-methods'

		then:
		pageSource.contains('getPrincipal: org.springframework.security.core.userdetails.User')
		pageSource.contains('Username=__grails.anonymous.user__')
		pageSource.contains('Granted Authorities=[ROLE_ANONYMOUS]')
		pageSource.contains('isLoggedIn: false')
		pageSource.contains('loggedIn: false')
		pageSource.contains('getAuthenticatedUser: null')
		pageSource.contains('authenticatedUser: null')
	}

	void 'controller methods authenticated'() {
		when:
		login 'admin'

		then:
		at IndexPage

		when:
		go 'misc-test/test-controller-methods'

		then:
		pageSource.contains('getPrincipal: grails.plugin.springsecurity.userdetails.GrailsUser')
		pageSource.contains('principal: grails.plugin.springsecurity.userdetails.GrailsUser')
		pageSource.contains('Username=admin')
		pageSource.contains('isLoggedIn: true')
		pageSource.contains('loggedIn: true')
		pageSource.contains('getAuthenticatedUser: TestUser(username:admin)')
		pageSource.contains('authenticatedUser: TestUser(username:admin)')
	}

	void 'test hyphenated'() {
		when:
		go 'foo-bar'

		then:
		pageSource.contains('Please Login')

		when:
		go 'foo-bar/index'

		then:
		pageSource.contains('Please Login')

		when:
		go 'foo-bar/bar-foo'

		then:
		pageSource.contains('Please Login')

		when:
		logout()
		login 'admin'

		then:
		at IndexPage

		when:
		go 'foo-bar'

		then:
		pageSource.contains('INDEX')

		when:
		go 'foo-bar/index'

		then:
		pageSource.contains('INDEX')

		when:
		go 'foo-bar/bar-foo'

		then:
		pageSource.contains('barFoo')
	}

	@Issue('https://github.com/apache/grails-spring-security/issues/414')
	void 'test Servlet API methods unauthenticated'() {
		when:
		go 'misc-test/test-servlet-api-methods'

		then:
		pageSource.contains('request.getUserPrincipal(): null')
		pageSource.contains('request.userPrincipal: null')
		pageSource.contains('request.isUserInRole(\'ROLE_ADMIN\'): false')
		pageSource.contains('request.isUserInRole(\'ROLE_FOO\'): false')
		pageSource.contains('request.getRemoteUser(): null')
		pageSource.contains('request.remoteUser: null')
	}

	@Issue('https://github.com/apache/grails-spring-security/issues/414')
	void 'test Servlet API methods authenticated'() {
		when:
		login 'admin'

		then:
		at IndexPage

		when:
		go 'misc-test/test-servlet-api-methods'

		then:
		pageSource.contains('request.getUserPrincipal(): UsernamePasswordAuthenticationToken')
		pageSource.contains('request.userPrincipal: UsernamePasswordAuthenticationToken')
		pageSource.contains('request.isUserInRole(\'ROLE_ADMIN\'): true')
		pageSource.contains('request.isUserInRole(\'ROLE_FOO\'): false')
		pageSource.contains('request.getRemoteUser(): admin')
		pageSource.contains('request.remoteUser: admin')
	}

	@Issue('https://github.com/apache/grails-spring-security/issues/403')
	void 'test controller with annotated index action, unauthenticated'() {
		when:
		go 'index-annotated'

		then:
		pageSource.contains('Please Login')

		when:
		go 'index-annotated/'

		then:
		pageSource.contains('Please Login')

		when:
		go 'index-annotated/index'

		then:
		pageSource.contains('Please Login')

		when:
		go 'index-annotated/show'

		then:
		pageSource.contains('Please Login')
	}

	@Issue('https://github.com/apache/grails-spring-security/issues/403')
	void 'test controller with annotated index action, authenticated'() {
		when:
		login 'admin'

		then:
		at IndexPage

		when:
		go 'index-annotated'

		then:
		pageSource.contains('index action, principal: ')

		when:
		go 'index-annotated/'

		then:
		pageSource.contains('index action, principal: ')

		when:
		go 'index-annotated/index'

		then:
		pageSource.contains('index action, principal: ')

		when:
		go 'index-annotated/show'

		then:
		pageSource.contains('Sorry, you\'re not authorized to view this page.')
	}
}
