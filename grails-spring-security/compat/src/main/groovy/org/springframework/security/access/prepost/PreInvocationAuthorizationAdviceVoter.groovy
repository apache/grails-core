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

import groovy.transform.CompileStatic

import org.aopalliance.intercept.MethodInvocation

import org.springframework.security.access.AccessDecisionVoter
import org.springframework.security.access.ConfigAttribute
import org.springframework.security.access.expression.method.ExpressionBasedPreInvocationAdvice
import org.springframework.security.core.Authentication

@CompileStatic
class PreInvocationAuthorizationAdviceVoter implements AccessDecisionVoter<MethodInvocation> {

    final ExpressionBasedPreInvocationAdvice preInvocationAdvice

    PreInvocationAuthorizationAdviceVoter(ExpressionBasedPreInvocationAdvice preInvocationAdvice) {
        this.preInvocationAdvice = preInvocationAdvice
    }

    @Override
    boolean supports(ConfigAttribute attribute) {
        attribute instanceof ExpressionBasedAnnotationConfigAttribute &&
                ((ExpressionBasedAnnotationConfigAttribute) attribute).hasPreInvocationExpression()
    }

    @Override
    boolean supports(Class<?> clazz) {
        MethodInvocation.isAssignableFrom(clazz)
    }

    @Override
    int vote(Authentication authentication, MethodInvocation object, Collection<ConfigAttribute> attributes) {
        def attribute = attributes.find {
            supports(it)
        }
        if (attribute == null) {
            return ACCESS_ABSTAIN
        }
        preInvocationAdvice.before(authentication, object, attribute) ? ACCESS_GRANTED : ACCESS_DENIED
    }
}
