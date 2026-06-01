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

import grails.gsp.NotATag;
import grails.gsp.Tag;

public final class TagMethodInvoker {

    /**
     * Method names from framework traits, Spring lifecycle interfaces, and the like
     * that must never be treated as tag methods regardless of the declaring class.
     */
    private static final Set<String> FRAMEWORK_METHOD_NAMES = Set.of(
            "afterPropertiesSet",
            "currentRequestAttributes",
            "destroy",
            "initializeTagLibrary",
            "onApplicationEvent",
            "raw",
            "throwTagError",
            "withCodec"
    );

    private static final Set<String> OBJECT_METHOD_SIGNATURES = collectSignatures(Object.class);
    private static final Set<String> GROOVY_OBJECT_METHOD_SIGNATURES = collectSignatures(GroovyObject.class);

    private static Set<String> collectSignatures(Class<?> type) {
        Set<String> signatures = new HashSet<>();
        for (Method method : type.getMethods()) {
            signatures.add(signature(method));
        }
        return Collections.unmodifiableSet(signatures);
    }

    private static final ClassValue<Map<String, Field>> CLOSURE_FIELDS_BY_NAME = new ClassValue<>() {
        @Override
        protected Map<String, Field> computeValue(Class<?> type) {
            Map<String, Field> fields = new HashMap<>();
            Set<String> shadowed = new HashSet<>();
            Class<?> current = type;
            while (current != null && current != Object.class) {
                for (Field field : current.getDeclaredFields()) {
                    String name = field.getName();
                    // A field declared in a more-derived class shadows any field of the
                    // same name in a superclass, regardless of type. Match the previous
                    // getDeclaredField-based lookup behavior.
                    if (!shadowed.add(name)) {
                        continue;
                    }
                    if (Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    if (!Closure.class.isAssignableFrom(field.getType())) {
                        continue;
                    }
                    field.setAccessible(true);
                    fields.put(name, field);
                }
                current = current.getSuperclass();
            }
            return Collections.unmodifiableMap(fields);
        }
    };

    private static final ClassValue<Map<String, List<Method>>> INVOKABLE_METHODS_BY_NAME = new ClassValue<>() {
        @Override
        protected Map<String, List<Method>> computeValue(Class<?> type) {
            Map<String, List<Method>> methodsByName = new HashMap<>();
            for (Method method : type.getDeclaredMethods()) {
                if (isTagMethodCandidate(method)) {
                    methodsByName.computeIfAbsent(method.getName(), ignored -> new ArrayList<>()).add(method);
                }
            }
            Map<String, List<Method>> immutableMethodsByName = new HashMap<>(methodsByName.size());
            for (Map.Entry<String, List<Method>> entry : methodsByName.entrySet()) {
                // Sort methods by descending parameter count so that (Map, Closure) signatures
                // are tried before (Map) signatures, preventing infinite recursion when a
                // 1-arg convenience overload delegates to the 2-arg variant. Break ties by
                // signature string so the resolution order is stable across JVMs (HotSpot,
                // Graal, J9 may otherwise return getDeclaredMethods() in different orders).
                List<Method> sorted = new ArrayList<>(entry.getValue());
                sorted.sort((a, b) -> {
                    int byArity = Integer.compare(b.getParameterCount(), a.getParameterCount());
                    return byArity != 0 ? byArity : signature(a).compareTo(signature(b));
                });
                immutableMethodsByName.put(entry.getKey(), Collections.unmodifiableList(sorted));
            }
            return Collections.unmodifiableMap(immutableMethodsByName);
        }
    };

    private TagMethodInvoker() {
    }

    public static Object getClosureTagProperty(GroovyObject tagLib, String tagName) {
        Field field = CLOSURE_FIELDS_BY_NAME.get(tagLib.getClass()).get(tagName);
        if (field == null) {
            return null;
        }
        try {
            return field.get(tagLib);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
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
                    if (targetException instanceof Error error) {
                        throw error;
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
        if (method.isAnnotationPresent(NotATag.class)) {
            return false;
        }
        if (method.isAnnotationPresent(Tag.class)) {
            return true;
        }
        String name = method.getName();
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
        String signature = signature(method);
        if (OBJECT_METHOD_SIGNATURES.contains(signature) || GROOVY_OBJECT_METHOD_SIGNATURES.contains(signature)) {
            return false;
        }
        return hasConventionalTagSignature(method);
    }

    private static boolean hasConventionalTagSignature(Method method) {
        Parameter[] parameters = method.getParameters();
        if (parameters.length == 1) {
            return isAttrsParameter(parameters[0]) || isBodyParameter(parameters[0]);
        }
        return parameters.length == 2 && isAttrsParameter(parameters[0]) && isBodyParameter(parameters[1]);
    }

    private static boolean isAttrsParameter(Parameter parameter) {
        if (!Map.class.isAssignableFrom(parameter.getType())) {
            return false;
        }
        return "attrs".equals(parameter.getName()) || !parameter.isNamePresent();
    }

    private static boolean isBodyParameter(Parameter parameter) {
        if (!Closure.class.isAssignableFrom(parameter.getType())) {
            return false;
        }
        return "body".equals(parameter.getName()) || !parameter.isNamePresent();
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
            if (isAttrsParameter(parameters[i])) {
                args[i] = attrs;
                continue;
            }
            if (isBodyParameter(parameters[i])) {
                args[i] = body != null ? body : TagOutput.EMPTY_BODY_CLOSURE;
                continue;
            }
            // The attribute must be present in the map by parameter name. An absent
            // attribute rejects this overload so resolution can try a different one.
            if (attrs == null || !attrs.containsKey(parameterName)) {
                return null;
            }
            Object value = attrs.get(parameterName);
            // null is a legal binding for reference-typed parameters; primitives can't take it.
            if (value == null && parameterType.isPrimitive()) {
                return null;
            }
            args[i] = value;
        }
        return args;
    }
}
