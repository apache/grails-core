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

import spock.lang.Stepwise

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
@Stepwise
@IgnoreIf({ System.getProperty('TESTCONFIG') != 'basic' })
class BasicAuthSecuritySpec extends AbstractSecuritySpec {

	private HttpURLConnection connection

	void 'create roles'() {
		when:
		def listRolePage = to(ListRolePage)

		then:
		listRolePage.roleRows.size() == 0

		when:
		listRolePage.newRoleButton.click()

		then:
		at(CreateRolePage)

		when:
		authority = 'ROLE_ADMIN'
		createButton.click()

		then:
		at(ShowRolePage)

		when:
		listRolePage = to(ListRolePage)

		then:
		listRolePage.roleRows.size() == 1

		when:
		listRolePage.newRoleButton.click()

		then:
		at(CreateRolePage)

		when:
		authority = 'ROLE_ADMIN2'
		createButton.click()

		then:
		at(ShowRolePage)

		when:
		listRolePage = to(ListRolePage)

		then:
		listRolePage.roleRows.size() == 2
	}

	void 'create users'() {

		when:
		def listUserPage = to(ListUserPage)

		then:
		listUserPage.userRows.size() == 0

		when:
		listUserPage.newUserButton.click()

		then:
		at(CreateUserPage)

		when:
		username = 'admin1'
		password = 'password1'
		$('#enabled').click()
		$('#ROLE_ADMIN').click()
		createButton.click()

		then:
		at(ShowUserPage)

		when:
		listUserPage = to(ListUserPage)

		then:
		listUserPage.userRows.size() == 1

		when:
		listUserPage.newUserButton.click()

		then:
		at(CreateUserPage)

		when:
		username = 'admin2'
		password = 'password2'
		$('#enabled').click()
		$('#ROLE_ADMIN').click()
		$('#ROLE_ADMIN2').click()
		createButton.click()

		then:
		at(ShowUserPage)

		when:
		listUserPage = to(ListUserPage)

		then:
		listUserPage.userRows.size() == 2
	}

	void 'secured urls not visible without login'() {

		// secureClassAnnotated is Basic auth, everything else is form auth

		when:
		go('secureAnnotated')

		then:
		at(LoginPage)

		when:
		go('secureAnnotated/index')

		then:
		at(LoginPage)

		when:
		go('secureAnnotated/adminEither')

		then:
		at(LoginPage)

		when:
		getWithoutAuth('secureClassAnnotated')

		then:
		401 == connection.responseCode

		when:
		getWithoutAuth('secureClassAnnotated/index')

		then:
		401 == connection.responseCode

		when:
		getWithoutAuth('secureClassAnnotated/otherAction')

		then:
		401 == connection.responseCode

		when:
		getWithoutAuth('secureClassAnnotated/admin2')

		then:
		401 == connection.responseCode

		when:
		getWithoutAuth('secureClassAnnotated/admin2.xml')

		then:
		401 == connection.responseCode

		when:
		getWithoutAuth('secureClassAnnotated/admin2;jsessionid=5514B068198CC7DBF372713326E14C12')

		then:
		401 == connection.responseCode
	}

	void 'check allowed for admin1'() {

		// Check with admin1 auth, some @Secure actions are accessible

		when:
		go('secureAnnotated')

		then:
		at(LoginPage)

		when:
		login('admin1', 'password1')

		then:
		pageSource.contains('you have ROLE_ADMIN')

		when:
		logout()
		go('secureAnnotated/index')

		then:
		at(LoginPage)

		when:
		login('admin1', 'password1')

		then:
		pageSource.contains('you have ROLE_ADMIN')

		when:
		logout()
		go('secureAnnotated/adminEither')

		then:
		at(LoginPage)

		when:
		login('admin1', 'password1')

		then:
		pageSource.contains('you have ROLE_ADMIN')

		when:
		logout()
		getWithAuth('secureClassAnnotated', 'admin1', 'password1')

		then:
		pageSource.contains('you have ROLE_ADMIN')

		when:
		logout()
		getWithAuth('secureClassAnnotated/index', 'admin1', 'password1')

		then:
		pageSource.contains('you have ROLE_ADMIN')

		when:
		logout()
		getWithAuth('secureClassAnnotated/otherAction', 'admin1', 'password1')

		then:
		pageSource.contains('you have ROLE_ADMIN')

		when:
		logout()
		getWithAuth('secureClassAnnotated/admin2', 'admin1', 'password1')

		then:
		pageSource.contains('Error 403 Forbidden')
	}

	void 'check allowed for admin2'() {

		// Check that with admin2 auth, some @Secure actions are accessible

		when:
		go('secureAnnotated')

		then:
		at(LoginPage)

		when:
		login('admin2', 'password2')

		then:
		pageSource.contains('you have ROLE_ADMIN')

		when:
		logout()
		go('secureAnnotated/index')

		then:
		at(LoginPage)

		when:
		login('admin2', 'password2')

		then:
		pageSource.contains('you have ROLE_ADMIN')

		when:
		logout()
		go('secureAnnotated/adminEither')

		then:
		at(LoginPage)

		when:
		login('admin2', 'password2')

		then:
		pageSource.contains('you have ROLE_ADMIN')

		when:
		logout()
		getWithAuth('secureClassAnnotated', 'admin2', 'password2')

		then:
		pageSource.contains('you have ROLE_ADMIN')

		when:
		logout()
		getWithAuth('secureClassAnnotated/index', 'admin2', 'password2')

		then:
		pageSource.contains('you have ROLE_ADMIN')

		when:
		logout()
		getWithAuth('secureClassAnnotated/otherAction', 'admin2', 'password2')

		then:
		pageSource.contains('you have ROLE_ADMIN')

		when:
		logout()
		getWithAuth('secureClassAnnotated/admin2', 'admin2', 'password2')

		then:
		pageSource.contains('you have ROLE_ADMIN')
	}

	protected void logout() {
		super.logout()
		// cheesy, but the 'Authentication' header from basic auth
		// isn't cleared, so this forces an invalid header
		getWithAuth('', 'not_a_valid_username', '')
	}

	private void getWithAuth(String path, String username, String password) {
		String uri = new URI(browser.baseUrl).resolve(new URI(path))
		go(uri.replace('http://', "http://$username:$password@"))
	}

	private void getWithoutAuth(String uri) {
		connection = download("/$uri")
	}
}
