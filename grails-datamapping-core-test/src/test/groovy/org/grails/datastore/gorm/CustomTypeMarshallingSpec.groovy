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
package org.grails.datastore.gorm

import grails.persistence.Entity
import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.Issue
import spock.lang.PendingFeature
import spock.lang.Shared

class CustomTypeMarshallingSpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {

    @Shared
    Date now = new Date()

    void setupSpec() {
        manager.addAllDomainClasses([Person])
    }

    def setup() {
        def p = new Person(name: "Fred", birthday: new Birthday(now))
        p.save(flush: true)
        manager.session.clear()
    }

    def cleanup() {
        Person.list()*.delete(flush: true)
        manager.session.clear()
    }

    void "can retrieve custom values from the datastore"() {
        when: "We query the person"
        def p = Person.findByName("Fred")

        then: "The birthday is returned"
        p != null
        p.name == "Fred"
        p.birthday != null
    }

    void "can query based on custom types"() {
        when: "We query with a custom type"
        def p = Person.findByBirthday(new Birthday(now))

        then:
        p != null
    }

    void "can perform a range query based on custom types"() {
        when: "A range query is executed"
        def p = Person.findByBirthdayBetween(new Birthday(now - 1), new Birthday(now + 1))
        def p2 = Person.findByBirthdayBetween(new Birthday(now + 1), new Birthday(now + 2))

        then:
        p != null
        p2 == null
    }

    @Issue("https://github.com/apache/grails-core/issues/4546")
    @PendingFeature(reason = 'Was previously @Ignore')
    void "can re-save an existing instance without modifications"() {
        given:
        def p = Person.findByName("Fred")

        when: "we can re-save an existing instance without modifications"
        p.birthday = new Birthday(now)
        boolean saveResult = p.save(flush: true)

        then: 'the save is successful'
        saveResult

        and: "the version is not incremented"
        p.version == old(p.version)
    }

    @Issue("https://github.com/apache/grails-core/issues/4546")
    void "can modify the value of a custom type property"() {
        given:
        def p = Person.findByName("Fred")

        when: "we modify the value of a custom property"
        p.birthday = new Birthday(now + 1)
        boolean saveResult = p.save(flush: true)

        then: 'the save is successful'
        saveResult

        and: "the version is incremented"
        p.version == old(p.version) + 1

        and: "we can query based on the modified value"
        manager.session.clear()
        Person.countByBirthdayGreaterThan(new Birthday(now)) == 1
    }

    @Issue("https://github.com/apache/grails-core/issues/4546")
    void "can nullify the value of a custom type property"() {
        given:
        def p = Person.findByName("Fred")

        when: "we modify the value of a custom property"
        p.birthday = null
        boolean saveResult = p.save(flush: true)

        then: 'the save is successful'
        saveResult

        and: "the version is incremented"
        p.version == old(p.version) + 1

        and: "we can query based on the modified value"
        manager.session.clear()
        Person.countByBirthdayIsNull() == 1
    }
}

@Entity
class Person {


    Long id
    Integer version
    String name
    Birthday birthday

}

class Birthday implements Comparable {
    Date date

    Birthday(Date date) {
        this.date = date
    }

    @Override
    int hashCode() {
        date.hashCode()
    }

    @Override
    boolean equals(Object obj) {
        obj instanceof Birthday && date == obj.date
    }

    int compareTo(t) {
        date.compareTo(t.date)
    }

    @Override
    String toString() {
        "Birthday[$date.time]"
    }
}
