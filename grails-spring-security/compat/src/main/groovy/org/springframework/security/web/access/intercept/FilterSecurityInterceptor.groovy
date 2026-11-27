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
package org.springframework.security.web.access.intercept

import groovy.transform.CompileStatic

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.FilterConfig
import jakarta.servlet.ServletException
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse

import org.springframework.security.access.intercept.AbstractSecurityInterceptor
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.FilterInvocation

@CompileStatic
class FilterSecurityInterceptor extends AbstractSecurityInterceptor implements Filter {

    /**
     * Marks a request this interceptor has authorized, so that its later dispatches, such as the ERROR dispatch
     * that renders its error page, pass through when {@code observeOncePerRequest} is set. The attribute is the
     * one Spring Security's own {@code FilterSecurityInterceptor} used before Spring Security 7 removed it.
     */
    private static final String FILTER_APPLIED = '__spring_security_filterSecurityInterceptor_filterApplied'

    @Override
    void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (observeOncePerRequest) {
            if (request.getAttribute(FILTER_APPLIED) != null) {
                chain.doFilter(request, response)
                return
            }
            request.setAttribute(FILTER_APPLIED, Boolean.TRUE)
        }
        def invocation = new FilterInvocation(request, response, chain)
        def attributes = ((FilterInvocationSecurityMetadataSource) securityMetadataSource)?.getAttributes(invocation)
        if (attributes == null) {
            if (rejectPublicInvocations) {
                throw new IllegalStateException('Public invocations are not allowed')
            }
            chain.doFilter(request, response)
            return
        }

        Authentication authentication = SecurityContextHolder.context?.authentication
        if (authentication == null) {
            throw new AuthenticationCredentialsNotFoundException('An Authentication object was not found in the SecurityContext')
        }
        accessDecisionManager?.decide(authenticateIfRequired(authentication), invocation, attributes)
        if (!invocation.response.committed) {
            chain.doFilter(request, response)
        }
    }

    /**
     * Authenticates, for a request that has security attributes, what a filter left in the context without doing so,
     * as {@code DigestAuthenticationFilter} does so that the account checks of the authentication manager still run.
     * A request without security attributes is passed on before this, so nothing is authenticated for it, as Spring
     * Security's own {@code AbstractSecurityInterceptor} did for public invocations before Spring Security 7 removed it.
     *
     * <p>With {@code alwaysReauthenticate}, what is authenticated already is authenticated again too, but only while it
     * still holds its credentials. The authentication manager erases them by default once it has authenticated it, as
     * for a form login kept in the session, and such an authentication, with nothing left to authenticate with, is
     * returned as it is.</p>
     */
    private Authentication authenticateIfRequired(Authentication authentication) {
        if (authentication.authenticated && (!alwaysReauthenticate || authentication.credentials == null)) {
            return authentication
        }
        Authentication authenticated = authenticationManager.authenticate(authentication)
        SecurityContext context = SecurityContextHolder.createEmptyContext()
        context.authentication = authenticated
        SecurityContextHolder.context = context
        authenticated
    }

    @Override
    void destroy() {
    }
}
