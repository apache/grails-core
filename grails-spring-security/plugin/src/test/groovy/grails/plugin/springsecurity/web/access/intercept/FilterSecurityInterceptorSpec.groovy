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
package grails.plugin.springsecurity.web.access.intercept

import grails.plugin.springsecurity.AbstractUnitSpec
import org.springframework.security.access.AccessDecisionManager
import org.springframework.security.access.SecurityConfig
import org.springframework.security.web.FilterInvocation
import org.springframework.security.web.access.intercept.FilterInvocationSecurityMetadataSource
import org.springframework.security.web.access.intercept.FilterSecurityInterceptor

import jakarta.servlet.FilterChain

class FilterSecurityInterceptorSpec extends AbstractUnitSpec {

    void 'doFilter delegates to the chain for public invocations when allowed'() {
        given:
        def interceptor = new FilterSecurityInterceptor(
            rejectPublicInvocations: false,
            securityMetadataSource: Stub(FilterInvocationSecurityMetadataSource) {
                getAttributes(_ as FilterInvocation) >> null
            }
        )
        FilterChain chain = Mock()

        when:
        interceptor.doFilter(request, response, chain)

        then:
        1 * chain.doFilter(request, response)
    }

    void 'doFilter consults the access decision manager before continuing the chain'() {
        given:
        def attributes = [new SecurityConfig('ROLE_USER')]
        def interceptor = new FilterSecurityInterceptor(
            rejectPublicInvocations: false,
            securityMetadataSource: Stub(FilterInvocationSecurityMetadataSource) {
                getAttributes(_ as FilterInvocation) >> attributes
            }
        )
        interceptor.accessDecisionManager = Mock(AccessDecisionManager) {
            1 * decide(null, _ as FilterInvocation, attributes)
        }
        FilterChain chain = Mock()

        when:
        interceptor.doFilter(request, response, chain)

        then:
        1 * chain.doFilter(request, response)
    }
}
