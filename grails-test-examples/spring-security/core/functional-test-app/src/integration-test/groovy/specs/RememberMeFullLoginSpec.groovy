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
import spock.lang.IgnoreIf
import spock.lang.Issue

@Integration
@IgnoreIf({ System.getProperty('TESTCONFIG') != 'annotation' })
@Issue('https://github.com/apache/grails-core/issues/15877')
class RememberMeFullLoginSpec extends AbstractSecuritySpec {

	protected void resetDatabase() {
		super.resetDatabase()
		go 'testData/addTestUsers'
	}

	void 'a user remembered by cookie only is sent to the full login page for a page that needs a full login'() {
		given: 'a user who logged in with remember-me and then lost the session'
		login 'testuser', 'password', true
		assert at(IndexPage)
		driver.manage().deleteCookieNamed('JSESSIONID')

		when: 'a page that needs IS_AUTHENTICATED_FULLY is requested'
		go 'secureNamespaced'

		then: 'the browser ends on the full login page, not in a redirect loop and not on the default target'
		waitFor { currentUrl.contains('/login/full') }
		$('#loginForm, form[action*="authenticate"]').size() > 0
	}
}
