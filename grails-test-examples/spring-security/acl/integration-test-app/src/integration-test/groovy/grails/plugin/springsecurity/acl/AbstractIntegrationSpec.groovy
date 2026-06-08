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
package grails.plugin.springsecurity.acl

import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User
import spock.lang.Specification
import test.Report

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
abstract class AbstractIntegrationSpec extends Specification {

	protected static final String USER = 'user'
	protected static final String ADMIN = 'admin'
	protected static final String ROLE_USER = 'ROLE_USER'
	protected static final String ROLE_ADMIN = 'ROLE_ADMIN'

	void cleanup() {
		SecurityContextHolder.clearContext()
	}

	protected Authentication authenticateAsAdmin() {
		authenticate ADMIN, ROLE_ADMIN
	}

	protected Authentication authenticateAsUser(boolean makeCurrent = true) {
		authenticate USER, ROLE_USER, makeCurrent
	}

	protected Authentication authenticate(String username = 'username', String role, boolean makeCurrent = true) {
		def authorities = [new SimpleGrantedAuthority(role)]
		def principal = new User(username, 'password', true, true, true, true, authorities)
		Authentication authentication = new TestingAuthenticationToken(principal, 'password', authorities)
		authentication.authenticated = true
		if (makeCurrent) {
			SecurityContextHolder.context.authentication = authentication
		}
		authentication
	}

	protected void flushAndClear() {
		Report.withSession { session ->
			session.flush()
			session.clear()
		}
	}
}
