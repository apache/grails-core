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

import grails.plugin.springsecurity.SecurityTestUtils
import org.aopalliance.aop.Advice
import org.aopalliance.intercept.MethodInvocation
import org.springframework.security.access.AccessDecisionManager
import org.springframework.security.access.SecurityConfig
import org.springframework.security.access.intercept.AfterInvocationManager
import org.springframework.security.access.intercept.RunAsManagerImpl
import org.springframework.security.access.method.MethodSecurityMetadataSource
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import spock.lang.Specification

class MethodSecurityInterceptorSpec extends Specification {

    void cleanup() {
        SecurityTestUtils.logout()
    }

    void 'invoke acts as advice and applies access and after-invocation processing'() {
        given:
        Authentication authentication = SecurityTestUtils.authenticate(['ROLE_USER'])
        def method = String.getMethod('trim')
        def invocation = Stub(MethodInvocation) {
            getMethod() >> method
            getThis() >> '  ok  '
            proceed() >> 'ok'
        }
        def attributes = [new SecurityConfig('ROLE_USER')]
        def interceptor = new MethodSecurityInterceptor(
            securityMetadataSource: Stub(MethodSecurityMetadataSource) {
                getAttributes(method, String) >> attributes
            },
            accessDecisionManager: Mock(AccessDecisionManager) {
                1 * decide(authentication, invocation, attributes)
            },
            afterInvocationManager: Mock(AfterInvocationManager) {
                1 * decide(authentication, invocation, attributes, 'ok') >> 'filtered'
            }
        )

        expect:
        interceptor instanceof Advice
        interceptor.invoke(invocation) == 'filtered'
    }

    void 'invoke proceeds directly when there are no security attributes'() {
        given:
        def method = String.getMethod('trim')
        def invocation = Stub(MethodInvocation) {
            getMethod() >> method
            getThis() >> '  ok  '
            proceed() >> 'ok'
        }
        def interceptor = new MethodSecurityInterceptor(
            securityMetadataSource: Stub(MethodSecurityMetadataSource) {
                getAttributes(method, String) >> null
            }
        )

        expect:
        interceptor.invoke(invocation) == 'ok'
    }

    void 'invoke fails with credentials not found when secured invocation has no authentication'() {
        given:
        def method = String.getMethod('trim')
        def invocation = Stub(MethodInvocation) {
            getMethod() >> method
            getThis() >> '  ok  '
        }
        def interceptor = new MethodSecurityInterceptor(
            securityMetadataSource: Stub(MethodSecurityMetadataSource) {
                getAttributes(method, String) >> [new SecurityConfig('ROLE_USER')]
            }
        )

        when:
        interceptor.invoke(invocation)

        then:
        thrown AuthenticationCredentialsNotFoundException
    }

    void 'invoke applies run-as authentication during the secured invocation and restores the original authentication afterwards'() {
        given:
        Authentication authentication = SecurityTestUtils.authenticate(['ROLE_ADMIN'])
        def method = String.getMethod('trim')
        def attributes = [new SecurityConfig('ROLE_ADMIN'), new SecurityConfig('RUN_AS_SUPERUSER')]
        def interceptor = new MethodSecurityInterceptor(
            securityMetadataSource: Stub(MethodSecurityMetadataSource) {
                getAttributes(method, String) >> attributes
            },
            accessDecisionManager: Mock(AccessDecisionManager) {
                1 * decide(authentication, _ as MethodInvocation, attributes)
            },
            runAsManager: new RunAsManagerImpl(),
            afterInvocationManager: Mock(AfterInvocationManager) {
                1 * decide(_ as Authentication, _ as MethodInvocation, attributes, 'ok') >> { Authentication activeAuthentication, MethodInvocation ignored, Collection ignoredAttributes, Object returnedObject ->
                    assert activeAuthentication.authorities*.authority.contains('ROLE_RUN_AS_SUPERUSER')
                    return returnedObject
                }
            }
        )
        def invocation = Stub(MethodInvocation) {
            getMethod() >> method
            getThis() >> '  ok  '
            proceed() >> {
                assert SecurityContextHolder.context.authentication.authorities*.authority.contains('ROLE_RUN_AS_SUPERUSER')
                'ok'
            }
        }

        when:
        def result = interceptor.invoke(invocation)

        then:
        result == 'ok'
        SecurityContextHolder.context.authentication.is(authentication)
        !SecurityContextHolder.context.authentication.authorities*.authority.contains('ROLE_RUN_AS_SUPERUSER')
    }
}
