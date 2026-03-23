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
package functionaltests.fileupload

import spock.lang.Specification

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport
import org.apache.grails.testing.http.client.MultipartBody

/**
 * Integration tests for file upload functionality in Grails.
 * <p>
 * Tests various file upload patterns including single file, multiple files,
 * file validation, and metadata extraction.
 */
@Integration
class FileUploadSpec extends Specification implements HttpClientSupport {

    // ========== Single File Upload Tests ==========

    def "upload single text file returns file info"() {
        given:
        def content = 'Hello, this is a test file content!'
        def body = MultipartBody.builder()
                .addPart('file', 'test.txt', 'text/plain', content)
                .build()

        when:
        def response = httpPostMultipart('/fileUploadTest/uploadSingle', body)

        then:
        response.assertJson(200, [
                contentType: 'text/plain',
                filename: 'test.txt',
                size: content.bytes.length,
                success: true
        ])
    }

    def "upload single file without file returns error"() {
        given:
        def body = MultipartBody.builder()
                .addPart('other', 'value')
                .build()

        when:
        def response = httpPostMultipart('/fileUploadTest/uploadSingle', body)

        then:
        response.assertStatus(400)
    }

    def "upload file with metadata includes description and category"() {
        given:
        def content = 'File with metadata'
        def body = MultipartBody.builder()
                .addPart('file', 'data.txt', 'text/plain', content.bytes)
                .addPart('description', 'My test file')
                .addPart('category', 'documents')
                .build()

        when:
        def response = httpPostMultipart('/fileUploadTest/uploadWithMetadata', body)

        then:
        response.assertJsonContains(200, [
                success: true,
                description: 'My test file',
                category: 'documents'
        ])
    }

    // ========== Multiple File Upload Tests ==========

    def "upload multiple files returns all file info"() {
        given:
        def body = MultipartBody.builder()
                .addPart('files', 'file1.txt', 'text/plain', 'Content 1'.bytes)
                .addPart('files', 'file2.txt', 'text/plain', 'Content 2'.bytes)
                .addPart('files', 'file3.txt', 'text/plain', 'Content 3'.bytes)
                .build()

        when:
        def response = httpPostMultipart('/fileUploadTest/uploadMultiple', body)

        then:
        response.assertStatus(200)
        with(response.json()) {
            success == true
            count == 3
            files.size() == 3
            files*.filename.containsAll(['file1.txt', 'file2.txt', 'file3.txt'])
        }
    }

    // ========== File Content Processing Tests ==========

    def "upload text file returns line and word count"() {
        given:
        def content = '''
            |Line 1
            |Line 2
            |Line 3
            |This is a longer line with more words
        '''.trim().stripMargin()
        def body = MultipartBody.builder()
                .addPart('file', 'multiline.txt', 'text/plain', content.bytes)
                .build()

        when:
        def response = httpPostMultipart('/fileUploadTest/uploadTextFile', body)

        then:
        response.assertStatus(200)
        with(response.json()) {
            success == true
            lineCount == 4
            wordCount > 0
            preview.startsWith('Line 1')
        }
    }

    def "upload and echo returns original content"() {
        given:
        def content = 'Echo this content back to me!'
        def body = MultipartBody.builder()
                .addPart('file', 'echo.txt', 'text/plain', content.bytes)
                .build()

        when:
        def response = httpPostMultipart('/fileUploadTest/uploadAndEcho', body)

        then:
        response.assertJsonContains(200, [
                success: true,
                content: content
        ])
    }

    // ========== File Validation Tests ==========

    def "upload file with allowed type passes validation"() {
        given:
        def body = MultipartBody.builder()
                .addPart('file', 'valid.txt', 'text/plain', 'Valid content'.bytes)
                .build()

        when:
        def response = httpPostMultipart('/fileUploadTest/uploadWithValidation', body)

        then:
        response.assertJsonContains(200, [
                success: true,
                validated: true
        ])
    }

