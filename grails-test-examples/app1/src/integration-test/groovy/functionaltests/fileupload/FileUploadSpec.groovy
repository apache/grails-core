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

import groovy.json.JsonSlurper

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.multipart.MultipartBody
import spock.lang.Specification

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.httpclient.HttpClientSupport

/**
 * Integration tests for file upload functionality in Grails.
 * Tests various file upload patterns including single file, multiple files,
 * file validation, and metadata extraction.
 */
@Rollback
@Integration
class FileUploadSpec extends Specification implements HttpClientSupport {

    // ========== Single File Upload Tests ==========

    def "upload single text file returns file info"() {
        given:
        def content = 'Hello, this is a test file content!'
        def body = MultipartBody.builder()
            .addPart('file', 'test.txt', MediaType.TEXT_PLAIN_TYPE, content.bytes)
            .build()

        when:
        def response = httpClient.exchange(
            HttpRequest.POST('/fileUploadTest/uploadSingle', body)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.success == true
        json.filename == 'test.txt'
        json.size == content.bytes.length
    }

    def "upload single file without file returns error"() {
        given:
        def body = MultipartBody.builder()
            .addPart('other', 'value')
            .build()

        when:
        httpClient.exchange(
            HttpRequest.POST('/fileUploadTest/uploadSingle', body)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            String
        )

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.BAD_REQUEST
    }

    def "upload file with metadata includes description and category"() {
        given:
        def content = 'File with metadata'
        def body = MultipartBody.builder()
            .addPart('file', 'data.txt', MediaType.TEXT_PLAIN_TYPE, content.bytes)
            .addPart('description', 'My test file')
            .addPart('category', 'documents')
            .build()

        when:
        def response = httpClient.exchange(
            HttpRequest.POST('/fileUploadTest/uploadWithMetadata', body)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.success == true
        json.description == 'My test file'
        json.category == 'documents'
    }

    // ========== Multiple File Upload Tests ==========

    def "upload multiple files returns all file info"() {
        given:
        def body = MultipartBody.builder()
            .addPart('files', 'file1.txt', MediaType.TEXT_PLAIN_TYPE, 'Content 1'.bytes)
            .addPart('files', 'file2.txt', MediaType.TEXT_PLAIN_TYPE, 'Content 2'.bytes)
            .addPart('files', 'file3.txt', MediaType.TEXT_PLAIN_TYPE, 'Content 3'.bytes)
            .build()

        when:
        def response = httpClient.exchange(
            HttpRequest.POST('/fileUploadTest/uploadMultiple', body)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.success == true
        json.count == 3
        json.files.size() == 3
        json.files*.filename.containsAll(['file1.txt', 'file2.txt', 'file3.txt'])
    }

    // ========== File Content Processing Tests ==========

    def "upload text file returns line and word count"() {
        given:
        def content = '''Line 1
Line 2
Line 3
This is a longer line with more words'''
        def body = MultipartBody.builder()
            .addPart('file', 'multiline.txt', MediaType.TEXT_PLAIN_TYPE, content.bytes)
            .build()

        when:
        def response = httpClient.exchange(
            HttpRequest.POST('/fileUploadTest/uploadTextFile', body)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.success == true
        json.lineCount == 4
        json.wordCount > 0
        json.preview.startsWith('Line 1')
    }

    def "upload and echo returns original content"() {
        given:
        def content = 'Echo this content back to me!'
        def body = MultipartBody.builder()
            .addPart('file', 'echo.txt', MediaType.TEXT_PLAIN_TYPE, content.bytes)
            .build()

        when:
        def response = httpClient.exchange(
            HttpRequest.POST('/fileUploadTest/uploadAndEcho', body)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.success == true
        json.content == content
    }

    // ========== File Validation Tests ==========

    def "upload file with allowed type passes validation"() {
        given:
        def body = MultipartBody.builder()
            .addPart('file', 'valid.txt', MediaType.TEXT_PLAIN_TYPE, 'Valid content'.bytes)
            .build()

        when:
        def response = httpClient.exchange(
            HttpRequest.POST('/fileUploadTest/uploadWithValidation', body)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.success == true
        json.validated == true
    }

    def "upload file with valid extension passes validation"() {
        given:
        def body = MultipartBody.builder()
            .addPart('file', 'data.json', MediaType.APPLICATION_JSON_TYPE, '{"key":"value"}'.bytes)
            .build()

        when:
        def response = httpClient.exchange(
            HttpRequest.POST('/fileUploadTest/uploadWithExtensionValidation', body)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.success == true
        json.validated == true
        json.extension == 'json'
    }

    def "upload file with csv extension passes validation"() {
        given:
        def csvContent = 'name,age,city\nJohn,30,NYC\nJane,25,LA'
        def body = MultipartBody.builder()
            .addPart('file', 'data.csv', MediaType.of('text/csv'), csvContent.bytes)
            .build()

        when:
        def response = httpClient.exchange(
            HttpRequest.POST('/fileUploadTest/uploadWithExtensionValidation', body)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.success == true
        json.extension == 'csv'
    }

    // ========== File Info Extraction Tests ==========

    def "get file info extracts all metadata"() {
        given:
        def body = MultipartBody.builder()
            .addPart('file', 'document.txt', MediaType.TEXT_PLAIN_TYPE, 'Some content here'.bytes)
            .build()

        when:
        def response = httpClient.exchange(
            HttpRequest.POST('/fileUploadTest/getFileInfo', body)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.originalFilename == 'document.txt'
        json.basename == 'document'
        json.extension == 'txt'
        json.size == 'Some content here'.bytes.length
        json.isEmpty == false
    }

    def "get file info handles filename without extension"() {
        given:
        def body = MultipartBody.builder()
            .addPart('file', 'README', MediaType.TEXT_PLAIN_TYPE, 'Readme content'.bytes)
            .build()

        when:
        def response = httpClient.exchange(
            HttpRequest.POST('/fileUploadTest/getFileInfo', body)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.originalFilename == 'README'
        json.basename == 'README'
        json.extension == ''
    }

    // ========== Params Access Tests ==========

    def "upload via params accesses file correctly"() {
        given:
        def body = MultipartBody.builder()
            .addPart('file', 'params-test.txt', MediaType.TEXT_PLAIN_TYPE, 'Accessed via params'.bytes)
            .build()

        when:
        def response = httpClient.exchange(
            HttpRequest.POST('/fileUploadTest/uploadViaParams', body)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.success == true
        json.accessedViaParams == true
        json.filename == 'params-test.txt'
    }

    // ========== Large File Tests ==========

    def "upload larger text file succeeds"() {
        given:
        def content = ('X' * 1000) // 1KB of X characters
        def body = MultipartBody.builder()
            .addPart('file', 'large.txt', MediaType.TEXT_PLAIN_TYPE, content.bytes)
            .build()

        when:
        def response = httpClient.exchange(
            HttpRequest.POST('/fileUploadTest/uploadSingle', body)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.success == true
        json.size == 1000
    }

    def "upload json file with content"() {
        given:
        def jsonContent = '{"users":[{"name":"Alice","age":30},{"name":"Bob","age":25}]}'
        def body = MultipartBody.builder()
            .addPart('file', 'users.json', MediaType.APPLICATION_JSON_TYPE, jsonContent.bytes)
            .build()

        when:
        def response = httpClient.exchange(
            HttpRequest.POST('/fileUploadTest/uploadAndEcho', body)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.success == true
        json.filename == 'users.json'
        json.content == jsonContent
    }

    def "upload xml file with content"() {
        given:
        def xmlContent = '<?xml version="1.0"?><root><item id="1">Test</item></root>'
        def body = MultipartBody.builder()
            .addPart('file', 'data.xml', MediaType.APPLICATION_XML_TYPE, xmlContent.bytes)
            .build()

        when:
        def response = httpClient.exchange(
            HttpRequest.POST('/fileUploadTest/uploadAndEcho', body)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.success == true
        json.filename == 'data.xml'
        json.content == xmlContent
    }
}
