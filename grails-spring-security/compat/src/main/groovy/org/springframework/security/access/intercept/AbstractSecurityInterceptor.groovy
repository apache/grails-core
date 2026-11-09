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

import org.springframework.security.access.AccessDecisionManager
import org.springframework.security.authentication.AuthenticationManager

/**
 * Based on the class of the same name in Spring Security, removed in
 * Spring Security 7. This compatibility shim keeps the property-bag API
 * (authenticationManager, accessDecisionManager, securityMetadataSource, etc.)
 * that the Grails Spring Security plugin still relies on, so subclasses such as
 * the plugin's filter-security and method-security interceptors continue to
 * compile and run unchanged.
 */
@CompileStatic
abstract class AbstractSecurityInterceptor {

    AuthenticationManager authenticationManager
    AccessDecisionManager accessDecisionManager
    Object securityMetadataSource
    Object runAsManager
    AfterInvocationManager afterInvocationManager
    boolean alwaysReauthenticate
    boolean rejectPublicInvocations
    boolean validateConfigAttributes
    boolean publishAuthorizationSuccess
    boolean observeOncePerRequest = true

    Object obtainSecurityMetadataSource() {
        securityMetadataSource
    }
}
