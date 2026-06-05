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
package org.grails.util

import grails.util.TypeConvertingMap
import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

/**
 * @author Luke Daley
 */
class TypeConvertingMapTests {

    @Test
    void testEqualsImpl() {
        assert toTypeConverting([:]) == toTypeConverting([:])
        assert toTypeConverting(a: 1) == toTypeConverting(a: 1)
        assert toTypeConverting(a: 1, b: 2) == toTypeConverting(a: 1, b: 2)
        assert toTypeConverting(a: 1, b: 2) == toTypeConverting(b: 2, a: 1)

        assert toTypeConverting(a: 1, b: 2) != toTypeConverting(a: 1, b: "2")
        assert toTypeConverting(a: 1) != toTypeConverting(a: 1, b: 2)
        assert toTypeConverting(a: 1, b: 2) == toTypeConverting(b: 2, "a": 1)
    }

    @Test
    @CompileStatic
    void testEqualsWithNullsCompileStatic() {
        assert toTypeConverting(a: null, b: 2).equals(toTypeConverting(b: 2, a: null))
        assert !toTypeConverting(a: null, b: 2).equals(toTypeConverting(b: 2, a: 1))
        assert !toTypeConverting(a: 1, b: 2).equals(toTypeConverting(b: 2, a: null))
    }

    @Test
    void testEqualsWithNullsCompileDynamic() {
        assert toTypeConverting(a: null, b: 2).equals(toTypeConverting(b: 2, a: null))
        assert !toTypeConverting(a: null, b: 2).equals(toTypeConverting(b: 2, a: 1))
        assert !toTypeConverting(a: 1, b: 2).equals(toTypeConverting(b: 2, a: null))
    }

    @Test
    void testGetString() {
        def map = toTypeConverting(name: 'Bob', count: 5, missing: null)

        assert map.getString('name') == 'Bob'
        assert map.getString('count') == '5'
        assert map.getString('missing') == null
        assert map.getString('absent') == null
    }

    @Test
    void testGetStringWithDefault() {
        def map = toTypeConverting(name: 'Bob')

        assert map.getString('name', 'fallback') == 'Bob'
        assert map.getString('absent', 'fallback') == 'fallback'
    }

    @Test
    void testGetStringWithArrayValueReturnsFirstElement() {
        def map = toTypeConverting(names: ['Bob', 'Judy'] as String[], empty: new String[0])

        assert map.getString('names') == 'Bob'
        assert map.getString('empty') == null
    }

    @Test
    void testStringFacade() {
        def map = toTypeConverting(name: 'Bob')

        assert map.string('name') == 'Bob'
        assert map.string('absent', 'fallback') == 'fallback'
    }

    @Test
    @CompileStatic
    void testStringIsStaticallyTyped() {
        TypeConvertingMap map = new TypeConvertingMap(name: 'Bob')
        String name = map.string('name')

        assert name == 'Bob'
    }

    @Test
    void testTypeConverters() {
        assert TypeConverters.toInteger('42') == 42
        assert TypeConverters.toInteger(42L) == 42
        assert TypeConverters.toInteger('not a number') == null
        assert TypeConverters.toInteger(null) == null
        assert TypeConverters.toLong('42') == 42L
        assert TypeConverters.toBoolean('true') == true
        assert TypeConverters.toStringValue(['a', 'b'] as String[]) == 'a'
        assert TypeConverters.toList('one') == ['one']
        assert TypeConverters.toList(['a', 'b'] as String[]) == ['a', 'b']
    }

    @Test
    void testTypeConvertersWithDefaults() {
        assert TypeConverters.toInteger('42', 7) == 42
        assert TypeConverters.toInteger(null, 7) == 7
        assert TypeConverters.toInteger('not a number', 7) == 7
        assert TypeConverters.toStringValue('present', 'fallback') == 'present'
        assert TypeConverters.toStringValue(null, 'fallback') == 'fallback'
        assert TypeConverters.toByte(null, 5) == (byte) 5
        assert TypeConverters.toShort(null, 5) == (short) 5
        assert TypeConverters.toCharacter(null, (int) 'A') == 'A' as char
    }

    @Test
    void testConvertersWithValueWhoseToStringReturnsNull() {
        def value = new NullToString()

        assert TypeConverters.toByte(value) == null
        assert TypeConverters.toCharacter(value) == null
        assert TypeConverters.toInteger(value) == null
        assert TypeConverters.toLong(value) == null
        assert TypeConverters.toShort(value) == null
        assert TypeConverters.toDouble(value) == null
        assert TypeConverters.toFloat(value) == null
        assert TypeConverters.toBoolean(value) == null
        assert TypeConverters.toStringValue(value) == null
        assert TypeConverters.toDate(value) == null
        assert TypeConverters.toInteger(value, 7) == 7
        assert TypeConverters.toStringValue(value, 'fallback') == 'fallback'
    }

    static class NullToString {
        @Override
        String toString() { null }
    }

    @Test
    void testHashCode() {
        assert toTypeConverting(a: 1, b: 2).hashCode() == toTypeConverting(a: 1, b: 2).hashCode()
        assert toTypeConverting([:]).hashCode() == toTypeConverting([:]).hashCode()
        assert toTypeConverting(a: 1, b: 2).hashCode() == toTypeConverting(b: 2, a: 1).hashCode()

        assert toTypeConverting(a: 1, b: 2).hashCode() != [b: 2, a: 1].hashCode()
        assert toTypeConverting(a: 1, b: 2).hashCode() != ["b": 2, a: 1].hashCode()
    }

    protected toTypeConverting(map) {
        new TypeConvertingMap(map)
    }
}
