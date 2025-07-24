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
package org.grails.plugins.codecs

import java.nio.charset.StandardCharsets

import org.codehaus.groovy.runtime.NullObject

class HexCodecExtensionMethods {

    static HEXDIGITS = '0123456789abcdef'

    // Expects an array/list of numbers
    static encodeAsHex(theTarget) {
        if (theTarget == null || theTarget instanceof NullObject) {
            return null
        }

        def result = new StringBuilder()
        if (theTarget instanceof String) {
            theTarget = theTarget.getBytes(StandardCharsets.UTF_8)
        }
        theTarget.each() {
            result << HexCodecExtensionMethods.HEXDIGITS[(it & 0xF0) >> 4]
            result << HexCodecExtensionMethods.HEXDIGITS[it & 0x0F]
        }
        return result.toString()
    }

    static decodeHex(theTarget) {
        if (!theTarget) return null

        def output = []

        def str = theTarget.toString().toLowerCase()
        if (str.size() % 2) {
            throw new UnsupportedOperationException("Decode of hex strings requires strings of even length")
        }

        def currentByte
        str.eachWithIndex { val, idx ->
            if (!(idx % 2)) {
                currentByte = HEXDIGITS.indexOf(val) << 4
            }
            else {
                output << (currentByte | HEXDIGITS.indexOf(val))
                currentByte = 0
            }
        }

        def result = new byte[output.size()]
        output.eachWithIndex { v, i -> result[i] = v }
        return result
    }
}
