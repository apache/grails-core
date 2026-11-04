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

    @Issue('https://github.com/apache/grails-core/issues/16280')
    void 'Test a parameter named identifier does not resolve a domain command object'() {
        given: 'a saved domain object a request could try to address'
        def decoy = new DomainClassCommandObject(name: 'Decoy')
        decoy.save(flush: true)

        expect:
        decoy.id != null

        when: 'a GET submits only identifier, so the binding source has no id and the id fallback runs'
        request.method = 'GET'
        params.identifier = decoy.id
        controller.domainCommandObject()

        then: 'the identifier parameter is not treated as the entity id'
        response.status == HttpServletResponse.SC_OK
        model.commandObject == null

        and: 'and it is still readable as an ordinary request parameter'
        params['identifier'] == decoy.id
    }

    @Issue('https://github.com/apache/grails-core/issues/16280')
    void 'Test id wins over a parameter named identifier when resolving a domain command object'() {
        given:
        def target = new DomainClassCommandObject(name: 'Target')
        def decoy = new DomainClassCommandObject(name: 'Decoy')
        target.save()
        decoy.save(flush: true)

        expect:
        target.id != null
        decoy.id != null
        target.id != decoy.id

        when:
        request.method = 'GET'
        params.id = target.id
        params.identifier = decoy.id
        controller.domainCommandObject()

        then:
        response.status == HttpServletResponse.SC_OK
        model.commandObject.id == target.id
        model.commandObject.name == 'Target'

        and:
        params['identifier'] == decoy.id
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
