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
import page.aclClass.AclClassCreatePage
import page.aclClass.AclClassEditPage
import page.aclClass.AclClassSearchPage

@Integration
class AclClassSpec extends AbstractSecuritySpec {

	void testFindAll() {
		when:
		def aclClassSearchPage = browser.to(AclClassSearchPage)

		then:
		aclClassSearchPage.assertNotSearched()

		when:
		aclClassSearchPage.submit()

		then:
		browser.at(AclClassSearchPage)
		aclClassSearchPage.assertResults(1, 1, 1)
	}

	void testFindByName() {
		when:
		def aclClassSearchPage = browser.to(AclClassSearchPage).tap {
			search('report')
		}

		then:
		browser.at(AclClassSearchPage)
		aclClassSearchPage.assertResults(1, 1, 1)
		assertContentContains('test.Report')
	}

	void testUniqueName() {
		when:
		def aclClassCreatePage = browser.to(AclClassCreatePage).tap {
			create('test.Report')
		}

		then:
		browser.at(AclClassCreatePage)
		aclClassCreatePage.assertNotUnique()
	}

	void testCreateAndEdit() {
		given:
		String newName = "com.some.domain.Clazz${UUID.randomUUID()}"

		// make sure it doesn't exist
		when:
		def aclClassSearchPage = browser.to(AclClassSearchPage).tap {
			search(newName)
		}

		then:
		browser.at(AclClassSearchPage)
		aclClassSearchPage.assertNoResults()

		// create
		when:
		browser.to(AclClassCreatePage).with {
			create(newName)
		}

		then:
		def aclClassEditPage = browser.at(AclClassEditPage)
		aclClassEditPage.className.text == newName

		// edit
		when:
		aclClassEditPage.update("${newName}_new")

		then:
		browser.at(AclClassEditPage)
		aclClassEditPage.className.text == "${newName}_new"

		// delete
		when:
		aclClassEditPage.delete()

		then:
		browser.at(AclClassSearchPage)

		when:
		aclClassSearchPage.search("${newName}_new")

		then:
		browser.at(AclClassSearchPage)
		aclClassSearchPage.assertNoResults()
	}
}
