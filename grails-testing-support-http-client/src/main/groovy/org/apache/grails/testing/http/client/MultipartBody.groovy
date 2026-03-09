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
package org.apache.grails.testing.http.client

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

import groovy.transform.CompileStatic

/**
 * Represents a pre-encoded {@code multipart/form-data} request body.
 * <p>
 * Use {@code builder()} to create instances.
 */
@CompileStatic
class MultipartBody {

    /** Multipart boundary token used in body delimiters and the content type header. */
    final String boundary

    /** Full encoded multipart payload bytes. */
    final byte[] bytes

    /**
     * @param boundary boundary token for multipart sections
     * @param bytes encoded multipart payload
     */
    MultipartBody(String boundary, byte[] bytes) {
        this.boundary = boundary
        this.bytes = bytes
    }

    /**
     * @return content type header value for this multipart payload
     */
    String getContentType() {
        "multipart/form-data; boundary=$boundary"
    }

    /**
     * @return a new multipart body builder
     */
    static MultipartBuilder builder() {
        new MultipartBuilder()
    }

    /**
     * Builder used to assemble multipart text and file parts.
     */
    @CompileStatic
    static class MultipartBuilder {

        private final String boundary = "----jdk-http-${UUID.randomUUID()}"
        private final List<Part> parts = []
        private Charset charset = StandardCharsets.UTF_8

        /**
         * Adds a text form field.
         *
         * @param name form field name
         * @param value field value
         * @return this builder
         */
        MultipartBuilder addPart(String name, String value) {
            parts.add(Part.text(name, value))
            this
        }

        /**
         * Adds a file form field.
         *
         * @param name form field name
         * @param filename uploaded filename
         * @param contentType mime type for the file
         * @param bytes file payload bytes
         * @return this builder
         */
        MultipartBuilder addPart(String name, String filename, String contentType, byte[] bytes) {
            parts.add(Part.file(name, filename, contentType, bytes))
            this
        }

        /**
         * Sets the charset used when encoding multipart headers and text values.
         * <p>
         * Defaults to UTF-8.
         *
         * @param charset charset to use; if null, UTF-8 is used
         * @return this builder
         */
        MultipartBuilder withCharset(Charset charset) {
            this.charset = charset ?: StandardCharsets.UTF_8
            this
        }

        /**
         * Adds a file form field using the configured charset to convert text content.
         *
         * @param name form field name
         * @param filename uploaded filename
         * @param contentType mime type for the file
         * @param content file content as text
         * @return this builder
         */
        MultipartBuilder addPart(String name, String filename, String contentType, String content) {
            parts.add(Part.fileText(name, filename, contentType, content))
            this
        }

        /**
         * Encodes all configured parts into a single multipart payload.
         *
         * @return immutable multipart body with boundary and bytes
         */
        MultipartBody build() {
            def nl = '\r\n'
            def out = new ByteArrayOutputStream()

            parts.each { part ->
                out.write(("--$boundary$nl".toString()).getBytes(charset))

                if (part.isFile) {
                    out.write(("Content-Disposition: form-data; name=\"${part.name}\"; filename=\"${part.filename}\"$nl".toString())
                            .getBytes(charset))
                    out.write(("Content-Type: ${part.contentType ?: 'application/octet-stream'}$nl$nl".toString())
                            .getBytes(charset))
                    def payload = part.bytes != null ? part.bytes : (part.value ?: '').getBytes(charset)
                    out.write(payload)
                    out.write(nl.getBytes(charset))
                } else {
                    out.write(("Content-Disposition: form-data; name=\"${part.name}\"$nl$nl".toString())
                            .getBytes(charset))
                    out.write(((part.value ?: '') + nl).getBytes(charset))
                }
            }

            out.write(("--$boundary--$nl".toString()).getBytes(charset))

            new MultipartBody(boundary, out.toByteArray())
        }

        @CompileStatic
        private static class Part {
            final String name
            final boolean isFile
            final String value
            final String filename
            final String contentType
            final byte[] bytes

            private Part(String name, boolean isFile, String value, String filename, String contentType, byte[] bytes) {
                this.name = name
                this.isFile = isFile
                this.value = value
                this.filename = filename
                this.contentType = contentType
                this.bytes = bytes
            }

            static Part text(String name, String value) {
                new Part(name, false, value, null, null, null)
            }

            static Part file(String name, String filename, String contentType, byte[] bytes) {
                new Part(name, true, null, filename, contentType, bytes)
            }

            static Part fileText(String name, String filename, String contentType, String content) {
                new Part(name, true, content, filename, contentType, null)
            }
        }
    }
}
