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

package com.test

import grails.testing.mixin.integration.Integration
import grails.util.Environment
import pages.IndexPage
import pages.LoginPage
import pages.SecureSuperuserPage
import pages.SecureUserPage

@Integration
class RetrieveGroupRolesFunctionalSpec extends AbstractSecurityFunctionalSpec {

	void 'secured urls are not visible without auth'() {
		when:
		via(SecureUserPage)

		then:
		at(LoginPage)

		when:
		via(SecureSuperuserPage)

		then:
		at(LoginPage)
	}

	void 'secured urls are visible when authenticated'() {
		when:
		login('euler', 'password1')

		then:
		at(LoginPage)

		when:
		via(SecureUserPage)

		then:
		at(LoginPage)

		when:
		login('euler', 'password')

		then:
		at(SecureUserPage)
		pageSource.contains('ROLE_MATHEMATICIANS')
		pageSource.contains('ROLE_USER')
		!pageSource.contains('ROLE_SUPERUSER')

		when:
		via(SecureSuperuserPage)

		then:
		pageSource.contains('Sorry, you\'re not authorized to view this page.')

		when:
		logout()

		then:
		at(IndexPage)

		when:
		via(SecureUserPage)

		then:
		at(LoginPage)

		when: 'logging in with a scientist'
		login('tesla', 'password')

		then:
		at(SecureUserPage)

		and: 'it does not belong to group mathematicians, thus it does not have the role mathematician'
		!pageSource.contains('ROLE_MATHEMATICIANS')
		pageSource.contains('ROLE_USER')
		!pageSource.contains('ROLE_SUPERUSER')

		when:
		logout()

		then:
		at(IndexPage)

		when:
		via(SecureUserPage)

		then:
		at(LoginPage)

		when:
		login('gauss', 'password')

		then:
		at(SecureUserPage)

		and:
		pageSource.contains('ROLE_MATHEMATICIANS')
		pageSource.contains('ROLE_USER')
		pageSource.contains('ROLE_SUPERUSER')

		when:
		to(SecureSuperuserPage)

		then:
		pageSource.contains('ROLE_USER')
		pageSource.contains('ROLE_SUPERUSER')
	}
}
