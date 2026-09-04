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
package org.grails.taglib

import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

class GroovyPageAttributesTests {

    @Test
    void testCloneAttributes() {
        def originalMap = [framework: 'Grails', company: 'SpringSource']
        def wrapper = new GroovyPageAttributes(originalMap)
        def cloned = wrapper.clone()
        assertNotNull cloned
        assert System.identityHashCode(cloned) != System.identityHashCode(wrapper) : "Should not be the same map"
        assertEquals "Grails", cloned.framework
        assertEquals "SpringSource", cloned.company
    }

    @Test
    void testMutatingImpactsWrappedMap() {
        def originalMap = [framework: 'Grails', company: 'SpringSource']
        def wrapper = new GroovyPageAttributes(originalMap)

        // remove an entry from the wrapper
        wrapper.remove('framework')
        assertEquals 1, originalMap.size()
        assertNull originalMap.framework
        assertEquals 'SpringSource', originalMap.company

        // add an entry to the wrapper
        wrapper.lang = 'Groovy'
        assertEquals 2, originalMap.size()
        assertNull originalMap.framework
        assertEquals 'SpringSource', originalMap.company
        assertEquals 'Groovy', originalMap.lang

        // add several entries (via putAll) to the wrapper
        def newMap = [ide: 'STS', target: 'JVM']
        wrapper.putAll(newMap)
        assertEquals 4, originalMap.size()
        assertNull originalMap.framework
        assertEquals 'SpringSource', originalMap.company
        assertEquals 'Groovy', originalMap.lang
        assertEquals 'STS', originalMap.ide
        assertEquals 'JVM', originalMap.target
    }

    @Test
    void testEqualsImpl() {
        assert toGroovyPageAttributes([:]) == toGroovyPageAttributes([:])
        assert toGroovyPageAttributes(a: 1) == toGroovyPageAttributes(a: 1)
        assert toGroovyPageAttributes(a: 1, b: 2) == toGroovyPageAttributes(a: 1, b: 2)
        assert toGroovyPageAttributes(a: 1, b: 2) == toGroovyPageAttributes(b: 2, a: 1)

        assert toGroovyPageAttributes(a: 1, b: 2) != toGroovyPageAttributes(a: 1, b: "2")
        assert toGroovyPageAttributes(a: 1) != toGroovyPageAttributes(a: 1, b: 2)
        assert toGroovyPageAttributes(a: 1, b: 2) == toGroovyPageAttributes(b: 2, "a": 1)
    }

    @Test
    void testHashCode() {
        assert toGroovyPageAttributes(a: 1, b: 2).hashCode() == toGroovyPageAttributes(a: 1, b: 2).hashCode()
        assert toGroovyPageAttributes([:]).hashCode() == toGroovyPageAttributes([:]).hashCode()
        assert toGroovyPageAttributes(a: 1, b: 2).hashCode() == toGroovyPageAttributes(b: 2, a: 1).hashCode()

        assert toGroovyPageAttributes(a: 1, b: 2).hashCode() != [b: 2, a: 1].hashCode()
        assert toGroovyPageAttributes(a: 1, b: 2).hashCode() != ["b": 2, a: 1].hashCode()
    }

    @Test
    void testToString() {
        def attrs = toGroovyPageAttributes(one:"foo")

        assert '[one:foo]' == attrs.toString()
    }

    // https://github.com/apache/grails-core/issues/16280
    // Reading an attribute named after an accessor on this class addresses the map, not the accessor.
    @Test
    void testReadingAnAttributeNamedAfterAnAccessorAddressesTheMap() {
        def attrs = toGroovyPageAttributes([:])
        attrs.put('gspTagSyntaxCall', 'an attribute value')

        assertEquals 'an attribute value', attrs['gspTagSyntaxCall']
        assertEquals 'an attribute value', attrs.gspTagSyntaxCall
        assertTrue attrs.gspTagSyntaxCall()
    }

    // https://github.com/apache/grails-core/issues/16280
    @Test
    void testStaticallyCompiledAttributeReadAddressesTheMap() {
        def attrs = toGroovyPageAttributes([:])
        StaticallyCompiledAccess.write(attrs, 'an attribute value')

        assertEquals 'an attribute value', attrs['gspTagSyntaxCall']
        assertEquals 'an attribute value', StaticallyCompiledAccess.readSubscript(attrs)
        assertEquals 'an attribute value', StaticallyCompiledAccess.readProperty(attrs)
        assertTrue attrs.gspTagSyntaxCall()
    }

    // https://github.com/apache/grails-core/issues/16280
    // gspTagSyntaxCall keeps a real setter, so assigning that one name invokes the setter rather
    // than storing an entry - both in dotted and subscript form. That is the Grails 7 behaviour,
    // and TagOutput and GroovyPage rely on it. Use put() to store an attribute of that name.
    @Test
    void testAssigningGspTagSyntaxCallInvokesTheSetter() {
        def dotted = toGroovyPageAttributes([:])
        dotted.gspTagSyntaxCall = false
        assertFalse dotted.gspTagSyntaxCall()
        assertFalse dotted.containsKey('gspTagSyntaxCall')

        def subscript = toGroovyPageAttributes([:])
        subscript['gspTagSyntaxCall'] = false
        assertFalse subscript.gspTagSyntaxCall()
        assertFalse subscript.containsKey('gspTagSyntaxCall')
    }

    @Test
    void testGspTagSyntaxCallDefaultsToTrueAndIsSettable() {
        def attrs = toGroovyPageAttributes([:])
        assertTrue attrs.gspTagSyntaxCall()

        attrs.setGspTagSyntaxCall(false)
        assertFalse attrs.gspTagSyntaxCall()

        assertFalse new GroovyPageAttributes([:], false).gspTagSyntaxCall()
    }

    protected toGroovyPageAttributes(map) {
        new GroovyPageAttributes(map)
    }

    @CompileStatic
    static class StaticallyCompiledAccess {

        static void write(GroovyPageAttributes attrs, Object value) {
            attrs.put('gspTagSyntaxCall', value)
        }

        static Object readSubscript(GroovyPageAttributes attrs) {
            attrs['gspTagSyntaxCall']
        }

        static Object readProperty(GroovyPageAttributes attrs) {
            attrs.gspTagSyntaxCall
        }
    }
}
