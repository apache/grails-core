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
package org.grails.plugins.testing

import groovy.transform.CompileStatic
import org.springframework.mock.web.MockMultipartFile

/**
 * Extends the default Spring MockMultipartFile to provide an implementation of transferTo that
 * doesn't use the file system.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@CompileStatic
class GrailsMockMultipartFile extends MockMultipartFile {

    private File targetFileLocation

    GrailsMockMultipartFile(String name, byte[] content) {
        super(name, content)
    }

    GrailsMockMultipartFile(String name, InputStream contentStream) throws IOException {
        super(name, contentStream)
    }

    GrailsMockMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
        super(name, originalFilename, contentType, content)
    }

    GrailsMockMultipartFile(String name, String originalFilename, String contentType, InputStream contentStream) throws IOException {
        super(name, originalFilename, contentType, contentStream)
    }

    @Override
    void transferTo(File dest) throws IOException, IllegalStateException {
        this.targetFileLocation = dest
    }

    /**
     * @return The location where the MultipartFile was transfered to
     */
    File getTargetFileLocation() {
        return targetFileLocation
    }

}
