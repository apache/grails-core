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
import pages.LoginPage
import spock.lang.IgnoreIf

@Integration
@IgnoreIf({ System.getProperty('TESTCONFIG') != 'annotation' })
class InheritanceSecuritySpec extends AbstractSecuritySpec {

	protected void resetDatabase() {
		super.resetDatabase()
		go 'testData/addTestUsers'
	}

	void 'should redirect to login page for anonymous'() {
		when:
		go uri

		then:
		at LoginPage

		where:
		uri << ['base/index', 'extended/index', 'base/delete', 'extended/delete', 'base/update', 'extended/update']
	}

	void 'verify security for testuser'() {
		when:
		login 'testuser', 'password'

		then:
		at IndexPage

		when:
		go 'base/index'

		then:
		pageSource =~ /BaseController/

		when:
		go 'base/delete'

		then:
		pageSource =~ /DELETED/

		when:
		go 'base/update'

		then:
		pageSource =~ /BaseController - UPDATED/

		when:
		go 'extended/index'

		then:
		pageSource =~ /ExtendedController/

		when:
		go 'extended/delete'

		then:
		pageSource =~ /DELETED/

		when:
		go 'extended/update'

		then:
		pageSource =~ /ExtendedController - UPDATED/
	}

	void 'verify security for other user'() {
		when:
		login 'testuser_books', 'password'

		then:
		at IndexPage

		when:
		go 'base/index'

		then:
		$('.errors').text() == "Sorry, you're not authorized to view this page."

		when:
		go 'base/delete'

		then:
		$('.errors').text() == "Sorry, you're not authorized to view this page."

		when:
		go 'base/update'

		then:
		$('.errors').text() == "Sorry, you're not authorized to view this page."

		when:
		go 'extended/index'

		then:
		$('.errors').text() == "Sorry, you're not authorized to view this page."

		when:
		go 'extended/delete'

		then:
		$('.errors').text() == "Sorry, you're not authorized to view this page."

		when:
		go 'extended/update'

		then:
		$('.errors').text() == "Sorry, you're not authorized to view this page."
	}
}
