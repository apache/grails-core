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
package org.grails.web.binding

import grails.artefact.Artefact
import grails.persistence.Entity
import grails.testing.gorm.DomainUnitTest
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Issue
import spock.lang.Specification

class GrailsParameterMapBindingSpec extends Specification implements ControllerUnitTest<MyController>, DomainUnitTest<MyDomain> {
    
    void 'Test binding body to command object'() {
        when: 'the body contains JSON'
        request.json = '{"name":"JSON Name"}'
        request.method = 'POST'
        controller.bindToCommandObject()
        
        then: 'the JSON is used for binding'
        response.text == 'JSON Name'
    }
    
    void 'Test binding request parameters to command object'() {
        when: 'request parameters are present'
        request.method = 'POST'
        params.name = 'Request Parameter Name'
        controller.bindToCommandObject()
        
        then: 'the request parameters are used for binding'
        response.text == 'Request Parameter Name'
    }
    
    void 'Test binding body to command object when request parameters are also present'() {
        when: 'the body contains JSON and request parameters are present'
        request.method = 'POST'
        request.json = '{"name":"JSON Name"}'
        params.name = 'Request Parameter Name'
        controller.bindToCommandObject()
        
        then: 'the JSON is used for binding'
        response.text == 'JSON Name'
    }
    
    void 'Test binding nested parameter Map to command object'() {
        when: 'nested request parameters are present'
        params.'mydomain.name' = 'Nested Request Parameter Name'
        controller.bindWithNestedParameterMap()
        
        then: 'the request parameters are used for binding'
        response.text == 'Nested Request Parameter Name'
    }
    
    @Issue('GRAILS-11179')
    void 'Test binding nested parameter Map to command object when the request has a body'() {
        when: 'nested request parameters are present and the request has a body'
        request.json = '{"name":"JSON Name"}'
        params.'mydomain.name' = 'Nested Request Parameter Name'
        controller.bindWithNestedParameterMap()
        
        then: 'the request parameters are used for binding'
        response.text == 'Nested Request Parameter Name'
    }

    void 'Test binding with request parameters'() {
        when: 'request parameters are present'
        params.name = 'Request Parameter Name'
        controller.bindWithParameterMap()
        
        then: 'the request parameters are used for binding'
        response.text == 'Request Parameter Name'
    }
    
    @Issue('GRAILS-11179')
    void 'Test binding with request parameters when the request has a body'() {
        when: 'the request contains a body and request parameters'
        params.name = 'Request Parameter Name'
        request.json = '{"name":"JSON Name"}'
        controller.bindWithParameterMap()
        
        then: 'the request parameters are used for binding'
        response.text == 'Request Parameter Name'
    }
    
    void 'Test binding with the request when request parameters are present'() {
        when: 'request parameters are present'
        params.name = 'Request Parameter Name'
        controller.bindWithRequest()
        
        then: 'the request parameters are used for binding'
        response.text == 'Request Parameter Name'
    }
    
    void 'Test binding with the request when the request has a body'() {
        when: 'request parameters are present'
        request.method = 'POST'
        request.json = '{"name":"JSON Name"}'
        controller.bindWithRequest()
        
        then: 'the body is used for binding'
        response.text == 'JSON Name'
    }
    
    void 'Test binding with the request when the request has both a body and request parameters'() {
        when: 'request parameters are present and the request has a body'
        request.method = 'POST'
        request.json = '{"name":"JSON Name"}'
        params.name = 'Request Parameter Name'
        controller.bindWithRequest()
        
        then: 'the body is used for binding'
        response.text == 'JSON Name'
    }
}

@Artefact('Controller')
class MyController {
    
    def bindToCommandObject(MyDomain obj) {
        render obj.name
    }
    def bindWithNestedParameterMap() {
        def obj = new MyDomain(params['mydomain'])
        render obj.name
    }
    
    def bindWithParameterMap() {
        def obj = new MyDomain(params)
        render obj.name
    }
    
    def bindWithRequest() {
        def obj = new MyDomain()
        obj.properties = request
        render obj.name
    }
}

@Entity
class MyDomain {
    String name
}