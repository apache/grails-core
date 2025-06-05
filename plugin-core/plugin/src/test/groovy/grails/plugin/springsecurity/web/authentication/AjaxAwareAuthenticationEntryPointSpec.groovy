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
package grails.plugin.springsecurity.web.authentication

import grails.plugin.springsecurity.AbstractUnitSpec
import grails.plugin.springsecurity.ReflectionUtils
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.web.SecurityRequestHolder

/**
 * Unit tests for WithAjaxAuthenticationProcessingFilterEntryPoint.
 *
 * @author Burt Beckwith
 */
class AjaxAwareAuthenticationEntryPointSpec extends AbstractUnitSpec {

	private static final String loginFormUrl = '/loginFormUrl'
	private static final String ajaxLoginFormUrl = '/ajaxLoginFormUrl'

	private final AjaxAwareAuthenticationEntryPoint entryPoint = new AjaxAwareAuthenticationEntryPoint(loginFormUrl)

	void setup() {
		entryPoint.useForward = true
		entryPoint.ajaxLoginFormUrl = ajaxLoginFormUrl
		ReflectionUtils.setConfigProperty 'ajaxHeader', SpringSecurityUtils.AJAX_HEADER
		SecurityRequestHolder.set request, response
	}

	void 'commence() with Ajax false'() {
		when:
		entryPoint.commence request, response, null

		then:
		loginFormUrl == response.forwardedUrl
	}

	void 'commence() with Ajax true'() {
		when:
		request.addHeader SpringSecurityUtils.AJAX_HEADER, 'XMLHttpRequest'

		entryPoint.commence request, response, null

		then:
		ajaxLoginFormUrl == response.forwardedUrl
	}

	void 'setAjaxLoginFormUrl'() {
		when:
		entryPoint.ajaxLoginFormUrl = 'foo'

		then:
		thrown AssertionError

		when:
		entryPoint.ajaxLoginFormUrl = '/foo'

		then:
		notThrown AssertionError
	}
}
