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
package grails.plugin.springsecurity.access.vote

import org.springframework.security.access.AccessDecisionVoter
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.ConfigAttribute
import org.springframework.security.access.vote.AbstractAccessDecisionManager
import org.springframework.security.access.vote.AuthenticatedVoter
import org.springframework.security.authentication.InsufficientAuthenticationException
import org.springframework.security.core.Authentication

import groovy.transform.CompileStatic

/**
 * Uses the affirmative-based logic for roles, i.e. any in the list will grant access, but allows
 * an authenticated voter to 'veto' access. This allows specification of roles and
 * <code>IS_AUTHENTICATED_FULLY</code> on one line in SecurityConfig.groovy.
 *
 * @author Burt Beckwith
 */
@CompileStatic
class AuthenticatedVetoableDecisionManager extends AbstractAccessDecisionManager {

	AuthenticatedVetoableDecisionManager(List<AccessDecisionVoter> decisionVoters) {
		super(decisionVoters)
	}

	void decide(Authentication authentication, object, Collection<ConfigAttribute> configAttributes)
			throws AccessDeniedException, InsufficientAuthenticationException {

		boolean authenticatedVotersGranted = checkAuthenticatedVoters(authentication, object, configAttributes)
		boolean otherVotersGranted = checkOtherVoters(authentication, object, configAttributes)

		logger.trace "decide(): authenticatedVotersGranted=$authenticatedVotersGranted otherVotersGranted=$otherVotersGranted"

		if (!authenticatedVotersGranted && !otherVotersGranted) {
			checkAllowIfAllAbstainDecisions()
		}
	}

	/**
	 * Allow any {@link AuthenticatedVoter} to veto. If any voter denies,
	 * throw an exception; if any grant, return <code>true</code>;
	 * otherwise return <code>false</code> if all abstain.
	 */
	@SuppressWarnings(['rawtypes', 'unchecked'])
	protected boolean checkAuthenticatedVoters(Authentication authentication, object, Collection<ConfigAttribute> configAttributes) {

		boolean grant = false
		for (AccessDecisionVoter voter in decisionVoters) {
			if (voter instanceof AuthenticatedVoter) {
                boolean voterSupportsSecurityContext = object == null || voter.supports(object.class)
                if (voterSupportsSecurityContext) {
                    int result = voter.vote(authentication, object, configAttributes)
                    switch (result) {
                        case AccessDecisionVoter.ACCESS_GRANTED: grant = true; break
                        case AccessDecisionVoter.ACCESS_DENIED: deny(); break
                        default: break // abstain
                    }
                }
			}
		}

		grant
	}

	/**
	 * Check the other (non-{@link AuthenticatedVoter}) voters. If any voter grants,
	 * return true. If any voter denies, throw exception. Otherwise return <code>false</code>
	 * to indicate that all abstained.
	 */
	@SuppressWarnings(['rawtypes', 'unchecked'])
	protected boolean checkOtherVoters(Authentication authentication, object, Collection<ConfigAttribute> configAttributes) {
		int denyCount = 0
		for (AccessDecisionVoter voter in getDecisionVoters()) {
			if (voter instanceof AuthenticatedVoter) {
				continue
			}
            boolean voterSupportsSecurityContext = object == null || voter.supports(object.class)
            if (voterSupportsSecurityContext) {
                int result = voter.vote(authentication, object, configAttributes)
                switch (result) {
                    case AccessDecisionVoter.ACCESS_GRANTED: return true
                    case AccessDecisionVoter.ACCESS_DENIED: denyCount++; break
                    default: break // abstain
                }
            }
		}

		if (denyCount) {
			deny()
		}

		// all abstain
		false
	}

	protected void deny() {
		throw new AccessDeniedException(messages.getMessage('AbstractAccessDecisionManager.accessDenied', 'Access is denied'))
	}
}
