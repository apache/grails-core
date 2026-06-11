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
package org.grails.web.servlet

import groovy.transform.CompileStatic

import org.apache.grails.core.internal.util.TypeConverters
import org.springframework.util.ClassUtils

import jakarta.servlet.ServletContext

/**
 * An extension that adds methods to the {@link ServletContext} interface
 *
 * @author Jeff Brown
 * @since 3.0
 */
@CompileStatic
class ServletContextExtension {

    static propertyMissing(ServletContext context, String name, value) {
        context.setAttribute(name, value)
    }

    static propertyMissing(ServletContext context, String name) {
        context.getAttribute(name)
    }

    static Byte 'byte'(ServletContext context, String name) {
        TypeConverters.toByte(context.getAttribute(name))
    }

    static Byte 'byte'(ServletContext context, String name, Integer defaultValue) {
        TypeConverters.toByte(context.getAttribute(name), defaultValue)
    }

    static Character 'char'(ServletContext context, String name) {
        TypeConverters.toCharacter(context.getAttribute(name))
    }

    static Character 'char'(ServletContext context, String name, Character defaultValue) {
        TypeConverters.toCharacter(context.getAttribute(name), defaultValue)
    }

    static Character 'char'(ServletContext context, String name, Integer defaultValue) {
        TypeConverters.toCharacter(context.getAttribute(name), defaultValue)
    }

    static Short 'short'(ServletContext context, String name) {
        TypeConverters.toShort(context.getAttribute(name))
    }

    static Short 'short'(ServletContext context, String name, Integer defaultValue) {
        TypeConverters.toShort(context.getAttribute(name), defaultValue)
    }

    static Integer 'int'(ServletContext context, String name) {
        TypeConverters.toInteger(context.getAttribute(name))
    }

    static Integer 'int'(ServletContext context, String name, Integer defaultValue) {
        TypeConverters.toInteger(context.getAttribute(name), defaultValue)
    }

    static Long 'long'(ServletContext context, String name) {
        TypeConverters.toLong(context.getAttribute(name))
    }

    static Long 'long'(ServletContext context, String name, Long defaultValue) {
        TypeConverters.toLong(context.getAttribute(name), defaultValue)
    }

    static Double 'double'(ServletContext context, String name) {
        TypeConverters.toDouble(context.getAttribute(name))
    }

    static Double 'double'(ServletContext context, String name, Double defaultValue) {
        TypeConverters.toDouble(context.getAttribute(name), defaultValue)
    }

    static Float 'float'(ServletContext context, String name) {
        TypeConverters.toFloat(context.getAttribute(name))
    }

    static Float 'float'(ServletContext context, String name, Float defaultValue) {
        TypeConverters.toFloat(context.getAttribute(name), defaultValue)
    }

    static Boolean 'boolean'(ServletContext context, String name) {
        TypeConverters.toBoolean(context.getAttribute(name))
    }

    // boolean default is presence-based (attribute set), which cannot be expressed from the value alone
    static Boolean 'boolean'(ServletContext context, String name, Boolean defaultValue) {
        Object value = context.getAttribute(name)
        value != null ? TypeConverters.toBoolean(value) : defaultValue
    }

    static String string(ServletContext context, String name) {
        TypeConverters.toStringValue(context.getAttribute(name))
    }

    static String string(ServletContext context, String name, String defaultValue) {
        TypeConverters.toStringValue(context.getAttribute(name), defaultValue)
    }

    static List list(ServletContext context, String name) {
        TypeConverters.toList(context.getAttribute(name))
    }

    static Date date(ServletContext context, String name) {
        TypeConverters.toDate(context.getAttribute(name))
    }

    static Date date(ServletContext context, String name, String format) {
        TypeConverters.toDate(context.getAttribute(name), format)
    }

    static Date date(ServletContext context, String name, Collection<String> formats) {
        TypeConverters.toDate(context.getAttribute(name), formats)
    }
    /**
     * Null-safe, typed read of an attribute. Returns the attribute when it is an
     * instance of {@code type}; otherwise {@code null}. No coercion is attempted —
     * use the named converters ({@code string}, {@code int}, ...) for type conversion.
     */
    static <T> T getAttribute(ServletContext context, String name, Class<T> type) {
        Object value = context.getAttribute(name)
        Class<T> resolvedType = (Class<T>) ClassUtils.resolvePrimitiveIfNecessary(type)
        resolvedType.isInstance(value) ? resolvedType.cast(value) : null
    }

    /**
     * Null-safe, typed read of an attribute with a default. Returns {@code defaultValue}
     * when the attribute is absent or is not an instance of {@code type}.
     */
    static <T> T getAttribute(ServletContext context, String name, Class<T> type, T defaultValue) {
        T value = getAttribute(context, name, type)
        value != null ? value : defaultValue
    }
}
