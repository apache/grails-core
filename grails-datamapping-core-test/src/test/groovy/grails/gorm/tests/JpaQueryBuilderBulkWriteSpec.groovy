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
package grails.gorm.tests

import grails.gorm.DetachedCriteria
import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.Person
import org.apache.grails.data.testing.tck.domains.Pet
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.jpa.JpaQueryBuilder
import org.springframework.dao.InvalidDataAccessResourceUsageException

/**
 * The HQL a bulk delete or update is built from: subqueries, exists, and to-one associations.
 */
class JpaQueryBuilderBulkWriteSpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {

    private static final String PET = 'org.apache.grails.data.testing.tck.domains.Pet'
    private static final String PERSON = 'org.apache.grails.data.testing.tck.domains.Person'

    void setupSpec() {
        manager.registerDomainClasses(Person, Pet)
    }

    private JpaQueryBuilder builder(Class type, DetachedCriteria criteria) {
        PersistentEntity entity = manager.session.mappingContext.getPersistentEntity(type.name)
        new JpaQueryBuilder(entity, criteria.criteria)
    }

    void "a subquery with several criteria joins them with AND"() {
        given:
        DetachedCriteria between = new DetachedCriteria(Person).build {
            gt 'age', 15
            lt 'age', 35
        }.property('firstName')
        DetachedCriteria criteria = new DetachedCriteria(Person).build { 'in'('firstName', between) }

        when:
        def info = builder(Person, criteria).buildDelete()

        then:
        info.query == "DELETE FROM ${PERSON} person WHERE (person.firstName IN (SELECT person0.firstName FROM ${PERSON} person0 WHERE person0.age > :p1 AND person0.age < :p2))"
        info.parameters == [15, 35]
    }

    void "a subquery without criteria has no WHERE"() {
        given:
        DetachedCriteria all = new DetachedCriteria(Person).property('firstName')
        DetachedCriteria criteria = new DetachedCriteria(Person).build { 'in'('firstName', all) }

        when:
        def info = builder(Person, criteria).buildDelete()

        then:
        info.query == "DELETE FROM ${PERSON} person WHERE (person.firstName IN (SELECT person0.firstName FROM ${PERSON} person0))"
        info.parameters == []
    }

    void "two subqueries keep their own parameters and aliases in an update"() {
        given:
        DetachedCriteria young = new DetachedCriteria(Person).build { lt 'age', 20 }.property('firstName')
        DetachedCriteria notBob = new DetachedCriteria(Person).build { ne 'firstName', 'Bob' }.property('firstName')
        DetachedCriteria criteria = new DetachedCriteria(Person).build {
            'in'('firstName', young)
            'in'('firstName', notBob)
        }

        when:
        def info = builder(Person, criteria).buildUpdate(lastName: 'X')

        then:
        info.query == "UPDATE ${PERSON} person SET person.lastName=:p1 WHERE (person.firstName IN (SELECT person1.firstName FROM ${PERSON} person1 WHERE person1.age < :p2) AND person.firstName IN (SELECT person2.firstName FROM ${PERSON} person2 WHERE person2.firstName != :p3))"
        info.parameters == ['X', 20, 'Bob']
    }

    void "exists writes an EXISTS subquery"() {
        given:
        DetachedCriteria criteria = new DetachedCriteria(Person).build {
            exists(new DetachedCriteria(Pet).build { eq 'name', 'Ted' })
            eq 'firstName', 'Bob'
        }

        when:
        def info = builder(Person, criteria).buildDelete()

        then:
        info.query == "DELETE FROM ${PERSON} person WHERE (EXISTS (SELECT DISTINCT pet0 FROM ${PET} pet0 WHERE pet0.name=:p1) AND person.firstName=:p2)"
        info.parameters == ['Ted', 'Bob']
    }

    void "notExists writes a NOT EXISTS subquery"() {
        given:
        DetachedCriteria criteria = new DetachedCriteria(Person).build {
            notExists(new DetachedCriteria(Pet).build { eq 'name', 'Ted' })
        }

        when:
        def info = builder(Person, criteria).buildUpdate(lastName: 'X')

        then:
        info.query == "UPDATE ${PERSON} person SET person.lastName=:p1 WHERE (NOT EXISTS (SELECT DISTINCT pet1 FROM ${PET} pet1 WHERE pet1.name=:p2))"
        info.parameters == ['X', 'Ted']
    }

