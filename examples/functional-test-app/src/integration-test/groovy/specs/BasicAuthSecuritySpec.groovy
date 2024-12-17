package specs

import grails.testing.mixin.integration.Integration
import pages.LoginPage
import pages.role.CreateRolePage
import pages.role.ListRolePage
import pages.role.ShowRolePage
import pages.user.CreateUserPage
import pages.user.ListUserPage
import pages.user.ShowUserPage
import spock.lang.IgnoreIf

@Integration
@IgnoreIf({ System.getProperty('TESTCONFIG') != 'basic' })
class BasicAuthSecuritySpec extends AbstractSecuritySpec {

	private HttpURLConnection connection

	void 'create roles'() {
		when:
		to ListRolePage

		then:
		roleRows.size() == 0

		when:
		newRoleButton.click()

		then:
		at CreateRolePage

		when:
		authority = 'ROLE_ADMIN'
		createButton.click()

		then:
		at ShowRolePage

		when:
		to ListRolePage

		then:
		roleRows.size() == 1

		when:
		newRoleButton.click()

		then:
		at CreateRolePage

		when:
		authority = 'ROLE_ADMIN2'
		createButton.click()

		then:
		at ShowRolePage

		when:
		to ListRolePage

		then:
		roleRows.size() == 2
	}

	void 'create users'() {
		when:
		to ListUserPage

		then:
		userRows.size() == 0

		when:
		newUserButton.click()

		then:
		at CreateUserPage

		when:
		username = 'admin1'
		password = 'password1'
		$('#enabled').click()
		$('#ROLE_ADMIN').click()
		createButton.click()

		then:
		at ShowUserPage

		when:
		to ListUserPage

		then:
		userRows.size() == 1

		when:
		newUserButton.click()

		then:
		at CreateUserPage

		when:
		username = 'admin2'
		password = 'password2'
		$('#enabled').click()
		$('#ROLE_ADMIN').click()
		$('#ROLE_ADMIN2').click()
		createButton.click()

		then:
		at ShowUserPage

		when:
		to ListUserPage

		then:
		userRows.size() == 2
	}

	void 'secured urls not visible without login'() {

		// secureClassAnnotated is Basic auth, everything else is form auth

		when:
		go 'secureAnnotated'

		then:
		at LoginPage

		when:
		go 'secureAnnotated/index'

		then:
		at LoginPage

		when:
		go 'secureAnnotated/adminEither'

		then:
		at LoginPage

		when:
		getWithoutAuth 'secureClassAnnotated'

		then:
		401 == connection.responseCode

		when:
		getWithoutAuth 'secureClassAnnotated/index'

		then:
		401 == connection.responseCode

		when:
		getWithoutAuth 'secureClassAnnotated/otherAction'

		then:
		401 == connection.responseCode

		when:
		getWithoutAuth 'secureClassAnnotated/admin2'

		then:
		401 == connection.responseCode

		when:
		getWithoutAuth 'secureClassAnnotated/admin2.xml'

		then:
		401 == connection.responseCode

		when:
		getWithoutAuth 'secureClassAnnotated/admin2;jsessionid=5514B068198CC7DBF372713326E14C12'

		then:
		401 == connection.responseCode
	}

	void 'check allowed for admin1'() {

		// Check with admin1 auth, some @Secure actions are accessible

		when:
		go 'secureAnnotated'

		then:
		at LoginPage

		when:
		login 'admin1', 'password1'

		then:
		assertContentContains 'you have ROLE_ADMIN'

		when:
		logout()
		go 'secureAnnotated/index'

		then:
		at LoginPage

		when:
		login 'admin1', 'password1'

		then:
		assertContentContains 'you have ROLE_ADMIN'

		when:
		logout()
		go 'secureAnnotated/adminEither'

		then:
		at LoginPage

		when:
		login 'admin1', 'password1'

		then:
		assertContentContains 'you have ROLE_ADMIN'

		when:
		logout()
		getWithAuth 'secureClassAnnotated', 'admin1', 'password1'

		then:
		assertContentContains 'you have ROLE_ADMIN'

		when:
		logout()
		getWithAuth 'secureClassAnnotated/index', 'admin1', 'password1'

		then:
		assertContentContains 'you have ROLE_ADMIN'

		when:
		logout()
		getWithAuth 'secureClassAnnotated/otherAction', 'admin1', 'password1'

		then:
		assertContentContains 'you have ROLE_ADMIN'

		when:
		logout()
		getWithAuth 'secureClassAnnotated/admin2', 'admin1', 'password1'

		then:
		assertContentContains 'Error 403 Forbidden'
	}

	void 'check allowed for admin2'() {

		// Check that with admin2 auth, some @Secure actions are accessible

		when:
		go 'secureAnnotated'

		then:
		at LoginPage

		when:
		login 'admin2', 'password2'

		then:
		assertContentContains 'you have ROLE_ADMIN'

		when:
		logout()
		go 'secureAnnotated/index'

		then:
		at LoginPage

		when:
		login 'admin2', 'password2'

		then:
		assertContentContains 'you have ROLE_ADMIN'

		when:
		logout()
		go 'secureAnnotated/adminEither'

		then:
		at LoginPage

		when:
		login 'admin2', 'password2'

		then:
		assertContentContains 'you have ROLE_ADMIN'

		when:
		logout()
		getWithAuth 'secureClassAnnotated', 'admin2', 'password2'

		then:
		assertContentContains 'you have ROLE_ADMIN'

		when:
		logout()
		getWithAuth 'secureClassAnnotated/index', 'admin2', 'password2'

		then:
		assertContentContains 'you have ROLE_ADMIN'

		when:
		logout()
		getWithAuth 'secureClassAnnotated/otherAction', 'admin2', 'password2'

		then:
		assertContentContains 'you have ROLE_ADMIN'

		when:
		logout()
		getWithAuth 'secureClassAnnotated/admin2', 'admin2', 'password2'

		then:
		assertContentContains 'you have ROLE_ADMIN'
	}

	protected void logout() {
		super.logout()
		// cheesy, but the 'Authentication' header from basic auth
		// isn't cleared, so this forces an invalid header
		getWithAuth '', 'not_a_valid_username', ''
	}

	private void getWithAuth(String path, String username, String password) {
		String uri = new URI(baseUrlRequired).resolve(new URI(path))
		go uri.replace('http://', 'http://' + username + ':' + password + '@')
	}

	private void getWithoutAuth(String uri) {
		connection = download("/${uri}")
	}
}
