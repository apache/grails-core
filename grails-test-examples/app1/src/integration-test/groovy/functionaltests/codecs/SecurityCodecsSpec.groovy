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

import spock.lang.Specification
import spock.lang.Tag

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

/**
 * Comprehensive integration tests for Grails codec functionality.
 *
 * Tests cover:
 * - HTML encoding/decoding (XSS prevention)
 * - URL encoding/decoding
 * - Base64 encoding/decoding
 * - MD5, SHA1, SHA256 hashing
 * - Hex encoding/decoding
 * - JavaScript encoding
 * - Raw output handling
 * - Edge cases (null, empty, special characters)
 * - Hash consistency verification
 */
@Integration
@Tag('http-client')
class SecurityCodecsSpec extends Specification implements HttpClientSupport {

    // ========== HTML Encoding Tests (XSS Prevention) ==========

    def "test HTML encoding escapes dangerous tags"() {
        when:
        def response = http(
                '/codecTest/encodeHtml?input=%3Cscript%3Ealert(%22XSS%22)%3C/script%3E'
        )

        then: "script tags should be HTML encoded"
        response.assertStatus(200)
        with(response.json()) {
            input == '<script>alert("XSS")</script>'
            encoded.contains('&lt;script&gt;')
            encoded.contains('&lt;/script&gt;')
            !encoded.contains('<script>')
            decodedBack == '<script>alert("XSS")</script>'
        }
    }

    def "test HTML encoding escapes quotes"() {
        when:
        def response = http('/codecTest/encodeHtml?input=%22quoted%22')

        then: "quotes should be HTML encoded"
        response.assertStatus(200)
        with(response.json()) {
            encoded.contains('&quot;')
            decodedBack == '"quoted"'
        }
    }

    def "test HTML encoding escapes ampersands"() {
        when:
        def response = http('/codecTest/encodeHtml?input=foo%26bar')

        then: "ampersands should be HTML encoded"
        response.assertStatus(200)
        with(response.json()) {
            input == 'foo&bar'
            encoded.contains('&amp;')
            decodedBack == 'foo&bar'
        }
    }

    // ========== URL Encoding Tests ==========

    def "test URL encoding escapes spaces and special chars"() {
        when:
        def response = http('/codecTest/encodeUrl?input=hello+world%26foo%3Dbar')

        then: "spaces and special chars should be URL encoded"
        response.assertStatus(200)
        with(response.json()) {
            input == 'hello world&foo=bar'
            encoded.contains('+') || json.encoded.contains('%20')
            encoded.contains('%26')
            encoded.contains('%3D')
            decodedBack == 'hello world&foo=bar'
        }
    }

    // ========== Base64 Encoding Tests ==========

    def "test Base64 encoding and decoding text"() {
        when:
        def response = http('/codecTest/encodeBase64?input=Hello%2C+World!')

        then: "text should be Base64 encoded and decodable"
        response.assertJson(200, [
                input      : 'Hello, World!',
                encoded    : 'SGVsbG8sIFdvcmxkIQ==',
                decodedBack: 'Hello, World!'
        ])
    }

    def "test Base64 encoding with binary data"() {
        when:
        def response = http('/codecTest/encodeBase64Binary')

        then: "binary data should be correctly Base64 encoded"
        response.assertJson(200, [
                originalBytes: [72, 101, 108, 108, 111], // "Hello" in ASCII
                encoded      : 'SGVsbG8=',
                decodedBytes : [72, 101, 108, 108, 111]
        ])
    }

    // ========== MD5 Hash Tests ==========

    def "test MD5 hashing produces consistent 32-char hex string"() {
        when:
        def response = http('/codecTest/encodeMd5?input=password123')

        then: "MD5 hash should be 32 characters (hex)"
        response.assertStatus(200)
        with(response.json()) {
            input == 'password123'
            hashLength == 32
            md5Hash ==~ /^[a-f0-9]{32}$/
        }
    }

    def "test MD5 bytes produces 16 bytes"() {
        when:
        def response = http('/codecTest/encodeMd5Bytes?input=password123')

        then: "MD5 bytes should be 16 bytes"
        response.assertStatus(200)
        with(response.json()) {
            bytesLength == 16
            md5Bytes.size() == 16
        }
    }

    // ========== SHA1 Hash Tests ==========

    def "test SHA1 hashing produces consistent 40-char hex string"() {
        when:
        def response = http('/codecTest/encodeSha1?input=password123')

        then: "SHA1 hash should be 40 characters (hex)"
        response.assertStatus(200)
        with(response.json()) {
            hashLength == 40
            sha1Hash ==~ /^[a-f0-9]{40}$/
        }
    }

    def "test SHA1 bytes produces 20 bytes"() {
        when:
        def response = http('/codecTest/encodeSha1Bytes?input=password123')

        then: "SHA1 bytes should be 20 bytes"
        response.assertJsonContains(200, [bytesLength: 20])
    }

    // ========== SHA256 Hash Tests ==========

    def "test SHA256 hashing produces consistent 64-char hex string"() {
        when:
        def response = http('/codecTest/encodeSha256?input=password123')

        then: "SHA256 hash should be 64 characters (hex)"
        response.assertStatus(200)
        with(response.json()) {
            hashLength == 64
            sha256Hash ==~ /^[a-f0-9]{64}$/
        }
    }

