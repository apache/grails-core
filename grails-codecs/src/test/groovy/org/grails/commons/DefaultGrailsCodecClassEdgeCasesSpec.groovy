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
package org.grails.commons

import org.springframework.core.Ordered
import spock.lang.Specification

import org.grails.encoder.CodecFactory
import org.grails.encoder.Decoder
import org.grails.encoder.Encoder
import org.grails.encoder.StreamingEncoder

class DefaultGrailsCodecClassEdgeCasesSpec extends Specification {

    void 'the artefact handler matches classes ending in Codec that are not domain classes'() {
        given:
        CodecArtefactHandler handler = new CodecArtefactHandler()

        expect:
        CodecArtefactHandler.TYPE == 'Codec'
        handler.isArtefactClass(DgcOrderedCodec)
        !handler.isArtefactClass(DgcNotArtefact)
        !handler.isArtefactClass(null)
    }

    void 'a codec implementing CodecFactory is used directly and its order is honoured'() {
        given:
        DefaultGrailsCodecClass codecClass = new DefaultGrailsCodecClass(DgcOrderedCodec)

        when:
        codecClass.afterPropertiesSet()

        then:
        codecClass.encoder.encode('x') == 'factory-encoded'
        codecClass.decoder.decode('x') == 'factory-decoded'
        codecClass.order == 42
    }

    void 'a codec implementing Encoder directly is autowired and used as is'() {
        given:
        DefaultGrailsCodecClass codecClass = new DefaultGrailsCodecClass(DgcEncoderCodec)

        when:
        codecClass.afterPropertiesSet()

        then:
        codecClass.encoder.encode('y') == 'direct-encoded'
        codecClass.decoder == null
    }

    void 'a streaming encoder codec is wrapped so streaming still works'() {
        given:
        DefaultGrailsCodecClass codecClass = new DefaultGrailsCodecClass(DgcStreamingCodec)

        when:
        codecClass.afterPropertiesSet()

        then:
        codecClass.encoder.encode('z') == 'streaming-encoded'
        codecClass.encoder instanceof StreamingEncoder
    }

    void 'a static order property overrides the instantiation order'() {
        given:
        DefaultGrailsCodecClass codecClass = new DefaultGrailsCodecClass(DgcExplicitOrderCodec)

        when:
        codecClass.afterPropertiesSet()

        then:
        codecClass.order == 7
    }

    void 'configureCodecMethods initializes the codec at most once and is idempotent'() {
        given:
        DefaultGrailsCodecClass codecClass = new DefaultGrailsCodecClass(DgcOrderedCodec)

        when:
        codecClass.configureCodecMethods()
        Encoder firstEncoder = codecClass.encoder
        codecClass.configureCodecMethods()

        then:
        codecClass.encoder.is(firstEncoder)
    }

    void 'a null encode target passed to a closure codec short circuits to null'() {
        given:
        DefaultGrailsCodecClass codecClass = new DefaultGrailsCodecClass(DgcClosureCodec)
        codecClass.afterPropertiesSet()

        expect:
        codecClass.encoder.encode(null) == null
        codecClass.decoder.decode('anything') == 'decoded-anything'
    }

}

class DgcOrderedCodec implements CodecFactory, Ordered {

    Encoder getEncoder() {
        new DgcFixedEncoder('factory-encoded')
    }

    Decoder getDecoder() {
        new DgcFixedDecoder('factory-decoded')
    }

    int getOrder() {
        42
    }

}

class DgcFixedEncoder implements Encoder {

    private final String result

    DgcFixedEncoder(String result) {
        this.result = result
    }

    org.grails.encoder.CodecIdentifier getCodecIdentifier() {
        new org.grails.encoder.DefaultCodecIdentifier('DgcFixed')
    }

    Object encode(Object target) {
        result
    }

    void markEncoded(CharSequence string) {
    }

    boolean isSafe() {
        false
    }

    boolean isApplyToSafelyEncoded() {
        true
    }

}

class DgcFixedDecoder implements Decoder {

    private final String result

    DgcFixedDecoder(String result) {
        this.result = result
    }

    org.grails.encoder.CodecIdentifier getCodecIdentifier() {
        new org.grails.encoder.DefaultCodecIdentifier('DgcFixed')
    }

    Object decode(Object o) {
        result
    }

}

class DgcNotArtefact {
}

class DgcEncoderCodec implements Encoder {

    org.grails.encoder.CodecIdentifier getCodecIdentifier() {
        new org.grails.encoder.DefaultCodecIdentifier('DgcEncoder')
    }

    Object encode(Object target) {
        'direct-encoded'
    }

    void markEncoded(CharSequence string) {
    }

    boolean isSafe() {
        false
    }

    boolean isApplyToSafelyEncoded() {
        true
    }

}

class DgcStreamingCodec implements StreamingEncoder {

    org.grails.encoder.CodecIdentifier getCodecIdentifier() {
        new org.grails.encoder.DefaultCodecIdentifier('DgcStreaming')
    }

    Object encode(Object target) {
        'streaming-encoded'
    }

    void markEncoded(CharSequence string) {
    }

    boolean isSafe() {
        false
    }

    boolean isApplyToSafelyEncoded() {
        true
    }

    void encodeToStream(Encoder thisInstance, CharSequence source, int offset, int len,
            org.grails.encoder.EncodedAppender appender, org.grails.encoder.EncodingState encodingState) {
    }

}

class DgcExplicitOrderCodec implements CodecFactory {

    static order = 7

    Encoder getEncoder() {
        new DgcFixedEncoder('unused')
    }

    Decoder getDecoder() {
        new DgcFixedDecoder('unused')
    }

}

class DgcClosureCodec {

    static encode = { it }
    static decode = { "decoded-${it}" }

}
