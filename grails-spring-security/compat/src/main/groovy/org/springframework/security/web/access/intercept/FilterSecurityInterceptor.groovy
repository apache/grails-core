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
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.FilterInvocation

@CompileStatic
class FilterSecurityInterceptor extends AbstractSecurityInterceptor implements Filter {

    @Override
    void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        def invocation = new FilterInvocation(request, response, chain)
        def attributes = ((FilterInvocationSecurityMetadataSource) securityMetadataSource)?.getAttributes(invocation)
        if (attributes == null) {
            if (rejectPublicInvocations) {
                throw new IllegalStateException('Public invocations are not allowed')
            }
            chain.doFilter(request, response)
            return
        }

        def authentication = SecurityContextHolder.context?.authentication
        accessDecisionManager?.decide(authentication, invocation, attributes)
        if (!invocation.response.committed) {
            chain.doFilter(request, response)
        }
    }

    @Override
    void destroy() {
    }
}
