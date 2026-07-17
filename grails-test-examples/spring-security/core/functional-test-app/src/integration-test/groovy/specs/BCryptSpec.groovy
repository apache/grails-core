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
import pages.user.CreateUserPage
import pages.user.ListUserPage
import pages.user.ShowUserPage
import spock.lang.IgnoreIf

@Integration
@IgnoreIf({ System.getProperty('TESTCONFIG') != 'bcrypt' })
class BCryptSpec extends AbstractSecuritySpec {

	void 'create a user'() {
		when:
		to ListUserPage
		newUserButton.click()

		then:
		at CreateUserPage

		when:
		username = 'user1'
		password = 'p4ssw0rd'
		$('#enabled').click()
		createButton.click()

		then:
		at ShowUserPage
		username == 'user1'
		userEnabled == true

		when:
		to ListUserPage

		then:
		userRows.size() == 1
	}

	void 'test bcrypt'() {
		when:
		String encryptedPassword = getContent('hack/getUserProperty?user=user1&propName=password')

		then:
		encryptedPassword.startsWith '{bcrypt}$2a$'

		when:
		def shaPasswordEncoder = createSha256Encoder()
		String notSalted = shaPasswordEncoder.encode('p4ssw0rd')

		then:
		notSalted != encryptedPassword
	}
}
