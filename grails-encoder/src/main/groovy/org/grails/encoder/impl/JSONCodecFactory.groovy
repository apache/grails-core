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

import groovy.transform.CompileStatic

import groovy.json.StringEscapeUtils
import org.grails.encoder.CodecFactory
import org.grails.encoder.CodecIdentifier
import org.grails.encoder.Encoder
import org.grails.encoder.Decoder


@CompileStatic
class JSONCodecFactory implements CodecFactory {
    Encoder encoder = new BasicJSONEncoder()

    Decoder decoder = new Decoder() {
        def decode(Object obj) {
            obj != null ? StringEscapeUtils.unescapeJavaScript(obj.toString()) : null
        }

        CodecIdentifier getCodecIdentifier() {
            BasicJSONEncoder.JSON_CODEC_IDENTIFIER
        }
    }
}
