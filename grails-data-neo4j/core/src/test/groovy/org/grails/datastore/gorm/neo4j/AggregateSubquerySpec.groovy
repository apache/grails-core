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
package org.grails.datastore.gorm.neo4j

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import org.apache.grails.data.neo4j.core.Neo4jGormDatastoreSpec
import org.springframework.dao.InvalidDataAccessResourceUsageException

class AggregateSubquerySpec extends Neo4jGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(AggSubNode, AggSubPet)
    }

    private void seed() {
        [['Homer', 45], ['Marge', 40], ['Bart', 10], ['Lisa', 8], ['Barney', 47]].each {
            new AggSubNode(firstName: it[0], age: it[1]).save(failOnError: true)
        }
        manager.session.flush()
        manager.session.clear()
    }

    void "age greater than the average age"() {
        given:
        seed()

        when:
        List<AggSubNode> found = AggSubNode.where { age > avg(age) }.list(sort: 'firstName')

        then:
        found*.firstName == ['Barney', 'Homer', 'Marge']
    }

    void "a comparison against a subquery inside an or"() {
        given:
        seed()

        when:
        List<AggSubNode> found = AggSubNode.where { age > avg(age) || firstName == 'Lisa' }.list(sort: 'firstName')

        then:
        found*.firstName == ['Barney', 'Homer', 'Lisa', 'Marge']
    }

    void "age at most the average age"() {
        given:
        seed()

        when:
        List<AggSubNode> found = AggSubNode.where { age <= avg(age) }.list(sort: 'firstName')

        then:
        found*.firstName == ['Bart', 'Lisa']
    }

    void "the subquery can carry its own criteria"() {
        given:
        seed()

        when:
        List<AggSubNode> found = AggSubNode.where {
            age > avg(age).of { firstName in ['Bart', 'Lisa'] }
        }.list(sort: 'firstName')

        then: 'the average of Bart (10) and Lisa (8) is 9'
        found*.firstName == ['Barney', 'Bart', 'Homer', 'Marge']
    }

    void "comparisons with the maximum and minimum"() {
        given:
        seed()

        when:
        List<AggSubNode> equalToMax = AggSubNode.where { age == max(age) }.list()
        List<AggSubNode> equalToMin = AggSubNode.where { age == min(age) }.list()

        then:
        equalToMax*.firstName == ['Barney']
        equalToMin*.firstName == ['Lisa']
    }

    void "in and not in against a subquery take every row it returns"() {
        given:
        seed()
        def over40 = AggSubNode.where { age > 40 }.property('firstName')

        when:
        List<AggSubNode> inList = new DetachedCriteria(AggSubNode).build { inList('firstName', over40) }.list(sort: 'firstName')
        List<AggSubNode> notIn = new DetachedCriteria(AggSubNode).build { notIn('firstName', over40) }.list(sort: 'firstName')

        then:
        inList*.firstName == ['Barney', 'Homer']
        notIn*.firstName == ['Bart', 'Lisa', 'Marge']
    }

    void "the subquery runs again each time the query runs"() {
        given:
        seed()
        def query = AggSubNode.where { age > avg(age) }

        when:
        List<AggSubNode> before = query.list(sort: 'firstName')
        new AggSubNode(firstName: 'Old', age: 200).save(failOnError: true)
        manager.session.flush()
        manager.session.clear()
        List<AggSubNode> after = query.list(sort: 'firstName')

        then:
        before*.firstName == ['Barney', 'Homer', 'Marge']
        after*.firstName == ['Old']
    }

    void "a comparison against a subquery that returns no row matches no row"() {
        given:
        seed()

        when:
        List<AggSubNode> greater = AggSubNode.where { age > avg(age).of { firstName == 'Absent' } }.list()
        List<AggSubNode> equal = AggSubNode.where { age == max(age).of { firstName == 'Absent' } }.list()
        List<AggSubNode> notEqual = AggSubNode.where { age != max(age).of { firstName == 'Absent' } }.list()

        then:
        greater.isEmpty()
        equal.isEmpty()
        notEqual.isEmpty()
    }

    void "a negated comparison against a subquery that returns no row matches no row, as in SQL"() {
        given:
        seed()
        new AggSubNode(firstName: 'Nobody').save(failOnError: true)
        manager.session.flush()
        manager.session.clear()

        when:
        List<AggSubNode> negated = AggSubNode.where { !(age > avg(age).of { firstName == 'Absent' }) }.list()
        List<AggSubNode> notBlock = AggSubNode.createCriteria().list {
            not { gt('age', AggSubNode.where { firstName == 'Absent' }.property('age')) }
        }
        List<AggSubNode> twice = AggSubNode.createCriteria().list {
            not { not { gt('age', AggSubNode.where { firstName == 'Absent' }.property('age')) } }
        }
        List<AggSubNode> inNegatedOr = AggSubNode.where { !(age > avg(age).of { firstName == 'Absent' } || firstName == 'Bart') }.list()
        List<AggSubNode> inNegatedAnd = AggSubNode.where { !(age > avg(age).of { firstName == 'Absent' } && firstName == 'Bart') }.list(sort: 'firstName')

        then: 'the comparison is unknown, so its negation is unknown too, and the rows follow SQL three-valued logic'
        negated.isEmpty()
        notBlock.isEmpty()
        twice.isEmpty()
        inNegatedOr.isEmpty()
        inNegatedAnd*.firstName == ['Barney', 'Homer', 'Lisa', 'Marge', 'Nobody']
    }

    void "a comparison against a subquery that returns one null matches no row, as with no row"() {
        given:
        seed()
        new AggSubPerson(firstName: 'Nobody').save(flush: true, failOnError: true)
        manager.session.clear()

        when: 'the age of the person whose age is not set is one null'
        List<AggSubPerson> greater = AggSubPerson.createCriteria().list {
            gt('age', AggSubPerson.where { firstName == 'Nobody' }.property('age'))
        }
        List<AggSubPerson> equal = AggSubPerson.createCriteria().list {
            eq('age', AggSubPerson.where { firstName == 'Nobody' }.property('age'))
        }
        List<AggSubPerson> notEqual = AggSubPerson.createCriteria().list {
            ne('age', AggSubPerson.where { firstName == 'Nobody' }.property('age'))
        }
        List<AggSubPerson> negated = AggSubPerson.createCriteria().list {
            not { gt('age', AggSubPerson.where { firstName == 'Nobody' }.property('age')) }
        }

        then: 'not the rows whose age is missing, which a null test would have matched'
        greater.isEmpty()
        equal.isEmpty()
        notEqual.isEmpty()
        negated.isEmpty()
    }

    void "a subquery inside an association block is run"() {
        given:
        seed()
        def barney = AggSubNode.findByFirstName('Barney')
        def lisa = AggSubNode.findByFirstName('Lisa')
        new AggSubPet(name: 'Rex', owner: barney).save(failOnError: true)
        new AggSubPet(name: 'Tom', owner: lisa).save(failOnError: true)
        manager.session.flush()
        manager.session.clear()
        def over46 = AggSubNode.where { age > 46 }.property('firstName')

        when:
        List<AggSubPet> found = new DetachedCriteria(AggSubPet).build { owner { inList('firstName', over46) } }.list()

        then:
        found*.name == ['Rex']
    }

    void "a comparison against a subquery that returns several rows is rejected"() {
        given:
        seed()
        def everyAge = AggSubNode.where { age > 0 }.property('age')

        when:
        new DetachedCriteria(AggSubNode).build { gt('age', everyAge) }.list()

        then:
        InvalidDataAccessResourceUsageException e = thrown()
        e.message.contains("property 'age'")
        e.message.contains('5 rows')
    }

    void "exists is rejected by name rather than ignored"() {
        given:
        seed()
        def over40 = AggSubNode.where { age > 40 }.property('age')

        when:
        new DetachedCriteria(AggSubNode).build { exists(over40) }.list()

        then:
        InvalidDataAccessResourceUsageException e = thrown()
        e.message.contains('Exists subquery form is not supported by Neo4jQuery')
    }
}

@Entity
class AggSubNode {
    String firstName
    Integer age
}

@Entity
class AggSubPet {
    String name
    AggSubNode owner
}
