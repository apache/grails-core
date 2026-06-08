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
package grails.plugin.springsecurity.web.access

import grails.plugin.springsecurity.AbstractUnitSpec
import grails.plugin.springsecurity.ReflectionUtils
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.web.SecurityRequestHolder
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AuthenticationTrustResolverImpl
import org.springframework.security.authentication.RememberMeAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder as SCH
import org.springframework.security.web.PortResolverImpl
import org.springframework.security.web.savedrequest.HttpSessionRequestCache

/**
 * @author Burt Beckwith
 */
class AjaxAwareAccessDeniedHandlerSpec extends AbstractUnitSpec {

	private final AjaxAwareAccessDeniedHandler handler = new AjaxAwareAccessDeniedHandler()

	void setup() {
		handler.errorPage = '/fail'
		handler.ajaxErrorPage = '/ajaxFail'
		handler.portResolver = new PortResolverImpl()
		handler.authenticationTrustResolver = new AuthenticationTrustResolverImpl()
		handler.requestCache = new HttpSessionRequestCache()
		ReflectionUtils.setConfigProperty 'ajaxHeader', SpringSecurityUtils.AJAX_HEADER
		SecurityRequestHolder.set request, response
	}

	void 'handle authenticated, remember-me, redirect'() {
		when:
		handler.useForward = false

		SCH.context.authentication = new RememberMeAuthenticationToken('username', 'password', null)

		then:
		!request.session.getAttribute(SpringSecurityUtils.SAVED_REQUEST)

		when:
		handler.handle request, response, new AccessDeniedException('fail')

		then:
		request.session.getAttribute(SpringSecurityUtils.SAVED_REQUEST)

		'http://localhost/fail' == response.redirectedUrl
		!response.forwardedUrl
	}

	void 'handle authenticated, remember-me, forward'() {
		when:
		handler.useForward = true

		SCH.context.authentication = new RememberMeAuthenticationToken('username', 'password', null)

		then:
		!request.session.getAttribute(SpringSecurityUtils.SAVED_REQUEST)

		when:
		handler.handle request, response, new AccessDeniedException('fail')

		then:
		request.session.getAttribute(SpringSecurityUtils.SAVED_REQUEST)

		!response.redirectedUrl
		'/fail' == response.forwardedUrl
	}

	void 'handle authenticated, Ajax, redirect'() {
		when:
		handler.useForward = false

		request.addHeader SpringSecurityUtils.AJAX_HEADER, 'XMLHttpRequest'

		handler.handle request, response, new AccessDeniedException('fail')

		then:
		'http://localhost/ajaxFail' == response.redirectedUrl
		!response.forwardedUrl
	}

	void 'handle authenticated, Ajax, forward'() {
		when:
		handler.useForward = true

		request.addHeader SpringSecurityUtils.AJAX_HEADER, 'XMLHttpRequest'

		handler.handle request, response, new AccessDeniedException('fail')

		then:
		'/ajaxFail' == response.forwardedUrl
		!response.redirectedUrl
	}

	void 'handle authenticated, not Ajax, redirect'() {
		when:
		handler.useForward = false

		handler.handle request, response, new AccessDeniedException('fail')

		then:
		'http://localhost/fail' == response.redirectedUrl
		!response.forwardedUrl
	}

	void 'handle authenticated, not Ajax, forward'() {
		when:
		handler.useForward = true

		handler.handle request, response, new AccessDeniedException('fail')

		then:
		'/fail' == response.forwardedUrl
		!response.redirectedUrl
	}

	void 'respecting Grails serverURL'() {
		when:
		ReflectionUtils.application.config.grails.serverURL = 'http://somewhere.org'
		handler.useForward = false

		handler.handle request, response, new AccessDeniedException('fail')

		then:
		'http://somewhere.org/fail' == response.redirectedUrl
		!response.forwardedUrl
	}
}
