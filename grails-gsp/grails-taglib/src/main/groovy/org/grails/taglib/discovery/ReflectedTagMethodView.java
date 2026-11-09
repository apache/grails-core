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
package org.grails.taglib.discovery;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Map;

import groovy.lang.Closure;

import grails.gsp.NotATag;
import grails.gsp.Tag;

/**
 * A compiled method, seen through {@link TagMethodView} so that {@link TagDiscoveryRules} can classify
 * it at runtime.
 *
 * <p>Groovy compiles a parameter default into separate overloads, so by the time a method is
 * reflected on there are no optional parameters left to report.
 *
 * @since 8.0.0
 */
public final class ReflectedTagMethodView implements TagMethodView {

    private final Method method;
    private final Parameter[] parameters;

    public ReflectedTagMethodView(Method method) {
        this.method = method;
        this.parameters = method.getParameters();
    }

    @Override
    public String getName() {
        return method.getName();
    }

    @Override
    public boolean isPublic() {
        return Modifier.isPublic(method.getModifiers());
    }

    @Override
    public boolean isStatic() {
        return Modifier.isStatic(method.getModifiers());
    }

    @Override
    public boolean isGenerated() {
        return method.isBridge() || method.isSynthetic();
    }

    @Override
    public boolean hasTagAnnotation() {
        return method.isAnnotationPresent(Tag.class);
    }

    @Override
    public boolean hasNotATagAnnotation() {
        return method.isAnnotationPresent(NotATag.class);
    }

    @Override
    public int getParameterCount() {
        return parameters.length;
    }

    @Override
    public boolean isParameterMapAssignable(int index) {
        return Map.class.isAssignableFrom(parameters[index].getType());
    }

    @Override
    public boolean isParameterClosureAssignable(int index) {
        return Closure.class.isAssignableFrom(parameters[index].getType());
    }

    @Override
    public String getParameterName(int index) {
        return parameters[index].getName();
    }

    @Override
    public boolean isParameterNamePresent(int index) {
        return parameters[index].isNamePresent();
    }

    @Override
    public boolean isParameterOptional(int index) {
        // Defaults have already been expanded into overloads by the time the class is compiled.
        return false;
    }
}
