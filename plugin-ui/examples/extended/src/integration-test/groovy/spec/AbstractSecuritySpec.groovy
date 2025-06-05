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

package spec

import geb.driver.CachingDriverFactory
import grails.plugin.geb.ContainerGebSpec
import grails.plugin.springsecurity.SpringSecurityUtils

abstract class AbstractSecuritySpec extends ContainerGebSpec {

	void setup() {
		logout()
	}

	void cleanup() {
		CachingDriverFactory.clearCache()
	}

	protected void logout() {
		String url = SpringSecurityUtils.securityConfig.logout.filterProcessesUrl
		browser.go(url)
		browser.clearCookies()
	}

	protected void assertContentContains(String expected) {
		assert browser.$().text().contains(expected)
	}

	// used to verify hidden content like menus and jGrowl flash messages
	protected void assertHtmlContains(String expected) {
		// For some reason, an extra call to pageSource is sometimes
		// needed here, for jGrowl messages to be rendered to the page
		assert browser.driver.pageSource
		assert browser.driver.pageSource.contains(expected)
	}

	protected void assertContentContainsOne(String expected1, String expected2) {
		assert browser.$().text().contains(expected1) || $().text().contains(expected2)
	}

	protected void assertContentMatches(String regex) {
		assert browser.$().text() ==~ regex
	}

	protected void assertContentDoesNotContain(String unexpected) {
		assert !browser.$().text().contains(unexpected)
	}
}
