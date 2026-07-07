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

import java.lang.annotation.Annotation
import java.lang.reflect.Method

import groovy.transform.CompileStatic

import org.springframework.security.access.ConfigAttribute
import org.springframework.security.access.expression.method.ExpressionBasedAnnotationAttributeFactory
import org.springframework.security.access.method.AbstractMethodSecurityMetadataSource
import org.springframework.util.ClassUtils

@CompileStatic
class PrePostAnnotationSecurityMetadataSource extends AbstractMethodSecurityMetadataSource {

    final ExpressionBasedAnnotationAttributeFactory attributeFactory

    PrePostAnnotationSecurityMetadataSource(ExpressionBasedAnnotationAttributeFactory attributeFactory) {
        this.attributeFactory = attributeFactory
    }

    @Override
    Collection<ConfigAttribute> getAttributes(Method method, Class<?> targetClass) {
        def specificMethod = targetClass == null ? method : ClassUtils.getMostSpecificMethod(method, targetClass)
        def preAuthorize = findAnnotationValue(PreAuthorize, specificMethod, method, targetClass)
        def preFilter = findAnnotationValue(PreFilter, specificMethod, method, targetClass)
        def postAuthorize = findAnnotationValue(PostAuthorize, specificMethod, method, targetClass)
        def postFilter = findAnnotationValue(PostFilter, specificMethod, method, targetClass)
        if (preAuthorize == null && preFilter == null && postAuthorize == null && postFilter == null) {
            return null
        }

        Collections.<ConfigAttribute>singletonList(
                new ExpressionBasedAnnotationConfigAttribute(
                        preAuthorize, preFilter, postAuthorize, postFilter
                )
        )
    }

    @Override
    Collection<ConfigAttribute> getAllConfigAttributes() {
        Collections.emptyList()
    }

    private static String findAnnotationValue(Class annotationType, Method specificMethod, Method originalMethod, Class<?> targetClass) {
        def value = annotationValue(specificMethod, annotationType)
        if (value != null) {
            return value
        }
        value = annotationValue(specificMethod?.declaringClass, annotationType)
        if (value != null) {
            return value
        }
        if (specificMethod != originalMethod) {
            value = annotationValue(originalMethod, annotationType)
            if (value != null) {
                return value
            }
            value = annotationValue(originalMethod?.declaringClass, annotationType)
            if (value != null) {
                return value
            }
        }
        annotationValue(targetClass, annotationType)
    }

    private static String annotationValue(Method method, Class annotationType) {
        readAnnotationValue(method?.getAnnotation(annotationType))
    }

    private static String annotationValue(Class<?> type, Class annotationType) {
        readAnnotationValue(type?.getAnnotation(annotationType))
    }

    private static String readAnnotationValue(Annotation annotation) {
        annotation == null ?
                null :
                (String) annotation.annotationType().getMethod('value').invoke(annotation)
    }
}
