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

package org.grails.web.metaclass

import grails.artefact.Artefact
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

class CollectionBindDataMethodSpec extends Specification implements ControllerUnitTest<DemoController> {

    void 'Test bindData with a CollectionDataBindingSource argument using XML'() {
        when:
        request.xml = '''
<people>
    <person>
        <firstName>Peter</firstName>
        <lastName>Gabriel</lastName>
    </person>
    <person>
        <firstName>Tony</firstName>
        <lastName>Banks</lastName>
    </person>
    <person>
        <firstName>Steve</firstName>
        <lastName>Hackett</lastName>
    </person>
</people>
'''
        def model = controller.createPeopleWithBindingSource()
        def people = model.people

        then:
        people instanceof List
        people.size() == 3
        people[0] instanceof Person
        people[0].firstName == 'Peter'
        people[0].lastName == 'Gabriel'
        people[1] instanceof Person
        people[1].firstName == 'Tony'
        people[1].lastName == 'Banks'
        people[2] instanceof Person
        people[2].firstName == 'Steve'
        people[2].lastName == 'Hackett'
    }

    void 'Test bindData with a CollectionDataBindingSource argument using JSON'() {
        when:
        request.json = '''
  [{"firstName": "Phil", "lastName" : "Lynott"},
   {"firstName": "Scott", "lastName" : "Gorham"},
   {"firstName": "Brian", "lastName" : "Downey"}]
'''
        def model = controller.createPeopleWithBindingSource()
        def people = model.people

        then:
        people instanceof List
        people.size() == 3
        people[0] instanceof Person
        people[0].firstName == 'Phil'
        people[0].lastName == 'Lynott'
        people[1] instanceof Person
        people[1].firstName == 'Scott'
        people[1].lastName == 'Gorham'
        people[2] instanceof Person
        people[2].firstName == 'Brian'
        people[2].lastName == 'Downey'
    }

    void 'Test bindData with the request using XML'() {
        when:
        request.xml = '''
<people>
    <person>
        <firstName>Alex</firstName>
        <lastName>Lifeson</lastName>
    </person>
    <person>
        <firstName>Neil</firstName>
        <lastName>Peart</lastName>
    </person>
    <person>
        <firstName>Geddy</firstName>
        <lastName>Lee</lastName>
    </person>
</people>
'''
        def model = controller.createPeopleWithRequest()
        def people = model.people

        then:
        people instanceof List
        people.size() == 3
        people[0] instanceof Person
        people[0].firstName == 'Alex'
        people[0].lastName == 'Lifeson'
        people[1] instanceof Person
        people[1].firstName == 'Neil'
        people[1].lastName == 'Peart'
        people[2] instanceof Person
        people[2].firstName == 'Geddy'
        people[2].lastName == 'Lee'
    }

    void 'Test bindData with the request using JSON'() {
        when:
        request.json = '''
  [{"firstName": "Danny", "lastName" : "Carey"},
   {"firstName": "Adam", "lastName" : "Jones"},
   {"firstName": "Justin", "lastName" : "Chancellor"},
   {"firstName": "Maynard", "lastName" : "Keenan"}]
'''
        def model = controller.createPeopleWithRequest()
        def people = model.people

        then:
        people instanceof List
        people.size() == 4
        people[0] instanceof Person
        people[0].firstName == 'Danny'
        people[0].lastName == 'Carey'
        people[1] instanceof Person
        people[1].firstName == 'Adam'
        people[1].lastName == 'Jones'
        people[2] instanceof Person
        people[2].firstName == 'Justin'
        people[2].lastName == 'Chancellor'
        people[3] instanceof Person
        people[3].firstName == 'Maynard'
        people[3].lastName == 'Keenan'
    }
}

@Artefact('Controller')
class DemoController {
    def dataBindingSourceRegistry
    def mimeTypeResolver

    def createPeopleWithRequest() {
        def listOfPeople = []

        bindData Person, listOfPeople, request

        [people: listOfPeople]
    }

    def createPeopleWithBindingSource() {
        def mimeType = mimeTypeResolver.resolveRequestMimeType()
        def bindingSource = dataBindingSourceRegistry.createCollectionDataBindingSource mimeType, Person, request

        def listOfPeople = []

        bindData Person, listOfPeople, bindingSource

        [people: listOfPeople]
    }
}

class Person {
    String firstName
    String lastName
}
