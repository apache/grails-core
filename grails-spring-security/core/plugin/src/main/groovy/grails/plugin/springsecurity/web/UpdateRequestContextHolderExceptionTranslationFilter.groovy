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
package grails.plugin.springsecurity.web

import grails.async.web.AsyncGrailsWebRequest
import groovy.transform.CompileStatic
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.WebUtils
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.ExceptionTranslationFilter
import org.springframework.security.web.savedrequest.RequestCache
import org.springframework.web.context.request.RequestContextHolder

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * Replaces the current GrailsWebRequest with one that delegates to the real current instance but uses the request and
 * response from the filter chain instead of the cached instances from earlier in the chain to ensure that controllers
 * and other classes that access the request from the thread-local RequestContextHolder use the correct instances.
 *
 * @author Burt Beckwith
 */
@CompileStatic
class UpdateRequestContextHolderExceptionTranslationFilter extends ExceptionTranslationFilter {

	UpdateRequestContextHolderExceptionTranslationFilter(AuthenticationEntryPoint authenticationEntryPoint) {
		super(authenticationEntryPoint)
	}

	UpdateRequestContextHolderExceptionTranslationFilter(AuthenticationEntryPoint authenticationEntryPoint, RequestCache requestCache) {
		super(authenticationEntryPoint, requestCache)
	}

	void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) req;
		HttpServletResponse response = (HttpServletResponse) res;
		GrailsWebRequest current = (GrailsWebRequest)RequestContextHolder.requestAttributes
		if (current && !(current instanceof DelegatingGrailsWebRequest) && !(current instanceof DelegatingAsyncGrailsWebRequest)) {
			if (current instanceof AsyncGrailsWebRequest) {
				WebUtils.storeGrailsWebRequest new DelegatingAsyncGrailsWebRequest(request, response, current)
			}
			else {
				WebUtils.storeGrailsWebRequest new DelegatingGrailsWebRequest(request, response, current)
			}
		}

		super.doFilter request, response, chain
	}
}

@CompileStatic
class DelegatingGrailsWebRequest extends GrailsWebRequest {

	@Delegate
	GrailsWebRequest current

	DelegatingGrailsWebRequest(HttpServletRequest request, HttpServletResponse response, GrailsWebRequest current) {
		super(request, response, current.attributes)
	}
}

@CompileStatic
class DelegatingAsyncGrailsWebRequest extends AsyncGrailsWebRequest {

	@Delegate
	AsyncGrailsWebRequest current

	DelegatingAsyncGrailsWebRequest(HttpServletRequest request, HttpServletResponse response, AsyncGrailsWebRequest current) {
		super(request, response, current.attributes)
	}
}
