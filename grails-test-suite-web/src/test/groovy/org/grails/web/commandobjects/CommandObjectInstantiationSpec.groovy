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

package org.grails.web.commandobjects

import grails.artefact.Artefact
import grails.persistence.Entity
import grails.testing.gorm.DomainUnitTest
import grails.testing.web.controllers.ControllerUnitTest
import jakarta.servlet.http.HttpServletResponse
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

class CommandObjectInstantiationSpec extends Specification implements ControllerUnitTest<InstantiationController>, DomainUnitTest<DomainClassCommandObject> {

    @Unroll
    @Issue('GRAILS-11247')
    void 'Test non domain command object instantiation for #requestMethod request'() {
        when:
        request.method = requestMethod
        params.name = "Name for ${requestMethod} request"
        controller.nonDomainCommandObject()
        
        then:
        response.status == HttpServletResponse.SC_OK
        model.commandObject.name == "Name for ${requestMethod} request"
        
        where:
        requestMethod << ['POST', 'PUT', 'GET', 'DELETE']
    }

    @Unroll
    @Issue('GRAILS-11247')
    void 'Test domain command object instantiation for #requestMethod request with no id'() {
        when:
        request.method = requestMethod
        params.name = "Name for ${requestMethod} request with no id"
        controller.domainCommandObject()
        
        then:
        response.status == HttpServletResponse.SC_OK
        model.commandObject == null
        
        where:
        requestMethod << ['PUT', 'GET', 'DELETE']
    }
    
    @Issue('GRAILS-11247')
    void 'Test domain command object instantiation for POST request with no id'() {
        when:
        request.method = 'POST'
        params.name = "Name for POST request with no id"
        controller.domainCommandObject()
        
        then:
        response.status == HttpServletResponse.SC_OK
        model.commandObject.name == "Name for POST request with no id"
    }

    @Issue('GRAILS-11712')
    void 'Test domain command object instantiation for POST request with empty id'() {
        when:
        request.method = 'POST'
        params.name = "Name for POST request with empty id"
        params.id = ''
        controller.domainCommandObject()

        then:
        response.status == HttpServletResponse.SC_OK
        model.commandObject.name == "Name for POST request with empty id"
    }

    @Issue('GRAILS-11712')
    void 'Test domain command object instantiation for POST request with blank id'() {
        when:
        request.method = 'POST'
        params.name = "Name for POST request with blank id"
        params.id = '  '
        controller.domainCommandObject()

        then:
        response.status == HttpServletResponse.SC_OK
        model.commandObject.name == "Name for POST request with blank id"
    }

    @Unroll
    @Issue('GRAILS-11247')
    void 'Test domain command object instantiation for #requestMethod request with id'() {
        given:
        def domainObject = new DomainClassCommandObject(name: 'My Domain Name')
        
        when:
        domainObject.save()
        def id = domainObject.id
        
        then:
        id != null
        
        when:
        request.method = requestMethod
        params.id = id
        controller.domainCommandObject()
        
        then:
        response.status == HttpServletResponse.SC_OK
        model.commandObject.id == id
        model.commandObject.name == 'My Domain Name'
        
        where:
        requestMethod << ['POST', 'PUT', 'GET', 'DELETE']
    }
}

@Artefact('Controller')
class InstantiationController {

    def nonDomainCommandObject(CommandObject co) {
        render view: 'view', model: [commandObject: co]
    }
    
    def domainCommandObject(DomainClassCommandObject co) {
        render view: 'view', model: [commandObject: co]
    } 
}

@Entity
class DomainClassCommandObject {
    String name
}

class CommandObject {
    String name
}