    def "upload file with valid extension passes validation"() {
        given:
        def body = MultipartBody.builder()
                .addPart('file', 'data.json', 'application/json', '{"key":"value"}'.bytes)
                .build()

        when:
        def response = httpPostMultipart('/fileUploadTest/uploadWithExtensionValidation', body)

        then:
        response.assertJsonContains(200, [
                success: true,
                validated: true,
                extension: 'json'
        ])
    }

    def "upload file with csv extension passes validation"() {
        given:
        def csvContent = 'name,age,city\nJohn,30,NYC\nJane,25,LA'
        def body = MultipartBody.builder()
                .addPart('file', 'data.csv', 'text/csv', csvContent.bytes)
                .build()

        when:
        def response = httpPostMultipart('/fileUploadTest/uploadWithExtensionValidation', body)

        then:
        response.assertJsonContains(200, [
                    success: true,
                    extension :'csv'
        ])
    }

    // ========== File Info Extraction Tests ==========

    def "get file info extracts all metadata"() {
        given:
        def content = 'Some content here'
        def body = MultipartBody.builder()
                .addPart('file', 'document.txt', 'text/plain', content.bytes)
                .build()

        when:
        def response = httpPostMultipart('/fileUploadTest/getFileInfo', body)

        then:
        response.assertJsonContains(200, [
                originalFilename: 'document.txt',
                basename: 'document',
                extension: 'txt',
                size: content.bytes.length,
                isEmpty: false
        ])
    }

    def "get file info handles filename without extension"() {
        given:
        def body = MultipartBody.builder()
                .addPart('file', 'README', 'text/plain', 'Readme content'.bytes)
                .build()

        when:
        def response = httpPostMultipart('/fileUploadTest/getFileInfo', body)

        then:
        response.assertJsonContains(200, [
                originalFilename: 'README',
                basename: 'README',
                extension: ''
        ])
    }

    // ========== Params Access Tests ==========

    def "upload via params accesses file correctly"() {
        given:
        def body = MultipartBody.builder()
                .addPart('file', 'params-test.txt', 'text/plain', 'Accessed via params'.bytes)
                .build()

        when:
        def response = httpPostMultipart('/fileUploadTest/uploadViaParams', body)

        then:
        response.assertJsonContains(200, [
                success: true,
                accessedViaParams: true,
                filename: 'params-test.txt'
        ])
    }

    // ========== Large File Tests ==========

    def "upload larger text file succeeds"() {
        given:
        def content = ('X' * 1000) // 1KB of X characters
        def body = MultipartBody.builder()
                .addPart('file', 'large.txt', 'text/plain', content.bytes)
                .build()

        when:
        def response = httpPostMultipart('/fileUploadTest/uploadSingle', body)

        then:
        response.assertJsonContains(200, [
                success: true,
                size: 1000
        ])
    }

    def "upload json file with content"() {
        given:
        def jsonContent = '{"users":[{"name":"Alice","age":30},{"name":"Bob","age":25}]}'
        def body = MultipartBody.builder()
                .addPart('file', 'users.json', 'application/json', jsonContent.bytes)
                .build()

        when:
        def response = httpPostMultipart('/fileUploadTest/uploadAndEcho', body)

        then:
        response.assertJson(200, [
                success: true,
                filename: 'users.json',
                content: jsonContent
        ])
    }

    def "upload xml file with content"() {
        given:
        def xmlContent = '<?xml version="1.0"?><root><item id="1">Test</item></root>'
        def body = MultipartBody.builder()
                .addPart('file', 'data.xml', 'application/xml', xmlContent.bytes)
                .build()

        when:
        def response = httpPostMultipart('/fileUploadTest/uploadAndEcho', body)

        then:
        response.assertJson(200, [
                success: true,
                filename: 'data.xml',
                content: xmlContent
        ])
    }
}
