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
package org.grails.web.mime

import org.springframework.http.MediaType

import grails.web.mime.MimeType
import spock.lang.Specification

class SpringMediaTypeAdapterSpec extends Specification {

    void "converts a Grails mime type to a Spring media type"() {
        given:
        MimeType mimeType = new MimeType(
                'application/vnd.example+json',
                'example',
                [v: '2', profile: 'compact', q: '0.8']
        )

        when:
        MediaType mediaType = SpringMediaTypeAdapter.toMediaType(mimeType)

        then:
        mediaType.type == 'application'
        mediaType.subtype == 'vnd.example+json'
        mediaType.parameters == [q: '0.8', v: '2', profile: 'compact']
    }

    void "converts a Spring media type to a Grails mime type with its format alias"() {
        given:
        MediaType mediaType = MediaType.parseMediaType('application/hal+json;profile=compact;q=0.7')

        when:
        MimeType mimeType = SpringMediaTypeAdapter.toMimeType(mediaType, 'hal')

        then:
        mimeType.name == 'application/hal+json'
        mimeType.extension == 'hal'
        mimeType.parameters == [q: '0.7', profile: 'compact']
    }

    void "preserves media type data across a round trip"() {
        given:
        MimeType original = new MimeType('application/xml', 'xml', [charset: 'UTF-8', q: '0.6'])

        when:
        MimeType converted = SpringMediaTypeAdapter.toMimeType(
                SpringMediaTypeAdapter.toMediaType(original),
                original.extension
        )

        then:
        converted.name == original.name
        converted.extension == original.extension
        converted.parameters == original.parameters
    }

    void "converts numeric parameters produced during accept header negotiation"() {
        given:
        MimeType mimeType = new MimeType('application/json', 'json', [q: 0.8G])

        expect:
        SpringMediaTypeAdapter.toMediaType(mimeType).qualityValue == 0.8d
    }

    void "returns null when there is no type to adapt"() {
        expect:
        SpringMediaTypeAdapter.toMediaType(null) == null
        SpringMediaTypeAdapter.toMimeType(null) == null
    }
}
