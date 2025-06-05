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

import grails.util.GrailsUtil
import groovy.transform.TypeChecked
import groovy.util.logging.Slf4j
import org.springframework.security.web.FilterChainProxy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.util.UrlUtils
import org.springframework.web.filter.GenericFilterBean

import jakarta.servlet.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession

/**
 * Based on the package-scope org.springframework.security.config.debug.DebugFilter.
 *
 * @author Luke Taylor
 * @author Rob Winch
 * @author Burt Beckwith
 */
@Slf4j
@TypeChecked
class DebugFilter extends GenericFilterBean {

	protected static final String ALREADY_FILTERED_ATTR_NAME = DebugFilter.name + '.FILTERED'
	protected static final String JAVA_LANG_EXCEPTION = 'java.lang.Exception'
	protected static final int JAVA_LANG_EXCEPTION_LENGTH = JAVA_LANG_EXCEPTION.length()

	final FilterChainProxy filterChainProxy

	DebugFilter(FilterChainProxy fcp) {
		filterChainProxy = fcp
	}

	void doFilter(ServletRequest req, ServletResponse res, FilterChain filterChain) throws ServletException, IOException {

		HttpServletRequest request = (HttpServletRequest)req
		HttpServletResponse response = (HttpServletResponse)res

		List<Filter> filters = getFilters(request)
		debugLog false, "Request received for '{}':\n\n{}\n\nservletPath:{}\npathInfo:{}\n\n{}",
				UrlUtils.buildRequestUrl(request), request, request.servletPath, request.pathInfo, formatFilters(filters)

		if (!request.getAttribute(ALREADY_FILTERED_ATTR_NAME)) {
			invokeWithWrappedRequest request, response, filterChain
		}
		else {
			filterChainProxy.doFilter request, response, filterChain
		}
	}

	protected void invokeWithWrappedRequest(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws IOException, ServletException {

		request.setAttribute ALREADY_FILTERED_ATTR_NAME, true

		request = new HttpServletRequestWrapper(request) {

			@Override
			HttpSession getSession() {
				boolean sessionExists = super.getSession(false)
				HttpSession session = super.session

				if (!sessionExists) {
					debugLog true, 'New HTTP session created: {}', session.id
				}

				session
			}

			@Override
			HttpSession getSession(boolean create) {
				create ? session : super.getSession(false)
			}
		}

		try {
			filterChainProxy.doFilter request, response, filterChain
		}
		finally {
			request.removeAttribute ALREADY_FILTERED_ATTR_NAME
		}
	}

	protected String formatFilters(List<Filter> filters) {
		StringBuilder sb = new StringBuilder('Security filter chain: ')
		if (filters == null) {
			sb << 'no match'
		}
		else if (filters.empty) {
			sb << "[] empty (bypassed by security='none') "
		}
		else {
			sb << '[\n'
			filters.each { Filter f -> sb << '  ' << f.getClass().simpleName << '\n' }
			sb << ']'
		}

		sb
	}

	protected List<Filter> getFilters(HttpServletRequest request) {
		filterChainProxy.filterChains.find({ SecurityFilterChain chain -> chain.matches(request) })?.filters
	}

	protected void debugLog(boolean dumpStack, String message, Object... args) {
		StringBuilder output = new StringBuilder(256)
		output << '\n\n************************************************************\n\n'
		output << message << '\n'

		if (dumpStack) {
			StringWriter os = new StringWriter()
			GrailsUtil.deepSanitize(new Exception()).printStackTrace(new PrintWriter(os))
			StringBuffer buffer = os.buffer
			// Remove the exception in case it scares people.
			int start = buffer.indexOf(JAVA_LANG_EXCEPTION)
			buffer.replace start, start + JAVA_LANG_EXCEPTION_LENGTH, ''
			output << '\nCall stack: \n' << os
		}

		output << '\n\n************************************************************\n\n'
		log.info output.toString(), args
	}
}
