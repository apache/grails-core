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
package org.grails.taglib;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import groovy.lang.MissingMethodException;

public final class TagMethodInvoker {
    private TagMethodInvoker() {
    }

    public static Object getClosureTagProperty(GroovyObject tagLib, String tagName) {
        Class<?> type = tagLib.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField(tagName);
                if (!Modifier.isStatic(field.getModifiers()) && Closure.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field.get(tagLib);
                }
                return null;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    public static Collection<String> getInvokableTagMethodNames(Class<?> tagLibClass) {
        if (tagLibClass == null) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        for (Method method : tagLibClass.getDeclaredMethods()) {
            if (isTagMethodCandidate(method)) {
                names.add(method.getName());
            }
        }
        return names;
    }

    public static boolean hasInvokableTagMethod(GroovyObject tagLib, String tagName) {
        for (Method method : tagLib.getClass().getMethods()) {
            if (isTagMethodCandidate(method) && method.getName().equals(tagName)) {
                return true;
            }
        }
        return false;
    }

    public static Object invokeTagMethod(GroovyObject tagLib, String tagName, Map<?, ?> attrs, Closure<?> body) {
        for (Method method : tagLib.getClass().getMethods()) {
            if (!isTagMethodCandidate(method) || !method.getName().equals(tagName)) {
                continue;
            }
            Object[] args = toMethodArguments(method, attrs, body);
            if (args != null) {
                try {
                    return method.invoke(tagLib, args);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                } catch (InvocationTargetException e) {
                    Throwable targetException = e.getTargetException();
                    if (targetException instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    throw new RuntimeException(targetException);
                }
            }
        }
        throw new MissingMethodException(tagName, tagLib.getClass(), new Object[] { attrs, body });
    }

    public static boolean isTagMethodCandidate(Method method) {
        int modifiers = method.getModifiers();
        if (!Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers) || method.isBridge() || method.isSynthetic()) {
            return false;
        }
        String name = method.getName();
        if ("afterPropertiesSet".equals(name)) {
            return false;
        }
        if (name.startsWith("get") && method.getParameterCount() == 0) {
            return false;
        }
        if (name.startsWith("set") && method.getParameterCount() == 1) {
            return false;
        }
        if ("invokeMethod".equals(name) || "methodMissing".equals(name) || "propertyMissing".equals(name)) {
            return false;
        }
        return method.getDeclaringClass() != Object.class && method.getDeclaringClass() != GroovyObject.class;
    }

    private static Object[] toMethodArguments(Method method, Map<?, ?> attrs, Closure<?> body) {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Class<?> parameterType = parameters[i].getType();
            if (Map.class.isAssignableFrom(parameterType)) {
                args[i] = attrs;
                continue;
            }
            if (Closure.class.isAssignableFrom(parameterType)) {
                args[i] = body != null ? body : TagOutput.EMPTY_BODY_CLOSURE;
                continue;
            }
            String parameterName = parameters[i].getName();
            Object value = attrs != null ? attrs.get(parameterName) : null;
            if (value == null && parameters.length == 1 && attrs != null && attrs.size() == 1) {
                value = attrs.values().iterator().next();
            }
            if (value == null) {
                return null;
            }
            args[i] = value;
        }
        return args;
    }
}
