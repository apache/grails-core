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

package grails.views.api.internal

import grails.views.api.http.Parameters
import groovy.transform.CompileStatic

/**
 * An empty parameters implementation
 *
 * @author Graeme Rocher
 * @since 1.1.0
 */
@CompileStatic
class EmptyParameters implements Parameters {

    @Override
    Set<String> keySet() {
        return Collections.emptySet()
    }

    @Override
    boolean containsKey(Object key) {
        return false
    }

    @Override
    String get(String name) {
        return null
    }

    @Override
    String getAt(Object key) {
        return null
    }

    @Override
    Object getProperty(String property) {
        return null
    }

    @Override
    String get(String name, String defaultValue) {
        return defaultValue
    }

    @Override
    Byte "byte"(String name) {
        return null
    }

    @Override
    Byte "byte"(String name, Integer defaultValue) {
        return defaultValue?.byteValue()
    }

    @Override
    Character "char"(String name) {
        return null
    }

    @Override
    Character "char"(String name, Character defaultValue) {
        return defaultValue
    }


    @Override
    Integer "int"(String name) {
        return null
    }

    @Override
    Integer "int"(String name, Integer defaultValue) {
        return defaultValue
    }

    @Override
    Long "long"(String name) {
        return null
    }

    @Override
    Long "long"(String name, Long defaultValue) {
        return defaultValue
    }

    @Override
    Short "short"(String name) {
        return null
    }

    @Override
    Short "short"(String name, Integer defaultValue) {
        return defaultValue?.shortValue()
    }

    @Override
    Double "double"(String name) {
        return null
    }

    @Override
    Double "double"(String name, Double defaultValue) {
        return defaultValue
    }

    @Override
    Float "float"(String name) {
        return null
    }

    @Override
    Float "float"(String name, Float defaultValue) {
        return defaultValue
    }

    @Override
    Boolean "boolean"(String name) {
        return null
    }

    @Override
    Boolean "boolean"(String name, Boolean defaultValue) {
        return defaultValue
    }

    @Override
    Date date(String name) {
        return null
    }

    @Override
    Date date(String name, String format) {
        return null
    }

    @Override
    List<String> list(String name) {
        return Collections.emptyList()
    }

    @Override
    boolean asBoolean() {
        return false
    }

    @Override
    boolean isEmpty() {
        return true
    }
}
