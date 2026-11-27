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
import spock.lang.Issue
import spock.lang.Specification

class JsonBindingSpec extends Specification implements ControllerUnitTest<BindingController> {

    void 'Test binding JSON body'() {
        given:
        request.method = 'POST'
        request.json = '''
            {
    "name": "Douglas", "age": "42"}
'''
    when:
        def model = controller.createPersonCommandObject()
    then:
        model.person instanceof Person
        model.person.name == 'Douglas'
        model.person.age == 42
    }

    void 'Test binding nested collection elements'() {
        given:
        request.method = 'POST'
        request.json = '''
            {
    "lastName": "Brown",
    "familyMembers": [
        {"name": "Jake", "age": "12"},
        {"name": "Zack", "age": "15"}
    ]
}
'''
    when:
        def model = controller.createFamily()
    then:
        model.family.lastName == 'Brown'

        model.family.familyMembers.size() == 2

        model.family.familyMembers[0].name == 'Jake'
        model.family.familyMembers[0].age == 12

        model.family.familyMembers[1].name == 'Zack'
        model.family.familyMembers[1].age == 15
    }

    void 'Test parsing invalid JSON'() {
        when:
        request.method = 'POST'
        request.json = '''
            {
    "name": [foo.[} this is unparseable JSON{[
'''
        def model = controller.createPerson()
        def person = model.person

        then:
        person.hasErrors()
        person.errors.errorCount == 1
        person.errors.allErrors[0].defaultMessage == 'An error occurred parsing the body of the request'
        person.errors.allErrors[0].code == 'invalidRequestBody'
        'invalidRequestBody' in person.errors.allErrors[0].codes
        'org.grails.web.binding.json.Person.invalidRequestBody' in person.errors.allErrors[0].codes
    }

    void 'Test parsing JSON with other than UTF-8 content type'() {
        given:
            String jsonString = '{"name":"Hello öäåÖÄÅ"}'
            request.method = 'POST'
            request.contentType = 'application/json; charset=UTF-16'
            request.content = jsonString.getBytes("UTF-16")
        when:
            def model = controller.createPersonCommandObject()
        then:
            model.person instanceof Person
            model.person.name == 'Hello öäåÖÄÅ'
    }

    void 'Test binding JSON to a Map'() {
        given:
        request.contentType = JSON_CONTENT_TYPE
        request.method = 'POST'
        request.JSON = '{"mapData": {"name":"Jeff", "country":"USA"}}'

        when:
        def model = controller.createFamily()

        then:
        model.family.mapData instanceof Map
        model.family.mapData.name == 'Jeff'
        model.family.mapData.country == 'USA'
    }
    
    @Issue('GRAILS-11576')
    void 'Test binding malformed JSON to a command object'() {
        given:
        request.contentType = JSON_CONTENT_TYPE
        request.method = 'POST'
        request.JSON = '{"mapData": {"name":"Jeff{{{"'
        
        when:
        def model = controller.createFamily()
        
        then:
        model.family.hasErrors()
        
        when:
        def familyError = model.family.errors.allErrors.find {
            it.objectName == 'family'
        }
        
        then: 'by the JsonSlurper parser, which binds JSON bodies until grails.databinding.json.jackson is set'
        familyError?.defaultMessage?.contains 'Error occurred initializing command object [family]. groovy.json.JsonException'
    }
    
    void 'without grails.databinding.json.jackson, a body with a trailing comma binds, as in Grails 8'() {
        given:
        request.contentType = JSON_CONTENT_TYPE
        request.method = 'POST'
        request.content = '{"name": "Douglas", "age": "42",}'.getBytes('UTF-8')

        when:
        def person = controller.createPerson().person

        then:
        !person.hasErrors()
        person.name == 'Douglas'
    }

    @Issue('GRAILS-11646')
    void 'should JSON encoding be handled'() {
        given:
        request.contentType = 'application/json; charset=ISO-8859-1'
        request.method = 'POST'
        request.content = '{"data": "Multibyte characters: äöåÄÖÅ"}'
        
        expect:
        request.JSON.toString() == '{"data":"Multibyte characters: äöåÄÖÅ"}'
    } 
}

class JacksonJsonBindingSpec extends Specification implements ControllerUnitTest<BindingController> {

    Closure doWithConfig() {
        { config ->
            config['grails.databinding.json.jackson'] = true
            config['spring.jackson.json.read.allow-java-comments'] = true
        }
    }

    void 'with grails.databinding.json.jackson, a request body binds as the application mapper parses it, with its spring.jackson read features, decimals as BigDecimal'() {
        given: 'a comment, which the mapper accepts as spring.jackson configures it, and JsonSlurper does not'
        request.contentType = JSON_CONTENT_TYPE
        request.method = 'POST'
        request.content = '{/* a family */"lastName": "Brown", "mapData": {"price": 1.15}, "familyMembers": [{"name": "Jake", "age": "12"}]}'
                .getBytes('UTF-8')

        when:
        def model = controller.createFamily()

        then:
        model.family.lastName == 'Brown'
        model.family.mapData.price instanceof BigDecimal
        model.family.mapData.price == 1.15G
        model.family.familyMembers*.name == ['Jake']
    }

    void 'with grails.databinding.json.jackson, a body only the legacy parser accepts is an invalid request body'() {
        given: 'a trailing comma, which JsonSlurper accepts and Jackson does not by default'
        request.contentType = JSON_CONTENT_TYPE
        request.method = 'POST'
        request.content = '{"name": "Douglas", "age": "42",}'.getBytes('UTF-8')

        when:
        def person = controller.createPerson().person

        then:
        person.hasErrors()
        person.errors.allErrors[0].code == 'invalidRequestBody'
    }
}

@Artefact('Controller')
class BindingController {
    def createPerson() {
        def person = new Person()
        bindData person, request
        [person: person]
    }

    def createPersonCommandObject(Person person) {
        [person: person]
    }

    def createFamily(Family family) {
        [family: family]
    }
}

class Person {
    String name
    Integer age
}

class Family {
    String lastName
    List<Person> familyMembers
    Map mapData
}
