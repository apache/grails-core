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
import org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation

import groovy.transform.CompileStatic

@CompileStatic
class HexCodecExtensionMethods {

    static Object HEXDIGITS = '0123456789abcdef'

    // Expects an array/list of numbers
    static Object encodeAsHex(Object theTarget) {
        if (theTarget == null || theTarget instanceof NullObject) {
            return null
        }

        byte[] bytes = theTarget instanceof String ? ((String) theTarget).getBytes(StandardCharsets.UTF_8) : DigestUtils.toByteArray(theTarget)
        StringBuilder result = new StringBuilder(bytes.length * 2)
        String hexDigits = (String) HEXDIGITS
        for (byte value : bytes) {
            int unsignedValue = value & 0xFF
            result.append(hexDigits.charAt((unsignedValue & 0xF0) >> 4))
            result.append(hexDigits.charAt(unsignedValue & 0x0F))
        }
        return result.toString()
    }

    static Object decodeHex(Object theTarget) {
        if (theTarget == null || theTarget instanceof NullObject || !DefaultTypeTransformation.castToBoolean(theTarget)) return null

        String str = theTarget.toString().toLowerCase()
        if (str.size() % 2 != 0) {
            throw new UnsupportedOperationException('Decode of hex strings requires strings of even length')
        }

        byte[] result = new byte[str.size().intdiv(2)]
        String hexDigits = (String) HEXDIGITS
        for (int i = 0; i < str.size(); i += 2) {
            int high = hexDigits.indexOf((int) str.charAt(i))
            int low = hexDigits.indexOf((int) str.charAt(i + 1))
            result[i.intdiv(2)] = (byte) ((high << 4) | low)
        }
        return result
    }
}
