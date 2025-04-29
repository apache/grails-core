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

import java.security.MessageDigest

abstract class DigestUtils {

    // Digest byte[], any list/array or string into a byte[]
    static digest(String algorithm, data) {
        if (data == null) {
            return null
        }

        def md = MessageDigest.getInstance(algorithm)
        def src
        if (data instanceof Byte[] || data instanceof byte[]) {
            src = data
        }
        else if (data instanceof List || data.getClass().isArray()) {
            src = new byte[data.size()]
            data.eachWithIndex { v, i -> src[i] = v }
        }
        else {
            src = data.toString().getBytes("UTF-8")
        }
        md.update(src) // This probably needs to use the thread's Locale encoding
        return md.digest()
    }
}
