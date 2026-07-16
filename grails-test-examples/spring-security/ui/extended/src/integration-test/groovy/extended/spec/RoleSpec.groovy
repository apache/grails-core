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

import page.role.RoleCreatePage
import page.role.RoleEditPage
import page.role.RoleForm
import page.role.RoleSearchPage
import spec.SecurityUISpec

import grails.testing.mixin.integration.Integration

@Integration
class RoleSpec extends SecurityUISpec {

	void testFindAll() {
		when:
		def page = to(RoleSearchPage)

		then:
		page.assertNotSearched()

		when:
		page = page.search()

		then:
		page.assertResults(1, 10, 12)
		pageSource.contains('ROLE_COFFEE')
	}

	void testFindByAuthority() {
		when:
		def page = to(RoleSearchPage).search(
				new RoleForm(
						authority: 'ad'
				)
		)

		then:
		page.assertResults(1, 2, 2)
		with(pageSource) {
			contains('ROLE_ADMIN')
			contains('ROLE_INSTEAD')
		}
	}

	void testUniqueName() {
		when:
		to(RoleCreatePage).submitCreate(
				new RoleForm(
						authority: 'ROLE_ADMIN'
				),
				RoleCreatePage
		)

		then:
		pageSource.contains('must be unique')
	}

	void testCreateAndEdit() {
		given:
		def newName = "ROLE_NEW_TEST${UUID.randomUUID()}"

		// make sure it doesn't exist
		when:
		def page = to(RoleSearchPage).search(
				new RoleForm(
						authority: newName
				)
		)

		then:
		page.assertNoResults()

		// create
		when:
		page = to(RoleCreatePage).submitCreate(
				new RoleForm(
						authority: newName
				),
				RoleEditPage
		)

		then:
		page.authority.text == newName

		// edit
		when:
		page = page.submitEdit(
				new RoleForm(
						authority: "${newName}_new"
				),
				RoleEditPage
		)

		then:
		page.authority.text == "${newName}_new"

		// delete
		when:
		page = page.submitDelete(RoleSearchPage)

		and:
		page = page.search(
				new RoleForm(
						authority: "${newName}_new"
				)
		)

		then:
		page.assertNoResults()
	}
}
