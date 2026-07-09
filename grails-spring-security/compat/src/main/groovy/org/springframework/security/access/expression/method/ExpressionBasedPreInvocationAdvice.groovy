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
package org.springframework.security.access.expression.method

import java.lang.reflect.Method

import groovy.transform.CompileStatic

import org.aopalliance.intercept.MethodInvocation

import org.springframework.expression.EvaluationContext
import org.springframework.expression.ExpressionParser
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.security.access.PermissionEvaluator
import org.springframework.security.access.prepost.ExpressionBasedAnnotationConfigAttribute
import org.springframework.security.core.Authentication
import org.springframework.security.core.parameters.P

@CompileStatic
class ExpressionBasedPreInvocationAdvice {

    DefaultMethodSecurityExpressionHandler expressionHandler

    private final ExpressionParser expressionParser = new SpelExpressionParser()

    boolean before(Authentication authentication, MethodInvocation invocation, Object attribute) {
        if (!(attribute instanceof ExpressionBasedAnnotationConfigAttribute)) {
            return true
        }

        def expressionAttribute = attribute as ExpressionBasedAnnotationConfigAttribute
        if (expressionAttribute.preAuthorizeExpression == null) {
            return true
        }

        def context = createEvaluationContext(authentication, invocation)
        def allowed = expressionParser
                .parseExpression(expressionAttribute.preAuthorizeExpression)
                .getValue(context, Boolean)
        Boolean.TRUE == allowed
    }

    private EvaluationContext createEvaluationContext(Authentication authentication, MethodInvocation invocation) {
        def permissionEvaluator = resolvePermissionEvaluator()
        def root = new MethodSecurityExpressionRoot(authentication, permissionEvaluator)
        def context = new StandardEvaluationContext(root)
        bindMethodArguments(context, invocation.method, invocation.arguments)
        context
    }

    private PermissionEvaluator resolvePermissionEvaluator() {
        if (expressionHandler == null) {
            return null
        }

        def current = expressionHandler.class
        while (current != null) {
            try {
                def method = current.getDeclaredMethod('getPermissionEvaluator').tap {
                    accessible = true
                }
                return (PermissionEvaluator) method.invoke(expressionHandler)
            }
            catch (NoSuchMethodException ignored) {
            }

            try {
                def field = current.getDeclaredField('permissionEvaluator').tap {
                    accessible = true
                }
                return (PermissionEvaluator) field.get(expressionHandler)
            }
            catch (NoSuchFieldException ignored) {
            }

            current = current.superclass
        }

        return null
    }

    private static void bindMethodArguments(StandardEvaluationContext context, Method method, Object[] arguments) {
        def parameterAnnotations = method.parameterAnnotations
        for (int i = 0; i < parameterAnnotations.length; i++) {
            for (def annotation : parameterAnnotations[i]) {
                if (annotation instanceof P) {
                    context.setVariable(((P) annotation).value(), arguments[i])
                }
            }
        }
    }

    private static class MethodSecurityExpressionRoot {
        final Authentication authentication
        final PermissionEvaluator permissionEvaluator
        final Object read = 1
        final Object write = 2
        final Object create = 4
        final Object delete = 8
        final Object admin = 16

        MethodSecurityExpressionRoot(Authentication authentication, PermissionEvaluator permissionEvaluator) {
            this.authentication = authentication
            this.permissionEvaluator = permissionEvaluator
        }

        boolean hasRole(String role) {
            authentication?.authorities?.any { grantedAuthority ->
                grantedAuthority.authority == role
            }
        }

        boolean hasPermission(Object target, Object permission) {
            permissionEvaluator != null && authentication != null &&
                    permissionEvaluator.hasPermission(authentication, target, permission)
        }

        boolean hasPermission(Object targetId, String targetType, Object permission) {
            permissionEvaluator != null && authentication != null &&
                    permissionEvaluator.hasPermission(authentication, (Serializable) targetId, targetType, permission)
        }
    }
}
