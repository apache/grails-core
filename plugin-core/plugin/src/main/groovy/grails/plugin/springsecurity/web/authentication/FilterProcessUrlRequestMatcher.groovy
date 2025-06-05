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

import jakarta.servlet.http.HttpServletRequest

import org.springframework.security.web.util.UrlUtils
import org.springframework.security.web.util.matcher.RequestMatcher

import groovy.transform.CompileStatic

/**
 * Based on the class of the same name which is a private static inner class in
 * org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter.
 *
 * @author Ben Alex
 * @author Luke Taylor
 * @author Burt Beckwith
 */
@CompileStatic
class FilterProcessUrlRequestMatcher implements RequestMatcher {

	final String filterProcessesUrl

	FilterProcessUrlRequestMatcher(String filterProcessesUrl) {
		assert filterProcessesUrl, 'filterProcessesUrl must be specified'
		assert UrlUtils.isValidRedirectUrl(filterProcessesUrl), "$filterProcessesUrl isn't a valid redirect URL"
		this.filterProcessesUrl = filterProcessesUrl
	}

	boolean matches(HttpServletRequest request) {
		String uri = request.requestURI

		int pathParamIndex = uri.indexOf(';')
		if (pathParamIndex > 0) {
			// strip everything after the first semi-colon
			uri = uri.substring(0, pathParamIndex)
		}

		request.contextPath ? uri.endsWith(request.contextPath + filterProcessesUrl) : uri.endsWith(filterProcessesUrl)
	}
}
