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

import spec.SecurityUISpec

import grails.testing.mixin.integration.Integration
import page.persistentLogin.PersistentLoginSearchPage

@Integration
class PersistentLoginSpec extends SecurityUISpec {

	void testFindAll() {
		when:
		def page = to(PersistentLoginSearchPage)

		then:
		page.assertNotSearched()

		when:
		page = page.search()

		then:
		page.assertResults(1, 10, 20)
	}

	void testFindByUsername() {
		when:
		def page = to(PersistentLoginSearchPage).search(
				new PersistentLoginSearchPage.Form(
						username: '3'
				)
		)

		then:
		page.assertResults(1, 2, 2)
		with(pageSource) {
			contains('persistent_login_test_3')
			contains('persistent_login_test_13')
			contains('series3')
			contains('series13')
		}
	}

	void testFindByToken() {
		when:
		def page = to(PersistentLoginSearchPage).search(
				new PersistentLoginSearchPage.Form(
						token: '3'
				)
		)

		then:
		page.assertResults(1, 2, 2)
		with(pageSource) {
			contains('token13')
			contains('token3')
		}
	}

	void testFindBySeries() {
		when:
		def page = to(PersistentLoginSearchPage).search(
				new PersistentLoginSearchPage.Form(
						series: '4'
				)
		)

		then:
		page.assertResults(1, 2, 2)
		with(pageSource) {
			contains('series4')
			contains('series14')
			contains('persistent_login_test_4')
			contains('persistent_login_test_14')
		}
	}
}
