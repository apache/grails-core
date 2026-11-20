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

package functionaltests

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration
import org.testcontainers.images.builder.Transferable

/**
 */
@Integration(applicationClass = Application)
class UploadControllerSpec extends ContainerGebSpec {

    void "Test file upload"() {
        when:"When go to an upload page"
        go "/upload/index"

        getContainer().copyFileToContainer(Transferable.of("Test upload", 0777), "/test.txt")
        def form = $('#myForm')

        form.myFile = "/test.txt"
        $('#input1').click()

        then:"The file is uploaded"
        waitFor {
            $('p').text() == 'Test upload'
        }
    }

    void "Test file upload parameters"() {
        when:"When go to an upload page"
        go "/upload/index"

        getContainer().copyFileToContainer(Transferable.of("Test upload", 0777), "/test.txt")
        def form = $('#myForm2')

        form.myFile = "/test.txt"
        $('#input2').click()

        then:"The file is uploaded"
        waitFor {
            $('p').text() == 'ok'
        }
    }
}
