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

import org.grails.taglib.discovery.ReflectedTagMethodView;
import org.grails.taglib.discovery.TagDiscoveryRules;

public final class TagMethodInvoker {

    /**
     * Names that live on every tag library through the framework traits and are therefore never tags.
     * <p>
     * Exposed so that the compile-time tag library index derives the same tag names from the AST that
     * this class derives by reflection at runtime. A name recorded in the index but rejected here
     * would resolve when a GSP is compiled and then fail to dispatch when it renders.
     *
     * @since 8.0.0
     */
    public static final Set<String> FRAMEWORK_METHOD_NAMES = TagDiscoveryRules.getFrameworkMethodNames();

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

    private static final ClassValue<Map<String, List<TagMethodBinding>>> INVOKABLE_METHODS_BY_NAME = new ClassValue<>() {
        @Override
        protected Map<String, List<TagMethodBinding>> computeValue(Class<?> type) {
            Map<String, List<Method>> methodsByName = new HashMap<>();
            for (Method method : type.getDeclaredMethods()) {
                if (isTagMethodCandidate(method)) {
                    methodsByName.computeIfAbsent(method.getName(), ignored -> new ArrayList<>()).add(method);
                }
            }
            Map<String, List<TagMethodBinding>> immutableMethodsByName = new HashMap<>(methodsByName.size());
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
                List<TagMethodBinding> bindings = new ArrayList<>(sorted.size());
                for (Method method : sorted) {
                    bindings.add(new TagMethodBinding(method));
                }
                immutableMethodsByName.put(entry.getKey(), Collections.unmodifiableList(bindings));
            }
            return Collections.unmodifiableMap(immutableMethodsByName);
        }
    };

    /**
     * How one parameter of a tag method is supplied when the tag is invoked.
     */
    private enum ParameterSource {
        /** The whole attribute map. */
        ATTRS,
        /** The tag body, or an empty body when the tag was called without one. */
        BODY,
        /** A single named attribute, looked up by the parameter's own name. */
        NAMED_ATTRIBUTE
    }

    /**
     * A tag method together with everything needed to build its argument array.
     *
     * <p>Classifying parameters means reading {@code Method.getParameters()}, which allocates a fresh
     * array and materialises reflection metadata on every access. Doing that per invocation showed up
     * directly in profiles of tag-heavy pages, and the answer never changes for a given method, so it
     * is computed once when the tag library class is first seen.
     */
    private static final class TagMethodBinding {

        private final Method method;
        private final ParameterSource[] sources;
        private final String[] attributeNames;
        private final boolean[] primitive;

        private TagMethodBinding(Method method) {
            this.method = method;
            Parameter[] parameters = method.getParameters();
            this.sources = new ParameterSource[parameters.length];
            this.attributeNames = new String[parameters.length];
            this.primitive = new boolean[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                Parameter parameter = parameters[i];
                if (isAttrsParameter(parameter)) {
                    sources[i] = ParameterSource.ATTRS;
                } else if (isBodyParameter(parameter)) {
                    sources[i] = ParameterSource.BODY;
                } else {
                    sources[i] = ParameterSource.NAMED_ATTRIBUTE;
                    attributeNames[i] = parameter.getName();
                    primitive[i] = parameter.getType().isPrimitive();
                }
            }
            try {
                // A public method on a Groovy class still pays an access check on every reflective
                // call unless the check is suppressed once, here.
                method.setAccessible(true);
            } catch (RuntimeException ignored) {
                // A module boundary may refuse; the call still works, it just keeps the access check.
            }
        }

        private Method getMethod() {
            return method;
        }

        /**
         * @return the argument array for this method, or {@code null} when the attributes on hand
         *         cannot satisfy it and another overload should be tried
         */
        private Object[] toArguments(Map<?, ?> attrs, Closure<?> body) {
            Object[] args = new Object[sources.length];
            for (int i = 0; i < sources.length; i++) {
                switch (sources[i]) {
                    case ATTRS -> args[i] = attrs;
                    case BODY -> args[i] = body != null ? body : TagOutput.EMPTY_BODY_CLOSURE;
                    case NAMED_ATTRIBUTE -> {
                        // The attribute must be present in the map by parameter name. An absent
                        // attribute rejects this overload so resolution can try a different one.
                        if (attrs == null || !attrs.containsKey(attributeNames[i])) {
                            return null;
                        }
                        Object value = attrs.get(attributeNames[i]);
                        // null is a legal binding for reference-typed parameters; primitives can't take it.
                        if (value == null && primitive[i]) {
                            return null;
                        }
                        args[i] = value;
                    }
                }
            }
            return args;
        }
    }

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
        List<TagMethodBinding> bindings = INVOKABLE_METHODS_BY_NAME.get(tagLib.getClass()).get(tagName);
        return bindings != null && !bindings.isEmpty();
    }

    public static Object invokeTagMethod(GroovyObject tagLib, String tagName, Map<?, ?> attrs, Closure<?> body) {
        List<TagMethodBinding> bindings = INVOKABLE_METHODS_BY_NAME.get(tagLib.getClass()).get(tagName);
        if (bindings == null) {
            throw new MissingMethodException(tagName, tagLib.getClass(), new Object[] { attrs, body });
        }
        for (TagMethodBinding binding : bindings) {
            Object[] args = binding.toArguments(attrs, body);
            if (args != null) {
                try {
                    return binding.getMethod().invoke(tagLib, args);
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
        return TagDiscoveryRules.isTagMethod(new ReflectedTagMethodView(method));
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

}
