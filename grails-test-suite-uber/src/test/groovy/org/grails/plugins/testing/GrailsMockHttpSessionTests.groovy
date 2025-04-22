/* Copyright 2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.plugins.testing

import org.grails.plugins.testing.GrailsMockHttpSession
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNull

/**
 * Test case for {@link org.grails.plugins.testing.GrailsMockHttpSession}.
 */
class GrailsMockHttpSessionTests {
    /**
     * Tests that property access on the session retrieves the value
     * for the attribute with the same name as the property unless
     * that property exists on the session itself. In other words,
     * the session should only retrieve an attribute if the property
     * is missing.
     */
    @Test
    void testGetProperty() {
        // Set up the test session with some attributes.
        def testSession = new GrailsMockHttpSession()
        testSession.setAttribute("attr1", "value1")
        testSession.setAttribute("attr2", "value2")

        // Check that the attribute values are returned if the property
        // name matches an attribute.
        assertEquals "value1", testSession.attr1
        assertEquals "value2", testSession.attr2

        // Make sure that the real properties on the session are still
        // accessible.
        assertEquals 0, testSession.maxInactiveInterval

        // Unrecognised properties with no corresponding attribute
        // should simply return null.
        assertNull testSession.attr3
    }

    /**
     * Tests that attributes can be set on the session via property
     * notation, while real properties on the session can still be
     * modified.
     */
    @Test
    void testSetProperty() {
        // Set up the test session.
        def testSession = new GrailsMockHttpSession()

        // Add attributes via property notation.
        testSession.attr1 = "value1"
        testSession.attr2 = "value2"
        assertEquals "value1", testSession.getAttribute("attr1")
        assertEquals "value2", testSession.getAttribute("attr2")

        // Make sure that the real properties on the session are still
        // accessible.
        assertEquals 0, testSession.maxInactiveInterval
        testSession.maxInactiveInterval = 30
        assertEquals 30, testSession.maxInactiveInterval
    }
    /**
     * Tests that property access on the session retrieves the value
     * for the attribute with the same name as the property unless
     * that property exists on the session itself. In other words,
     * the session should only retrieve an attribute if the property
     * is missing.
     */
    @Test
    void testGetAt() {
        // Set up the test session with some attributes.
        def testSession = new GrailsMockHttpSession()
        testSession.setAttribute("attr1", "value1")
        testSession.setAttribute("attr2", "value2")

        // Check that the attribute values are returned if the property
        // name matches an attribute.
        assertEquals "value1", testSession["attr1"]
        assertEquals "value2", testSession["attr2"]

        // Unrecognised properties with no corresponding attribute
        // should simply return null.
        assertNull testSession["attr3"]
    }

    /**
     * Tests that attributes can be set on the session via property
     * notation, while real properties on the session can still be
     * modified.
     */
    @Test
    void testPutAt() {
        // Set up the test session.
        def testSession = new GrailsMockHttpSession()

        // Add attributes via property notation.
        testSession["attr1"] = "value1"
        testSession["attr2"] = "value2"
        assertEquals "value1", testSession.getAttribute("attr1")
        assertEquals "value2", testSession.getAttribute("attr2")
    }
}
