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

import grails.testing.mixin.integration.Integration
import pages.IndexPage
import pages.SecureAdminPage
import pages.SecureFooBarPage
import pages.SecureSuperuserPage
import pages.SecureUserPage

@Integration
class Person3FunctionalSpec extends AbstractSecurityFunctionalSpec {

	// person3 has ROLE_USER from LDAP and ROLE_SUPERUSER from database

	void 'secured urls are not visible without auth'() {
		when:
		to SecureAdminPage

		then:
		assertContentContains 'Please Login'

		when:
		to SecureUserPage

		then:
		assertContentContains 'Please Login'

		when:
		to SecureSuperuserPage

		then:
		assertContentContains 'Please Login'

		when:
		to SecureFooBarPage

		then:
		assertContentContains 'Please Login'
	}

	void 'secured urls are visible when authenticated'() {
		when:
		login 'person3', 'password3'

		then:
		at IndexPage

		when:
		to SecureSuperuserPage

		then:
		assertContentContains 'ROLE_USER'
		assertContentContains 'ROLE_SUPERUSER'
		assertContentDoesNotContain 'ROLE_ADMIN'
		assertContentDoesNotContain 'ROLE_FOO_BAR'

		when:
		to SecureUserPage

		then:
		assertContentContains 'ROLE_USER'
		assertContentContains 'ROLE_SUPERUSER'
		assertContentDoesNotContain 'ROLE_ADMIN'
		assertContentDoesNotContain 'ROLE_FOO_BAR'

		when:
		to SecureAdminPage

		then:
		assertContentContains "Sorry, you're not authorized to view this page."
	}
}
