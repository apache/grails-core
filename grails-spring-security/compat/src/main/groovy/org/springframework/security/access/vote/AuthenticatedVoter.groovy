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
package org.springframework.security.access.vote

import groovy.transform.CompileStatic

import org.springframework.security.access.AccessDecisionVoter
import org.springframework.security.access.ConfigAttribute
import org.springframework.security.authentication.AuthenticationTrustResolver
import org.springframework.security.authentication.AuthenticationTrustResolverImpl
import org.springframework.security.core.Authentication

@CompileStatic
class AuthenticatedVoter implements AccessDecisionVoter<Object> {

    static final String IS_AUTHENTICATED_FULLY = 'IS_AUTHENTICATED_FULLY'
    static final String IS_AUTHENTICATED_REMEMBERED = 'IS_AUTHENTICATED_REMEMBERED'
    static final String IS_AUTHENTICATED_ANONYMOUSLY = 'IS_AUTHENTICATED_ANONYMOUSLY'

    AuthenticationTrustResolver authenticationTrustResolver = new AuthenticationTrustResolverImpl()

    @Override
    boolean supports(ConfigAttribute attribute) {
        attribute?.attribute in [IS_AUTHENTICATED_FULLY, IS_AUTHENTICATED_REMEMBERED, IS_AUTHENTICATED_ANONYMOUSLY]
    }

    @Override
    boolean supports(Class<?> clazz) {
        true
    }

    @Override
    int vote(Authentication authentication, Object object, Collection<ConfigAttribute> attributes) {
        int result = ACCESS_ABSTAIN
        for (def attribute : attributes) {
            if (!supports(attribute)) {
                continue
            }
            result = ACCESS_DENIED
            if (IS_AUTHENTICATED_ANONYMOUSLY == attribute.attribute) {
                return ACCESS_GRANTED
            }
            if (authentication == null) {
                continue
            }
            if (IS_AUTHENTICATED_REMEMBERED == attribute.attribute && authenticationTrustResolver.isRememberMe(authentication)) {
                return ACCESS_GRANTED
            }
            if (authentication.isAuthenticated()) {
                if (IS_AUTHENTICATED_FULLY == attribute.attribute && !authenticationTrustResolver.isAnonymous(authentication) && !authenticationTrustResolver.isRememberMe(authentication)) {
                    return ACCESS_GRANTED
                }
                if (IS_AUTHENTICATED_REMEMBERED == attribute.attribute && !authenticationTrustResolver.isAnonymous(authentication)) {
                    return ACCESS_GRANTED
                }
            }
        }
        result
    }
}
