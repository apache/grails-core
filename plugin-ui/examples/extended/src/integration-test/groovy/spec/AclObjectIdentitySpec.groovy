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
import page.aclObjectIdentity.AclObjectIdentityCreatePage
import page.aclObjectIdentity.AclObjectIdentityEditPage
import page.aclObjectIdentity.AclObjectIdentitySearchPage

@Integration
class AclObjectIdentitySpec extends AbstractSecuritySpec {

	void testFindAll() {
		when:
		def aclObjectIdentitySearchPage = browser.to(AclObjectIdentitySearchPage)

		then:
		aclObjectIdentitySearchPage.assertNotSearched()

		when:
		aclObjectIdentitySearchPage.submit()

		then:
		browser.at(AclObjectIdentitySearchPage)
		aclObjectIdentitySearchPage.assertResults(1, 10, 100)
	}

	void testFindById() {
		when:
		def aclObjectIdentitySearchPage = browser.to(AclObjectIdentitySearchPage).tap {
			objectId = '10'
			submit()
		}

		then:
		browser.at(AclObjectIdentitySearchPage)
		aclObjectIdentitySearchPage.assertResults(1, 1, 1)
		assertContentContains('test.Report')
	}

	void testFindByOwner() {
		when:
		def aclObjectIdentitySearchPage = browser.to(AclObjectIdentitySearchPage).tap {
			ownerId = '1'
			submit()
		}

		then:
		browser.at(AclObjectIdentitySearchPage)
		aclObjectIdentitySearchPage.assertResults(1, 10, 98)
	}

	void testUniqueId() {
		when:
		def aclObjectIdentityCreatePage = browser.to(AclObjectIdentityCreatePage).tap {
			aclClass.selected = '1'
			objectId = 1
			ownerId.selected = '2'
			submit()
		}

		then:
		browser.at(AclObjectIdentityCreatePage)
		aclObjectIdentityCreatePage.assertNotUnique()
	}

	void testCreateAndEdit() {
		given:
		String newId = Math.abs(new Random().nextInt())

		// make sure it doesn't exist
		when:
		def aclObjectIdentitySearchPage = browser.to(AclObjectIdentitySearchPage).tap {
			objectId = newId
			submit()
		}

		then:
		aclObjectIdentitySearchPage.assertNoResults()

		// create
		when:
		browser.to(AclObjectIdentityCreatePage).with {
			aclClass.selected = '1'
			objectId = newId
			ownerId.selected = '2'
			submit()
		}

		then:
		def aclObjectIdentityEditPage = browser.at(AclObjectIdentityEditPage)
		aclObjectIdentityEditPage.objectId.text == newId

		// edit
		when:
		aclObjectIdentityEditPage.objectId = (newId.toInteger() + 1).toString()
		aclObjectIdentityEditPage.submit()

		then:
		browser.at(AclObjectIdentityEditPage)
		aclObjectIdentityEditPage.objectId.text == (newId.toInteger() + 1).toString()

		// delete
		when:
		aclObjectIdentityEditPage.delete()

		then:
		browser.at(AclObjectIdentitySearchPage)

		when:
		aclObjectIdentitySearchPage.objectId = (newId.toInteger() + 1).toString()
		aclObjectIdentitySearchPage.submit()

		then:
		browser.at(AclObjectIdentitySearchPage)
		aclObjectIdentitySearchPage.assertNoResults()
	}
}
