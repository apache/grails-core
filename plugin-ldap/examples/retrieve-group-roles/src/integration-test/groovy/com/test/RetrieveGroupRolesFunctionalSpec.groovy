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
		go SecureUserPage.url

		then:
		assertContentContains 'Please Login'

		when:
		go SecureSuperuserPage.url

		then:
		assertContentContains 'Please Login'
	}

	void 'secured urls are visible when authenticated'() {
		when:
		login 'euler', 'password1'

		then:
		at LoginPage

		Environment.current

		when:
		go SecureUserPage.url
		login 'euler', 'password'

		then:
		at SecureUserPage
		assertContentContains 'ROLE_MATHEMATICIANS'
		assertContentContains 'ROLE_USER'
		assertContentDoesNotContain 'ROLE_SUPERUSER'

		when:
		go SecureSuperuserPage.url

		then:
		assertContentContains "Sorry, you're not authorized to view this page."

		when:
		logout()

		then:
		at IndexPage

		when:
		go SecureUserPage.url

		then:
		assertContentContains 'Please Login'

		when: 'logging with a scientist'
		login 'tesla', 'password'

		then:
		at SecureUserPage

		and: 'it does not belong to group mathematicians, thus it does not have the role mathematician'
		assertContentDoesNotContain 'ROLE_MATHEMATICIANS'
		assertContentContains 'ROLE_USER'
		assertContentDoesNotContain 'ROLE_SUPERUSER'

		when:
		logout()

		then:
		at IndexPage

		when:
		go SecureUserPage.url

		then:
		assertContentContains 'Please Login'

		when:
		login 'gauss', 'password'

		then:
		at SecureUserPage

		and:
		assertContentContains 'ROLE_MATHEMATICIANS'
		assertContentContains 'ROLE_USER'
		assertContentContains 'ROLE_SUPERUSER'

		when:
		to SecureSuperuserPage

		then:
		assertContentContains 'ROLE_USER'
		assertContentContains 'ROLE_SUPERUSER'
	}
}
