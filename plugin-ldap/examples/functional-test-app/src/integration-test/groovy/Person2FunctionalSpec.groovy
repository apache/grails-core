import grails.testing.mixin.integration.Integration
import pages.IndexPage
import pages.SecureAdminPage
import pages.SecureFooBarPage
import pages.SecureSuperuserPage
import pages.SecureUserPage

@Integration
class Person2FunctionalSpec extends AbstractSecurityFunctionalSpec {

	// person2 has ROLE_USER and ROLE_ADMIN from LDAP

	void 'secured urls are not visible without auth'() {
		when:
		to SecureAdminPage

		then:
		assertContentContains 'Please Login'

		when:
		to SecureUserPage

		then:
		assertContentContains 'Please Login'

		when:
		to SecureSuperuserPage

		then:
		assertContentContains 'Please Login'

		when:
		to SecureFooBarPage

		then:
		assertContentContains 'Please Login'
	}

	void 'secured urls are visible when authenticated'() {
		when:
		login 'person2', 'password2'

		then:
		at IndexPage

		when:
		to SecureAdminPage

		then:
		assertContentContains 'ROLE_USER'
		assertContentContains 'ROLE_ADMIN'
		assertContentDoesNotContain 'ROLE_SUPERUSER'
		assertContentDoesNotContain 'ROLE_FOO_BAR'

		when:
		to SecureUserPage

		then:
		assertContentContains 'ROLE_USER'
		assertContentContains 'ROLE_ADMIN'
		assertContentDoesNotContain 'ROLE_SUPERUSER'
		assertContentDoesNotContain 'ROLE_FOO_BAR'

		when:
		to SecureSuperuserPage

		then:
		assertContentContains "Sorry, you're not authorized to view this page."
	}
}
