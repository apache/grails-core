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
package org.springframework.security.web.access.channel

import groovy.transform.CompileStatic

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse

import org.springframework.security.web.FilterInvocation
import org.springframework.security.web.access.intercept.FilterInvocationSecurityMetadataSource
import org.springframework.web.filter.GenericFilterBean

@CompileStatic
class ChannelProcessingFilter extends GenericFilterBean {

    ChannelDecisionManagerImpl channelDecisionManager
    FilterInvocationSecurityMetadataSource securityMetadataSource

    @Override
    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        def invocation = new FilterInvocation(request, response, chain)
        channelDecisionManager.decide(invocation, securityMetadataSource?.getAttributes(invocation))
        if (invocation.response.committed) {
            return
        }
        chain.doFilter(request, response)
    }
}
