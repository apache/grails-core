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

package spec

import grails.testing.mixin.integration.Integration
import page.user.UserCreatePage
import page.user.UserEditPage
import page.user.UserSearchPage

@Integration
class UserSpec extends AbstractSecuritySpec {

	void testFindAll() {
		when:
		def userSearchPage = browser.to(UserSearchPage)

		then:
		userSearchPage.assertNotSearched()

		when:
		userSearchPage.submit()

		then:
		browser.at(UserSearchPage)
		userSearchPage.assertResults(1, 10, 22)
	}

	void testFindByUsername() {
		when:
		def userSearchPage = browser.to(UserSearchPage).tap {
			username = 'foo'
			submit()
		}

		then:
		browser.at(UserSearchPage)
		userSearchPage.assertResults(1, 3, 3)

		assertContentContains('foon_2')
		assertContentContains('foolkiller')
		assertContentContains('foostra')
	}

	void testFindByDisabled() {
		when:
		def userSearchPage = browser.to(UserSearchPage)

		// Temporary workaround for problem with Geb RadioButtons module
		//userSearchPage.enabled.checked = '-1'
		browser.$('input', type: 'radio', name: 'enabled', value: '-1').click()
		userSearchPage.submit()

		then:
		browser.at(UserSearchPage)
		userSearchPage.assertResults(1, 1, 1)
		assertContentContains('billy9494')
	}

	void testFindByAccountExpired() {
		when:
		def userSearchPage = browser.to(UserSearchPage)

		// Temporary workaround for problem with Geb RadioButtons module
		//userSearchPage.accountExpired.checked = '1'
		browser.$('input', type: 'radio', name: 'accountExpired', value: '1').click()
		userSearchPage.submit()

		then:
		browser.at(UserSearchPage)
		userSearchPage.assertResults(1, 3, 3)
		assertContentContains('maryrose')
		assertContentContains('ratuig')
		assertContentContains('rome20c')
	}

	void testFindByAccountLocked() {
		when:
		def userSearchPage = browser.to(UserSearchPage)

		// Temporary workaround for problem with Geb RadioButtons module
		//userSearchPage.accountLocked.checked = '1'
		browser.$('input', type: 'radio', name: 'accountLocked', value: '1').click()
		userSearchPage.submit()

		then:
		browser.at(UserSearchPage)
		userSearchPage.assertResults(1, 3, 3)
		assertContentContains('aaaaaasd')
		assertContentContains('achen')
		assertContentContains('szhang1999')
	}

	void testFindByPasswordExpired() {
		when:
		def userSearchPage = browser.to(UserSearchPage)

		// Temporary workaround for problem with Geb RadioButtons module
		//userSearchPage.passwordExpired.checked = '1'
		browser.$('input', type: 'radio', name: 'passwordExpired', value: '1').click()
		userSearchPage.submit()

		then:
		browser.at(UserSearchPage)
		userSearchPage.assertResults(1, 3, 3)
		assertContentContains('hhheeeaaatt')
		assertContentContains('mscanio')
		assertContentContains('kittal')
	}

	void testCreateAndEdit() {
		given:
		String newUsername = "newuser${UUID.randomUUID()}"

		// make sure it doesn't exist
		when:
		def userSearchPage = browser.to(UserSearchPage).tap {
			username = newUsername
			submit()
		}

		then:
		browser.at(UserSearchPage)
		userSearchPage.assertNoResults()

		// create
		when:
		browser.to(UserCreatePage).with {
			username = newUsername
			password = 'password'
			enabled.check()
			submit()
		}


		then:
		def userEditPage = browser.at(UserEditPage)
		userEditPage.username.text == newUsername
		userEditPage.enabled.checked
		!userEditPage.accountExpired.checked
		!userEditPage.accountLocked.checked
		!userEditPage.passwordExpired.checked

		// edit
		when:
		String updatedName = "${newUsername}_updated"
		userEditPage.with {
			username = updatedName
			enabled.uncheck()
			accountExpired.check()
			accountLocked.check()
			passwordExpired.check()
			submit()
		}

		then:
		browser.at(UserEditPage)
		userEditPage.username.text == updatedName
		!userEditPage.enabled.checked
		userEditPage.accountExpired.checked
		userEditPage.accountLocked.checked
		userEditPage.passwordExpired.checked

		// delete
		when:
		userEditPage.delete()

		then:
		browser.at(UserSearchPage)

		when:
		userSearchPage.username = updatedName
		userSearchPage.submit()

		then:
		browser.at(UserSearchPage)
		userSearchPage.assertNoResults()
	}
}
