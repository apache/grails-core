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

package org.demo.spock

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration
import org.demo.spock.pages.UploadPage
import spock.lang.IgnoreIf
import spock.lang.Requires

@Integration
class UploadSpec extends ContainerGebSpec {

    @Requires({ os.windows })
    void 'should be able to upload files on a Windows host'() {
        given:
        def uploadPage = to UploadPage

        when:
        uploadPage.fileInput.file = createFileInputSource(
                'src/integration-test/resources/assets/upload-test.txt',
                '/tmp/upload-test.txt'
        )

        and:
        uploadPage.submitBtn.click()

        then:
        title == 'File Uploaded'
        pageSource.contains('File uploaded successfully')
    }

    @IgnoreIf({ os.windows })
    void 'should be able to upload files on a non-Windows host'() {
        given:
        def uploadPage = to UploadPage

        when:
        uploadPage.fileInput.file = createFileInputSource(
                'src/integration-test/resources/assets/upload-test.txt',
                '/tmp/upload-test.txt'
        )

        and:
        uploadPage.submitBtn.click()

        then:
        title == 'File Uploaded'
        pageSource.contains('File uploaded successfully')
    }
}