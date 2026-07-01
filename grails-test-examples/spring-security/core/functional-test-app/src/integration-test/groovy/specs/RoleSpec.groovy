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

package specs

import grails.testing.mixin.integration.Integration
import pages.role.CreateRolePage
import pages.role.EditRolePage
import pages.role.ListRolePage
import pages.role.ShowRolePage
import spock.lang.IgnoreIf

@Integration
@IgnoreIf({ !(
		System.getProperty('TESTCONFIG') == 'annotation' ||
        System.getProperty('TESTCONFIG') == 'basic' ||
        System.getProperty('TESTCONFIG') == 'basicCacheUsers' ||
        System.getProperty('TESTCONFIG') == 'requestmap' ||
        System.getProperty('TESTCONFIG') == 'static')
})
class RoleSpec extends AbstractSecuritySpec {

	void 'there are no roles initially'() {
		when:
		to ListRolePage

		then:
		roleRows.size() == 0
	}

	void 'add a role'() {
		when:
		to ListRolePage
		newRoleButton.click()

		then:
		at CreateRolePage

		when:
		authority = 'test'
		createButton.click()

		then:
		at ShowRolePage
		authority == 'test'
	}

	void 'edit the details'() {
		when:
		to ListRolePage
		roleRow(0).showLink.click()

		then:
		at ShowRolePage

		when:
		editButton.click()

		then:
		at EditRolePage

		when:
		authority = 'test_new'
		updateButton.click()

		then:
		at ShowRolePage

		when:
		to ListRolePage

		then:
		roleRows.size() == 1

		def row = roleRow(0)
		row.authority == 'test_new'
	}

	void 'show role'() {
		when:
		to ListRolePage
		roleRow(0).showLink.click()

		then:
		at ShowRolePage
	}

	void 'delete role'() {
		when:
		to ListRolePage
		roleRow(0).showLink.click()
		def deletedId = id

		then:
		at ShowRolePage

		when:
		withConfirm { deleteButton.click() }

		then:
		at ListRolePage

		message == "TestRole $deletedId deleted"
		roleRows.size() == 0
	}
}
