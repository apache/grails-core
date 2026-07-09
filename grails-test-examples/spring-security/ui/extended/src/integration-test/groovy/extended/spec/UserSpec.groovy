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
package extended.spec

import page.HomePage
import page.user.UserCreatePage
import page.user.UserEditPage
import page.user.UserForm
import page.user.UserSearchPage
import spec.SecurityUISpec
import spock.lang.Stepwise

import grails.testing.mixin.integration.Integration

@Stepwise
@Integration
class UserSpec extends SecurityUISpec {

	void testFindAll() {
		when:
		def page = to(UserSearchPage)

		then:
		page.assertNotSearched()

		when:
		page = page.search()

		then:
		page.assertResults(1, 10, 22)
	}

	void testFindByUsername() {
		when:
		def page = to(UserSearchPage).search(
				new UserSearchPage.Form(
						username: 'foo'
				)
		)

		then:
		page.assertResults(1, 3, 3)
		with(pageSource) {
			contains('foon_2')
			contains('foolkiller')
			contains('foostra')
		}
	}

	void testFindByDisabled() {
		when:
		def page = to(UserSearchPage)

		page = page.search(
				new UserSearchPage.Form(
						accountEnabled: UserSearchPage.Form.AccountEnabled.FALSE
				)
		)

		then:
		page.assertResults(1, 1, 1)
		pageSource.contains('billy9494')
	}

	void testFindByAccountExpired() {
		when:
		def page = to(UserSearchPage)

		page = page.search(
				new UserSearchPage.Form(
						accountExpired: UserSearchPage.Form.AccountExpired.TRUE
				)
		)

		then:
		page.assertResults(1, 3, 3)
		with(pageSource) {
			contains('maryrose')
			contains('ratuig')
			contains('rome20c')
		}
	}

	void testFindByAccountLocked() {
		when:
		def page = to(UserSearchPage)

		page = page.search(
				new UserSearchPage.Form(
						accountLocked: UserSearchPage.Form.AccountLocked.TRUE
				)
		)

		then:
		page.assertResults(1, 3, 3)
		with(pageSource) {
			contains('aaaaaasd')
			contains('achen')
			contains('szhang1999')
		}
	}

	void testFindByPasswordExpired() {
		when:
		def page = to(UserSearchPage)

		page = page.search(
				new UserSearchPage.Form(
						passwordExpired: UserSearchPage.Form.PasswordExpired.TRUE
				)
		)

		then:
		page.assertResults(1, 3, 3)
		with(pageSource) {
			contains('hhheeeaaatt')
			contains('mscanio')
			contains('kittal')
		}
	}

	void testCreateAndEdit() {
		given:
		def newUsername = "newuser${UUID.randomUUID()}"

		// make sure it doesn't exist
		when:
		def page = to(UserSearchPage).search(
				new UserSearchPage.Form(
						username: newUsername
				)
		)

		then:
		page.assertNoResults()

		// create
		when:
		page = to(UserCreatePage).submitCreate(
				new UserForm(
						username: newUsername,
						password: 'password',
						enabled: true
				),
				UserEditPage
		)

		then:
		with(page) {
			username.text == newUsername
			enabled.checked
			!accountExpired.checked
			!accountLocked.checked
			!passwordExpired.checked
		}

		// edit
		when:
		def updatedName = "${newUsername}_updated"
		page = page.submitEdit(
				new UserForm(
						username: updatedName,
						enabled: false,
						accountExpired: true,
						accountLocked: true,
						passwordExpired: true
				),
				UserEditPage
		)
		def userId = page.userId

		and: 'visit another page so the edit page can be verified properly after submit'
		to(HomePage)

		and:
		page = to(UserEditPage, userId)

		then:
		with(page) {
			username.text == updatedName
			!enabled.checked
			accountExpired.checked
			accountLocked.checked
			passwordExpired.checked
		}

		// delete
		when:
		page = page.submitDelete(UserSearchPage)

		and:
		page = page.search(
				new UserSearchPage.Form(
						username: updatedName
				)
		)

		then:
		page.assertNoResults()
	}
}
