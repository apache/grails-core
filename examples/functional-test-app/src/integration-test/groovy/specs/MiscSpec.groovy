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
		assertContentContains 'you have ROLE_ADMIN'

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
		assertContentContains 'Error 404 Page Not Found'

		// switch via POST
		when:
		go 'misc-test/test'
		def input = $("#username").module(TextInput)
		input.text = 'testuser'
		$("#switchUserFormSubmitButton").click()

		then:
		assertContentContains 'Available Controllers:'

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
		assertContentContains 'you have ROLE_USER'

		// verify not logged in as admin
		when:
		go 'secure-annotated/admin-either'

		then:
		assertContentContains "Sorry, you're not authorized to view this page."

		// switch back via GET
		when:
		go 'logout/impersonate'

		then:
		assertContentContains 'Error 404 Page Not Found'

		// switch via POST
		when:
		go 'misc-test/test'
		$("#exitUserFormSubmitButton").click()

		then:
		assertContentContains 'Available Controllers:'

		// verify logged in as admin
		when:
		go 'secure-annotated/admin-either'

		then:
		assertContentContains 'you have ROLE_ADMIN'

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
		assertContentContains 'you have ROLE_ADMIN'

		when:
		String auth = getSessionValue('SPRING_SECURITY_CONTEXT')

		then:
		auth.contains 'Authenticated=true'
		auth.contains 'ROLE_USER'

		// now get an action that's ROLE_USER only
		when:
		go 'secure-annotated/user-action'

		then:
		assertContentContains 'you have ROLE_USER'
	}

	void 'taglibs unauthenticated'() {
		when:
		go 'misc-test/test'

		then:
		assertContentDoesNotContain 'user and admin'
		assertContentDoesNotContain 'user and admin and foo'
		assertContentContains 'not user and not admin'
		assertContentDoesNotContain 'user or admin'
		assertContentContains 'accountNonExpired: "not logged in"'
		assertContentContains 'id: "not logged in"'
		assertContentContains 'Username is ""'
		assertContentDoesNotContain 'logged in true'
		assertContentContains 'logged in false'
		assertContentDoesNotContain 'switched true'
		assertContentContains 'switched false'
		assertContentContains 'switched original username ""'

		assertContentDoesNotContain 'access with role user: true'
		assertContentDoesNotContain 'access with role admin: true'
		assertContentContains 'access with role user: false'
		assertContentContains 'access with role admin: false'

		assertContentContains 'Can access /login/auth'
		assertContentDoesNotContain 'Can access /secure-annotated'
		assertContentDoesNotContain 'Cannot access /login/auth'
		assertContentContains 'Cannot access /secure-annotated'

		assertContentContains 'anonymous access: true'
		assertContentContains 'Can access /misc-test/test'
		assertContentDoesNotContain 'anonymous access: false'
		assertContentDoesNotContain 'Cannot access /misc-test/test'
	}

	void 'taglibs user'() {
		when:
		login 'testuser'

		then:
		at IndexPage

		when:
		go 'misc-test/test'

		then:
		assertContentDoesNotContain 'user and admin'
		assertContentDoesNotContain 'user and admin and foo'
		assertContentDoesNotContain 'not user and not admin'
		assertContentContains 'user or admin'
		assertContentContains 'accountNonExpired: "true"'
		assertContentDoesNotContain 'id: "not logged in"' // can't test on exact id, don't know what it is
		assertContentContains 'Username is "testuser"'
		assertContentContains 'logged in true'
		assertContentDoesNotContain 'logged in false'
		assertContentDoesNotContain 'switched true'
		assertContentContains 'switched false'
		assertContentContains 'switched original username ""'

		assertContentContains 'access with role user: true'
		assertContentDoesNotContain 'access with role admin: true'
		assertContentDoesNotContain 'access with role user: false'
		assertContentContains 'access with role admin: false'

		assertContentContains 'Can access /login/auth'
		assertContentDoesNotContain 'Can access /secure-annotated'
		assertContentDoesNotContain 'Cannot access /login/auth'
		assertContentContains 'Cannot access /secure-annotated'

		assertContentContains 'anonymous access: false'
		assertContentContains 'Can access /misc-test/test'
		assertContentDoesNotContain 'anonymous access: true'
	}

	void 'taglibs admin'() {
		when:
		login 'admin'

		then:
		at IndexPage

		when:
		go 'misc-test/test'

		then:
		assertContentContains 'user and admin'
		assertContentDoesNotContain 'user and admin and foo'
		assertContentDoesNotContain 'not user and not admin'
		assertContentContains 'user or admin'
		assertContentContains 'accountNonExpired: "true"'
		assertContentDoesNotContain 'id: "not logged in"' // can't test on exact id, don't know what it is
		assertContentContains 'Username is "admin"'

		assertContentContains 'logged in true'
		assertContentDoesNotContain 'logged in false'
		assertContentDoesNotContain 'switched true'
		assertContentContains 'switched false'
		assertContentContains 'switched original username ""'

		assertContentContains 'access with role user: true'
		assertContentContains 'access with role admin: true'
		assertContentDoesNotContain 'access with role user: false'
		assertContentDoesNotContain 'access with role admin: false'

		assertContentContains 'Can access /login/auth'
		assertContentContains 'Can access /secure-annotated'
		assertContentDoesNotContain 'Cannot access /login/auth'
		assertContentDoesNotContain 'Cannot access /secure-annotated'

		assertContentContains 'anonymous access: false'
		assertContentContains 'Can access /misc-test/test'
		assertContentDoesNotContain 'anonymous access: true'
		assertContentDoesNotContain 'Cannot access /misc-test/test'
	}

	void 'controller methods unauthenticated'() {
		when:
		go 'misc-test/test-controller-methods'

		then:
		assertContentContains 'getPrincipal: org.springframework.security.core.userdetails.User'
		assertContentContains 'Username=__grails.anonymous.user__'
		assertContentContains 'Granted Authorities=[ROLE_ANONYMOUS]'
		assertContentContains 'isLoggedIn: false'
		assertContentContains 'loggedIn: false'
		assertContentContains 'getAuthenticatedUser: null'
		assertContentContains 'authenticatedUser: null'
	}

	void 'controller methods authenticated'() {
		when:
		login 'admin'

		then:
		at IndexPage

		when:
		go 'misc-test/test-controller-methods'

		then:
		assertContentContains 'getPrincipal: grails.plugin.springsecurity.userdetails.GrailsUser'
		assertContentContains 'principal: grails.plugin.springsecurity.userdetails.GrailsUser'
		assertContentContains 'Username=admin'
		assertContentContains 'isLoggedIn: true'
		assertContentContains 'loggedIn: true'
		assertContentContains 'getAuthenticatedUser: TestUser(username:admin)'
		assertContentContains 'authenticatedUser: TestUser(username:admin)'
	}

	void 'test hyphenated'() {
		when:
		go 'foo-bar'

		then:
		assertContentContains 'Please Login'

		when:
		go 'foo-bar/index'

		then:
		assertContentContains 'Please Login'

		when:
		go 'foo-bar/bar-foo'

		then:
		assertContentContains 'Please Login'

		when:
		logout()
		login 'admin'

		then:
		at IndexPage

		when:
		go 'foo-bar'

		then:
		assertContentContains 'INDEX'

		when:
		go 'foo-bar/index'

		then:
		assertContentContains 'INDEX'

		when:
		go 'foo-bar/bar-foo'

		then:
		assertContentContains 'barFoo'
	}

	@Issue('https://github.com/grails/grails-spring-security-core/issues/414')
	void 'test Servlet API methods unauthenticated'() {
		when:
		go 'misc-test/test-servlet-api-methods'

		then:
		assertContentContains 'request.getUserPrincipal(): null'
		assertContentContains 'request.userPrincipal: null'
		assertContentContains "request.isUserInRole('ROLE_ADMIN'): false"
		assertContentContains "request.isUserInRole('ROLE_FOO'): false"
		assertContentContains 'request.getRemoteUser(): null'
		assertContentContains 'request.remoteUser: null'
	}

	@Issue('https://github.com/grails/grails-spring-security-core/issues/414')
	void 'test Servlet API methods authenticated'() {
		when:
		login 'admin'

		then:
		at IndexPage

		when:
		go 'misc-test/test-servlet-api-methods'

		then:
		assertContentContains 'request.getUserPrincipal(): UsernamePasswordAuthenticationToken'
		assertContentContains 'request.userPrincipal: UsernamePasswordAuthenticationToken'
		assertContentContains "request.isUserInRole('ROLE_ADMIN'): true"
		assertContentContains "request.isUserInRole('ROLE_FOO'): false"
		assertContentContains 'request.getRemoteUser(): admin'
		assertContentContains 'request.remoteUser: admin'
	}

	@Issue('https://github.com/grails/grails-spring-security-core/issues/403')
	void 'test controller with annotated index action, unauthenticated'() {
		when:
		go 'index-annotated'

		then:
		assertContentContains 'Please Login'

		when:
		go 'index-annotated/'

		then:
		assertContentContains 'Please Login'

		when:
		go 'index-annotated/index'

		then:
		assertContentContains 'Please Login'

		when:
		go 'index-annotated/show'

		then:
		assertContentContains 'Please Login'
	}

	@Issue('https://github.com/grails/grails-spring-security-core/issues/403')
	void 'test controller with annotated index action, authenticated'() {
		when:
		login 'admin'

		then:
		at IndexPage

		when:
		go 'index-annotated'

		then:
		assertContentContains 'index action, principal: '

		when:
		go 'index-annotated/'

		then:
		assertContentContains 'index action, principal: '

		when:
		go 'index-annotated/index'

		then:
		assertContentContains 'index action, principal: '

		when:
		go 'index-annotated/show'

		then:
		assertContentContains "Sorry, you're not authorized to view this page."
	}
}
