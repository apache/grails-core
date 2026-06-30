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

import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.springframework.beans.factory.InitializingBean
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AuthenticationTrustResolver
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.PortResolver
import org.springframework.security.web.WebAttributes
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.savedrequest.RequestCache

import grails.plugin.springsecurity.ReflectionUtils
import grails.plugin.springsecurity.SpringSecurityUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * @author Burt Beckwith
 */
@CompileStatic
@Slf4j
class AjaxAwareAccessDeniedHandler implements AccessDeniedHandler, InitializingBean {

	protected String ajaxErrorPage
	protected String errorPage

	/** Dependency injection for the {@link AuthenticationTrustResolver}. */
	AuthenticationTrustResolver authenticationTrustResolver

	/** Dependency injection for the request cache. */
	RequestCache requestCache

	/** Dependency injection for the port resolver. */
	PortResolver portResolver

	/** Dependency injection for whether to forward to render the denied page or redirect. */
	boolean useForward = true

	void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e) throws IOException, ServletException {

		if (e && loggedIn && authenticationTrustResolver.isRememberMe(authentication)) {
			// user has a cookie but is getting bounced because of IS_AUTHENTICATED_FULLY,
			// so Spring Security won't save the original request
			requestCache.saveRequest request, response
		}

		if (response.committed) {
			log.trace 'response is committed'
			return
		}

		boolean ajaxError = ajaxErrorPage != null && SpringSecurityUtils.isAjax(request)
		if (errorPage == null && !ajaxError) {
			log.trace 'Sending 403 for non-Ajax request without errorPage specified'
			response.sendError HttpServletResponse.SC_FORBIDDEN, e.message
			return
		}

		if (useForward && (errorPage != null || ajaxError)) {
			log.trace 'Forwarding to error page'
			// Put exception into request scope (perhaps of use to a view)
			request.setAttribute(WebAttributes.ACCESS_DENIED_403, e)
			response.status = HttpServletResponse.SC_FORBIDDEN
			request.getRequestDispatcher(ajaxError ? ajaxErrorPage : errorPage).forward request, response
			return
		}

		String redirectUrl
		String serverURL = ReflectionUtils.grailsServerURL
		if (serverURL == null) {
			boolean includePort = true
			String scheme = request.scheme
			String serverName = request.serverName
			int serverPort = portResolver.getServerPort(request)
			String contextPath = request.contextPath
			boolean inHttp = 'http' == scheme.toLowerCase()
			boolean inHttps = 'https' == scheme.toLowerCase()

			if (inHttp && (serverPort == 80)) {
				includePort = false
			}
			else if (inHttps && (serverPort == 443)) {
				includePort = false
			}
			redirectUrl = scheme + '://' + serverName + ((includePort) ? (':' + serverPort) : '') + contextPath
		}
		else {
			redirectUrl = serverURL
		}

		if (ajaxError) {
			redirectUrl += ajaxErrorPage
		}
		else if (errorPage != null) {
			redirectUrl += errorPage
		}

		String encodedRedirectUrl = response.encodeRedirectURL(redirectUrl)
		log.trace 'Redirecting to {}', encodedRedirectUrl
		response.sendRedirect encodedRedirectUrl
	}

	protected Authentication getAuthentication() {
		SecurityContextHolder.context?.authentication
	}

	protected boolean isLoggedIn() {
		Authentication authentication = getAuthentication()
		authentication && !authenticationTrustResolver.isAnonymous(authentication)
	}

	/**
	 * Dependency injection for the error page, e.g. '/login/denied'.
	 * @param page the page
	 */
	void setErrorPage(final String page) {
		assert page == null || page.startsWith('/'), "ErrorPage must begin with '/'"
		errorPage = page
	}

	/**
	 * Dependency injection for the Ajax error page, e.g. '/login/ajaxDenied'.
	 * @param page the page
	 */
	void setAjaxErrorPage(String page) {
		assert page == null || page.startsWith('/'), "Ajax ErrorPage must begin with '/'"
		ajaxErrorPage = page
	}

	void afterPropertiesSet() {
		assert portResolver, 'portResolver is required'
		assert authenticationTrustResolver, 'authenticationTrustResolver is required'
		assert requestCache, 'requestCache is required'
	}
}
