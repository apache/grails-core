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

import page.aclEntry.AclEntryCreatePage
import page.aclEntry.AclEntryEditPage
import page.aclEntry.AclEntryForm
import page.aclEntry.AclEntrySearchPage
import spec.SecurityUISpec

import grails.testing.mixin.integration.Integration

@Integration
class AclEntrySpec extends SecurityUISpec {

	void testFindAll() {
		when:
		def page = to(AclEntrySearchPage)

		then:
		page.assertNotSearched()

		when:
		page = page.search()

		then:
		page.assertResults(1, 10, 275)
	}

	void testFindByOid() {
		when:
		def page = to(AclEntrySearchPage).search(
				new AclEntrySearchPage.Form(
						aclObjectIdentityId: 60
				)
		)

		then:
		page.assertResults(1, 3, 3)
			pageSource.contains('60')
			pageSource.contains('62')
			pageSource.contains('194')
			pageSource.contains('195')
			pageSource.contains('user1')
			pageSource.contains('admin')
			pageSource.contains('BasePermission[...............................R=1]')
			pageSource.contains('BasePermission[...........................A....=16]')
			!pageSource.contains('>user2</a>')

	}

	void testFindByAceOrder() {
		when:
		def page = to(AclEntrySearchPage).search(
				new AclEntrySearchPage.Form(
						aceOrder: 2
				)
		)

		then:
		page.assertResults(1, 10, 67)
			pageSource.contains('75')
			pageSource.contains('76')
			pageSource.contains('78')
			pageSource.contains('80')
			pageSource.contains('82')
			pageSource.contains('87')
			pageSource.contains('89')
			pageSource.contains('91')
			pageSource.contains('93')
			pageSource.contains('95')

	}

	void testFindByMask() {
		when:
		def page = to(AclEntrySearchPage).search(
				new AclEntrySearchPage.Form(
						mask: 1
				)
		)

		then:
		page.assertResults(1, 10, 172)
	}

	void testUniqueOrder() {
		when:
		to(AclEntryCreatePage).submitCreate(
				new AclEntryForm(
						aclObjectIdentityId: 3,
						aceOrder: 1,
						sid: 1,
						mask: 1
				),
				AclEntryCreatePage
		)

		then:
		pageSource.contains('must be unique')
	}

	void testCreateAndEdit() {
		given:
		def newOrder = Math.abs(new Random().nextInt())

		// make sure it doesn't exist
		when:
		def page = to(AclEntrySearchPage).search(
				new AclEntrySearchPage.Form(
						aclObjectIdentityId: 10,
						aceOrder: newOrder
				)
		)

		then:
		page.assertNoResults()

		// create
		when:
		page = to(AclEntryCreatePage).submitCreate(
				new AclEntryForm(
						aclObjectIdentityId: 10,
						aceOrder: newOrder,
						sid: 2,
						mask: 2
				),
				AclEntryEditPage
		)

		then:
		page.aceOrder.text == newOrder as String

		// edit
		when:
		page.submitEdit(
				new AclEntryForm(
						aceOrder: newOrder + 1
				),
				AclEntryEditPage
		)

		then:
		page.aceOrder.text == (newOrder + 1) as String

		// delete
		when:
		page = page.submitDelete(AclEntrySearchPage)
		page = page.search(
				new AclEntrySearchPage.Form(
						aceOrder: newOrder + 1
				)
		)

		then:
		page.assertNoResults()
	}
}
