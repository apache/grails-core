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
package org.grails.datastore.gorm.mongo

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.springframework.dao.InvalidDataAccessResourceUsageException

class AggregateSubquerySpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.registerDomainClasses(AggSubPerson, AggSubCity)
    }

    private void seed() {
        [['Homer', 45], ['Marge', 40], ['Bart', 10], ['Lisa', 8], ['Barney', 47]].each {
            new AggSubPerson(firstName: it[0], age: it[1]).save(flush: true, failOnError: true)
        }
        manager.session.clear()
    }

    void "age greater than the average age"() {
        given:
        seed()

        when:
        List<AggSubPerson> found = AggSubPerson.where { age > avg(age) }.list(sort: 'firstName')

        then:
        found*.firstName == ['Barney', 'Homer', 'Marge']
    }

    void "age at least the average age and combined with another criterion"() {
        given:
        seed()

        when:
        List<AggSubPerson> found = AggSubPerson.where { age >= avg(age) && firstName != 'Marge' }.list(sort: 'firstName')

        then:
        found*.firstName == ['Barney', 'Homer']
    }

    void "age below the average age"() {
        given:
        seed()

        when:
        List<AggSubPerson> found = AggSubPerson.where { age < avg(age) }.list(sort: 'firstName')

        then:
        found*.firstName == ['Bart', 'Lisa']
    }

    void "a comparison against a subquery inside an or"() {
        given:
        seed()

        when:
        List<AggSubPerson> found = AggSubPerson.where { age > avg(age) || firstName == 'Lisa' }.list(sort: 'firstName')

        then:
        found*.firstName == ['Barney', 'Homer', 'Lisa', 'Marge']
    }

    void "age at most the average age"() {
        given:
        seed()

        when:
        List<AggSubPerson> found = AggSubPerson.where { age <= avg(age) }.list(sort: 'firstName')

        then:
        found*.firstName == ['Bart', 'Lisa']
    }

    void "the subquery can carry its own criteria"() {
        given:
        seed()

        when:
        List<AggSubPerson> found = AggSubPerson.where {
            age > avg(age).of { firstName in ['Bart', 'Lisa'] }
        }.list(sort: 'firstName')

        then: 'the average of Bart (10) and Lisa (8) is 9'
        found*.firstName == ['Barney', 'Bart', 'Homer', 'Marge']
    }

    void "comparisons with the maximum, minimum and sum"() {
        given:
        seed()

        when:
        List<AggSubPerson> equalToMax = AggSubPerson.where { age == max(age) }.list()
        List<AggSubPerson> equalToMin = AggSubPerson.where { age == min(age) }.list()
        List<AggSubPerson> notMax = AggSubPerson.where { age != max(age) }.list(sort: 'firstName')
        List<AggSubPerson> aboveMax = AggSubPerson.where { age > max(age) }.list()
        List<AggSubPerson> belowSum = AggSubPerson.where { age < sum(age) }.list()

        then:
        equalToMax*.firstName == ['Barney']
        equalToMin*.firstName == ['Lisa']
        notMax*.firstName == ['Bart', 'Homer', 'Lisa', 'Marge']
        aboveMax.isEmpty()
        belowSum.size() == 5
    }

    void "a negated comparison against a subquery"() {
        given:
        seed()

        when:
        List<AggSubPerson> found = AggSubPerson.where { !(age > avg(age)) }.list(sort: 'firstName')

        then:
        found*.firstName == ['Bart', 'Lisa']
    }

    void "the subquery runs again each time the query runs"() {
        given:
        seed()
        def query = AggSubPerson.where { age > avg(age) }

        when:
        List<AggSubPerson> before = query.list(sort: 'firstName')
        new AggSubPerson(firstName: 'Old', age: 200).save(flush: true, failOnError: true)
        manager.session.clear()
        List<AggSubPerson> after = query.list(sort: 'firstName')

        then:
        before*.firstName == ['Barney', 'Homer', 'Marge']
        after*.firstName == ['Old']
    }

    void "in and not in against a subquery take every row it returns"() {
        given:
        seed()
        def over40 = AggSubPerson.where { age > 40 }.property('firstName')

        when:
        List<AggSubPerson> inList = new DetachedCriteria(AggSubPerson).build { inList('firstName', over40) }.list(sort: 'firstName')
        List<AggSubPerson> notIn = new DetachedCriteria(AggSubPerson).build { notIn('firstName', over40) }.list(sort: 'firstName')

        then:
        inList*.firstName == ['Barney', 'Homer']
        notIn*.firstName == ['Bart', 'Lisa', 'Marge']
    }

    void "a single result with a subquery in an in uses the values the subquery returns"() {
        given:
        seed()
        def over46 = AggSubPerson.where { age > 46 }.property('firstName')

        when:
        AggSubPerson found = new DetachedCriteria(AggSubPerson).build { inList('firstName', over46) }.get()
        AggSubPerson none = new DetachedCriteria(AggSubPerson).build { inList('firstName', AggSubPerson.where { age > 100 }.property('firstName')) }.get()

        then:
        found.firstName == 'Barney'
        none == null
    }

    void "a comparison against a subquery that returns no row matches no row"() {
        given:
        seed()
        new AggSubPerson(firstName: 'Nobody').save(flush: true, failOnError: true)
        manager.session.clear()

        when:
        List<AggSubPerson> greater = AggSubPerson.where { age > avg(age).of { firstName == 'Absent' } }.list()
        List<AggSubPerson> equal = AggSubPerson.where { age == max(age).of { firstName == 'Absent' } }.list()
        List<AggSubPerson> notEqual = AggSubPerson.where { age != max(age).of { firstName == 'Absent' } }.list()

        then: 'not the rows whose age is null, which an equality with null would have matched'
        greater.isEmpty()
        equal.isEmpty()
        notEqual.isEmpty()
    }

    void "a subquery inside an association block is run"() {
        given:
        new AggSubCity(name: 'Paris').save(flush: true, failOnError: true)
        new AggSubPerson(firstName: 'Amelie', address: new AggSubAddress(city: 'Paris')).save(flush: true, failOnError: true)
        new AggSubPerson(firstName: 'Bob', address: new AggSubAddress(city: 'Rome')).save(flush: true, failOnError: true)
        manager.session.clear()
        def names = AggSubCity.where { name == 'Paris' }.property('name')

        when:
        List<AggSubPerson> found = new DetachedCriteria(AggSubPerson).build { address { inList('city', names) } }.list()
        List<AggSubPerson> excluded = new DetachedCriteria(AggSubPerson).build { address { notIn('city', names) } }.list()

        then:
        found*.firstName == ['Amelie']
        excluded*.firstName == ['Bob']
    }

    void "a comparison against a subquery that returns several rows is rejected"() {
        given:
        seed()
        def everyAge = AggSubPerson.where { age > 0 }.property('age')

        when:
        new DetachedCriteria(AggSubPerson).build { gt('age', everyAge) }.list()

        then:
        InvalidDataAccessResourceUsageException e = thrown()
        e.message.contains("property 'age'")
        e.message.contains('5 rows')
    }

    void "exists is rejected by name rather than ignored"() {
        given:
        seed()
        def over40 = AggSubPerson.where { age > 40 }.property('age')

        when:
        new DetachedCriteria(AggSubPerson).build { exists(over40) }.list()

        then:
        InvalidDataAccessResourceUsageException e = thrown()
        e.message.contains('Exists subquery form is not supported by MongoQuery')
    }
}

@Entity
class AggSubPerson {
    String firstName
    Integer age
    AggSubAddress address

    static embedded = ['address']
}

class AggSubAddress {
    String city
}

@Entity
class AggSubCity {
    String name
}
