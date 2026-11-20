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

import org.springframework.security.access.ConfigAttribute
import org.springframework.security.access.hierarchicalroles.RoleHierarchy
import org.springframework.security.core.Authentication

@CompileStatic
class RoleHierarchyVoter extends RoleVoter {

    final RoleHierarchy roleHierarchy

    RoleHierarchyVoter(RoleHierarchy roleHierarchy) {
        this.roleHierarchy = roleHierarchy
    }

    @Override
    int vote(Authentication authentication, Object object, Collection<ConfigAttribute> attributes) {
        if (authentication == null) {
            return ACCESS_DENIED
        }
        def reachable = roleHierarchy == null ?
                authentication.authorities :
                roleHierarchy.getReachableGrantedAuthorities(authentication.authorities)
        def authorities = reachable*.authority as Set<String>
        int result = ACCESS_ABSTAIN
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
