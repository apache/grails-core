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
import org.springframework.security.core.Authentication

@CompileStatic
class RoleVoter implements AccessDecisionVoter<Object> {

    String rolePrefix = 'ROLE_'

    @Override
    boolean supports(ConfigAttribute attribute) {
        String candidate = attribute?.attribute
        candidate != null && candidate.startsWith(rolePrefix)
    }

    @Override
    boolean supports(Class<?> clazz) {
        true
    }

    @Override
    int vote(Authentication authentication, Object object, Collection<ConfigAttribute> attributes) {
        if (authentication == null) {
            return ACCESS_DENIED
        }
        int result = ACCESS_ABSTAIN
        def authorities = authentication.authorities*.authority as Set<String>
        for (def attribute : attributes) {
            if (!supports(attribute)) {
                continue
            }
            result = ACCESS_DENIED
            if (authorities.contains(attribute.attribute)) {
                return ACCESS_GRANTED
            }
        }
        result
    }
}
