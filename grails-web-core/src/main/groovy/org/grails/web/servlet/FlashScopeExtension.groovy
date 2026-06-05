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

import org.grails.util.TypeConverters

import grails.web.mvc.FlashScope

/**
 * An extension that adds type conversion methods to the {@link FlashScope} interface
 *
 * @since 8.0
 */
@CompileStatic
class FlashScopeExtension {

    static Byte 'byte'(FlashScope flash, String name) {
        TypeConverters.toByte(flash.get(name))
    }

    static Byte 'byte'(FlashScope flash, String name, Integer defaultValue) {
        TypeConverters.toByte(flash.get(name), defaultValue)
    }

    static Character 'char'(FlashScope flash, String name) {
        TypeConverters.toCharacter(flash.get(name))
    }

    static Character 'char'(FlashScope flash, String name, Character defaultValue) {
        TypeConverters.toCharacter(flash.get(name), defaultValue)
    }

    static Character 'char'(FlashScope flash, String name, Integer defaultValue) {
        TypeConverters.toCharacter(flash.get(name), defaultValue)
    }

    static Short 'short'(FlashScope flash, String name) {
        TypeConverters.toShort(flash.get(name))
    }

    static Short 'short'(FlashScope flash, String name, Integer defaultValue) {
        TypeConverters.toShort(flash.get(name), defaultValue)
    }

    static Integer 'int'(FlashScope flash, String name) {
        TypeConverters.toInteger(flash.get(name))
    }

    static Integer 'int'(FlashScope flash, String name, Integer defaultValue) {
        TypeConverters.toInteger(flash.get(name), defaultValue)
    }

    static Long 'long'(FlashScope flash, String name) {
        TypeConverters.toLong(flash.get(name))
    }

    static Long 'long'(FlashScope flash, String name, Long defaultValue) {
        TypeConverters.toLong(flash.get(name), defaultValue)
    }

    static Double 'double'(FlashScope flash, String name) {
        TypeConverters.toDouble(flash.get(name))
    }

    static Double 'double'(FlashScope flash, String name, Double defaultValue) {
        TypeConverters.toDouble(flash.get(name), defaultValue)
    }

    static Float 'float'(FlashScope flash, String name) {
        TypeConverters.toFloat(flash.get(name))
    }

    static Float 'float'(FlashScope flash, String name, Float defaultValue) {
        TypeConverters.toFloat(flash.get(name), defaultValue)
    }

    static Boolean 'boolean'(FlashScope flash, String name) {
        TypeConverters.toBoolean(flash.get(name))
    }

    // boolean default is presence-based (key present), which cannot be expressed from the value alone
    static Boolean 'boolean'(FlashScope flash, String name, Boolean defaultValue) {
        flash.containsKey(name) ? TypeConverters.toBoolean(flash.get(name)) : defaultValue
    }

    static String string(FlashScope flash, String name) {
        TypeConverters.toStringValue(flash.get(name))
    }

    static String string(FlashScope flash, String name, String defaultValue) {
        TypeConverters.toStringValue(flash.get(name), defaultValue)
    }

    static List list(FlashScope flash, String name) {
        TypeConverters.toList(flash.get(name))
    }

    static Date date(FlashScope flash, String name) {
        TypeConverters.toDate(flash.get(name))
    }

    static Date date(FlashScope flash, String name, String format) {
        TypeConverters.toDate(flash.get(name), format)
    }

    static Date date(FlashScope flash, String name, Collection<String> formats) {
        TypeConverters.toDate(flash.get(name), formats)
    }
}
