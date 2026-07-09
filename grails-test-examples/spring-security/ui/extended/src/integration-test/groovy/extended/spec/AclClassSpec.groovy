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

import page.aclClass.AclClassCreatePage
import page.aclClass.AclClassEditPage
import page.aclClass.AclClassSearchPage
import spec.SecurityUISpec

import grails.testing.mixin.integration.Integration

@Integration
class AclClassSpec extends SecurityUISpec {

	void testFindAll() {
		when:
		def page = to(AclClassSearchPage)

		then:
		page.assertNotSearched()

		when:
		page = page.search()

		then:
		page.assertResults(1, 1, 1)
	}

	void testFindByName() {
		when:
		def page = to(AclClassSearchPage)
				.search('report')

		then:
		page.assertResults(1, 1, 1)
		pageSource.contains('test.Report')
	}

	void testUniqueName() {
		when:
		to(AclClassCreatePage)
				.submitCreate('test.Report', AclClassCreatePage)

		then:
		pageSource.contains('must be unique')
	}

	void testCreateAndEdit() {
		given:
		def newName = "com.some.domain.Clazz${UUID.randomUUID()}"

		// make sure it doesn't exist
		when:
		def page = to(AclClassSearchPage)
				.search(newName)

		then:
		page.assertNoResults()

		// create
		when:
		page = to(AclClassCreatePage)
				.submitCreate(newName, AclClassEditPage)

		then:
		page.className.text == newName

		// edit
		when:
		page = page.submitEdit("${newName}_new", AclClassEditPage)

		then:
		page.className.text == "${newName}_new"

		// delete
		when:
		page = page.submitDelete(AclClassSearchPage)

		and:
		page = page.search("${newName}_new")

		then:
		page.assertNoResults()
	}
}
