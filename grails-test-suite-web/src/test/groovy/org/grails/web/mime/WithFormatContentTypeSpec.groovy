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
package org.grails.web.mime

import grails.artefact.Artefact
import grails.testing.web.controllers.ControllerUnitTest
import grails.web.mime.MimeType
import spock.lang.Issue
import spock.lang.Specification

class WithFormatContentTypeSpec extends Specification implements ControllerUnitTest<FormatController> {

    Closure doWithConfig() {{ config ->
        // unit tests in real applications will not need to do 
        // this because the real Config.groovy will be loaded
        config['grails.mime.types'] = [(MimeType.ALL.extension): MimeType.ALL.name,
                                    (MimeType.FORM.extension): MimeType.FORM.name,
                                    (MimeType.MULTIPART_FORM.extension): MimeType.MULTIPART_FORM.name,
                                    (MimeType.JSON.extension): MimeType.JSON.name]
    }}

    @Issue('GRAILS-11093')
    void 'Test specifying form contentType'() {
        when: 'content type is specified'
        request.contentType = FORM_CONTENT_TYPE
        controller.index()
        
        then: 'the corresponding block is executed'
        response.status == 200
        view == '/formView'
    }

    @Issue('GRAILS-11093')
    void 'Test specifying multipartForm contentType'() {
        when: 'content type is specified'
        request.contentType = MULTIPART_FORM_CONTENT_TYPE
        controller.index()
        
        then: 'the corresponding block is executed'
        response.status == 200
        view == '/formView'
    }

    @Issue('GRAILS-11093')
    void 'Test not specifying contentType'() {
        when: 'no content type is specified'
        controller.index()
        
        then: 'the wildcard block is executed'
        response.status == 200
        view == '/wildcardView'
    }

    @Issue('GRAILS-11093')
    void 'Test specifying request format'() {
        when: 'a request format is specified'
        request.format = 'form'
        controller.index()
        
        then: 'the corresponding block is executed'
        response.status == 200
        view == '/formView'
    }
}

@Artefact('Controller')
class FormatController {
    
    def index() {
        request.withFormat {
            multipartForm form {
                render view: '/formView'
            }
            '*' {
                render view: '/wildcardView'
            }
        }
    }
}
