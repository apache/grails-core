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
package org.grails.web.binding.hal.xml

import grails.persistence.Entity
import grails.testing.gorm.DataTest
import grails.testing.web.controllers.ControllerUnitTest
import grails.web.Controller
import spock.lang.Specification

class HalXmlBindingSpec extends Specification implements ControllerUnitTest<BindingController>, DataTest {

    Class<?>[] getDomainClassesToMock() {
        [Person, Address]
    }

    void 'Test binding XML body'() {
        when:
        request.method = 'POST'
        request.xml = '''<?xml version="1.0" encoding="UTF-8"?>
<resource href="http://localhost/people/1" hreflang="en">
    <name>Douglas</name>
    <age>42</age>
    <resource rel="homeAddress">
        <state>Missouri</state>
        <city>O'Fallon</city>
    </resource>
    <resource rel="workAddress">
        <state>California</state>
        <city>San Mateo</city>
    </resource>
</resource>'''

        request.contentType = HAL_XML_CONTENT_TYPE
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
}

@Controller
class BindingController {
    def createPerson() {
        def person = new Person()
        person.properties = request
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
