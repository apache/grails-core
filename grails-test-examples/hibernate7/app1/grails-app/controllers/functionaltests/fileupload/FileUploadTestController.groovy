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

import grails.converters.JSON
import org.springframework.web.multipart.MultipartFile

/**
 * Controller for testing file upload functionality in Grails.
 * Tests various file upload patterns including single file, multiple files,
 * file validation, and metadata extraction.
 */
class FileUploadTestController {

    static responseFormats = ['json', 'html']

    // ========== Single File Upload ==========

    def uploadSingle() {
        def file = request.getFile('file')
        if (!file || file.empty) {
            response.status = 400
            render([error: 'no_file', message: 'No file uploaded'] as JSON)
            return
        }

        render([
            success: true,
            filename: file.originalFilename,
            size: file.size,
            contentType: file.contentType
        ] as JSON)
    }

    def uploadWithMetadata() {
        def file = request.getFile('file')
        def description = params.description
        def category = params.category

        if (!file || file.empty) {
            response.status = 400
            render([error: 'no_file', message: 'No file uploaded'] as JSON)
            return
        }

        render([
            success: true,
            filename: file.originalFilename,
            size: file.size,
            contentType: file.contentType,
            description: description,
            category: category
        ] as JSON)
    }

    // ========== Multiple File Upload ==========

    def uploadMultiple() {
        def files = request.getFiles('files')
        if (!files || files.every { it.empty }) {
            response.status = 400
            render([error: 'no_files', message: 'No files uploaded'] as JSON)
            return
        }

        def uploadedFiles = files.findAll { !it.empty }.collect { file ->
            [
                filename: file.originalFilename,
                size: file.size,
                contentType: file.contentType
            ]
        }

        render([
            success: true,
            count: uploadedFiles.size(),
            files: uploadedFiles
        ] as JSON)
    }

    // ========== File Content Processing ==========

    def uploadTextFile() {
        def file = request.getFile('file')
        if (!file || file.empty) {
            response.status = 400
            render([error: 'no_file', message: 'No file uploaded'] as JSON)
            return
        }

        def content = new String(file.bytes, 'UTF-8')
        def lineCount = content.count('\n') + 1
        def wordCount = content.split(/\s+/).length

        render([
            success: true,
            filename: file.originalFilename,
            size: file.size,
            lineCount: lineCount,
            wordCount: wordCount,
            preview: content.take(100)
        ] as JSON)
    }

    def uploadAndEcho() {
        def file = request.getFile('file')
        if (!file || file.empty) {
            response.status = 400
            render([error: 'no_file', message: 'No file uploaded'] as JSON)
            return
        }

        def content = new String(file.bytes, 'UTF-8')
        render([
            success: true,
            filename: file.originalFilename,
            content: content
        ] as JSON)
    }

    // ========== File Validation ==========

    def uploadWithValidation() {
        def file = request.getFile('file')
        if (!file || file.empty) {
            response.status = 400
            render([error: 'no_file', message: 'No file uploaded'] as JSON)
            return
        }

        // Size validation (max 10KB for this test)
        def maxSize = 10 * 1024
        if (file.size > maxSize) {
            response.status = 400
            render([error: 'file_too_large', message: "File exceeds max size of ${maxSize} bytes", actualSize: file.size] as JSON)
            return
        }

        // Type validation - normalize content type (remove charset if present)
        def contentType = file.contentType?.split(';')?.first()?.trim()
        def allowedTypes = ['text/plain', 'application/json', 'text/csv']
        if (!allowedTypes.contains(contentType)) {
            response.status = 400
            render([error: 'invalid_type', message: "File type ${file.contentType} not allowed", allowedTypes: allowedTypes] as JSON)
            return
        }

        render([
            success: true,
            validated: true,
            filename: file.originalFilename,
            size: file.size,
            contentType: file.contentType
        ] as JSON)
    }

    def uploadWithExtensionValidation() {
        def file = request.getFile('file')
        if (!file || file.empty) {
            response.status = 400
            render([error: 'no_file', message: 'No file uploaded'] as JSON)
            return
        }

        def allowedExtensions = ['txt', 'csv', 'json', 'xml']
        def filename = file.originalFilename
        def extension = filename.contains('.') ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase() : ''

        if (!allowedExtensions.contains(extension)) {
            response.status = 400
            render([error: 'invalid_extension', message: "Extension '${extension}' not allowed", allowedExtensions: allowedExtensions] as JSON)
            return
        }

        render([
            success: true,
            filename: filename,
            extension: extension,
            validated: true
        ] as JSON)
    }

    // ========== File Info Extraction ==========

    def getFileInfo() {
        def file = request.getFile('file')
        if (!file || file.empty) {
            response.status = 400
            render([error: 'no_file', message: 'No file uploaded'] as JSON)
            return
        }

        def filename = file.originalFilename
        def extension = filename.contains('.') ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase() : ''
        def basename = filename.contains('.') ? filename.substring(0, filename.lastIndexOf('.')) : filename

        render([
            originalFilename: filename,
            basename: basename,
            extension: extension,
            size: file.size,
            sizeKB: (file.size / 1024).round(2),
            contentType: file.contentType,
            isEmpty: file.empty
        ] as JSON)
    }

    // ========== Params-based Access ==========

    def uploadViaParams() {
        def file = params.file
        if (!file || !(file instanceof MultipartFile) || file.empty) {
            response.status = 400
            render([error: 'no_file', message: 'No file in params'] as JSON)
            return
        }

        render([
            success: true,
            accessedViaParams: true,
            filename: file.originalFilename,
            size: file.size
        ] as JSON)
    }
}
