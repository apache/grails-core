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

import grails.testing.mixin.integration.Integration
import org.springframework.security.core.userdetails.UserCache
import pages.LoginPage
import pages.role.CreateRolePage
import pages.role.ListRolePage
import pages.role.ShowRolePage
import pages.user.CreateUserPage
import pages.user.ListUserPage
import pages.user.ShowUserPage
import spock.lang.IgnoreIf

@Integration
@IgnoreIf({ System.getProperty('TESTCONFIG') != 'basicCacheUsers' })
class BasicAuthCacheUsersSecuritySpec extends AbstractSecuritySpec {

	private HttpURLConnection connection
	UserCache userCache

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

	void 'check userDetails caching'() {

		when:
		go 'secureAnnotated'

		then:
		at LoginPage

		when:
		login 'admin1', 'password1'

		then:
		assertContentContains 'you have ROLE_ADMIN'

		and:
		userCache.getUserFromCache('admin1')

		cleanup:
		logout()
	}

	protected void logout() {
		super.logout()
		// cheesy, but the 'Authentication' header from basic auth
		// isn't cleared, so this forces an invalid header
		getWithAuth '', 'not_a_valid_username', ''
	}

	private void getWithAuth(String path, String username, String password) {
		String uri = new URI(browser.baseUrl).resolve(new URI(path))
		go uri.replace('http://', 'http://' + username + ':' + password + '@')
	}
}
