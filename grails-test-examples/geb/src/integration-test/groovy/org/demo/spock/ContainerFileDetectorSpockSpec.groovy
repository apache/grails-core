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

import grails.plugin.geb.ContainerFileDetector
import grails.plugin.geb.ContainerGebSpec
import grails.plugin.geb.UselessContainerFileDetector
import grails.plugin.geb.serviceloader.ServiceRegistry
import grails.testing.mixin.integration.Integration
import org.demo.spock.pages.UploadPage
import org.openqa.selenium.WebDriverException
import spock.lang.PendingFeature

/**
 * Altered copy of {@link ContainerFileDetectorAnnotationSpec}
 */
@Integration
class ContainerFileDetectorSpockSpec extends ContainerGebSpec {

    def setupSpec(){
        ServiceRegistry.setInstance(ContainerFileDetector, new UselessContainerFileDetector())
    }

    def cleanupSpec(){
        ServiceRegistry.setInstance(ContainerFileDetector, null)
    }

    @PendingFeature(reason = 'https://github.com/apache/grails-geb/pull/146#issuecomment-2691433277')
    void 'should fail to find file with fileDetector changed to UselessContainerFileDetector in setupSpec'() {
        given:
        def uploadPage = to UploadPage

        when:
        uploadPage.fileInput.file = new File('src/integration-test/resources/assets/upload-test.txt')

        and:
        uploadPage.submitBtn.click()

        then:
        def e = thrown(WebDriverException)
        e.message.contains('File not found')
    }
}
