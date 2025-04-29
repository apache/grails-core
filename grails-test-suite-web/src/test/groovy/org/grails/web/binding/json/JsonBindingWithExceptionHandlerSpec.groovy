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
package org.grails.web.binding.json

import grails.artefact.Artefact
import grails.testing.web.controllers.ControllerUnitTest
import org.grails.databinding.bindingsource.DataBindingSourceCreationException
import spock.lang.Specification

class JsonBindingWithExceptionHandlerSpec extends Specification implements ControllerUnitTest<BindingWithExceptionHandlerMethodController> {

    void 'test binding malformed JSON'() {
        given:
        request.contentType = JSON_CONTENT_TYPE
        request.method = 'POST'
        request.JSON = '{"mapData": {"name":"Jeff{{{"'

        when:
        controller.bindWithCommandObject()
        
        then:
        response.status == 400
        model.errorMessage == 'caught a DataBindingSourceCreationException'
        view == '/bindingProblems'
    }

    void 'test binding valid JSON'() {
        given:
        request.contentType = JSON_CONTENT_TYPE
        request.method = 'POST'
        request.JSON = '{"name":"Jeff"}'

        when:
        def model = controller.bindWithCommandObject()
        
        then:
        response.status == 200
        flash.message == null
        model.command.name == 'Jeff'
        
    }
}

@Artefact('Controller')
class BindingWithExceptionHandlerMethodController {
    
    def bindWithCommandObject(SomeCommandObject co) {
        [command: co]
    }
    
    def handleDataBindingException(DataBindingSourceCreationException e) {
        response.status = 400
        render view: '/bindingProblems', model: [errorMessage: 'caught a DataBindingSourceCreationException']
    }
} 

class SomeCommandObject {
    String name
    static constraints = {
        name matches: /[A-Z].*/
    }
}
