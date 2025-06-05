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

import org.springframework.beans.factory.InitializingBean
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.ExceptionMappingAuthenticationFailureHandler

import grails.plugin.springsecurity.SpringSecurityUtils
import groovy.transform.CompileStatic

/**
 * Ajax-aware failure handler that detects failed Ajax logins and redirects to the appropriate URL.
 *
 * @author Burt Beckwith
 */
@CompileStatic
class AjaxAwareAuthenticationFailureHandler extends ExceptionMappingAuthenticationFailureHandler implements InitializingBean {

	/** Dependency injection for the Ajax auth fail url. */
	String ajaxAuthenticationFailureUrl

	@Override
	void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException, ServletException {

		if (SpringSecurityUtils.isAjax(request)) {
			saveException request, exception
			redirectStrategy.sendRedirect request, response, ajaxAuthenticationFailureUrl
		}
		else {
			super.onAuthenticationFailure request, response, exception
		}
	}

	/**
	 * Dependency injection for the exception -> url mappings; each map has an 'exception' key and a 'url' key, and
	 * all are merged into one map, where each key is an exception name and each value is the url.
	 * @param mappings list of single-entry maps
	 */
	void setExceptionMappings(List<Map<String, ?>> mappings) {
		super.setExceptionMappings((Map)mappings.inject([:], { LinkedHashMap map, Map mapping -> map[mapping.exception] = mapping.url; map }))
	}

	void setExceptionMappingsList(List<Map<String, ?>> mappings) {
		super.setExceptionMappings((Map)mappings.inject([:], { LinkedHashMap map, Map mapping -> map[mapping.exception] = mapping.url; map }))
	}

	void afterPropertiesSet() {
		assert ajaxAuthenticationFailureUrl, 'ajaxAuthenticationFailureUrl is required'
	}
}
