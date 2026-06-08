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

import groovy.util.logging.Slf4j

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest

import org.springframework.security.authentication.AuthenticationDetailsSource
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.GenericFilterBean

import grails.plugin.springsecurity.authentication.GrailsAnonymousAuthenticationToken
import groovy.transform.CompileStatic

/**
 * Replaces org.springframework.security.web.authentication.AnonymousAuthenticationFilter.
 *
 * @author Burt Beckwith
 */
@Slf4j
@CompileStatic
class GrailsAnonymousAuthenticationFilter extends GenericFilterBean {

	/** Dependency injection for authenticationDetailsSource. */
	AuthenticationDetailsSource<HttpServletRequest, ?> authenticationDetailsSource

	/** Dependency injection for the key. */
	String key

	@Override
	void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
		applyAnonymousForThisRequest((HttpServletRequest)req)
		chain.doFilter req, res
	}

	protected void applyAnonymousForThisRequest(HttpServletRequest request) {
		SecurityContext context = SecurityContextHolder.context
		if (!context.authentication) {
			context.authentication = createAuthentication(request)
			log.debug "Populated SecurityContextHolder with anonymous token: '{}'", context.authentication
		}
		else {
			log.debug "SecurityContextHolder not populated with anonymous token, as it already contained: '{}'", context.authentication
		}
	}

	protected Authentication createAuthentication(HttpServletRequest request) {
		new GrailsAnonymousAuthenticationToken(key, authenticationDetailsSource.buildDetails(request))
	}

   @Override
	void afterPropertiesSet() throws ServletException {
   	super.afterPropertiesSet()
   	assert authenticationDetailsSource, 'authenticationDetailsSource must be set'
   	assert key, 'key must be set'
   }
}
