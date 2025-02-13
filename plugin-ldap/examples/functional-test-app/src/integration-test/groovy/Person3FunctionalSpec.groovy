import grails.testing.mixin.integration.Integration
import pages.IndexPage
import pages.SecureAdminPage
import pages.SecureFooBarPage
import pages.SecureSuperuserPage
import pages.SecureUserPage

@Integration
class Person3FunctionalSpec extends AbstractSecurityFunctionalSpec {

	// person3 has ROLE_USER from LDAP and ROLE_SUPERUSER from database

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
		login 'person3', 'password3'

		then:
		at IndexPage

		when:
		to SecureSuperuserPage

		then:
		assertContentContains 'ROLE_USER'
		assertContentContains 'ROLE_SUPERUSER'
		assertContentDoesNotContain 'ROLE_ADMIN'
		assertContentDoesNotContain 'ROLE_FOO_BAR'

		when:
		to SecureUserPage

		then:
		assertContentContains 'ROLE_USER'
		assertContentContains 'ROLE_SUPERUSER'
		assertContentDoesNotContain 'ROLE_ADMIN'
		assertContentDoesNotContain 'ROLE_FOO_BAR'

		when:
		to SecureAdminPage

		then:
		assertContentContains "Sorry, you're not authorized to view this page."
	}
}
