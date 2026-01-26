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

import functionaltests.Application
import grails.testing.mixin.integration.Integration
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import spock.lang.Specification

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
@Integration(applicationClass = Application)
class SecurityCodecsSpec extends Specification {

    private HttpClient createClient() {
        HttpClient.create(new URL("http://localhost:$serverPort"))
    }

    // ========== HTML Encoding Tests (XSS Prevention) ==========

    def "test HTML encoding escapes dangerous tags"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/codecTest/encodeHtml?input=%3Cscript%3Ealert(%22XSS%22)%3C/script%3E'),
            Map
        )

        then: "script tags should be HTML encoded"
        response.status.code == 200
        response.body().input == '<script>alert("XSS")</script>'
        response.body().encoded.contains('&lt;script&gt;')
        response.body().encoded.contains('&lt;/script&gt;')
        !response.body().encoded.contains('<script>')
        response.body().decodedBack == '<script>alert("XSS")</script>'

        cleanup:
        client.close()
    }

    def "test HTML encoding escapes quotes"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/codecTest/encodeHtml?input=%22quoted%22'),
            Map
        )

        then: "quotes should be HTML encoded"
        response.status.code == 200
        response.body().encoded.contains('&quot;')
        response.body().decodedBack == '"quoted"'

        cleanup:
        client.close()
    }

    def "test HTML encoding escapes ampersands"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/codecTest/encodeHtml?input=foo%26bar'),
            Map
        )

        then: "ampersands should be HTML encoded"
        response.status.code == 200
        response.body().input == 'foo&bar'
        response.body().encoded.contains('&amp;')
        response.body().decodedBack == 'foo&bar'

        cleanup:
        client.close()
    }

    // ========== URL Encoding Tests ==========

    def "test URL encoding escapes spaces and special chars"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/codecTest/encodeUrl?input=hello+world%26foo%3Dbar'),
            Map
        )

        then: "spaces and special chars should be URL encoded"
        response.status.code == 200
        response.body().input == 'hello world&foo=bar'
        response.body().encoded.contains('+') || response.body().encoded.contains('%20')
        response.body().encoded.contains('%26')
        response.body().encoded.contains('%3D')
        response.body().decodedBack == 'hello world&foo=bar'

        cleanup:
        client.close()
    }

    // ========== Base64 Encoding Tests ==========

    def "test Base64 encoding and decoding text"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/codecTest/encodeBase64?input=Hello%2C+World!'),
            Map
        )

        then: "text should be Base64 encoded and decodable"
        response.status.code == 200
        response.body().input == 'Hello, World!'
        response.body().encoded == 'SGVsbG8sIFdvcmxkIQ=='
        response.body().decodedBack == 'Hello, World!'

        cleanup:
        client.close()
    }

    def "test Base64 encoding with binary data"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/codecTest/encodeBase64Binary'),
            Map
        )

        then: "binary data should be correctly Base64 encoded"
        response.status.code == 200
        response.body().originalBytes == [72, 101, 108, 108, 111] // "Hello" in ASCII
        response.body().encoded == 'SGVsbG8='
        response.body().decodedBytes == [72, 101, 108, 108, 111]

        cleanup:
        client.close()
    }

    // ========== MD5 Hash Tests ==========

    def "test MD5 hashing produces consistent 32-char hex string"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/codecTest/encodeMd5?input=password123'),
            Map
        )

        then: "MD5 hash should be 32 characters (hex)"
        response.status.code == 200
        response.body().input == 'password123'
        response.body().hashLength == 32
        response.body().md5Hash ==~ /^[a-f0-9]{32}$/

        cleanup:
        client.close()
    }

    def "test MD5 bytes produces 16 bytes"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/codecTest/encodeMd5Bytes?input=password123'),
            Map
        )

        then: "MD5 bytes should be 16 bytes"
        response.status.code == 200
        response.body().bytesLength == 16
        response.body().md5Bytes.size() == 16

        cleanup:
        client.close()
    }

    // ========== SHA1 Hash Tests ==========

    def "test SHA1 hashing produces consistent 40-char hex string"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/codecTest/encodeSha1?input=password123'),
            Map
        )

        then: "SHA1 hash should be 40 characters (hex)"
        response.status.code == 200
        response.body().hashLength == 40
        response.body().sha1Hash ==~ /^[a-f0-9]{40}$/

        cleanup:
        client.close()
    }

    def "test SHA1 bytes produces 20 bytes"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/codecTest/encodeSha1Bytes?input=password123'),
            Map
        )

        then: "SHA1 bytes should be 20 bytes"
        response.status.code == 200
        response.body().bytesLength == 20

        cleanup:
        client.close()
    }

    // ========== SHA256 Hash Tests ==========

    def "test SHA256 hashing produces consistent 64-char hex string"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/codecTest/encodeSha256?input=password123'),
            Map
        )

        then: "SHA256 hash should be 64 characters (hex)"
        response.status.code == 200
        response.body().hashLength == 64
        response.body().sha256Hash ==~ /^[a-f0-9]{64}$/

        cleanup:
        client.close()
    }

    def "test SHA256 bytes produces 32 bytes"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/codecTest/encodeSha256Bytes?input=password123'),
            Map
        )

        then: "SHA256 bytes should be 32 bytes"
        response.status.code == 200
        response.body().bytesLength == 32

        cleanup:
        client.close()
    }

    // ========== Hex Encoding Tests ==========

    def "test Hex encoding and decoding"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/codecTest/encodeHex?input=Hello'),
            Map
        )

        then: "text should be Hex encoded and decodable"
        response.status.code == 200
        response.body().input == 'Hello'
        response.body().hexEncoded == '48656c6c6f' // "Hello" in hex
        response.body().decodedBack == 'Hello'

        cleanup:
        client.close()
    }

    // ========== JavaScript Encoding Tests ==========

    def "test JavaScript encoding escapes quotes and newlines"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET("/codecTest/encodeJavaScript"),
            Map
        )

        then: "JavaScript special chars should be escaped"
        response.status.code == 200
        response.body().input.contains("'")
        response.body().input.contains('"')
        response.body().input.contains('\n')
        // The encoded output should escape these characters
        response.body().encoded.contains("\\'") || response.body().encoded.contains("\\u0027")

        cleanup:
        client.close()
    }

    // ========== Raw Output Tests ==========

    def "test Raw encoding preserves content without escaping"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/codecTest/encodeRaw'),
            Map
        )

        then: "raw content should be preserved"
        response.status.code == 200
        response.body().input == '<b>Bold</b>'
        response.body().raw == '<b>Bold</b>'

        cleanup:
        client.close()
    }

    // ========== Multiple Encodings Tests ==========

    def "test chaining multiple encodings"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/codecTest/multipleEncodings'),
            Map
        )

        then: "multiple encodings should be reversible"
        response.status.code == 200
        response.body().input == '<script>alert(1)</script>'
        response.body().htmlEncoded.contains('&lt;')
        response.body().fullyDecoded == '<script>alert(1)</script>'

        cleanup:
        client.close()
    }

    // ========== Special Characters Tests ==========

    def "test encoding with Unicode and special characters"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/codecTest/encodeSpecialChars?input=%E6%97%A5%E6%9C%AC%E8%AA%9E+%26+%C3%A9moji+%F0%9F%91%8D+%3Ctag%3E'),
            Map
        )

        then: "special characters should be properly encoded"
        response.status.code == 200
        response.body().input.contains('日本語')
        response.body().htmlEncoded.contains('&lt;tag&gt;')
        response.body().urlEncoded != null
        response.body().base64Encoded != null

        cleanup:
        client.close()
    }

    // ========== Null Handling Tests ==========

    def "test encoding null values returns null safely"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/codecTest/encodeNull'),
            Map
        )

        then: "null values should be handled gracefully"
        response.status.code == 200
        response.body().nullBase64 == null
        response.body().nullMd5 == null
        response.body().nullHtml == null

        cleanup:
        client.close()
    }

    // ========== Empty String Tests ==========

    def "test encoding empty strings"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/codecTest/encodeEmpty'),
            Map
        )

        then: "empty strings should be encoded without errors"
        response.status.code == 200
        response.body().input == ''
        // Empty string Base64 is empty
        response.body().base64Encoded == ''
        // Empty string still has a hash
        response.body().md5Hash != null
        response.body().md5Hash.length() == 32
        response.body().sha256Hash != null
        response.body().sha256Hash.length() == 64

        cleanup:
        client.close()
    }

    // ========== Hash Consistency Tests ==========

    def "test hash functions produce consistent results"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/codecTest/hashConsistency?input=test-consistency'),
            Map
        )

        then: "same input should always produce same hash"
        response.status.code == 200
        response.body().md5Consistent == true
        response.body().sha1Consistent == true
        response.body().sha256Consistent == true

        cleanup:
        client.close()
    }

    def "test different inputs produce different hashes"() {
        given:
        def client = createClient()

        when:
        def response1 = client.toBlocking().exchange(
            HttpRequest.GET('/codecTest/hashConsistency?input=input1'),
            Map
        )
        def response2 = client.toBlocking().exchange(
            HttpRequest.GET('/codecTest/hashConsistency?input=input2'),
            Map
        )

        then: "different inputs should produce different hashes"
        response1.status.code == 200
        response2.status.code == 200
        response1.body().md5Hash != response2.body().md5Hash
        response1.body().sha1Hash != response2.body().sha1Hash
        response1.body().sha256Hash != response2.body().sha256Hash

        cleanup:
        client.close()
    }

    // ========== Known Hash Values Tests ==========

    def "test MD5 produces known hash for 'hello'"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/codecTest/encodeMd5?input=hello'),
            Map
        )

        then: "MD5 of 'hello' should match known value"
        response.status.code == 200
        response.body().md5Hash == '5d41402abc4b2a76b9719d911017c592'

        cleanup:
        client.close()
    }

    def "test SHA1 produces known hash for 'hello'"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/codecTest/encodeSha1?input=hello'),
            Map
        )

        then: "SHA1 of 'hello' should match known value"
        response.status.code == 200
        response.body().sha1Hash == 'aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d'

        cleanup:
        client.close()
    }

    def "test SHA256 produces known hash for 'hello'"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/codecTest/encodeSha256?input=hello'),
            Map
        )

        then: "SHA256 of 'hello' should match known value"
        response.status.code == 200
        response.body().sha256Hash == '2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824'

        cleanup:
        client.close()
    }
}
