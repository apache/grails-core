import grails.testing.mixin.integration.Integration
import pages.IndexPage
import pages.SecureAdminPage
import pages.SecureFooBarPage
import pages.SecureSuperuserPage
import pages.SecureUserPage

@Integration
class Person1FunctionalSpec extends AbstractSecurityFunctionalSpec {

	// person1 has ROLE_USER and ROLE_FOO_BAR (from group "foo bar") from LDAP

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
		login 'person1', 'password1'

		then:
		at IndexPage

		when:
		to SecureUserPage

		then:
		assertContentContains 'ROLE_USER'
		assertContentContains 'ROLE_FOO_BAR'
		assertContentDoesNotContain 'ROLE_ADMIN'
		assertContentDoesNotContain 'ROLE_SUPERUSER'

		when:
		to SecureFooBarPage

		then:
		assertContentContains 'ROLE_USER'
		assertContentContains 'ROLE_FOO_BAR'
		assertContentDoesNotContain 'ROLE_ADMIN'
		assertContentDoesNotContain 'ROLE_SUPERUSER'

		when:
		to SecureAdminPage

		then:
		assertContentContains "Sorry, you're not authorized to view this page."

		when:
		to SecureSuperuserPage

		then:
		assertContentContains "Sorry, you're not authorized to view this page."
	}
}
