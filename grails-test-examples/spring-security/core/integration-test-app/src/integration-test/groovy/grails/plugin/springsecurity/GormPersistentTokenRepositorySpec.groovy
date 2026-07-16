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
package grails.plugin.springsecurity

import grails.plugin.springsecurity.web.authentication.rememberme.GormPersistentTokenRepository
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken
import test.TestPersistentLogin
import test.TestPersistentLoginService

import java.text.SimpleDateFormat

/**
 * Integration tests for <code>GormPersistentTokenRepository</code>, based on the tests
 * for <code>JdbcTokenRepositoryImpl</code>.
 *
 * @author Burt Beckwith
 */
class GormPersistentTokenRepositorySpec extends AbstractIntegrationSpec {

	private static final String DATE_FORMAT = 'yyyy-MM-dd HH:mm:ss'
	private static final Date DATE = new SimpleDateFormat(DATE_FORMAT).parse('2007-10-09 18:19:25')

	GormPersistentTokenRepository tokenRepository
	TestPersistentLoginService testPersistentLoginService

	void 'create new token inserts correct data'() {
		when:
		Date currentDate = new Date()
		def token = new PersistentRememberMeToken('joeuser', 'joesseries', 'atoken', currentDate)
		tokenRepository.createNewToken token
		flushAndClear()

		then:
		1 == TestPersistentLogin.count()

		when:
		TestPersistentLogin row = testPersistentLoginService.findAll([offset: 0, max: 1])[0]

		then:
		row
		currentDate.time == row.lastUsed.time
		'joeuser' == row.username
		'joesseries' == row.series
		'atoken' == row.token
	}

	void 'retrieving token returns correct data'() {
		when:
		insertToken 'joesseries', 'joeuser', 'atoken', DATE

		PersistentRememberMeToken token = tokenRepository.getTokenForSeries('joesseries')

		then:
		'joeuser' == token.username
		'joesseries' == token.series
		'atoken' == token.tokenValue
		DATE.time == token.date.time
	}

	void 'removing user tokens deletes data'() {
		when:
		insertToken 'joesseries2', 'joeuser', 'atoken2', DATE
		insertToken 'joesseries', 'joeuser', 'atoken', DATE

		tokenRepository.removeUserTokens 'joeuser'

		then:
		0 == testPersistentLoginService.countByUsername('joeuser')
	}

	void 'updating token modifies token value and lastUsed'() {
		when:
		Date date = new Date(System.currentTimeMillis() - 1)
		TestPersistentLogin inserted = insertToken 'joesseries', 'joeuser', 'atoken', date

		then:
		inserted
		inserted.series == 'joesseries'

		when:
		tokenRepository.updateToken 'joesseries', 'newtoken', new Date()
		TestPersistentLogin row = testPersistentLoginService.get('joesseries')

		then:
		row
		'joeuser' == row.username
		'joesseries' == row.series
		'newtoken' == row.token
		row.lastUsed.time > date.time
	}

	private TestPersistentLogin insertToken(String series, String username, String token, Date lastUsed) {
		testPersistentLoginService.save(series, token, username, lastUsed)
	}
}
