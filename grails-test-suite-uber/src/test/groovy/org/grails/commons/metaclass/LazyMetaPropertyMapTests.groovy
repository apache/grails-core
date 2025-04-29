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
package org.grails.commons.metaclass

import grails.beans.util.LazyMetaPropertyMap
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * @author Graeme Rocher
 */
class LazyMetaPropertyMapTests {

    @Test
    void testOverridePropertiesRecursionBug() {
        PropertyMapTest.metaClass.getProperties = {-> new LazyMetaPropertyMap(delegate) }

        def obj = new PropertyMapTest(name:"Homer", age:45)

        assertFalse obj.properties.containsKey('properties')
        assertEquals 3, obj.properties.size()
    }

    @Test
    void testSelectSubMap() {

        def map = new LazyMetaPropertyMap(new PropertyMapTest(name:"Bart", age:11, other:"stuff"))

        def submap = map['name', 'age']
        assertEquals 2, (int) submap.size()
        assertEquals "Bart", submap.name
        assertEquals 11, (int) submap.age
    }

    @Test
    void testSize() {
        def map = new LazyMetaPropertyMap(new PropertyMapTest())
        assertEquals 3, map.size()
    }

    @Test
    void testIsEmpty() {
        def map = new LazyMetaPropertyMap(new PropertyMapTest())
        assertFalse map.isEmpty()
    }

    @Test
    void testContainsKey() {
        def map = new LazyMetaPropertyMap(new PropertyMapTest())

        assertTrue map.containsKey("name")
        assertTrue map.containsKey("age")
        assertFalse map.containsKey("fo")
    }

    @Test
    void testContainsValue() {
        def map = new LazyMetaPropertyMap(new PropertyMapTest(name:"Homer", age:45))

        assertTrue map.containsValue("Homer")
        assertTrue map.containsValue(45)
        assertFalse map.containsValue("fo")
    }

    @Test
    void testGet() {
        def map = new LazyMetaPropertyMap(new PropertyMapTest(name:"Homer", age:45))

        assertEquals "Homer", map.get("name")
        assertEquals "Homer", map.name
        assertEquals "Homer", map['name']

        assertEquals 45, map.get("age")
        assertEquals 45, (int) map.age
        assertEquals 45, map['age']

        assertNull map.foo
        assertNull map['foo']
        assertNull map.get('foo')
    }

    @Test
    void testPut() {
        def map = new LazyMetaPropertyMap(new PropertyMapTest(name:"Bart", age:11))

        map.name = "Homer"
        map.age = 45
        assertEquals "Homer", map.get("name")
        assertEquals "Homer", map.name
        assertEquals "Homer", map['name']

        def old = map.put("name", "lisa")
        assertEquals "Homer", old

        assertEquals "lisa", map.name
    }

    @Test
    void testKeySet() {
        def map = new LazyMetaPropertyMap(new PropertyMapTest(name:"Bart", age:11))

        def keys = map.keySet()

        assertTrue keys.contains("name")
        assertTrue keys.contains("age")
    }

    @Test
    void testValues() {
        def map = new LazyMetaPropertyMap(new PropertyMapTest(name:"Bart", age:11))

        def values = map.values()

        assertTrue values.contains("Bart")
        assertTrue values.contains(11)
    }
}

class PropertyMapTest {
    String name
    Integer age
    String other
}
