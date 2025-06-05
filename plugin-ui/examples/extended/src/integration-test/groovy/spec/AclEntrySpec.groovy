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
import page.aclEntry.AclEntryCreatePage
import page.aclEntry.AclEntryEditPage
import page.aclEntry.AclEntrySearchPage

@Integration
class AclEntrySpec extends AbstractSecuritySpec {

	void testFindAll() {
		when:
		def aclEntrySearchPage = browser.to(AclEntrySearchPage)

		then:
		aclEntrySearchPage.assertNotSearched()

		when:
		aclEntrySearchPage.submit()

		then:
		browser.at(AclEntrySearchPage)
		aclEntrySearchPage.assertResults(1, 10, 275)
	}

	void testFindByOid() {
		when:
		def aclEntrySearchPage = browser.to(AclEntrySearchPage).tap {
			aclObjectIdentity = '60'
			submit()
		}

		then:
		browser.at(AclEntrySearchPage)
		aclEntrySearchPage.assertResults(1, 3, 3)

		assertContentContains('60')
		assertContentContains('398')
		assertContentContains('399')
		assertContentContains('400')
		assertContentContains('user1')
		assertContentContains('admin')
		assertContentDoesNotContain('>user2</a>')
		assertContentContains('BasePermission[...............................R=1]')
		assertContentContains('BasePermission[...........................A....=16]')
	}

	void testFindByAceOrder() {
		when:
		def aclEntrySearchPage = browser.to(AclEntrySearchPage).tap {
			aceOrder = '2'
			submit()
		}

		then:
		browser.at(AclEntrySearchPage)
		aclEntrySearchPage.assertResults(1, 10, 67)
		['104', '111', '119', '126', '131', '136', '141', '146', '152', '159'].each {
			assertContentContains it
		}
	}

	void testFindByMask() {
		when:
		def aclEntrySearchPage = browser.to(AclEntrySearchPage).tap {
			mask = '1'
			submit()
		}

		then:
		browser.at(AclEntrySearchPage)
		aclEntrySearchPage.assertResults(1, 10, 172)
	}

	void testUniqueOrder() {
		when:
		def aclEntryCreatePage = browser.to(AclEntryCreatePage).tap {
			aclObjectIdentityId = '3'
			aceOrder = '1'
			sid.selected = '1'
			mask = '1'
			submit()
		}

		then:
		browser.at(AclEntryCreatePage)
		aclEntryCreatePage.assertNotUnique()
	}

	void testCreateAndEdit() {
		given:
		String newOrder = Math.abs(new Random().nextInt())

		// make sure it doesn't exist
		when:
		def aclEntrySearchPage = browser.to(AclEntrySearchPage).tap {
			aclObjectIdentity = '10'
			aceOrder = newOrder
			submit()
		}

		then:
		browser.at(AclEntrySearchPage)
		aclEntrySearchPage.assertNoResults()

		// create
		when:
		def aclEntryCreatePage = browser.to(AclEntryCreatePage).tap {
			aclObjectIdentityId = '10'
			aceOrder = newOrder
			sid.selected = '2'
			mask = '2'
			submit()
		}

		then:
		def aclEntryEditPage = browser.at(AclEntryEditPage)
		aclEntryEditPage.aceOrder.text == newOrder

		// edit
		when:
		aclEntryEditPage.aceOrder = ((newOrder as int) + 1) as String
		aclEntryEditPage.submit()

		then:
		browser.at(AclEntryEditPage)
		aclEntryEditPage.aceOrder.text == ((newOrder as int) + 1) as String

		// delete
		when:
		aclEntryEditPage.delete()

		then:
		browser.at(AclEntrySearchPage)

		when:
		aclEntrySearchPage.aceOrder = ((newOrder as int) + 1) as String
		aclEntrySearchPage.submit()

		then:
		browser.at(AclEntrySearchPage)
		aclEntrySearchPage.assertNoResults()
	}
}
