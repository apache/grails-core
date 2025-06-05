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
package grails.plugin.springsecurity.web.filter

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.RememberMeServices
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationFilter
import org.springframework.security.web.savedrequest.RequestCache

import groovy.transform.CompileStatic

/**
 * Stores a SavedRequest so remember-me autologin gets redirected to requested url.
 *
 * @author Burt Beckwith
 */
@CompileStatic
class GrailsRememberMeAuthenticationFilter extends RememberMeAuthenticationFilter {

	protected RequestCache requestCache

	/** Dependency injection for createSessionOnSuccess. */
	boolean createSessionOnSuccess = true

	GrailsRememberMeAuthenticationFilter(AuthenticationManager authenticationManager, RememberMeServices rememberMeServices,
	                                     RequestCache requestCache) {
		super(authenticationManager, rememberMeServices)
		this.requestCache = requestCache
	}

	@Override
	protected void onSuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, Authentication authResult) {
		if (!requestCache.getRequest(request, response)) {
			requestCache.saveRequest request, response
		}

		try {
			if (createSessionOnSuccess) {
				request.session
			}
		}
		catch (IllegalStateException ignored) {}
	}

	@Override
	void afterPropertiesSet() {
		super.afterPropertiesSet()
		assert requestCache, 'requestCache is required'
	}
}
