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
package org.grails.web.codecs;

import org.grails.encoder.impl.HTMLJSCodec
import org.grails.encoder.Decoder
import org.grails.encoder.DefaultEncodingStateRegistry
import org.grails.encoder.EncodingStateRegistry
import org.grails.encoder.StreamingEncoder
import org.grails.buffer.FastStringWriter

import spock.lang.Specification

public class HTMLJSCodecSpec extends Specification {
    StreamingEncoder encoder
    Decoder decoder
    EncodingStateRegistry registry=new DefaultEncodingStateRegistry();
    
    def setup() {
        def codec = new HTMLJSCodec()
        encoder = codec.encoder
        decoder = codec.decoder
    }
    
    def "do html and js encoding"() {
        expect:
            encoder.encode("<script>") == '\\u0026lt\\u003bscript\\u0026gt\\u003b'
            decoder.decode('\\u0026lt\\u003bscript\\u0026gt\\u003b') == '<script>'
    }
    
    def "do streaming html and js encoding"() {
        given:
            def target = new FastStringWriter()
            def writer = target.getWriterForEncoder(encoder, registry)
        when:
            writer << "<script>"
            writer.flush()
        then:
            target.toString() == '\\u0026lt\\u003bscript\\u0026gt\\u003b'
    }
}
