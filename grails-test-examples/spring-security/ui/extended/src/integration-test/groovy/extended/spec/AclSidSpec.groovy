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

import page.aclSid.AclSidCreatePage
import page.aclSid.AclSidEditPage
import page.aclSid.AclSidForm
import page.aclSid.AclSidSearchPage
import spec.SecurityUISpec

import grails.testing.mixin.integration.Integration

@Integration
class AclSidSpec extends SecurityUISpec {

	void testFindAll() {
		when:
		def page = to(AclSidSearchPage)

		then:
		page.assertNotSearched()

		when:
		page.search()

		then:
		page.assertResults(1, 3, 3)
	}

	void testFindBySid() {
		when:
		def page = to(AclSidSearchPage).search(
				new AclSidSearchPage.Form(
						sid: 'user'
				)
		)

		then:
		page.assertResults(1, 2, 2)
		with(pageSource) {
			contains('user1')
			contains('user2')
		}
	}

	void testFindByPrincipal() {
		when:
		to(AclSidSearchPage).search(
				new AclSidSearchPage.Form(
						principal: AclSidSearchPage.Form.Principal.TRUE
				)
		)

		then:
		with(pageSource) {
			contains('user1')
			contains('user2')
			contains('admin')
		}
	}

	void testUniqueName() {
		when:
		to(AclSidCreatePage).submitCreate(
				new AclSidForm(
						sid: 'user1',
						principal: true
				),
				AclSidCreatePage
		)

		then:
		pageSource.contains('must be unique')
	}

	void testCreateAndEdit() {
		given:
		def newName = "newuser${UUID.randomUUID()}"

		// make sure it doesn't exist
		when:
		def page = to(AclSidSearchPage).search(
				new AclSidSearchPage.Form(
						sid: newName
				)
		)

		then:
		page.assertNoResults()

		// create
		when:
		page = to(AclSidCreatePage).submitCreate(
				new AclSidForm(
						sid: newName,
						principal: true
				),
				AclSidEditPage
		)

		then:
		with(page) {
			sid.text == newName
			principal.checked
		}

		// edit
		when:
		page = page.submitEdit(
				new AclSidForm(
						sid: "${newName}_new"
				),
				AclSidEditPage
		)

		then:
		page.sid.text == "${newName}_new"

		// delete
		when:
		page = page.submitDelete(AclSidSearchPage)

		and:
		page = page.search(new AclSidSearchPage.Form(
				sid: "${newName}_new"
		))

		then:
		page.assertNoResults()
	}
}
