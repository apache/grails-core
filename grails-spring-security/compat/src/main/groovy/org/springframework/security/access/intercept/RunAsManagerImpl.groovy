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
package org.springframework.security.access.intercept

import groovy.transform.CompileStatic

import org.springframework.security.access.ConfigAttribute
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority

@CompileStatic
class RunAsManagerImpl implements RunAsManager {

    String key
    String rolePrefix = 'ROLE_'
    String runAsPrefix = 'RUN_AS_'

    @Override
    Authentication buildRunAs(Authentication authentication, Object object, Collection<ConfigAttribute> attributes) {
        if (authentication == null || attributes == null) {
            return null
        }

        def runAsAuthorities = attributes
            .findAll { it?.attribute?.startsWith(runAsPrefix) }
            .collect { new SimpleGrantedAuthority(rolePrefix + it.attribute) } as List<GrantedAuthority>

        if (!runAsAuthorities) {
            return null
        }

        def currentAuthorities = authentication.authorities == null ?
                Collections.<GrantedAuthority>emptyList() :
                authentication.authorities
        def mergedAuthorities = new LinkedHashSet<GrantedAuthority>(currentAuthorities).tap {
            addAll(runAsAuthorities)
        }

        def runAsAuthentication = new UsernamePasswordAuthenticationToken(
            authentication.principal,
            authentication.credentials,
            new ArrayList<GrantedAuthority>(mergedAuthorities)
        )
        runAsAuthentication.details = authentication.details
        runAsAuthentication
    }
}
