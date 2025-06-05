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
import pages.IndexPage
import pages.role.CreateRolePage
import pages.role.ListRolePage
import pages.role.ShowRolePage
import pages.user.CreateUserPage
import pages.user.ListUserPage
import pages.user.ShowUserPage
import spock.lang.IgnoreIf

@Integration
@IgnoreIf({ System.getProperty('TESTCONFIG') != 'static' })
class StaticSecuritySpec extends AbstractSecuritySpec {

	void 'create roles'() {
		when:
		to ListRolePage

		then:
		roleRows.size() == 0

		when:
		newRoleButton.click()

		then:
		at CreateRolePage

		when:
		authority = 'ROLE_ADMIN'
		createButton.click()

		then:
		at ShowRolePage

		when:
		to ListRolePage

		then:
		roleRows.size() == 1

		when:
		newRoleButton.click()

		then:
		at CreateRolePage

		when:
		authority = 'ROLE_ADMIN2'
		createButton.click()

		then:
		at ShowRolePage

		when:
		to ListRolePage

		then:
		roleRows.size() == 2
	}

	void 'create users'() {
		when:
		to ListUserPage

		then:
		userRows.size() == 0

		when:
		newUserButton.click()

		then:
		at CreateUserPage

		when:
		username = 'admin1'
		password = 'password1'
		$('#enabled').click()
		$('#ROLE_ADMIN').click()
		createButton.click()

		then:
		at ShowUserPage

		when:
		to ListUserPage

		then:
		userRows.size() == 1

		when:
		newUserButton.click()

		then:
		at CreateUserPage

		when:
		username = 'admin2'
		password = 'password2'
		$('#enabled').click()
		$('#ROLE_ADMIN').click()
		$('#ROLE_ADMIN2').click()
		createButton.click()

		then:
		at ShowUserPage

		when:
		to ListUserPage

		then:
		userRows.size() == 2
	}

	void 'secured urls not visible without login'() {
		when:
		go 'secureAnnotated'

		then:
		assertContentContains 'Please Login'

		when:
		go 'secureAnnotated/index'

		then:
		assertContentContains 'Please Login'

		when:
		go 'secureAnnotated/index.xml'

		then:
		assertContentContains 'Please Login'

		when:
		go 'secureAnnotated/index;jsessionid=5514B068198CC7DBF372713326E14C12'

		then:
		assertContentContains 'Please Login'

		when:
		go 'secureAnnotated/adminEither'

		then:
		assertContentContains 'Please Login'

		when:
		go 'secureClassAnnotated'

		then:
		assertContentContains 'index: you have ROLE_ADMIN'

		when:
		go 'secureClassAnnotated/index'

		then:
		assertContentContains 'index: you have ROLE_ADMIN'

		when:
		go 'secureClassAnnotated/otherAction'

		then:
		assertContentContains 'otherAction: you have ROLE_ADMIN'

		when:
		go 'secureClassAnnotated/admin2'

		then:
		assertContentContains 'admin2: you have ROLE_ADMIN2'
	}

	void 'check allowed for admin1'() {
		when:
		login 'admin1', 'password1'

		then:
		at IndexPage

		// Check that after login as admin1, some @Secure actions are accessible
		when:
		go 'secureAnnotated'

		then:
		assertContentContains 'you have ROLE_ADMIN'

		when:
		go 'secureAnnotated/index'

		then:
		assertContentContains 'you have ROLE_ADMIN'

		when:
		go 'secureAnnotated/adminEither'

		then:
		assertContentContains 'you have ROLE_ADMIN'

		when:
		go 'secureClassAnnotated'

		then:
		assertContentContains 'you have ROLE_ADMIN'

		when:
		go 'secureClassAnnotated/index'

		then:
		assertContentContains 'you have ROLE_ADMIN'

		when:
		go 'secureClassAnnotated/otherAction'

		then:
		assertContentContains 'you have ROLE_ADMIN'

		when:
		go 'secureClassAnnotated/admin2'

		then:
		assertContentContains 'admin2: you have ROLE_ADMIN2'

		when:
		go 'secureAnnotated/expression'

		then:
		assertContentContains 'expression: OK'
	}

	void 'check allowed for admin2'() {
		when:
		login 'admin2', 'password2'

		then:
		at IndexPage

		// Check that after login as admin2, some @Secure actions are accessible
		when:
		go 'secureAnnotated'

		then:
		assertContentContains 'you have ROLE_ADMIN'

		when:
		go 'secureAnnotated/index'

		then:
		assertContentContains 'you have ROLE_ADMIN'

		when:
		go 'secureAnnotated/adminEither'

		then:
		assertContentContains 'you have ROLE_ADMIN'

		when:
		go 'secureClassAnnotated'

		then:
		assertContentContains 'you have ROLE_ADMIN'

		when:
		go 'secureClassAnnotated/index'

		then:
		assertContentContains 'you have ROLE_ADMIN'

		when:
		go 'secureClassAnnotated/otherAction'

		then:
		assertContentContains 'you have ROLE_ADMIN'

		when:
		go 'secureClassAnnotated/admin2'

		then:
		assertContentContains 'admin2: you have ROLE_ADMIN2'

		when:
		go 'secureAnnotated/expression'

		then:
		assertContentContains "Sorry, you're not authorized to view this page."
	}
}
