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

package org.grails.datastore.gorm.validation.jakarta

import groovy.transform.CompileStatic

import jakarta.validation.ParameterNameProvider
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * A configurable parameter name provider
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class ConfigurableParameterNameProvider implements ParameterNameProvider{

    public static final String PREFIX = "arg"
    private Map<MethodKey, List<String>> parameterNames = [:]

    /**
     * registers parameter names
     *
     * @param methodName The method name
     * @param parameterTypes The parameter types
     * @param parameterNames The parameter names
     */
    void addParameterNames(String methodName, Class[] parameterTypes, List<String> names) {
        if(methodName != null && parameterTypes != null && names != null) {
            parameterNames.put(new MethodKey(methodName, parameterTypes), names)
        }
    }

    @Override
    List<String> getParameterNames(Constructor<?> constructor) {
        Class[] parameterTypes = constructor.parameterTypes
        List<String> existing = parameterNames.get(new MethodKey("<init>", parameterTypes))
        if(existing != null) {
            return existing
        }
        else {
            // return default argument names
            return buildDefault(parameterTypes)
        }
    }

    @Override
    List<String> getParameterNames(Method method) {
        Class[] parameterTypes = method.parameterTypes
        List<String> existing = parameterNames.get(new MethodKey(method.name, parameterTypes))
        if(existing != null) {
            return existing
        }
        else {
            // return default argument names
            return buildDefault(parameterTypes)
        }
    }

    protected List<String> buildDefault(Class[] parameterTypes) {
        int i = 0
        List<String> newList = new ArrayList<>(parameterTypes.length)
        for (Class t in parameterTypes) {
            newList.add(PREFIX + i++)
        }
        return newList
    }
}
