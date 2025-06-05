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

import grails.plugin.springsecurity.SpringSecurityUtils
import groovy.transform.CompileStatic
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession

/**
 * Extends the default {@link UsernamePasswordAuthenticationFilter} to store the
 * last attempted login username in the session under the 'SPRING_SECURITY_LAST_USERNAME'
 * key if storeLastUsername is true.
 *
 * @author Burt Beckwith
 */
@CompileStatic
class GrailsUsernamePasswordAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

	/** Whether to store the last attempted username in the session. */
	Boolean storeLastUsername

	@Override
	Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {

		if (storeLastUsername) {
			// Place the last username attempted into HttpSession for views
			HttpSession session = request.getSession(false)
			if (!session && allowSessionCreation) {
				session = request.session
			}

			session?.setAttribute SpringSecurityUtils.SPRING_SECURITY_LAST_USERNAME_KEY, (obtainUsername(request) ?: '').trim()
		}

		super.attemptAuthentication request, response
	}

	@Override
	void afterPropertiesSet() {
		super.afterPropertiesSet()
		assert storeLastUsername != null, 'storeLastUsername must be set'
	}
}
