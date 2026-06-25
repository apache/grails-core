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

package functionaltests.codecs

import grails.converters.JSON
import grails.core.GrailsApplication

/**
 * Controller for testing codec functionality in an integration context.
 * Tests various encoding/decoding methods available in Grails.
 */
class CodecTestController {

    GrailsApplication grailsApplication

    /**
     * Test HTML encoding to prevent XSS attacks.
     */
    def encodeHtml() {
        def input = params.input ?: '<script>alert("XSS")</script>'
        def encoded = input.encodeAsHTML()
        render([
            input: input,
            encoded: encoded,
            decodedBack: encoded.decodeHTML()
        ] as JSON)
    }

    /**
     * Test URL encoding for safe URL parameters.
     */
    def encodeUrl() {
        def input = params.input ?: 'hello world&foo=bar'
        def encoded = input.encodeAsURL()
        render([
            input: input,
            encoded: encoded,
            decodedBack: encoded.decodeURL()
        ] as JSON)
    }

    /**
     * Test Base64 encoding/decoding.
     */
    def encodeBase64() {
        def input = params.input ?: 'Hello, World!'
        def encoded = input.encodeAsBase64()
        def decoded = encoded.decodeBase64()
        render([
            input: input,
            encoded: encoded,
            decodedBack: new String(decoded)
        ] as JSON)
    }

    /**
     * Test Base64 encoding with binary data.
     */
    def encodeBase64Binary() {
        byte[] data = [0x48, 0x65, 0x6C, 0x6C, 0x6F] as byte[] // "Hello"
        def encoded = data.encodeAsBase64()
        def decoded = encoded.decodeBase64()
        render([
            originalBytes: data.collect { it },
            encoded: encoded,
            decodedBytes: decoded.collect { it }
        ] as JSON)
    }

    /**
     * Test MD5 hashing.
     */
    def encodeMd5() {
        def input = params.input ?: 'password123'
        def hash = input.encodeAsMD5()
        render([
            input: input,
            md5Hash: hash,
            hashLength: hash.length()
        ] as JSON)
    }

    /**
     * Test MD5 bytes hashing.
     */
    def encodeMd5Bytes() {
        def input = params.input ?: 'password123'
        def hashBytes = input.encodeAsMD5Bytes()
        render([
            input: input,
            md5Bytes: hashBytes.collect { it & 0xFF }, // Convert to unsigned
            bytesLength: hashBytes.length
        ] as JSON)
    }

    /**
     * Test SHA1 hashing.
     */
    def encodeSha1() {
        def input = params.input ?: 'password123'
        def hash = input.encodeAsSHA1()
        render([
            input: input,
            sha1Hash: hash,
            hashLength: hash.length()
        ] as JSON)
    }

    /**
     * Test SHA1 bytes hashing.
     */
    def encodeSha1Bytes() {
        def input = params.input ?: 'password123'
        def hashBytes = input.encodeAsSHA1Bytes()
        render([
            input: input,
            sha1Bytes: hashBytes.collect { it & 0xFF },
            bytesLength: hashBytes.length
        ] as JSON)
    }

    /**
     * Test SHA256 hashing.
     */
    def encodeSha256() {
        def input = params.input ?: 'password123'
        def hash = input.encodeAsSHA256()
        render([
            input: input,
            sha256Hash: hash,
            hashLength: hash.length()
        ] as JSON)
    }

    /**
     * Test SHA256 bytes hashing.
     */
    def encodeSha256Bytes() {
        def input = params.input ?: 'password123'
        def hashBytes = input.encodeAsSHA256Bytes()
        render([
            input: input,
            sha256Bytes: hashBytes.collect { it & 0xFF },
            bytesLength: hashBytes.length
        ] as JSON)
    }

    /**
     * Test Hex encoding/decoding.
     */
    def encodeHex() {
        def input = params.input ?: 'Hello'
        def encoded = input.bytes.encodeAsHex()
        def decoded = encoded.decodeHex()
        render([
            input: input,
            hexEncoded: encoded,
            decodedBack: new String(decoded)
        ] as JSON)
    }

    /**
     * Test JavaScript encoding for safe inclusion in JS strings.
     */
    def encodeJavaScript() {
        def input = params.input ?: "alert('hello');\nvar x = \"test\";"
        def encoded = input.encodeAsJavaScript()
        render([
            input: input,
            encoded: encoded
        ] as JSON)
    }

    /**
     * Test raw output (no encoding).
     */
    def encodeRaw() {
        def input = params.input ?: '<b>Bold</b>'
        def raw = input.encodeAsRaw()
        render([
            input: input,
            raw: raw.toString(),
            rawClass: raw.getClass().name
        ] as JSON)
    }

    /**
     * Test multiple encodings combined (HTML then Base64).
     */
    def multipleEncodings() {
        def input = params.input ?: '<script>alert(1)</script>'
        def htmlEncoded = input.encodeAsHTML()
        def base64Encoded = htmlEncoded.encodeAsBase64()
        def base64Decoded = base64Encoded.decodeBase64()
        def htmlDecoded = new String(base64Decoded).decodeHTML()
        render([
            input: input,
            htmlEncoded: htmlEncoded,
            base64Encoded: base64Encoded,
            fullyDecoded: htmlDecoded
        ] as JSON)
    }

    /**
     * Test encoding with special characters.
     */
    def encodeSpecialChars() {
        def input = params.input ?: '日本語 & émoji 👍 <tag>'
        render([
            input: input,
            htmlEncoded: input.encodeAsHTML(),
            urlEncoded: input.encodeAsURL(),
            base64Encoded: input.encodeAsBase64()
        ] as JSON)
    }

    /**
     * Test encoding null values - should not throw errors.
     */
    def encodeNull() {
        String nullString = null
        render([
            nullBase64: nullString?.encodeAsBase64(),
            nullMd5: nullString?.encodeAsMD5(),
            nullHtml: nullString?.encodeAsHTML()
        ] as JSON)
    }

    /**
     * Test encoding empty strings.
     */
    def encodeEmpty() {
        def input = ''
        render([
            input: input,
            base64Encoded: input.encodeAsBase64(),
            md5Hash: input.encodeAsMD5(),
            sha256Hash: input.encodeAsSHA256(),
            htmlEncoded: input.encodeAsHTML()
        ] as JSON)
    }

    /**
     * Test hash consistency - same input should produce same hash.
     */
    def hashConsistency() {
        def input = params.input ?: 'consistent-input'
        def md5_1 = input.encodeAsMD5()
        def md5_2 = input.encodeAsMD5()
        def sha1_1 = input.encodeAsSHA1()
        def sha1_2 = input.encodeAsSHA1()
        def sha256_1 = input.encodeAsSHA256()
        def sha256_2 = input.encodeAsSHA256()
        render([
            input: input,
            md5Consistent: md5_1 == md5_2,
            sha1Consistent: sha1_1 == sha1_2,
            sha256Consistent: sha256_1 == sha256_2,
            md5Hash: md5_1,
            sha1Hash: sha1_1,
            sha256Hash: sha256_1
        ] as JSON)
    }
}
