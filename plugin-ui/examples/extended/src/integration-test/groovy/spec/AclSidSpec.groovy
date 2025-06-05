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
import page.aclSid.AclSidCreatePage
import page.aclSid.AclSidEditPage
import page.aclSid.AclSidSearchPage

@Integration
class AclSidSpec extends AbstractSecuritySpec {

	void testFindAll() {
		when:
		def aclSidSearchPage = browser.to(AclSidSearchPage)

		then:
		aclSidSearchPage.assertNotSearched()

		when:
		aclSidSearchPage.submit()

		then:
		browser.at(AclSidSearchPage)
		aclSidSearchPage.assertResults(1, 3, 3)
	}

	void testFindBySid() {
		when:
		def aclSidSearchPage = browser.to(AclSidSearchPage).tap {
			search('user')
		}

		then:
		browser.at(AclSidSearchPage)
		aclSidSearchPage.assertResults(1, 2, 2)

		assertContentContains('user1')
		assertContentContains('user2')
	}

	void testFindByPrincipal() {
		when:
		def aclSidSearchPage = browser.to(AclSidSearchPage)
		// Temporary workaround for problem with Geb RadioButtons module
		//aclSidSearchPage.principal.checked = '1'
		browser.$('input', type: 'radio', name: 'principal', value: '1').click()
		aclSidSearchPage.submit()

		then:
		browser.at(AclSidSearchPage)
		assertContentContains('user1')
		assertContentContains('user2')
		assertContentContains('admin')
	}

	void testUniqueName() {
		when:
		def aclSidCreatePage = browser.to(AclSidCreatePage).tap {
			create('user1', true)
		}

		then:
		browser.at(AclSidCreatePage)
		assertContentContains('must be unique')
	}

	void testCreateAndEdit() {
		given:
		String newName = "newuser${UUID.randomUUID()}"

		// make sure it doesn't exist
		when:
		def aclSidSearchPage = browser.to(AclSidSearchPage).tap {
			sid = newName
			submit()
		}

		then:
		aclSidSearchPage.assertNoResults()

		// create
		when:
		def aclSidCreatePage = browser.to(AclSidCreatePage).tap {
			create(newName, true)
		}

		then:
		def aclSidEditPage = browser.at(AclSidEditPage)
		aclSidEditPage.sid.text == newName
		aclSidEditPage.principal.checked

		// edit
		when:
		aclSidEditPage.sid = "${newName}_new"
		aclSidEditPage.submit()

		then:
		browser.at(AclSidEditPage)
		aclSidEditPage.sid.text == "${newName}_new"

		// delete
		when:
		aclSidEditPage.delete()

		then:
		browser.at(AclSidSearchPage)

		when:
		aclSidSearchPage.sid = "${newName}_new"
		aclSidSearchPage.submit()

		then:
		browser.at(AclSidSearchPage)
		aclSidSearchPage.assertNoResults()
	}
}
