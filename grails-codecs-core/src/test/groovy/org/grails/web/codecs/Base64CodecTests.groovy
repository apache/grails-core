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

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

class Base64CodecTests {

    byte[] dataPrimitive = new byte[256]
    Byte[] dataWrapper = new Byte[256]

    @BeforeEach
    protected void setUp() {
        for (i in 0..255) {
            dataPrimitive[i] = i
            dataWrapper[i] = (byte) i
        }
    }

    @Test
    void testEncode() {

        def expectedResult = 'AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8gISIjJCUmJygpKissLS4vMDEyMzQ1Njc4OTo7PD0+P0BBQkNERUZHSElKS0xNTk9QUVJTVFVWV1hZWltcXV5fYGFiY2RlZmdoaWprbG1ub3BxcnN0dXZ3eHl6e3x9fn+AgYKDhIWGh4iJiouMjY6PkJGSk5SVlpeYmZqbnJ2en6ChoqOkpaanqKmqq6ytrq+wsbKztLW2t7i5uru8vb6/wMHCw8TFxsfIycrLzM3Oz9DR0tPU1dbX2Nna29zd3t/g4eLj5OXm5+jp6uvs7e7v8PHy8/T19vf4+fr7/P3+/w=='

        // we want to verify that both Byte[] and byte[] inputs work
        String primitiveResult = dataPrimitive.encodeAsBase64()
        String wrapperResult = dataWrapper.encodeAsBase64()

        assertEquals(expectedResult, primitiveResult)
        assertEquals(expectedResult,wrapperResult)

        //make sure encoding null returns null
        assertNull null.encodeAsBase64()
    }

    @Test
    void testDecode() {
        String data = 'd2hhdA=='
        byte[] result = data.decodeBase64()

        assertEquals(119, result[0])
        assertEquals(104, result[1])
        assertEquals(97, result[2])
        assertEquals(116, result[3])

        //make sure decoding null returns null
        assertNull null.decodeBase64()
    }

    @Test
    void testRountrip() {
        assertArrayEquals(dataPrimitive, dataPrimitive.encodeAsBase64().decodeBase64())
        assertArrayEquals(dataWrapper, dataWrapper.encodeBase64().decodeBase64())
    }

    @Test
    void testEncodeDecodeAsBase64() {
        assertEquals "dGVzdA==", "test".bytes.encodeAsBase64()
        assertEquals "dGVzdA==", "test".encodeAsBase64()
        assertEquals "test", new String("dGVzdA==".decodeBase64())
    }
}
