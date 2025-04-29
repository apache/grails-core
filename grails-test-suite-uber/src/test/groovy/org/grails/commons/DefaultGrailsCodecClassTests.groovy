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

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals

/**
 * @author Graeme Rocher
 * @since 1.1
 */
class DefaultGrailsCodecClassTests {

    @BeforeEach
    protected void setUp() {
        ExpandoMetaClass.enableGlobally()
    }

    @AfterEach
    protected void tearDown() {
        ExpandoMetaClass.disableGlobally()
    }

    @Test
    void testCodecWithClosures() {
        def codecClass = new DefaultGrailsCodecClass(CodecWithClosuresCodec)
        codecClass.afterPropertiesSet();
        assertEquals "encoded", codecClass.encoder.encode("stuff")
        assertEquals "decoded", codecClass.decoder.decode("stuff")
    }

    @Test
    void testCodecWithMethods() {
        def codecClass = new DefaultGrailsCodecClass(CodecWithMethodsCodec)
        codecClass.afterPropertiesSet();
        assertEquals "encoded", codecClass.encoder.encode("stuff")
        assertEquals "decoded", codecClass.decoder.decode("stuff")
    }
}

class CodecWithClosuresCodec {
    static encode = { "encoded" }
    static decode = { "decoded" }
}

class CodecWithMethodsCodec {
    def encode(obj) { "encoded" }
    def decode(obj) { "decoded" }
}
