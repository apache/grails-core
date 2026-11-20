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

import groovy.transform.CompileStatic

import org.aopalliance.intercept.MethodInvocation

import org.springframework.expression.EvaluationContext
import org.springframework.expression.ExpressionParser
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.PermissionEvaluator
import org.springframework.security.access.prepost.ExpressionBasedAnnotationConfigAttribute
import org.springframework.security.core.Authentication

@CompileStatic
class ExpressionBasedPostInvocationAdvice {

    final DefaultMethodSecurityExpressionHandler expressionHandler

    private final ExpressionParser expressionParser = new SpelExpressionParser()

    ExpressionBasedPostInvocationAdvice(DefaultMethodSecurityExpressionHandler expressionHandler) {
        this.expressionHandler = expressionHandler
    }

    Object after(Authentication authentication, MethodInvocation invocation, Object attribute, Object returnedObject) {
        if (!(attribute instanceof ExpressionBasedAnnotationConfigAttribute)) {
            return returnedObject
        }

        def expressionAttribute = attribute as ExpressionBasedAnnotationConfigAttribute
        def filteredObject = applyPostFilter(authentication, expressionAttribute, returnedObject)
        applyPostAuthorize(authentication, expressionAttribute, filteredObject)
        filteredObject
    }

    private Object applyPostFilter(
            Authentication authentication,
            ExpressionBasedAnnotationConfigAttribute expressionAttribute,
            Object returnedObject
    ) {
        if (expressionAttribute.postFilterExpression == null || !(returnedObject instanceof Collection)) {
            return returnedObject
        }

        def collection = returnedObject as Collection<?>
        List<Object> filtered = []
        for (def candidate : collection) {
            def context = createEvaluationContext(authentication, candidate, returnedObject)
            def allowed = expressionParser.parseExpression(expressionAttribute.postFilterExpression)
                    .getValue(context, Boolean)
            if (Boolean.TRUE == allowed) {
                filtered << candidate
            }
        }
        filtered
    }

    private void applyPostAuthorize(
            Authentication authentication,
            ExpressionBasedAnnotationConfigAttribute expressionAttribute,
            Object returnedObject
    ) {
        if (expressionAttribute.postAuthorizeExpression == null) {
            return
        }

        def context = createEvaluationContext(authentication, null, returnedObject)
        def allowed = expressionParser.parseExpression(expressionAttribute.postAuthorizeExpression)
                .getValue(context, Boolean)
        if (Boolean.TRUE != allowed) {
            throw new AccessDeniedException('Access is denied')
        }
    }

    private EvaluationContext createEvaluationContext(Authentication authentication, Object filterObject, Object returnObject) {
        def permissionEvaluator = resolvePermissionEvaluator()
        def root = new MethodSecurityExpressionRoot(authentication, permissionEvaluator).tap {
            it.filterObject = filterObject
            it.returnObject = returnObject
        }
        new StandardEvaluationContext(root)
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

    private static class MethodSecurityExpressionRoot {

        final Authentication authentication
        final PermissionEvaluator permissionEvaluator
        final Object read = 1
        final Object write = 2
        final Object create = 4
        final Object delete = 8
        final Object admin = 16

        Object filterObject
        Object returnObject

        MethodSecurityExpressionRoot(Authentication authentication, PermissionEvaluator permissionEvaluator) {
            this.authentication = authentication
            this.permissionEvaluator = permissionEvaluator
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
