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

import page.aclObjectIdentity.AclObjectIdentityCreatePage
import page.aclObjectIdentity.AclObjectIdentityEditPage
import page.aclObjectIdentity.AclObjectIdentityForm
import page.aclObjectIdentity.AclObjectIdentitySearchPage
import spec.SecurityUISpec

import grails.testing.mixin.integration.Integration

@Integration
class AclObjectIdentitySpec extends SecurityUISpec {

	void testFindAll() {
		when:
		def page = to(AclObjectIdentitySearchPage)

		then:
		page.assertNotSearched()

		when:
		page = page.search()

		then:
		page.assertResults(1, 10, 100)
	}

	void testFindById() {
		when:
		def page = to(AclObjectIdentitySearchPage).search(
				new AclObjectIdentityForm(
						objectId: '10'
				)
		)

		then:
		page.assertResults(1, 1, 1)
		pageSource.contains('test.Report')
	}

	void testFindByOwner() {
		when:
		def page = to(AclObjectIdentitySearchPage).search(
				new AclObjectIdentityForm(
						ownerId: '1'
				)
		)

		then:
		page.assertResults(1, 10, 98)
	}

	void testUniqueId() {
		when:
		to(AclObjectIdentityCreatePage).submitCreate(
				new AclObjectIdentityForm(
						aclClass: '1',
						objectId: '1',
						ownerId: '2'
				),
				AclObjectIdentityCreatePage
		)

		then:
		pageSource.contains('must be unique')
	}

	void testCreateAndEdit() {
		given:
		def newId = Math.abs(new Random().nextInt()) as String

		// make sure it doesn't exist
		when:
		def page = to(AclObjectIdentitySearchPage).search(
				new AclObjectIdentityForm(
						objectId: newId
				)
		)

		then:
		page.assertNoResults()

		// create
		when:
		page = to(AclObjectIdentityCreatePage).submitCreate(
				new AclObjectIdentityForm(
						aclClass: '1',
						objectId: newId,
						ownerId: '2'
				),
				AclObjectIdentityEditPage
		)

		then:
		page.objectId.text == newId

		// edit
		when:
		page = page.submitEdit(
				new AclObjectIdentityForm(
						objectId: newId.toInteger() + 1,
				),
				AclObjectIdentityEditPage
		)

		then:
		page.objectId.text == (newId.toInteger() + 1).toString()

		// delete
		when:
		page = page.submitDelete(AclObjectIdentitySearchPage)
		page = page.search(
				new AclObjectIdentityForm(
						objectId: (newId.toInteger() + 1).toString()
				)
		)

		then:
		page.assertNoResults()
	}
}
