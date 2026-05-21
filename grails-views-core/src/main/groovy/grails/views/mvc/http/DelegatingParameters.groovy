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

package grails.views.mvc.http

import groovy.transform.CompileStatic

import grails.util.TypeConvertingMap
import grails.views.api.http.Parameters

/**
 * Delegate to a GrailsParameterMap implementation
 *
 * @author Graeme Rocher
 * @since 1.1
 */
@CompileStatic
class DelegatingParameters implements Parameters {

    final TypeConvertingMap parameterMap

    DelegatingParameters(TypeConvertingMap parameterMap) {
        this.parameterMap = parameterMap
    }

    @Override
    Set<String> keySet() {
        return parameterMap.keySet()
    }

    @Override
    boolean containsKey(Object key) {
        return parameterMap.containsKey(key)
    }

    @Override
    String get(String name) {
        return parameterMap.get(name)
    }

    @Override
    String getAt(Object key) {
        return parameterMap.get(key)
    }

    @Override
    String get(String name, String defaultValue) {
        return parameterMap.get(name, defaultValue)
    }

    @Override
    Byte "byte"(String name) {
        return parameterMap.byte(name)
    }

    @Override
    Byte "byte"(String name, Integer defaultValue) {
        return parameterMap.byte(name, defaultValue)
    }

    @Override
    Character "char"(String name) {
        return parameterMap.char(name)
    }

    @Override
    Character "char"(String name, Character defaultValue) {
        return parameterMap.char(name, defaultValue)
    }

    @Override
    Integer "int"(String name) {
        return parameterMap.int(name)
    }

    @Override
    Integer "int"(String name, Integer defaultValue) {
        return parameterMap.int(name, defaultValue)
    }

    @Override
    Long "long"(String name) {
        return parameterMap.long(name)
    }

    @Override
    Long "long"(String name, Long defaultValue) {
        return parameterMap.long(name, defaultValue)
    }

    @Override
    Short "short"(String name) {
        return parameterMap.short(name)
    }

    @Override
    Short "short"(String name, Integer defaultValue) {
        return parameterMap.short(name, defaultValue)
    }

    @Override
    Double "double"(String name) {
        return parameterMap.double(name)
    }

    @Override
    Double "double"(String name, Double defaultValue) {
        return parameterMap.double(name, defaultValue)
    }

    @Override
    Float "float"(String name) {
        return parameterMap.float(name)
    }

    @Override
    Float "float"(String name, Float defaultValue) {
        return parameterMap.float(name, defaultValue)
    }

    @Override
    Boolean "boolean"(String name) {
        return parameterMap.boolean(name)
    }

    @Override
    Boolean "boolean"(String name, Boolean defaultValue) {
        return parameterMap.boolean(name, defaultValue)
    }

    @Override
    Date date(String name) {
        return parameterMap.date(name)
    }

    @Override
    Date date(String name, String format) {
        return parameterMap.date(name, format)
    }

    @Override
    List<String> list(String name) {
        return parameterMap.list(name)
    }

    @Override
    boolean asBoolean() {
        return parameterMap.asBoolean()
    }

    @Override
    boolean isEmpty() {
        return parameterMap.isEmpty()
    }
}
