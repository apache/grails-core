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
