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
package org.springframework.security.access.intercept.aopalliance

import groovy.transform.CompileStatic

import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation

import org.springframework.security.access.intercept.AbstractSecurityInterceptor
import org.springframework.security.access.intercept.RunAsManager
import org.springframework.security.access.method.MethodSecurityMetadataSource
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.core.context.SecurityContextHolder

@CompileStatic
class MethodSecurityInterceptor extends AbstractSecurityInterceptor implements MethodInterceptor {

    @Override
    Object invoke(MethodInvocation invocation) throws Throwable {
        def metadataSource = securityMetadataSource as MethodSecurityMetadataSource
        def attributes = metadataSource?.getAttributes(invocation.method, invocation.this?.class)
        if (attributes == null) {
            return invocation.proceed()
        }

        def authentication = SecurityContextHolder.context?.authentication
        if (authentication == null) {
            throw new AuthenticationCredentialsNotFoundException(
                    'An Authentication object was not found in the SecurityContext'
            )
        }
        accessDecisionManager?.decide(authentication, invocation, attributes)

        def runAsAuthentication = (runAsManager instanceof RunAsManager) ?
            ((RunAsManager) runAsManager).buildRunAs(authentication, invocation, attributes) : null
        def activeAuthentication = runAsAuthentication ?: authentication

        if (runAsAuthentication != null) {
            SecurityContextHolder.context.authentication = runAsAuthentication
        }

        try {
            def returnedObject = invocation.proceed()
            return afterInvocationManager == null ?
                    returnedObject :
                    afterInvocationManager.decide(activeAuthentication, invocation, attributes, returnedObject)
        }
        finally {
            if (runAsAuthentication != null) {
                SecurityContextHolder.context.authentication = authentication
            }
        }
    }
}
