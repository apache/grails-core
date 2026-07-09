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

import page.requestmap.RequestmapCreatePage
import page.requestmap.RequestmapEditPage
import page.requestmap.RequestmapForm
import page.requestmap.RequestmapSearchPage
import spec.SecurityUISpec

import grails.testing.mixin.integration.Integration

@Integration
class RequestmapSpec extends SecurityUISpec {

	void testFindAll() {
		when:
		def page = to(RequestmapSearchPage)

		then:
		page.assertNotSearched()

		when:
		page = page.search()

		then:
		page.assertResults(1, 3, 3)
		with(pageSource) {
			contains('/secure/**')
			contains('ROLE_ADMIN')
			contains('/j_spring_security_switch_user')
			contains('ROLE_RUN_AS')
			contains('/**')
			contains('permitAll')
		}
	}

	void testFindByConfigAttribute() {
		when:
		def page = to(RequestmapSearchPage).search(
				new RequestmapForm(
						configAttribute: 'run'
				)
		)

		then:
		page.assertResults(1, 1, 1)
		with(pageSource) {
			contains('/j_spring_security_switch_user')
			contains('ROLE_RUN_AS')
		}
	}

	void testFindByUrl() {
		when:
		def page = to(RequestmapSearchPage).search(
				new RequestmapForm(
						urlPattern: 'secure'
				)
		)

		then:
		page.assertResults(1, 1, 1)
		with(pageSource) {
			contains('/secure/**')
			contains('ROLE_ADMIN')
		}
	}

	void testUniqueUrl() {
		when:
		def page = to(RequestmapCreatePage).submitCreate(
				new RequestmapForm(
						urlPattern: '/secure/**',
						configAttribute: 'ROLE_FOO'
				),
				RequestmapCreatePage
		)

		then:
		page.assertNotUnique()
	}

	void testCreateAndEdit() {
		given:
		def newPattern = "/foo/${UUID.randomUUID()}"

		// make sure it doesn't exist
		when:
		def page = to(RequestmapSearchPage).search(
				new RequestmapForm(
						urlPattern: newPattern
				)
		)

		then:
		page.assertNoResults()

		// create
		when:
		page = to(RequestmapCreatePage).submitCreate(
				new RequestmapForm(
						urlPattern:  newPattern,
						configAttribute:  'ROLE_FOO'
				),
				RequestmapEditPage
		)

		then:
		page.urlPattern.text == newPattern

		// edit
		when:
		page = page.submitEdit(
				new RequestmapForm(
						urlPattern: "$newPattern/new"
				),
				RequestmapEditPage
		)

		then:
		page.urlPattern.text == "$newPattern/new"

		when:
		page = page.submitDelete(RequestmapSearchPage)

		and:
		page = page.search(
				new RequestmapForm(
						urlPattern: "$newPattern/new"
				)
		)

		then:
		page.assertNoResults()
	}
}
