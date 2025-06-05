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

package test

import grails.gorm.transactions.Rollback
import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration
import pages.LoginPage
import spock.lang.Shared

@Rollback
@Integration
abstract class AbstractSecuritySpec extends ContainerGebSpec {

	@Shared boolean reset = false

	void setup() {
		if ( !reset ) {
			go 'testData/reset'
			reset = true
		}
		logout()
	}

	protected void login(String user) {
		to LoginPage
		username = user
		password = 'password'
		loginButton.click()
	}

	protected void logout() {
		go 'logout'
		browser.clearCookies()
	}

	protected boolean contentContains(String expected) {
		browser.driver.pageSource.contains(expected)
	}

	protected void assertContentContains(String expected) {
		assert contentContains(expected)
	}

	protected void assertContentDoesNotContain(String unexpected) {
		assert !contentContains(unexpected)
	}
}
