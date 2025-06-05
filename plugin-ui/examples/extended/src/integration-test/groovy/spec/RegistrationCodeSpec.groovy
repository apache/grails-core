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
import page.registrationCode.RegistrationCodeEditPage
import page.registrationCode.RegistrationCodeSearchPage

@Integration
class RegistrationCodeSpec extends AbstractSecuritySpec {

	void testFindAll() {
		when:
		def registrationCodeSearchPage = browser.to(RegistrationCodeSearchPage)

		then:
		registrationCodeSearchPage.assertNotSearched()

		when:
		registrationCodeSearchPage.submit()

		then:
		browser.at(RegistrationCodeSearchPage)
		registrationCodeSearchPage.assertResults(1, 10, 14)
		assertContentContains('registration_test_2')
		assertContentContains('0a154624f36d42e4aa68991a9477bd04')
	}

	void testFindByToken() {
		when:
		def registrationCodeSearchPage = browser.to(RegistrationCodeSearchPage).tap {
			token = '4a7f88afec3746f7aab2f5d0d8df6d8e'
			submit()
		}

		then:
		browser.at(RegistrationCodeSearchPage)
		registrationCodeSearchPage.assertResults(1, 1, 1)
		assertContentContains('registration_test_1')
		assertContentContains('4a7f88afec3746f7aab2f5d0d8df6d8e')
	}

	void testFindByUsername() {
		when:
		def registrationCodeSearchPage = browser.to(RegistrationCodeSearchPage).tap {
			username = 'registration_test_3'
			submit()
		}

		then:
		browser.at(RegistrationCodeSearchPage)
		registrationCodeSearchPage.assertResults(1, 5, 5)
		assertContentContains('registration_test_3')
		assertContentContains('89f9bbc658b14808ae4c77c6e17e551a')
	}

	void testEdit() {
		when:
		browser.go('registrationCode/edit/4')

		then:
		def registrationCodeEditPage = browser.at(RegistrationCodeEditPage)
		registrationCodeEditPage.username.text == 'registration_test_1'
		registrationCodeEditPage.token.text == 'a50e061e0e2f424fb7fbc2ff3dae597d'

		when:
		registrationCodeEditPage.with {
			username = 'new_user'
			token = 'new_token'
			submit()
		}

		then:
		browser.at(RegistrationCodeEditPage)
		registrationCodeEditPage.username.text == 'new_user'
		registrationCodeEditPage.token.text == 'new_token'

		when:
		browser.go('registrationCode/edit/4')

		then:
		browser.at(RegistrationCodeEditPage)
		registrationCodeEditPage.username.text == 'new_user'
		registrationCodeEditPage.token.text == 'new_token'
	}
}
