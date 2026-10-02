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

import groovy.transform.CompileStatic

import org.springframework.http.MediaType

import grails.web.mime.MimeType

/**
 * Internal bridge between the Grails format-aware MIME type and Spring's HTTP media type.
 */
@CompileStatic
final class SpringMediaTypeAdapter {

    private SpringMediaTypeAdapter() {
    }

    static MediaType toMediaType(MimeType mimeType) {
        if (mimeType == null) {
            return null
        }

        MediaType parsed = MediaType.parseMediaType(mimeType.name)
        Map<String, String> parameters = new LinkedHashMap<>(parsed.parameters)
        Map rawParameters = (Map) mimeType.parameters
        rawParameters.each { Object name, Object value ->
            if (value != null) {
                parameters.put(name.toString(), value.toString())
            }
        }
        return new MediaType(parsed.type, parsed.subtype, parameters)
    }

    static MimeType toMimeType(MediaType mediaType, String extension = null) {
        if (mediaType == null) {
            return null
        }

        return new MimeType(
                "${mediaType.type}/${mediaType.subtype}".toString(),
                extension,
                new LinkedHashMap<String, String>(mediaType.parameters)
        )
    }
}
