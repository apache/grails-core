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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import groovy.lang.MissingMethodException;

public final class TagMethodInvoker {

    /**
     * Method names inherited from framework traits and interfaces that must never
     * be treated as tag methods.  These come from {@code TagLibrary},
     * {@code TagLibraryInvoker}, {@code WebAttributes}, {@code ServletAttributes},
     * and related Spring interfaces.
     */
    private static final Set<String> FRAMEWORK_METHOD_NAMES = Set.of(
            "initializeTagLibrary",
            "raw",
            "throwTagError",
            "withCodec",
            "currentRequestAttributes"
    );

    private static final ClassValue<Map<String, List<Method>>> INVOKABLE_METHODS_BY_NAME = new ClassValue<>() {
        @Override
        protected Map<String, List<Method>> computeValue(Class<?> type) {
            Map<String, List<Method>> methodsByName = new HashMap<>();
            for (Method method : getCandidateMethods(type)) {
                if (isTagMethodCandidate(method)) {
                    methodsByName.computeIfAbsent(method.getName(), ignored -> new ArrayList<>()).add(method);
                }
            }
            Map<String, List<Method>> immutableMethodsByName = new HashMap<>(methodsByName.size());
            for (Map.Entry<String, List<Method>> entry : methodsByName.entrySet()) {
                // Sort methods by descending parameter count so that (Map, Closure) signatures
                // are tried before (Map) signatures, preventing infinite recursion when a
                // 1-arg convenience overload delegates to the 2-arg variant.
                List<Method> sorted = new ArrayList<>(entry.getValue());
                sorted.sort((a, b) -> Integer.compare(b.getParameterCount(), a.getParameterCount()));
                immutableMethodsByName.put(entry.getKey(), Collections.unmodifiableList(sorted));
            }
            return Collections.unmodifiableMap(immutableMethodsByName);
        }
    };

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
        for (Method method : getCandidateMethods(tagLibClass)) {
            if (isTagMethodCandidate(method)) {
                names.add(method.getName());
            }
        }
        return names;
    }

    public static boolean hasInvokableTagMethod(GroovyObject tagLib, String tagName) {
        List<Method> methods = INVOKABLE_METHODS_BY_NAME.get(tagLib.getClass()).get(tagName);
        return methods != null && !methods.isEmpty();
    }

    public static Object invokeTagMethod(GroovyObject tagLib, String tagName, Map<?, ?> attrs, Closure<?> body) {
        List<Method> methods = INVOKABLE_METHODS_BY_NAME.get(tagLib.getClass()).get(tagName);
        if (methods == null) {
            throw new MissingMethodException(tagName, tagLib.getClass(), new Object[] { attrs, body });
        }
        for (Method method : methods) {
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
        if (name.startsWith("is") && method.getParameterCount() == 0 &&
                (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
            return false;
        }
        if (name.startsWith("set") && method.getParameterCount() == 1) {
            return false;
        }
        if ("invokeMethod".equals(name) || "methodMissing".equals(name) || "propertyMissing".equals(name)) {
            return false;
        }
        if (FRAMEWORK_METHOD_NAMES.contains(name)) {
            return false;
        }
        return method.getDeclaringClass() != Object.class && method.getDeclaringClass() != GroovyObject.class;
    }

    private static Collection<Method> getCandidateMethods(Class<?> type) {
        List<Method> methods = new ArrayList<>();
        Set<String> seenSignatures = new HashSet<>();
        Class<?> current = type;
        while (current != null && current != Object.class && current != GroovyObject.class) {
            for (Method method : current.getDeclaredMethods()) {
                String signature = signature(method);
                if (seenSignatures.add(signature)) {
                    methods.add(method);
                }
            }
            current = current.getSuperclass();
        }
        return methods;
    }

    private static String signature(Method method) {
        StringBuilder builder = new StringBuilder(method.getName()).append('(');
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(parameterTypes[i].getName());
        }
        return builder.append(')').toString();
    }

    private static Object[] toMethodArguments(Method method, Map<?, ?> attrs, Closure<?> body) {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            String parameterName = parameters[i].getName();
            Class<?> parameterType = parameters[i].getType();
            if (Map.class.isAssignableFrom(parameterType) && "attrs".equals(parameterName)) {
                args[i] = attrs;
                continue;
            }
            if (Closure.class.isAssignableFrom(parameterType)) {
                args[i] = body != null ? body : TagOutput.EMPTY_BODY_CLOSURE;
                continue;
            }
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
