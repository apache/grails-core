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
package org.grails.plugins.testing

import org.grails.plugins.testing.GrailsMockHttpServletResponse
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals

/**
 * Test case for {@link org.grails.plugins.testing.GrailsMockHttpServletResponse}.
 */
class GrailsMockHttpServletResponseTests {
    /**
     * Tests that the left-shift operator appends the given text to the
     * response output.
     */
    @Test
    void testLeftShift() {
        def testResponse = new GrailsMockHttpServletResponse()
        assertEquals "", testResponse.contentAsString

        testResponse << "Some string or other"
        assertEquals "Some string or other", testResponse.contentAsString

        testResponse << "\nand another line"
        assertEquals "Some string or other\nand another line", testResponse.contentAsString
    }
}
