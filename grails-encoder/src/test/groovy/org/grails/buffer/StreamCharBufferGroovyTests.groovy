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
package org.grails.buffer

import org.grails.buffer.StreamCharBuffer
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

class StreamCharBufferGroovyTests {

    @Test
    void testStringDelegatingMetaClass() {
        def charBuffer = new StreamCharBuffer()
        charBuffer.writer.write('0123456789')
        assertEquals((int) 7, (int) charBuffer.indexOf('7'))
        // test caching
        assertEquals(1, (int) charBuffer.indexOf('123'))
        // test another java.lang.String method
        assertEquals('xxxxxxxxxx', charBuffer.replaceAll(/\d/, 'x'))
    }

    @Test
    void testCharSequence() {
        def charBuffer = new StreamCharBuffer()
        charBuffer.writer.write('0123456789')
        def pattern = ~/^0\d+9$/
        assertTrue(pattern.matcher(charBuffer).matches())
    }

    @Test
    void testStringNonArgMethods() {
        def charBuffer = new StreamCharBuffer()
        charBuffer.writer.write(' ABC ')
        assertEquals(' abc ', charBuffer.toLowerCase())
        assertEquals('ABC', charBuffer.trim())
    }

    @Test
    void testAsInteger() {
        def charBuffer = new StreamCharBuffer()
        charBuffer.writer.write('123')
        assertEquals(123, charBuffer as Integer)
    }

    @Test
    void testAsLong() {
        def charBuffer = new StreamCharBuffer()
        charBuffer.writer.write('123456789101112')
        assertEquals(123456789101112L, charBuffer as Long)
    }

    @Test
    void testAsCharArray() {
        def charBuffer = new StreamCharBuffer()
        charBuffer.writer.write('ABC')
        assertArrayEquals('ABC' as char[], charBuffer as char[])
    }

    @Test
    void testAsString() {
        def charBuffer = new StreamCharBuffer()
        charBuffer.writer.write('ABC')
        assertEquals('ABC', charBuffer as String)
    }
}
