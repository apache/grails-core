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
import page.persistentLogin.PersistentLoginSearchPage

@Integration
class PersistentLoginSpec extends AbstractSecuritySpec {

	void testFindAll() {
		when:
		def persistentLoginSearchPage = browser.to(PersistentLoginSearchPage)

		then:
		persistentLoginSearchPage.assertNotSearched()

		when:
		persistentLoginSearchPage.submit()

		then:
		browser.at(PersistentLoginSearchPage)
		persistentLoginSearchPage.assertResults(1, 10, 20)
	}

	void testFindByUsername() {
		when:
		def persistentLoginSearchPage = browser.to(PersistentLoginSearchPage).tap {
			username = '3'
			submit()
		}

		then:
		browser.at(PersistentLoginSearchPage)
		persistentLoginSearchPage.assertResults(1, 2, 2)

		assertContentContains('persistent_login_test_3')
		assertContentContains('persistent_login_test_13')

		assertContentContains('series3')
		assertContentContains('series13')
	}

	void testFindByToken() {
		when:
		def persistenLoginSearchPage = browser.to(PersistentLoginSearchPage).tap {
			token = '3'
			submit()
		}

		then:
		browser.at(PersistentLoginSearchPage)
		persistenLoginSearchPage.assertResults(1, 2, 2)

		assertContentContains('token13')
		assertContentContains('token3')
	}

	void testFindBySeries() {
		when:
		def persistentLoginSearchPage = browser.to(PersistentLoginSearchPage).tap {
			series = '4'
			submit()
		}

		then:
		browser.at(PersistentLoginSearchPage)
		persistentLoginSearchPage.assertResults(1, 2, 2)

		assertContentContains('series4')
		assertContentContains('series14')
		assertContentContains('persistent_login_test_4')
		assertContentContains('persistent_login_test_14')
	}
}
