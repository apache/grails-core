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

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Decides whether a method is a tag.
 *
 * <p>The single statement of those rules. Both the reflective discovery an application performs at
 * startup and the syntax-tree discovery a build performs while compiling a tag library route through
 * here, so the two cannot drift apart: a method is a tag for a compiler exactly when it is a tag for
 * the runtime.
 *
 * <p>The rules, in order:
 * <ol>
 * <li>plumbing — non-public, static, or compiler-generated methods are never tags;</li>
 * <li>{@code @NotATag} excludes, {@code @Tag} includes, each overriding everything below;</li>
 * <li>names belonging to Object, Groovy, or the framework traits are never tags;</li>
 * <li>property accessors are never tags;</li>
 * <li>what remains is a tag if it can be called as {@code (attrs)} or {@code (attrs, body)}.</li>
 * </ol>
 *
 * @since 8.0.0
 */
public final class TagDiscoveryRules {

    /**
     * The name a {@link java.util.Map} parameter must carry to be the attributes parameter, when the
     * method retains parameter names.
     */
    public static final String ATTRS_PARAMETER_NAME = "attrs";

    /**
     * The name a {@link groovy.lang.Closure} parameter must carry to be the body parameter, when the
     * method retains parameter names.
     */
    public static final String BODY_PARAMETER_NAME = "body";

    /**
     * Names that are Groovy or Object plumbing on any class.
     */
    private static final Set<String> LANGUAGE_METHOD_NAMES = Set.of(
            "invokeMethod", "methodMissing", "propertyMissing", "getProperty", "setProperty",
            "getMetaClass", "setMetaClass", "equals", "hashCode", "toString");

    /**
     * Names every tag library carries through the framework traits and lifecycle interfaces.
     */
    private static final Set<String> FRAMEWORK_METHOD_NAMES = Set.of(
            "afterPropertiesSet",
            "currentRequestAttributes",
            "destroy",
            "initializeTagLibrary",
            "onApplicationEvent",
            "raw",
            "throwTagError",
            "withCodec");

    private TagDiscoveryRules() {
    }

    /**
     * @return the names that are never tags, whatever their shape
     */
    public static Set<String> getFrameworkMethodNames() {
        return FRAMEWORK_METHOD_NAMES;
    }

    /**
     * Finds every tag a tag library declares, from either view of it.
     *
     * <p>The two kinds are enumerated differently, because the runtime dispatches them differently. A
     * method tag is read from the declaring class alone, since dispatch scans declared methods and an
     * inherited one is not callable as a tag. A closure tag is read up the whole hierarchy, since
     * dispatch finds it as a property and a property is inherited.
     *
     * @param view the tag library, from a syntax tree or from a compiled class
     * @return every tag name the library declares
     */
    public static Set<String> findTags(TagLibraryView view) {
        Set<String> tags = new LinkedHashSet<>();
        for (TagMethodView method : view.declaredMethods()) {
            if (isTagMethod(method)) {
                tags.add(method.getName());
            }
        }
        // A closure tag is read up the whole hierarchy, since dispatch finds it as a property and a
        // property is inherited, where a method tag is read from the declaring class alone because
        // dispatch scans declared methods.
        for (TagLibraryView current = view; current != null; current = current.superclassView()) {
            tags.addAll(current.declaredClosureFieldNames());
        }
        return tags;
    }

    /**
     * @param method the method to classify
     * @return true if the method can be invoked as a tag
     */
    public static boolean isTagMethod(TagMethodView method) {
        if (!method.isPublic() || method.isStatic() || method.isGenerated()) {
            return false;
        }
        if (method.hasNotATagAnnotation()) {
            return false;
        }
        if (method.hasTagAnnotation()) {
            return true;
        }
        String name = method.getName();
        if (name.isEmpty() || name.charAt(0) == '<' || name.indexOf('$') >= 0) {
            return false;
        }
        if (LANGUAGE_METHOD_NAMES.contains(name) || FRAMEWORK_METHOD_NAMES.contains(name)) {
            return false;
        }
        if (isPropertyAccessor(method, name)) {
            return false;
        }
        return hasInvocableTagShape(method);
    }

    private static boolean isPropertyAccessor(TagMethodView method, String name) {
        int parameterCount = method.getParameterCount();
        if (parameterCount == 0 && (name.startsWith("get") || name.startsWith("is"))) {
            return true;
        }
        return parameterCount == 1 && name.startsWith("set");
    }

    /**
     * A tag is called as {@code (attrs)} or {@code (attrs, body)}. Parameters with default values
     * produce further overloads, so every arity the declaration can be called at is considered.
     */
    private static boolean hasInvocableTagShape(TagMethodView method) {
        int parameterCount = method.getParameterCount();
        int required = 0;
        for (int i = 0; i < parameterCount; i++) {
            if (!method.isParameterOptional(i)) {
                required++;
            }
        }
        for (int arity = Math.max(required, 1); arity <= parameterCount; arity++) {
            if (arity == 1 && (isAttrs(method, 0) || isBody(method, 0))) {
                return true;
            }
            if (arity == 2 && isAttrs(method, 0) && isBody(method, 1)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAttrs(TagMethodView method, int index) {
        return method.isParameterMapAssignable(index) && carriesName(method, index, ATTRS_PARAMETER_NAME);
    }

    private static boolean isBody(TagMethodView method, int index) {
        return method.isParameterClosureAssignable(index) && carriesName(method, index, BODY_PARAMETER_NAME);
    }

    /**
     * A parameter qualifies when it carries the expected name, or when the method does not retain
     * parameter names and there is nothing to check against.
     */
    private static boolean carriesName(TagMethodView method, int index, String expectedName) {
        return !method.isParameterNamePresent(index) || expectedName.equals(method.getParameterName(index));
    }
}
