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
package grails.plugin.springsecurity

import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder as SCH

import java.lang.reflect.Modifier

/**
 * Utility methods for security unit tests.
 *
 * @author Burt Beckwith
 */
class SecurityTestUtils {

	/**
	 * Register a currently authenticated user.
	 * @return the authentication
	 */
	static Authentication authenticate() {
		authenticate null, null, null
	}

	/**
	 * Register a currently authenticated user.
	 *
	 * @param principal the principal
	 * @param credentials the password
	 * @param authorities the roles
	 * @return the authentication
	 */
	static Authentication authenticate(principal, credentials, List<GrantedAuthority> authorities) {
		Authentication authentication = authorities != null ? new TestingAuthenticationToken(principal, credentials, authorities) :  new TestingAuthenticationToken(principal, credentials)
		authentication.authenticated = true
		SCH.context.authentication = authentication
		authentication
	}

	static Authentication authenticate(roleNames) {
		authenticate null, null, roleNames.collect { new SimpleGrantedAuthority(it) }
	}

	/**
	 * De-register the currently authenticated user.
	 */
	static void logout() {
		SCH.clearContext()
	}

	static boolean testPrivateConstructor(Class clazz) {
		assert 1 == clazz.declaredConstructors.length
		def constructor = clazz.getDeclaredConstructor()
		assert Modifier.isPrivate(constructor.modifiers)
		assert !constructor.accessible
		constructor.accessible = true
		constructor.newInstance()
		true
	}
}
