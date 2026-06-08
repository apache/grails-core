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

import grails.plugin.springsecurity.web.SecurityRequestHolder
import grails.testing.web.GrailsWebUnitTest
import org.springframework.security.access.vote.AuthenticatedVoter
import org.springframework.security.access.vote.RoleVoter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.access.expression.DefaultWebSecurityExpressionHandler
import spock.lang.Specification

/**
 * @author Burt Beckwith
 */
abstract class AbstractUnitSpec extends Specification implements GrailsWebUnitTest {

	void setupSpec() {
		defineBeans {
			webExpressionHandler(DefaultWebSecurityExpressionHandler)
			roleVoter(RoleVoter)
			authenticatedVoter(AuthenticatedVoter)
		}
	}

	void setup() {
		ReflectionUtils.application = SpringSecurityUtils.application = grailsApplication
	}

	void cleanup() {
		SecurityContextHolder.clearContext()
		SecurityRequestHolder.reset()
		SecurityTestUtils.logout()
		SpringSecurityUtils.resetSecurityConfig()
		ReflectionUtils.application = null
		SpringSecurityUtils.application = null
	}
}
