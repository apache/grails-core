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
package grails.plugin.springsecurity.web.access.expression

import org.springframework.expression.EvaluationContext
import org.springframework.security.access.AccessDecisionVoter
import org.springframework.security.access.ConfigAttribute
import org.springframework.security.access.expression.ExpressionUtils
import org.springframework.security.access.expression.SecurityExpressionHandler
import org.springframework.security.core.Authentication
import org.springframework.security.web.FilterInvocation

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Based on the class of the same name in Spring Security which uses the
 * package-default WebExpressionConfigAttribute.
 *
 * @author Luke Taylor
 * @author Burt Beckwith
 */
@CompileStatic
@Slf4j
class WebExpressionVoter implements AccessDecisionVoter<FilterInvocation> {

	/** Dependency injection for the expression handler. */
	SecurityExpressionHandler<FilterInvocation> expressionHandler

	int vote(Authentication authentication, FilterInvocation fi, Collection<ConfigAttribute> attributes) {
		assert authentication, 'authentication cannot be null'
		assert fi, 'object cannot be null'
		assert attributes, 'attributes cannot be null'

		log.trace 'vote() Authentication {}, FilterInvocation {} ConfigAttributes {}', authentication, fi, attributes

		WebExpressionConfigAttribute weca = findConfigAttribute(attributes)
		if (!weca) {
			log.trace 'No WebExpressionConfigAttribute found'
			return ACCESS_ABSTAIN
		}

		EvaluationContext ctx = expressionHandler.createEvaluationContext(authentication, fi)

		ExpressionUtils.evaluateAsBoolean(weca.authorizeExpression, ctx) ? ACCESS_GRANTED : ACCESS_DENIED
	}

	protected WebExpressionConfigAttribute findConfigAttribute(Collection<ConfigAttribute> attributes) {
		(WebExpressionConfigAttribute)attributes.find { ConfigAttribute attribute -> attribute instanceof WebExpressionConfigAttribute }
	}

	boolean supports(ConfigAttribute attribute) {
		attribute instanceof WebExpressionConfigAttribute
	}

	boolean supports(Class<?> clazz) {
		clazz.isAssignableFrom FilterInvocation
	}
}