    def "test SHA256 bytes produces 32 bytes"() {
        when:
        def response = http('/codecTest/encodeSha256Bytes?input=password123')

        then: "SHA256 bytes should be 32 bytes"
        response.assertJsonContains(200, [bytesLength: 32])
    }

    // ========== Hex Encoding Tests ==========

    def "test Hex encoding and decoding"() {
        when:
        def response = http('/codecTest/encodeHex?input=Hello')

        then: "text should be Hex encoded and decodable"
        response.assertJson(200, [
                input      : 'Hello',
                hexEncoded : '48656c6c6f', // "Hello" in hex
                decodedBack: 'Hello'
        ])
    }

    // ========== JavaScript Encoding Tests ==========

    def "test JavaScript encoding escapes quotes and newlines"() {
        when:
        def response = http('/codecTest/encodeJavaScript')

        then: "JavaScript special chars should be escaped"
        response.assertStatus(200)
        with(response.json()) {
            input.contains("'")
            input.contains('"')
            input.contains('\n')
            // The encoded output should escape these characters
            encoded.contains("\\'") || encoded.contains("\\u0027")
        }
    }

    // ========== Raw Output Tests ==========

    def "test Raw encoding preserves content without escaping"() {
        when:
        def response = http('/codecTest/encodeRaw')

        then: "raw content should be preserved"
        response.assertJson(200, [
                input   : '<b>Bold</b>',
                raw     : '<b>Bold</b>',
                rawClass: 'java.lang.String'
        ])
    }

    // ========== Multiple Encodings Tests ==========

    def "test chaining multiple encodings"() {
        when:
        def response = http('/codecTest/multipleEncodings')

        then: "multiple encodings should be reversible"
        response.assertStatus(200)
        with(response.json()) {
            input == '<script>alert(1)</script>'
            htmlEncoded.contains('&lt;')
            fullyDecoded == '<script>alert(1)</script>'
        }
    }

    // ========== Special Characters Tests ==========

    def "test encoding with Unicode and special characters"() {
        when:
        def response = http(
                '/codecTest/encodeSpecialChars?input=%E6%97%A5%E6%9C%AC%E8%AA%9E+%26+%C3%A9moji+%F0%9F%91%8D+%3Ctag%3E'
        )

        then: "special characters should be properly encoded"
        response.assertStatus(200)
        with(response.json()) {
            input.contains('日本語')
            htmlEncoded.contains('&lt;tag&gt;')
            urlEncoded != null
            base64Encoded != null
        }
    }

    // ========== Null Handling Tests ==========

    def "test encoding null values returns null safely"() {
        when:
        def response = http('/codecTest/encodeNull')

        then: "null values should be handled gracefully"
        response.assertStatus(200)
        with(response.json()) {
            nullBase64 == null
            nullMd5 == null
            nullHtml == null
        }
    }

    // ========== Empty String Tests ==========

    def "test encoding empty strings"() {
        when:
        def response = http('/codecTest/encodeEmpty')

        then: "empty strings should be encoded without errors"
        response.assertStatus(200)
        with(response.json()) {
            input == ''
            // Empty string Base64 is empty
            base64Encoded == ''
            // Empty string still has a hash
            md5Hash != null
            md5Hash.length() == 32
            sha256Hash != null
            sha256Hash.length() == 64
        }
    }

    // ========== Hash Consistency Tests ==========

    def "test hash functions produce consistent results"() {
        when:
        def response = http('/codecTest/hashConsistency?input=test-consistency')

        then: "same input should always produce same hash"
        response.assertJsonContains(200, [
                md5Consistent   : true,
                sha1Consistent  : true,
                sha256Consistent: true
        ])
    }

    def "test different inputs produce different hashes"() {
        when:
        def response1 = http('/codecTest/hashConsistency?input=input1')
        def response2 = http('/codecTest/hashConsistency?input=input2')

        then: "different inputs should produce different hashes"
        response1.assertStatus(200)
        response2.assertStatus(200)
        def json1 = response1.json()
        def json2 = response2.json()
        json1.md5Hash != json2.md5Hash
        json1.sha1Hash != json2.sha1Hash
        json1.sha256Hash != json2.sha256Hash
    }

    // ========== Known Hash Values Tests ==========

    def "test MD5 produces known hash for 'hello'"() {
        when:
        def response = http('/codecTest/encodeMd5?input=hello')

        then: "MD5 of 'hello' should match known value"
        response.assertJsonContains(200, [
                md5Hash: '5d41402abc4b2a76b9719d911017c592'
        ])
    }

    def "test SHA1 produces known hash for 'hello'"() {
        when:
        def response = http('/codecTest/encodeSha1?input=hello')

        then: "SHA1 of 'hello' should match known value"
        response.assertJsonContains(200, [
                sha1Hash: 'aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d'
        ])
    }

    def "test SHA256 produces known hash for 'hello'"() {
        when:
        def response = http('/codecTest/encodeSha256?input=hello')

        then: "SHA256 of 'hello' should match known value"
        response.assertJsonContains(200, [
                sha256Hash: '2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824'
        ])
    }
}
