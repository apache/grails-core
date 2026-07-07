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
package org.springframework.security.access

import groovy.transform.CompileStatic

import org.springframework.security.core.Authentication

/**
 * Compatibility layer for Spring Security 7 removals used by the plugin.
 */
@CompileStatic
interface ConfigAttribute extends Serializable {

    String getAttribute()
}

@CompileStatic
class SecurityConfig implements ConfigAttribute {

    private static final long serialVersionUID = 1L

    final String attribute

    SecurityConfig(String attribute) {
        this.attribute = attribute
    }

    @Override
    String getAttribute() {
        attribute
    }

    static List<ConfigAttribute> createList(String... attributes) {
        attributes.collect { new SecurityConfig(it) } as List<ConfigAttribute>
    }

    @Override
    String toString() {
        attribute
    }
}

@CompileStatic
interface AccessDecisionVoter<T> {

    int ACCESS_GRANTED = 1
    int ACCESS_ABSTAIN = 0
    int ACCESS_DENIED = -1

    boolean supports(ConfigAttribute attribute)
    boolean supports(Class<?> clazz)
    int vote(Authentication authentication, T object, Collection<ConfigAttribute> attributes)
}

@CompileStatic
interface AccessDecisionManager {

    void decide(Authentication authentication, Object object, Collection<ConfigAttribute> configAttributes)
    boolean supports(ConfigAttribute attribute)
    boolean supports(Class<?> clazz)
}

@CompileStatic
interface AfterInvocationProvider {

    Object decide(Authentication authentication, Object object, Collection<ConfigAttribute> configAttributes, Object returnedObject)
    boolean supports(ConfigAttribute attribute)
    boolean supports(Class<?> clazz)
}
