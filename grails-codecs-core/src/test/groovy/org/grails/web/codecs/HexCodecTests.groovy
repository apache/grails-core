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
package org.grails.web.codecs

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertIterableEquals

class HexCodecTests {

    @Test
    void testEncode() {

        def expectedResult = '412042204320442045'

        // we want to verify that both Byte[] and byte[] inputs work
        String primitiveResult = [65, 32, 66, 32, 67, 32, 68, 32, 69].encodeAsHex()
        String toStringResult = 'A B C D E'.encodeAsHex()

        assertEquals(expectedResult, primitiveResult)
        assertEquals(expectedResult,toStringResult)

        //make sure encoding null returns null
        assertEquals(null.encodeAsHex(), null)
    }

    @Test
    void testDecode() {
        String data = '412042204320442045'
        byte[] result = data.decodeHex()
        assertIterableEquals(new Byte[] {65, 32, 66, 32, 67, 32, 68, 32, 69}.toList(), result.toList())
        //make sure decoding null returns null
        assertEquals(null.decodeHex(), null)
    }

    void testRoundtrip() {
        assertIterableEquals([65, 32, 66, 32, 67, 32, 68, 32, 69], [65, 32, 66, 32, 67, 32, 68, 32, 69].encodeAsHex().decodeHex().toList())
        assertIterableEquals([65, 32, 66, 32, 67, 32, 68, 32, 69], 'A B C D E'.encodeAsHex().decodeHex().toList())
    }
}
