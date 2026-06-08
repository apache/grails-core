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

import com.test.AdditionalLogoutHandler
import grails.plugin.springsecurity.web.authentication.logout.MutableLogoutFilter
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices

/**
 * @author George McIntosh
 * @author Burt Beckwith
 */
class AdditionalLogoutFiltersConfiguredSpec extends AbstractIntegrationSpec {

	void 'additional handlers exist'() {
		when:
		MutableLogoutFilter logoutFilter = grailsApplication.mainContext.logoutFilter

		then:
		3 == logoutFilter.handlers.size()

		when:
		def expected = [AdditionalLogoutHandler, SecurityContextLogoutHandler, PersistentTokenBasedRememberMeServices].sort()
		def handlerClasses = logoutFilter.handlers.collect { it.class }.sort()

		then:
		expected == handlerClasses
	}

	void 'invoke'() {
		when:
		MutableLogoutFilter logoutFilter = grailsApplication.mainContext.logoutFilter
		AdditionalLogoutHandler additionalLogoutHandler = grailsApplication.mainContext.additionalLogoutHandler
		additionalLogoutHandler.called = false

		logoutFilter.doFilter new MockHttpServletRequest(requestURI: '/logoff'),
		                      new MockHttpServletResponse(), new MockFilterChain()

		then:
		additionalLogoutHandler.called
	}
}
