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

import java.lang.reflect.Array
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import groovy.transform.CompileStatic

@CompileStatic
abstract class DigestUtils {

    // Digest byte[], any list/array or string into a byte[]
    static Object digest(String algorithm, Object data) {
        if (data == null) {
            return null
        }

        MessageDigest md = MessageDigest.getInstance(algorithm)
        byte[] src = toByteArray(data)
        md.update(src) // This probably needs to use the thread's Locale encoding
        return md.digest()
    }

    protected static byte[] toByteArray(Object data) {
        if (data instanceof byte[]) {
            return (byte[]) data
        }
        if (data instanceof Byte[]) {
            return toByteArrayFromWrapper((Byte[]) data)
        }
        if (data instanceof List) {
            return toByteArrayFromList((List<?>) data)
        }
        if (data.getClass().isArray()) {
            return toByteArrayFromArray(data, Array.getLength(data))
        }

        return data.toString().getBytes(StandardCharsets.UTF_8)
    }

    private static byte[] toByteArrayFromWrapper(Byte[] data) {
        byte[] result = new byte[data.length]
        for (int i = 0; i < data.length; i++) {
            result[i] = data[i].byteValue()
        }
        return result
    }

    private static byte[] toByteArrayFromList(List<?> data) {
        byte[] result = new byte[data.size()]
        for (int i = 0; i < data.size(); i++) {
            result[i] = toByte(data.get(i))
        }
        return result
    }

    private static byte[] toByteArrayFromArray(Object data, int length) {
        byte[] result = new byte[length]
        for (int i = 0; i < length; i++) {
            result[i] = toByte(Array.get(data, i))
        }
        return result
    }

    private static byte toByte(Object value) {
        return ((Number) value).byteValue()
    }
}
