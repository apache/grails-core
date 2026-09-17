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
package org.grails.taglib.discovery

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Parameter

import groovy.transform.CompileStatic

import grails.gsp.NotATag
import grails.gsp.Tag

/**
 * A compiled method, seen through {@link TagMethodView} so that {@link TagDiscoveryRules} can classify
 * it at runtime.
 *
 * <p>Groovy compiles a parameter default into separate overloads, so by the time a method is
 * reflected on there are no optional parameters left to report.
 *
 * @since 8.0.0
 */
@CompileStatic
final class ReflectedTagMethodView implements TagMethodView {

    private final Method method
    private final Parameter[] parameters

    ReflectedTagMethodView(Method method) {
        this.method = method
        this.parameters = method.getParameters()
    }

    @Override
    String getName() {
        return method.getName()
    }

    @Override
    boolean isPublic() {
        return Modifier.isPublic(method.getModifiers())
    }

    @Override
    boolean isStatic() {
        return Modifier.isStatic(method.getModifiers())
    }

    @Override
    boolean isGenerated() {
        return method.isBridge() || method.isSynthetic()
    }

    @Override
    boolean hasTagAnnotation() {
        return method.isAnnotationPresent(Tag)
    }

    @Override
    boolean hasNotATagAnnotation() {
        return method.isAnnotationPresent(NotATag)
    }

    @Override
    int getParameterCount() {
        return parameters.length
    }

    @Override
    boolean isParameterMapAssignable(int index) {
        return Map.isAssignableFrom(parameters[index].getType())
    }

    @Override
    boolean isParameterClosureAssignable(int index) {
        return Closure.isAssignableFrom(parameters[index].getType())
    }

    @Override
    String getParameterName(int index) {
        return parameters[index].getName()
    }

    @Override
    boolean isParameterNamePresent(int index) {
        return parameters[index].isNamePresent()
    }

    @Override
    boolean isParameterOptional(int index) {
        // Defaults have already been expanded into overloads by the time the class is compiled.
        return false
    }
}
