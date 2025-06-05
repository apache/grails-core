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
import page.requestmap.RequestmapCreatePage
import page.requestmap.RequestmapEditPage
import page.requestmap.RequestmapSearchPage

@Integration
class RequestmapSpec extends AbstractSecuritySpec {

	void testFindAll() {
		when:
		def requestmapSearchPage = browser.to(RequestmapSearchPage)

		then:
		requestmapSearchPage.assertNotSearched()

		when:
		requestmapSearchPage.submit()

		then:
		browser.at(RequestmapSearchPage)
		requestmapSearchPage.assertResults(1, 3, 3)
		assertContentContains('/secure/**')
		assertContentContains('ROLE_ADMIN')
		assertContentContains('/j_spring_security_switch_user')
		assertContentContains('ROLE_RUN_AS')
		assertContentContains('/**')
		assertContentContains('permitAll')
	}

	void testFindByConfigAttribute() {
		when:
		def requestmapSearchPage = browser.to(RequestmapSearchPage).tap {
			configAttribute = 'run'
			submit()
		}

		then:
		browser.at(RequestmapSearchPage)
		requestmapSearchPage.assertResults(1, 1, 1)
		assertContentContains('/j_spring_security_switch_user')
		assertContentContains('ROLE_RUN_AS')
	}

	void testFindByUrl() {
		when:
		def requestmapSearchPage = browser.to(RequestmapSearchPage).tap {
			urlPattern = 'secure'
			submit()
		}

		then:
		browser.at(RequestmapSearchPage)
		requestmapSearchPage.assertResults(1, 1, 1)
		assertContentContains('/secure/**')
		assertContentContains('ROLE_ADMIN')
	}

	void testUniqueUrl() {
		when:
		def requestmapCreatePage = browser.to(RequestmapCreatePage).tap {
			urlPattern = '/secure/**'
			configAttribute = 'ROLE_FOO'
			submit()
		}

		then:
		browser.at(RequestmapCreatePage)
		requestmapCreatePage.assertNotUnique()
	}

	void testCreateAndEdit() {
		given:
		String newPattern = "/foo/${UUID.randomUUID()}"

		// make sure it doesn't exist
		when:
		def requestmapSearchPage = browser.to(RequestmapSearchPage).tap {
			urlPattern = newPattern
			submit()
		}

		then:
		requestmapSearchPage.assertNoResults()

		// create
		when:
		browser.to(RequestmapCreatePage).with {
			urlPattern = newPattern
			configAttribute = 'ROLE_FOO'
			submit()
		}

		then:
		def requestmapEditPage = browser.at(RequestmapEditPage)
		requestmapSearchPage.urlPattern.text == newPattern

		// edit
		when:
		requestmapEditPage.urlPattern = "${newPattern}/new"
		requestmapEditPage.submit()

		then:
		browser.at(RequestmapEditPage)
		requestmapEditPage.urlPattern.text == "${newPattern}/new"

		// delete
		when:
		requestmapEditPage.delete()

		then:
		browser.at(RequestmapSearchPage)

		when:
		requestmapSearchPage.urlPattern = "${newPattern}/new"
		requestmapSearchPage.submit()

		then:
		browser.at(RequestmapSearchPage)
		requestmapSearchPage.assertNoResults()
	}
}
