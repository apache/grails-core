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
import page.HomePage

import grails.plugin.geb.ContainerGebSpec
import grails.plugin.springsecurity.SpringSecurityUtils

abstract class SecurityUISpec extends ContainerGebSpec {

	void setup() {
		logout()
	}

	void cleanup() {
		CachingDriverFactory.clearCache()
	}

	HomePage logout() {
		go(SpringSecurityUtils.securityConfig.logout.filterProcessesUrl as String)
		clearCookies()
		def page = to(HomePage)
		waitFor { page.loaded }
		page
	}

	protected boolean assertContentContainsOne(String expected1, String expected2) {
		assert pageSource.contains(expected1) || pageSource.contains(expected2)
		true
	}
}
