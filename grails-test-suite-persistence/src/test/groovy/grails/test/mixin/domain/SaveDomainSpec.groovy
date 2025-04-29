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
package grails.test.mixin.domain

import grails.persistence.Entity
import grails.testing.gorm.DomainUnitTest
import spock.lang.Specification

class SaveDomainSpec extends Specification implements DomainUnitTest<Person> {

    void 'test dateCreated and lastUpdated populated'() {
        given:
        Person person = new Person(name: 'Bobby')

        when:
        person.save(flush: true)

        then:
        person.dateCreated != null
        person.lastUpdated != null
        person.dateCreated == person.lastUpdated

        when:
        person.name = 'Bobby Updated'
        person.save(flush: true)

        then:
        person.lastUpdated > person.dateCreated
    }
}

@Entity
class Person {
    String name
    Date dateCreated
    Date lastUpdated
}