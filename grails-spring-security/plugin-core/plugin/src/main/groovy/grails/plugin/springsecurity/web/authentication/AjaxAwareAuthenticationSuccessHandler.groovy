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

import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler
import org.springframework.security.web.savedrequest.RequestCache

import grails.plugin.springsecurity.SpringSecurityUtils
import groovy.transform.CompileStatic

/**
 * @author Burt Beckwith
 */
@CompileStatic
class AjaxAwareAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

	protected RequestCache requestCache

	/** Dependency injection for the Ajax success url, e.g. '/login/ajaxSuccess'. */
	String ajaxSuccessUrl

	@Override
	void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws ServletException, IOException {

		boolean ajax = SpringSecurityUtils.isAjax(request)

		// GPSPRINGSECURITYCORE-240
		if (ajax) {
			requestCache.removeRequest request, response
		}

		try {
			if (ajax) {
				clearAuthenticationAttributes request
				if (logger.debugEnabled) {
					logger.debug 'Redirecting to Ajax Success Url: ' + ajaxSuccessUrl
				}
				redirectStrategy.sendRedirect request, response, ajaxSuccessUrl
			}
			else {
				super.onAuthenticationSuccess request, response, authentication
			}
		}
		finally {
			// always remove the saved request
			requestCache.removeRequest request, response
		}
	}

	@Override
	void setRequestCache(RequestCache cache) {
		super.setRequestCache cache
		requestCache = cache
	}
}
