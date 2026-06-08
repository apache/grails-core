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

import org.springframework.security.access.event.AbstractAuthorizationEvent
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent
import org.springframework.security.authentication.event.AuthenticationSuccessEvent
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent
import org.springframework.security.core.userdetails.User
import org.springframework.security.web.authentication.switchuser.AuthenticationSwitchUserEvent

/**
 * Unit tests for SecurityEventListener.
 *
 * @author Burt Beckwith
 */
class SecurityEventListenerSpec extends AbstractUnitSpec {

	private SecurityEventListener listener = new SecurityEventListener()
	private closures = new ConfigObject()

	void setup() {
		SpringSecurityUtils.securityConfig = closures
	}

	void 'Test handling InteractiveAuthenticationSuccessEvent'() {
		when:
		boolean called = false
		closures.onInteractiveAuthenticationSuccessEvent = { e, appCtx -> called = true }

		listener.onApplicationEvent(new InteractiveAuthenticationSuccessEvent(
				new TestingAuthenticationToken('', ''), getClass()))

		then:
		called
	}

	void 'Test handling AbstractAuthenticationFailureEvent'() {
		when:
		boolean called = false
		closures.onAbstractAuthenticationFailureEvent = { e, appCtx -> called = true }

		listener.onApplicationEvent new AuthenticationFailureBadCredentialsEvent(
				new TestingAuthenticationToken('', ''), new BadCredentialsException('bad credentials'))

		then:
		called
	}

	void 'Test handling AuthenticationSuccessEvent'() {
		when:
		boolean called = false
		closures.onAuthenticationSuccessEvent = { e, appCtx -> called = true }

		listener.onApplicationEvent(new AuthenticationSuccessEvent(
				new TestingAuthenticationToken('', '')))

		then:
		called
	}

	void 'Test handling AbstractAuthorizationEvent'() {
		when:
		boolean called = false
		closures.onAuthorizationEvent = { e, appCtx -> called = true }

		listener.onApplicationEvent new AbstractAuthorizationEvent(42) {}

		then:
		called
	}

	void 'Test handling AuthenticationSwitchUserEvent'() {
		when:
		boolean called = false
		closures.onAuthenticationSwitchUserEvent = { e, appCtx -> called = true }

		def authentication = SecurityTestUtils.authenticate(['ROLE_FOO'])
		def targetUser = new User('username', 'password', true, true, true,
				true, authentication.authorities)

		listener.onApplicationEvent(new AuthenticationSwitchUserEvent(authentication, targetUser))

		then:
		called
	}
}
