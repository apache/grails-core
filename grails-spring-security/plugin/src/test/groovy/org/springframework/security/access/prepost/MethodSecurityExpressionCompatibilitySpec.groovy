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
package org.springframework.security.access.prepost

import grails.plugin.springsecurity.SecurityTestUtils
import org.aopalliance.intercept.MethodInvocation
import org.springframework.security.access.PermissionEvaluator
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler
import org.springframework.security.access.expression.method.ExpressionBasedAnnotationAttributeFactory
import org.springframework.security.access.expression.method.ExpressionBasedPostInvocationAdvice
import org.springframework.security.access.expression.method.ExpressionBasedPreInvocationAdvice
import org.springframework.security.core.Authentication
import org.springframework.security.core.parameters.P
import spock.lang.Specification

class MethodSecurityExpressionCompatibilitySpec extends Specification {

    void cleanup() {
        SecurityTestUtils.logout()
    }

    void 'metadata source extracts pre and post annotations from secured methods'() {
        given:
        def metadataSource = new PrePostAnnotationSecurityMetadataSource(
                new ExpressionBasedAnnotationAttributeFactory(Stub(DefaultMethodSecurityExpressionHandler))
        )
        def method = SecuredService.getMethod('getReports')

        when:
        def attribute = metadataSource.getAttributes(method, SecuredService).first() as ExpressionBasedAnnotationConfigAttribute

        then:
        attribute.preAuthorizeExpression == 'hasRole("ROLE_USER")'
        attribute.postFilterExpression == 'hasPermission(filterObject, read)'
    }

    void 'pre-invocation voter denies and grants based on the evaluated pre-authorize expression'() {
        given:
        PermissionEvaluator permissionEvaluator = Mock()
        Authentication authentication = SecurityTestUtils.authenticate(['ROLE_USER'])
        def advice = new ExpressionBasedPreInvocationAdvice(
                expressionHandler: Stub(DefaultMethodSecurityExpressionHandler) {
                    getPermissionEvaluator() >> permissionEvaluator
                }
        )
        def voter = new PreInvocationAuthorizationAdviceVoter(advice)
        def reflectedMethod = SecuredService.getMethod('getReport', Long)
        MethodInvocation invocation = Stub(MethodInvocation)
        invocation.getMethod() >> reflectedMethod
        invocation.getArguments() >> ([42L] as Object[])
        def attribute = new ExpressionBasedAnnotationConfigAttribute(
                'hasPermission(#id, "com.testacl.Report", read)', null, null, null)

        when:
        def denied = voter.vote(authentication, invocation, [attribute])

        then:
        denied == PreInvocationAuthorizationAdviceVoter.ACCESS_DENIED
        1 * permissionEvaluator.hasPermission(authentication, 42L, 'com.testacl.Report', _) >> false

        when:
        def granted = voter.vote(authentication, invocation, [attribute])

        then:
        granted == PreInvocationAuthorizationAdviceVoter.ACCESS_GRANTED
        1 * permissionEvaluator.hasPermission(authentication, 42L, 'com.testacl.Report', _) >> true
    }

    void 'pre-invocation voter exposes the create ACL permission alias'() {
        given:
        PermissionEvaluator permissionEvaluator = Mock()
        Authentication authentication = SecurityTestUtils.authenticate(['ROLE_USER'])
        def advice = new ExpressionBasedPreInvocationAdvice(
                expressionHandler: Stub(DefaultMethodSecurityExpressionHandler) {
                    getPermissionEvaluator() >> permissionEvaluator
                }
        )
        def voter = new PreInvocationAuthorizationAdviceVoter(advice)
        def reflectedMethod = SecuredService.getMethod('createReport', Long)
        MethodInvocation invocation = Stub(MethodInvocation)
        invocation.getMethod() >> reflectedMethod
        invocation.getArguments() >> ([42L] as Object[])
        def attribute = new ExpressionBasedAnnotationConfigAttribute(
                'hasPermission(#id, "com.testacl.Report", create)', null, null, null)

        when:
        def granted = voter.vote(authentication, invocation, [attribute])

        then:
        granted == PreInvocationAuthorizationAdviceVoter.ACCESS_GRANTED
        1 * permissionEvaluator.hasPermission(authentication, 42L, 'com.testacl.Report', 4) >> true
    }

    void 'post-invocation advice filters returned collections using post-filter expressions'() {
        given:
        PermissionEvaluator permissionEvaluator = Mock()
        Authentication authentication = SecurityTestUtils.authenticate(['ROLE_USER'])
        def advice = new ExpressionBasedPostInvocationAdvice(Stub(DefaultMethodSecurityExpressionHandler) {
            getPermissionEvaluator() >> permissionEvaluator
        })
        def reflectedMethod = SecuredService.getMethod('getReports')
        MethodInvocation invocation = Stub(MethodInvocation)
        invocation.getMethod() >> reflectedMethod
        invocation.getArguments() >> ([] as Object[])
        def attribute = new ExpressionBasedAnnotationConfigAttribute(
                null, null, null, 'hasPermission(filterObject, read)')
        def report1 = new Object()
        def report2 = new Object()

        when:
        def filtered = advice.after(authentication, invocation, attribute, [report1, report2])

        then:
        filtered == [report1]
        1 * permissionEvaluator.hasPermission(authentication, report1, _) >> true
        1 * permissionEvaluator.hasPermission(authentication, report2, _) >> false
    }

    private static class SecuredService {
        @PreAuthorize('hasPermission(#id, "com.testacl.Report", read)')
        Object getReport(@P('id') Long id) {
            null
        }

        @PreAuthorize('hasPermission(#id, "com.testacl.Report", create)')
        Object createReport(@P('id') Long id) {
            null
        }

        @PreAuthorize('hasRole("ROLE_USER")')
        @PostFilter('hasPermission(filterObject, read)')
        List<Object> getReports() {
            []
        }
    }
}
