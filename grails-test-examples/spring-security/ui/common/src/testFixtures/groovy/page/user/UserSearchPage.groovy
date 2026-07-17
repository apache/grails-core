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
package page.user

import groovy.transform.Immutable

import geb.Page
import geb.module.RadioButtons
import geb.module.TextInput
import page.SearchPage

class UserSearchPage extends SearchPage {

	static url = 'user/search'
	static typeName = { 'User' }
	static content = {
		username { $('#username').module(TextInput) }
		enabled { $(name: 'enabled').module(RadioButtons) }
		accountExpired { $(name: 'accountExpired').module(RadioButtons) }
		accountLocked { $(name: 'accountLocked').module(RadioButtons) }
		passwordExpired { $(name: 'passwordExpired').module(RadioButtons) }
	}

	UserSearchPage search(Form form = null) {
		form?.applyTo(this)
		submit(UserSearchPage)
	}

	@Immutable
	static class Form {

		String username
		AccountEnabled accountEnabled
		AccountExpired accountExpired
		AccountLocked accountLocked
		PasswordExpired passwordExpired

		<P extends Page> void applyTo(P page) {
			if (username != null) page.$('#username').module(TextInput).text = username
			if (accountEnabled != null) {
				// Temporary workaround for problem with Geb RadioButtons module
				//page.enabled.checked = accountEnabled.value
				page.$('input', type: 'radio', name: 'enabled', value: accountEnabled.value).click()
			}
			if (accountExpired != null) {
				// Temporary workaround for problem with Geb RadioButtons module
				//page.accountExpired.checked = accountExpired.value
				page.$('input', type: 'radio', name: 'accountExpired', value: accountExpired.value).click()
			}
			if (accountLocked != null) {
				// Temporary workaround for problem with Geb RadioButtons module
				//page.accountLocked.checked = accountLocked.value
				page.$('input', type: 'radio', name: 'accountLocked', value: accountLocked.value).click()
			}
			if (passwordExpired != null) {
				// Temporary workaround for problem with Geb RadioButtons module
				//page.passwordExpired.checked = accountLocked.value
				page.$('input', type: 'radio', name: 'passwordExpired', value: passwordExpired.value).click()
			}
		}

		enum AccountEnabled {

			TRUE('1'),
			FALSE('-1'),
			EITHER('0')

			final String value

			private AccountEnabled(String value) {
				this.value = value
			}
		}

		enum AccountExpired {

			TRUE('1'),
			FALSE('-1'),
			EITHER('0')

			final String value

			private AccountExpired(String value) {
				this.value = value
			}
		}

		enum AccountLocked {

			TRUE('1'),
			FALSE('-1'),
			EITHER('0')

			final String value

			private AccountLocked(String value) {
				this.value = value
			}
		}

		enum PasswordExpired {

			TRUE('1'),
			FALSE('-1'),
			EITHER('0')

			final String value

			private PasswordExpired(String value) {
				this.value = value
			}
		}
	}
}
