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

import org.apache.commons.codec.binary.Base64

/**
 * A codec that encodes and decodes Objects using Base64 encoding.
 *
 * @author Drew Varner
 */
class Base64CodecExtensionMethods {

    static encodeAsBase64(theTarget) {
        if (theTarget == null || theTarget instanceof NullObject) {
            return null
        }

        if (theTarget instanceof Byte[] || theTarget instanceof byte[]) {
            return new String(Base64.encodeBase64(theTarget))
        }

        return new String(Base64.encodeBase64(theTarget.toString().getBytes(StandardCharsets.UTF_8)))
    }

    static decodeBase64(theTarget) {
        if (theTarget == null || theTarget instanceof NullObject) {
            return null
        }

        if (theTarget instanceof Byte[] || theTarget instanceof byte[]) {
            return Base64.decodeBase64(theTarget)
        }

        return Base64.decodeBase64(theTarget.toString().getBytes(StandardCharsets.UTF_8))
    }
}
