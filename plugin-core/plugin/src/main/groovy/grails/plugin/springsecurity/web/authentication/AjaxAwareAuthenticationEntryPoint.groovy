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

import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.RedirectStrategy
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint

import grails.plugin.springsecurity.SpringSecurityUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * @author Burt Beckwith
 */
@CompileStatic
@Slf4j
class AjaxAwareAuthenticationEntryPoint extends LoginUrlAuthenticationEntryPoint {

	protected String ajaxLoginFormUrl

	/** Dependency injection for the RedirectStrategy. */
	RedirectStrategy redirectStrategy

	/**
	 * @param loginFormUrl URL where the login page can be found. Should either be relative to the web-app context path
	 * (include a leading {@code /}) or an absolute URL.
	 */
	AjaxAwareAuthenticationEntryPoint(String loginFormUrl) {
		super(loginFormUrl)
	}

	void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e) throws IOException, ServletException {

		if ('true'.equalsIgnoreCase(request.getHeader('nopage'))) {
			response.sendError HttpServletResponse.SC_UNAUTHORIZED
			return
		}

		String redirectUrl

		if (useForward) {
			if (forceHttps && 'http' == request.scheme) {
				// First redirect the current request to HTTPS.
				// When that request is received, the forward to the login page will be used.
				redirectUrl = buildHttpsRedirectUrlForRequest(request)
			}

			if (redirectUrl == null) {
				String loginForm = determineUrlToUseForThisRequest(request, response, e)
				log.debug 'Server side forward to: {}', loginForm
				request.getRequestDispatcher(loginForm).forward request, response
				return
			}
		}
		else {
			// redirect to login page. Use https if forceHttps true
			redirectUrl = buildRedirectUrlToLoginPage(request, response, e)
		}

		redirectStrategy.sendRedirect request, response, redirectUrl
	}

	@Override
	protected String determineUrlToUseForThisRequest(HttpServletRequest req, HttpServletResponse res, AuthenticationException e) {
		ajaxLoginFormUrl && SpringSecurityUtils.isAjax(req) ? ajaxLoginFormUrl : loginFormUrl
	}

	/**
	 * Dependency injection for the Ajax login form url, e.g. '/login/authAjax'.
	 * @param url the url
	 */
	void setAjaxLoginFormUrl(String url) {
		assert url == null || url.startsWith('/'), "ajaxLoginFormUrl must begin with '/'"
		ajaxLoginFormUrl = url
	}
}
