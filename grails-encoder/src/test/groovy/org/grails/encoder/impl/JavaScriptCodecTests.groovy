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
package org.grails.encoder.impl

import org.grails.encoder.impl.JavaScriptEncoder
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals

class JavaScriptCodecTests {

    def codec = new JavaScriptEncoder()

    @Test
    void testEncode() {
        assertEquals('\\u0022\\u0022', codec.encode('""'))
        assertEquals("\\u0027\\u0027", codec.encode("''"))
        assertEquals('\\u005c', codec.encode('\\'))
    }

    @Test
    void testEncodeNewlines() {
        // CRLF should be collapsed to LF
        assertEquals("\\n", codec.encode("\r\n"))

        // All other combinations should pass through (although \r is encoded as \n)
        assertEquals("\\n", codec.encode("\r"))
        assertEquals("\\n", codec.encode("\n"))
        assertEquals("\\n\\n", codec.encode("\r\r"))
        assertEquals("\\n\\n", codec.encode("\n\n"))
        assertEquals("\\n\\n", codec.encode("\n\r"))
    }

    @Test
    void testEncodeSeparators() {
        String separators="\u2028\u2029"; // http://timelessrepo.com/json-isnt-a-javascript-subset
        assertEquals(2, separators.length());
        assertEquals("\\u2028\\u2029", codec.encode(separators));
    }
}
