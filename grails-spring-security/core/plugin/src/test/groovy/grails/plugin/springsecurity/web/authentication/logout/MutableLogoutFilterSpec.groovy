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
package grails.plugin.springsecurity.web.authentication.logout

import grails.plugin.springsecurity.AbstractUnitSpec
import grails.plugin.springsecurity.SecurityTestUtils
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.web.authentication.logout.LogoutHandler
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler

import jakarta.servlet.FilterChain

/**
 * Unit tests for MutableLogoutFilter.
 *
 * @author Burt Beckwith
 */
class MutableLogoutFilterSpec extends AbstractUnitSpec {

	private static final String filterProcessesUrl = '/logoff'
	private static final String afterLogoutUrl = '/loggedout'

	private final logoutSuccessHandler = new SimpleUrlLogoutSuccessHandler(defaultTargetUrl: afterLogoutUrl)
	private final handlers = []
	private final filter = new MutableLogoutFilter(logoutSuccessHandler)

	private int logoutCount

	void setup() {
		5.times {
			handlers << ([logout: { req, res, auth -> logoutCount++ }] as LogoutHandler)
		}
		filter.handlers = handlers
		filter.filterProcessesUrl = filterProcessesUrl
	}

	void 'doFilter'() {
		given:
		SecurityTestUtils.authenticate()

		def request1 = new MockHttpServletRequest(method: 'GET', requestURI: '/foo/bar')
		def response1 = new MockHttpServletResponse()
		def request2 = new MockHttpServletRequest(method: 'GET', requestURI: filterProcessesUrl)
		def response2 = new MockHttpServletResponse()

		boolean chain1Called = false
		boolean chain2Called = false
		def chain1 = [doFilter: { req, res ->
			chain1Called = true
		}] as FilterChain
		def chain2 = [doFilter: { req, res ->
			chain2Called = true
		}] as FilterChain

		when:
		// not a logout url, so chain.doFilter() is called
		filter.doFilter request1, response1, chain1

		then:
		!response1.redirectedUrl

		when:
		filter.doFilter request2, response2, chain2

		then:
		response2.redirectedUrl

		chain1Called
		!chain2Called
		5 == logoutCount
	}
}
