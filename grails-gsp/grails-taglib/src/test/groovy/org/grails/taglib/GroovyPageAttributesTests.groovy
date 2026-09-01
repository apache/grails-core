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
    @Test
    void testGspTagSyntaxCallAttributeIsAMapEntry() {
        def attrs = toGroovyPageAttributes([:])

        attrs['gspTagSyntaxCall'] = 'value'

        assertEquals 'value', attrs['gspTagSyntaxCall']
        assertEquals 'value', attrs.gspTagSyntaxCall
        assertTrue attrs.isGspTagSyntaxCall()

        attrs.setGspTagSyntaxCall(false)

        assertFalse attrs.isGspTagSyntaxCall()
        assertEquals 'value', attrs['gspTagSyntaxCall']
    }

    // https://github.com/apache/grails-core/issues/16280
    @Test
    void testAttributeNamesCollidingWithInheritedProperties() {
        def attrs = toGroovyPageAttributes([:])

        attrs['empty'] = 'e1'
        attrs['class'] = 'container'

        assertEquals 'e1', attrs['empty']
        assertEquals 'container', attrs['class']
        assertEquals 'container', attrs.class
        assertFalse attrs.isEmpty()
        assertEquals GroovyPageAttributes, attrs.getClass()
    }

    // https://github.com/apache/grails-core/issues/16280
    @Test
    void testWritingAnAttributeNamedAfterASetterNoLongerInvokesThatSetter() {
        def attrs = toGroovyPageAttributes([:])

        // Grails 7 routed both of these to setGspTagSyntaxCall(); they now write a map entry and
        // leave the flag alone, so an attribute of that name reaches the tag as an attribute
        attrs.gspTagSyntaxCall = false
        assertEquals false, attrs['gspTagSyntaxCall']
        assertTrue attrs.isGspTagSyntaxCall()

        def other = toGroovyPageAttributes([:])
        other['gspTagSyntaxCall'] = false
        assertEquals false, other['gspTagSyntaxCall']
        assertTrue other.isGspTagSyntaxCall()

        // the flag is still driven by its own accessors
        other.setGspTagSyntaxCall(false)
        assertFalse other.isGspTagSyntaxCall()
        assertEquals false, other['gspTagSyntaxCall']
    }

    // https://github.com/apache/grails-core/issues/16280
    @Test
    void testStaticCompilationWritesTheFieldRatherThanTheMap() {
        // Documented limitation: @CompileStatic binds property syntax to the declared setter, so
        // unlike params — where the equivalent assignment fails to compile — this one compiles
        // and silently drives the flag instead of the map. Use the subscript form in statically
        // compiled taglibs.
        def viaProperty = toGroovyPageAttributes([:])
        StaticallyCompiledAccess.write(viaProperty)

        assertFalse viaProperty.isGspTagSyntaxCall()             // the flag was written
        assertFalse viaProperty.containsKey('gspTagSyntaxCall')  // the map was not

        // the subscript form addresses the map under static compilation, as it does dynamically
        def viaSubscript = toGroovyPageAttributes([:])
        StaticallyCompiledAccess.writeViaSubscript(viaSubscript)

        assertEquals false, viaSubscript['gspTagSyntaxCall']      // the map was written
        assertTrue viaSubscript.isGspTagSyntaxCall()              // the flag was not
    }

    @CompileStatic
    static class StaticallyCompiledAccess {

        static void write(GroovyPageAttributes attrs) {
            attrs.gspTagSyntaxCall = false
        }

        static void writeViaSubscript(GroovyPageAttributes attrs) {
            attrs['gspTagSyntaxCall'] = false
        }
    }

    protected toGroovyPageAttributes(map) {
        new GroovyPageAttributes(map)
    }
}
