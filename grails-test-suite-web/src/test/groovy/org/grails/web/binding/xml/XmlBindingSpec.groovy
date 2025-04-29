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
package org.grails.web.binding.xml

import grails.artefact.Artefact
import grails.persistence.Entity
import grails.testing.gorm.DataTest
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Issue
import spock.lang.Specification

class XmlBindingSpec extends Specification implements ControllerUnitTest<BindingController>, DataTest {

    Class[] getDomainClassesToMock() {
        [Person, Address]
    }

    void 'Test binding XML body'() {
        when:
        request.method = 'POST'
        request.xml = '''
<person>
    <name>Douglas</name>
    <age>42</age>
    <homeAddress>
        <state>Missouri</state>
        <city>O'Fallon</city>
    </homeAddress>
    <workAddress>
        <state>California</state>
        <city>San Mateo</city>
    </workAddress>
</person>
'''
        def model = controller.createPerson()

    then:
        model.person instanceof Person
        model.person.name == 'Douglas'
        model.person.age == 42
        model.person.homeAddress.city == "O'Fallon"
        model.person.homeAddress.state == 'Missouri'
        model.person.workAddress.city == 'San Mateo'
        model.person.workAddress.state == 'California'
    }

    void 'Test parsing invalid XML'() {
        given:
        request.method = 'POST'
        request.xml = '''<person><someInvalid<this is invalid XML'''

        when:
        def model = controller.createPerson()
        def person = model.person

        then:
        response.status == 200
        person.hasErrors()
        person.errors.errorCount == 1
        person.errors.allErrors[0].defaultMessage == 'An error occurred parsing the body of the request'
        person.errors.allErrors[0].code == 'invalidRequestBody'
        'invalidRequestBody' in person.errors.allErrors[0].codes
        'org.grails.web.binding.xml.Person.invalidRequestBody' in person.errors.allErrors[0].codes
    }
    
    @Issue('GRAILS-11576')
    void 'Test binding malformed XML to a command object'() {
        given:
        request.contentType = XML_CONTENT_TYPE
        request.method = 'POST'
        request.xml = '''<person><someInvalid<this is invalid XML'''
        
        when:
        def model = controller.createPersonCommandObject()
        def person = model.person
        
        then:
        model.person.hasErrors()
        
        when:
        def personError = model.person.errors.allErrors.find {
            it.objectName == 'person'
        }
        
        then:
        personError?.defaultMessage?.contains 'Error occurred initializing command object [person]. org.xml.sax.SAXParseException'
    }

}

@Artefact('Controller')
class BindingController {
    def createPerson() {
        def person = new Person()
        person.properties = request
        [person: person]
    }
    def createPersonCommandObject(Person person) {
        [person: person]
    }
}

@Entity
class Person {
    String name
    Integer age
    Address homeAddress
    Address workAddress
}

@Entity
class Address {
    String city
    String state
}
