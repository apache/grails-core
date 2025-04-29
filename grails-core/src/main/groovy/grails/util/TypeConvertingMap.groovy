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
package grails.util

import groovy.transform.CompileStatic

/**
 * TypeConvertingMap is a Map with type conversion capabilities.
 *
 * Type converting maps have no inherent ordering. Two maps with identical entries
 * but arranged in a different order internally are considered equal.
 *
 * @author Graeme Rocher
 * @since 1.2
 */
@CompileStatic
class TypeConvertingMap extends AbstractTypeConvertingMap {
    TypeConvertingMap() {
        super()
    }

    TypeConvertingMap(Map map) {
        super(map)
    }

    Object clone() {
        new TypeConvertingMap(new LinkedHashMap(this.@wrappedMap))
    }

    Byte 'byte'(String name) {
        return getByte(name)
    }

    Byte 'byte'(String name, Integer defaultValue) {
        return getByte(name, defaultValue)
    }

    Character 'char'(String name) {
        return getChar(name)
    }

    Character 'char'(String name, Character defaultValue) {
        return getChar(name, defaultValue?.charValue() as Integer)
    }

    Character 'char'(String name, Integer defaultValue) {
        return getChar(name, defaultValue)
    }

    Integer 'int'(String name) {
        return getInt(name)
    }

    Integer 'int'(String name, Integer defaultValue) {
        return getInt(name, defaultValue)
    }

    Long 'long'(String name) {
        return getLong(name)
    }

    Long 'long'(String name, Long defaultValue) {
        return getLong(name, defaultValue)
    }

    Short 'short'(String name) {
        return getShort(name)
    }

    Short 'short'(String name, Integer defaultValue) {
        return getShort(name, defaultValue)
    }

    Double 'double'(String name) {
        return getDouble(name)
    }

    Double 'double'(String name, Double defaultValue) {
        return getDouble(name, defaultValue)
    }

    Float 'float'(String name) {
        return getFloat(name)
    }

    Float 'float'(String name, Float defaultValue) {
        return getFloat(name, defaultValue)
    }

    Boolean 'boolean'(String name) {
        return getBoolean(name)
    }

    Boolean 'boolean'(String name, Boolean defaultValue) {
        return getBoolean(name, defaultValue)
    }
}
