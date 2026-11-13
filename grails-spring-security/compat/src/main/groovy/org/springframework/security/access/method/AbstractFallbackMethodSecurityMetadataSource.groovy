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
package org.springframework.security.access.method

import java.lang.reflect.Method

import groovy.transform.CompileStatic

import org.springframework.security.access.ConfigAttribute
import org.springframework.util.ClassUtils

@CompileStatic
abstract class AbstractFallbackMethodSecurityMetadataSource extends AbstractMethodSecurityMetadataSource {

    @Override
    Collection<ConfigAttribute> getAttributes(Method method, Class<?> targetClass) {
        def specificMethod = targetClass == null ?
                method :
                ClassUtils.getMostSpecificMethod(method, targetClass)

        def attributes = findAttributes(specificMethod, targetClass)
        if (attributes != null) {
            return attributes
        }

        def declaringClass = specificMethod?.declaringClass
        if (declaringClass != null) {
            attributes = findAttributes(declaringClass)
            if (attributes != null) {
                return attributes
            }
        }

        if (specificMethod != method) {
            attributes = findAttributes(method, method?.declaringClass)
            if (attributes != null) {
                return attributes
            }

            declaringClass = method?.declaringClass
            if (declaringClass != null) {
                attributes = findAttributes(declaringClass)
                if (attributes != null) {
                    return attributes
                }
            }
        }

        if (targetClass != null) {
            return findAttributes(targetClass)
        }
        null
    }

    protected abstract Collection<ConfigAttribute> findAttributes(Class<?> clazz)

    protected abstract Collection<ConfigAttribute> findAttributes(Method method, Class<?> targetClass)
}
