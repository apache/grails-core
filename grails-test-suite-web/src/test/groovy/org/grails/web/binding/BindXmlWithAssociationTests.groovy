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
import grails.testing.gorm.DataTest
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

class BindXmlWithAssociationTests extends Specification implements ControllerUnitTest<PersonController>, DataTest {

    Class[] getDomainClassesToMock() {
        [TargetPerson, Book]
    }

    void testBindXmlWithAssociatedId() {
        when:
        Book b = new Book(title: "The Stand", pages: 1000).save(flush:true)
        request.method = 'POST'
        request.xml = """
<person><name>xyz</name><book id='${b.id}'></book></person>
""".toString()

        controller.save()

        then:
        response.text == 'saved'
    }

    void testBindXmlWithAssociatedIdAndProperties() {
        when:
        request.method = 'POST'
        request.xml = '''
<person><name>xyz</name><book id='1'><title>Blah</title><pages>300</pages></book></person>
'''

        controller.save()
        TargetPerson person = request.person

        then:
        response.text == 'saved'
        person != null
        person.name == 'xyz'
        person.book != null
        person.book.id == 1
        person.book.title == 'Blah'
        person.book.pages == 300
    }
}

@Artefact('Controller')
class PersonController {

    def save = {
        def person = new TargetPerson()
        person.properties = request

        // uncomment next line to avoid error
        //person.merge()
        person.save(failOnError:true)

        request.person = person
        render 'saved'
    }
}

@Entity
class TargetPerson {
    String name
    Book book
}

@Entity
class Book {

    String title
    int pages

    static constraints = {
        id bindable: true
    }
}
