package org.grails.taglib


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

    protected toGroovyPageAttributes(map) {
        new GroovyPageAttributes(map)
    }
}