    void "a to-one association block becomes an identifier subquery"() {
        given:
        DetachedCriteria criteria = new DetachedCriteria(Pet).build {
            owner { eq 'lastName', 'Simpson' }
            eq 'name', 'Ted'
        }

        when:
        def info = builder(Pet, criteria).buildDelete()

        then:
        info.query == "DELETE FROM ${PET} pet WHERE (pet.owner.id IN (SELECT person0.id FROM ${PERSON} person0 WHERE person0.lastName=:p1) AND pet.name=:p2)"
        info.parameters == ['Simpson', 'Ted']
    }

    void "a dotted path through a to-one association becomes the same subquery"() {
        given:
        DetachedCriteria criteria = new DetachedCriteria(Pet).build {
            eq 'name', 'Ted'
            eq 'owner.lastName', 'Simpson'
            lt 'age', 5
        }

        when:
        def info = builder(Pet, criteria).buildDelete()

        then:
        info.query == "DELETE FROM ${PET} pet WHERE (pet.name=:p1 AND pet.owner.id IN (SELECT person1.id FROM ${PERSON} person1 WHERE person1.lastName=:p2) AND pet.age < :p3)"
        info.parameters == ['Ted', 'Simpson', 5]
    }

    void "an alias of a to-one association resolves the dotted path in an update"() {
        given:
        DetachedCriteria criteria = new DetachedCriteria(Pet).build {
            createAlias('owner', 'o')
            eq 'o.lastName', 'Simpson'
        }

        when:
        def info = builder(Pet, criteria).buildUpdate(name: 'X')

        then:
        info.query.contains("pet.owner.id IN (SELECT person1.id FROM ${PERSON} person1 WHERE person1.lastName=:p2)")
        info.parameters == ['X', 'Simpson']
    }

    void "a dotted path through a to-many association is still refused"() {
        given:
        DetachedCriteria criteria = new DetachedCriteria(Person).build {
            pets { eq 'name', 'Ted' }
        }

        when:
        builder(Person, criteria).buildDelete()

        then:
        def e = thrown(InvalidDataAccessResourceUsageException)
        e.message == 'Joins cannot be used in a DELETE or UPDATE operation'
    }

    void "a dotted property that is not an association is left to the property check"() {
        given:
        DetachedCriteria criteria = new DetachedCriteria(Pet).build { eq 'name.length', 3 }

        when:
        builder(Pet, criteria).buildDelete()

        then:
        def e = thrown(InvalidDataAccessResourceUsageException)
        e.message.contains('non-existent property')
    }

    void "a property path that starts with a dot is not read as an association path"() {
        given:
        DetachedCriteria criteria = new DetachedCriteria(Pet).build { eq '.lastName', 'Simpson' }

        when:
        builder(Pet, criteria).buildDelete()

        then:
        def e = thrown(InvalidDataAccessResourceUsageException)
        e.message.contains('non-existent property: .lastName')
    }

    void "a renderer registered for a criterion type writes it into a bulk statement"() {
        given:
        JpaQueryBuilder.registerHqlRenderer(ShoutCriterion) { Query.Criterion criterion, PersistentEntity entity, String logicalName,
                                                              StringBuilder where, int position, List parameters ->
            where.append(logicalName).append('.name = :p').append(++position)
            parameters.add(((ShoutCriterion) criterion).text.toUpperCase())
            position
        }
        DetachedCriteria criteria = new DetachedCriteria(Pet)
        criteria.add(new ShoutCriterion('ted'))
        criteria.add(new Query.Equals('age', 5))

        when:
        def info = builder(Pet, criteria).buildDelete()

        then:
        info.query == "DELETE FROM ${PET} pet WHERE (pet.name = :p1 AND pet.age=:p2)"
        info.parameters == ['TED', 5]
    }

    void "a criterion type with no handler or renderer is refused by name"() {
        given:
        DetachedCriteria criteria = new DetachedCriteria(Pet)
        criteria.add(new UnknownCriterion())

        when:
        builder(Pet, criteria).buildDelete()

        then:
        def e = thrown(InvalidDataAccessResourceUsageException)
        e.message == 'Queries of type UnknownCriterion are not supported by this implementation'
    }

    static class ShoutCriterion implements Query.Criterion {
        final String text

        ShoutCriterion(String text) {
            this.text = text
        }
    }

    static class UnknownCriterion implements Query.Criterion {
    }
}
