/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.testing.httpclient

import java.nio.charset.StandardCharsets

import spock.lang.Specification

class MultipartBodySpec extends Specification {

    private static final String SAMPLE_TEXT = 'ol\u00E1'

    void 'builder defaults to UTF-8 encoding'() {
        when:
        def body = MultipartBody.builder()
                .addPart('message', SAMPLE_TEXT)
                .build()

        then:
        new String(body.bytes, StandardCharsets.UTF_8).contains(SAMPLE_TEXT)
    }

    void 'builder can override charset and falls back to UTF-8 when null is passed'() {
        when: 'a non UTF-8 charset is used'
        def isoBody = MultipartBody.builder()
                .withCharset(StandardCharsets.ISO_8859_1)
                .addPart('message', SAMPLE_TEXT)
                .build()

        and: 'null charset explicitly falls back to UTF-8'
        def utfBody = MultipartBody.builder()
                .withCharset(null)
                .addPart('message', SAMPLE_TEXT)
                .build()

        then:
        new String(isoBody.bytes, StandardCharsets.ISO_8859_1).contains(SAMPLE_TEXT)
        new String(utfBody.bytes, StandardCharsets.UTF_8).contains(SAMPLE_TEXT)
    }

    void 'string file parts honor charset set after addPart'() {
        when:
        def body = MultipartBody.builder()
                .addPart('upload', 'a.txt', 'text/plain', SAMPLE_TEXT)
                .withCharset(StandardCharsets.ISO_8859_1)
                .build()

        then:
        new String(body.bytes, StandardCharsets.ISO_8859_1).contains(SAMPLE_TEXT)
    }
}
