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

import jakarta.servlet.http.HttpSession

/**
 *
 * Methods added to the {@link HttpSession} interface
 *
 * @author Jeff Brown
 * @author Graeme Rocher
 *
 * @since 3.0
 *
 */
@CompileStatic
class HttpSessionExtension {

    static getProperty(HttpSession session, String name) {
        def mp = session.class.metaClass.getMetaProperty(name)
        return mp ? mp.getProperty(session) : session.getAttribute(name)
    }

    static propertyMissing(HttpSession session, String name, value) {
        session.setAttribute(name, value)
    }

    static getAt(HttpSession session, String name) {
        getProperty(session, name)
    }

    static propertyMissing(HttpSession session, String name) {
        getProperty(session, name)
    }

    static Byte 'byte'(HttpSession session, String name) {
        TypeConverters.toByte(session.getAttribute(name))
    }

    static Byte 'byte'(HttpSession session, String name, Integer defaultValue) {
        TypeConverters.toByte(session.getAttribute(name), defaultValue)
    }

    static Character 'char'(HttpSession session, String name) {
        TypeConverters.toCharacter(session.getAttribute(name))
    }

    static Character 'char'(HttpSession session, String name, Character defaultValue) {
        TypeConverters.toCharacter(session.getAttribute(name), defaultValue)
    }

    static Character 'char'(HttpSession session, String name, Integer defaultValue) {
        TypeConverters.toCharacter(session.getAttribute(name), defaultValue)
    }

    static Short 'short'(HttpSession session, String name) {
        TypeConverters.toShort(session.getAttribute(name))
    }

    static Short 'short'(HttpSession session, String name, Integer defaultValue) {
        TypeConverters.toShort(session.getAttribute(name), defaultValue)
    }

    static Integer 'int'(HttpSession session, String name) {
        TypeConverters.toInteger(session.getAttribute(name))
    }

    static Integer 'int'(HttpSession session, String name, Integer defaultValue) {
        TypeConverters.toInteger(session.getAttribute(name), defaultValue)
    }

    static Long 'long'(HttpSession session, String name) {
        TypeConverters.toLong(session.getAttribute(name))
    }

    static Long 'long'(HttpSession session, String name, Long defaultValue) {
        TypeConverters.toLong(session.getAttribute(name), defaultValue)
    }

    static Double 'double'(HttpSession session, String name) {
        TypeConverters.toDouble(session.getAttribute(name))
    }

    static Double 'double'(HttpSession session, String name, Double defaultValue) {
        TypeConverters.toDouble(session.getAttribute(name), defaultValue)
    }

    static Float 'float'(HttpSession session, String name) {
        TypeConverters.toFloat(session.getAttribute(name))
    }

    static Float 'float'(HttpSession session, String name, Float defaultValue) {
        TypeConverters.toFloat(session.getAttribute(name), defaultValue)
    }

    static Boolean 'boolean'(HttpSession session, String name) {
        TypeConverters.toBoolean(session.getAttribute(name))
    }

    // boolean default is presence-based (attribute set), which cannot be expressed from the value alone
    static Boolean 'boolean'(HttpSession session, String name, Boolean defaultValue) {
        Object value = session.getAttribute(name)
        value != null ? TypeConverters.toBoolean(value) : defaultValue
    }

    static String string(HttpSession session, String name) {
        TypeConverters.toStringValue(session.getAttribute(name))
    }

    static String string(HttpSession session, String name, String defaultValue) {
        TypeConverters.toStringValue(session.getAttribute(name), defaultValue)
    }

    static List list(HttpSession session, String name) {
        TypeConverters.toList(session.getAttribute(name))
    }

    static Date date(HttpSession session, String name) {
        TypeConverters.toDate(session.getAttribute(name))
    }

    static Date date(HttpSession session, String name, String format) {
        TypeConverters.toDate(session.getAttribute(name), format)
    }

    static Date date(HttpSession session, String name, Collection<String> formats) {
        TypeConverters.toDate(session.getAttribute(name), formats)
    }
    /**
     * Null-safe, typed read of an attribute. Returns the attribute when it is an
     * instance of {@code type}; otherwise {@code null}. No coercion is attempted —
     * use the named converters ({@code string}, {@code int}, ...) for type conversion.
     */
    static <T> T getAttribute(HttpSession session, String name, Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException('type must not be null - use getAttribute(name) for an untyped read')
        }
        Object value = session.getAttribute(name)
        Class<T> resolvedType = (Class<T>) ClassUtils.resolvePrimitiveIfNecessary(type)
        resolvedType.isInstance(value) ? resolvedType.cast(value) : null
    }

    /**
     * Null-safe, typed read of an attribute with a default. Returns {@code defaultValue}
     * when the attribute is absent or is not an instance of {@code type}.
     */
    static <T> T getAttribute(HttpSession session, String name, Class<T> type, T defaultValue) {
        T value = getAttribute(session, name, type)
        value != null ? value : defaultValue
    }
}
