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
        new AggSubNode(firstName: 'Nobody').save(flush: true, failOnError: true)
        manager.session.clear()

        when: 'the age of the person whose age is not set is one null'
        List<AggSubNode> greater = AggSubNode.createCriteria().list {
            gt('age', AggSubNode.where { firstName == 'Nobody' }.property('age'))
        }
        List<AggSubNode> equal = AggSubNode.createCriteria().list {
            eq('age', AggSubNode.where { firstName == 'Nobody' }.property('age'))
        }
        List<AggSubNode> notEqual = AggSubNode.createCriteria().list {
            ne('age', AggSubNode.where { firstName == 'Nobody' }.property('age'))
        }
        List<AggSubNode> negated = AggSubNode.createCriteria().list {
            not { gt('age', AggSubNode.where { firstName == 'Nobody' }.property('age')) }
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

    void "an association compared with an instance is compared as that association"() {
        given:
        AggSubNode homer = new AggSubNode(firstName: 'Homer', age: 45).save(failOnError: true)
        AggSubNode bart = new AggSubNode(firstName: 'Bart', age: 10).save(failOnError: true)
        new AggSubPet(name: 'Rex', owner: homer).save(failOnError: true)
        new AggSubPet(name: 'Santa', owner: homer).save(failOnError: true)
        new AggSubPet(name: 'Fido', owner: bart).save(failOnError: true)
        manager.session.flush()
        manager.session.clear()

        when:
        List<AggSubPet> found = AggSubPet.where { owner == homer }.list(sort: 'name')

        then:
        found*.name == ['Rex', 'Santa']
    }

    void "an association compared with a subquery that returns an association is compared as that association"() {
        given: 'two pets share one owner, and a third has another'
        AggSubNode homer = new AggSubNode(firstName: 'Homer', age: 45).save(failOnError: true)
        AggSubNode bart = new AggSubNode(firstName: 'Bart', age: 10).save(failOnError: true)
        new AggSubPet(name: 'Rex', owner: homer).save(failOnError: true)
        new AggSubPet(name: 'Santa', owner: homer).save(failOnError: true)
        new AggSubPet(name: 'Fido', owner: bart).save(failOnError: true)
        manager.session.flush()
        manager.session.clear()

        when:
        List<AggSubPet> found = AggSubPet.where {
            owner == AggSubPet.where { name == 'Rex' }.property('owner')
        }.list(sort: 'name')

        then: 'the pets of the owner of Rex'
        found*.name == ['Rex', 'Santa']
    }

    void "a negated list of values from a subquery with a null in it matches no row, as in SQL"() {
        given:
        new AggSubNode(firstName: 'Homer', age: 45).save(failOnError: true)
        new AggSubNode(firstName: 'Bart', age: 10).save(failOnError: true)
        new AggSubNode(firstName: 'Maggie').save(failOnError: true)
        manager.session.flush()
        manager.session.clear()

        when:
        List<AggSubNode> negated = AggSubNode.createCriteria().list {
            not { inList('age', AggSubNode.where { firstName in ['Homer', 'Maggie'] }.property('age')) }
        }
        List<AggSubNode> twice = AggSubNode.createCriteria().list(sort: 'firstName') {
            not { not { inList('age', AggSubNode.where { firstName in ['Homer', 'Maggie'] }.property('age')) } }
        }

        then: 'a null is unknown, so the negation is unknown for the rows that are not in the list, and so is its negation'
        negated.isEmpty()
        twice*.firstName == ['Homer']
    }

    void "a list of values from a subquery with a null in it matches the values that are not null"() {
        given:
        new AggSubNode(firstName: 'Homer', age: 45).save(failOnError: true)
        new AggSubNode(firstName: 'Bart', age: 10).save(failOnError: true)
        new AggSubNode(firstName: 'Maggie').save(failOnError: true)
        manager.session.flush()
        manager.session.clear()

        when:
        List<AggSubNode> found = AggSubNode.where {
            age in AggSubNode.where { firstName in ['Homer', 'Maggie'] }.property('age')
        }.list(sort: 'firstName')

        then: 'the age of Maggie is null, which no node has'
        found*.firstName == ['Homer']
    }

    void "deleteAll of a where query with a != against a subquery deletes the rows the read finds"() {
        given:
        seed()

        when: 'every age but the highest, as the list of the same query reads'
        long deleted = AggSubNode.where { age != max(age) }.deleteAll()

        then: 'the rows that match are deleted and the highest age stays, and the count says what was deleted'
        deleted == 4
        AggSubNode.list()*.firstName == ['Barney']
    }

    void "updateAll of a where query with a comparison against a subquery updates the rows the read finds"() {
        given:
        seed()

        when:
        long above = AggSubNode.where { age > avg(age) }.updateAll(age: 99)
        List<AggSubNode> updated = AggSubNode.where { age == 99 }.list(sort: 'firstName')

        then: 'the ages above the average of 30 are the three that changed'
        above == 3
        updated*.firstName == ['Barney', 'Homer', 'Marge']
    }

    void "updateAll of a where query with an equality against the maximum updates the rows that have it"() {
        given:
        seed()

        when:
        long changed = AggSubNode.where { age == max(age) }.updateAll(age: 99)
        List<AggSubNode> updated = AggSubNode.where { age == 99 }.list()

        then:
        changed == 1
        updated*.firstName == ['Barney']
    }
}

@Entity
class AggSubNode {
    String firstName
    Integer age

    static constraints = {
        age(nullable: true)
    }
}

@Entity
class AggSubPet {
    String name
    AggSubNode owner
}
