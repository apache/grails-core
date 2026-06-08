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
package simple.spec

import page.HomePage
import page.registrationCode.RegistrationCodeEditPage
import page.registrationCode.RegistrationCodeForm
import page.registrationCode.RegistrationCodeSearchPage
import spec.SecurityUISpec
import spock.lang.Stepwise

import grails.testing.mixin.integration.Integration

@Stepwise
@Integration
class RegistrationCodeSpec extends SecurityUISpec {

	void testFindAll() {
		when:
		def page = to(RegistrationCodeSearchPage)

		then:
		page.assertNotSearched()

		when:
		page.search()

		then:
		with(pageSource) {
			contains('Showing 1 through 10 out of 14.')
			contains('registration_test_2')
			contains('0a154624f36d42e4aa68991a9477bd04')
		}
	}

	void testFindByToken() {
		when:
		def page = to(RegistrationCodeSearchPage).search(
				new RegistrationCodeForm(
						token: '4a7f88afec3746f7aab2f5d0d8df6d8e'
				)
		)

		then:
		page.assertResults(1, 1, 1)
		with(pageSource) {
			contains('registration_test_1')
			contains('4a7f88afec3746f7aab2f5d0d8df6d8e')
		}
	}

	void testFindByUsername() {
		when:
		def page = to(RegistrationCodeSearchPage).search(
				new RegistrationCodeForm(
						username: 'registration_test_3'
				)
		)

		then:
		page.assertResults(1, 5, 5)
		with(pageSource) {
			contains('registration_test_3')
			contains('89f9bbc658b14808ae4c77c6e17e551a')
		}
	}

	void testEdit() {
		when:
		def page = to(RegistrationCodeEditPage, 4)

		then:
		with(page) {
			username.text == 'registration_test_1'
			token.text == 'a50e061e0e2f424fb7fbc2ff3dae597d'
		}

		when:
		page.submitEdit(
				new RegistrationCodeForm(
						username: 'new_user',
						token: 'new_token'
				),
				RegistrationCodeEditPage
		)

		and: 'visit another page to ensure the edit was saved and not just in the form'
		to(HomePage)

		and:
		page = to(RegistrationCodeEditPage, 4)

		then:
		page.username.text == 'new_user'
		page.token.text == 'new_token'
	}
}
