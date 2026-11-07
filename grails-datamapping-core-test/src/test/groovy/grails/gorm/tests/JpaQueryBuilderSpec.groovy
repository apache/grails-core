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
import org.apache.grails.data.testing.tck.domains.Person
import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.datastore.mapping.query.jpa.JpaQueryBuilder
import org.springframework.dao.InvalidDataAccessResourceUsageException

/**
 * Test for JPA builder
 */
class JpaQueryBuilderSpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {

    void setupSpec() {
        manager.registerDomainClasses(Person)
    }

    void "Test update query with ilike criterion"() {
        given: "Some criteria"
        DetachedCriteria criteria = new DetachedCriteria(Person).build {
            eq 'age', 10
            ilike 'firstName', 'Bob'
        }

        when: "A jpa query is built"
        def builder = new JpaQueryBuilder(manager.session.mappingContext.getPersistentEntity(Person.name), criteria.criteria)
        def queryInfo = builder.buildUpdate(firstName: "Fred")

        then: "The query is valid"
        queryInfo.query == 'UPDATE org.apache.grails.data.testing.tck.domains.Person person SET person.firstName=:p1 WHERE (person.age=:p2 AND lower(person.firstName) like lower(:p3))'
    }

    void "Test update query with subquery"() {
        given: "Some criteria"
        DetachedCriteria criteria = new DetachedCriteria(Person).build {
            notIn("age", new DetachedCriteria(Person).build {
                eq('lastName', 'Simpson')
            }.distinct('age'))
        }

        when: "A jpa query is built"
        def builder = new JpaQueryBuilder(manager.session.mappingContext.getPersistentEntity(Person.name), criteria.criteria)
        def queryInfo = builder.buildUpdate(firstName: "Fred")

        then: "The query is valid"
        queryInfo.query == 'UPDATE org.apache.grails.data.testing.tck.domains.Person person SET person.firstName=:p1 WHERE (person.age NOT IN (SELECT person1.age FROM org.apache.grails.data.testing.tck.domains.Person person1 WHERE person1.lastName=:p2))'
        queryInfo.parameters == ["Fred", "Simpson"]

    }

    void "Test exception is thrown in join with delete"() {
        given: "Some criteria"
        DetachedCriteria criteria = new DetachedCriteria(Person).build {
            pets {
                eq 'name', 'Ted'
            }
            eq 'firstName', 'Bob'
        }

        when: "A jpa query is built"
        def builder = new JpaQueryBuilder(manager.session.mappingContext.getPersistentEntity(Person.name), criteria.criteria)
        builder.buildDelete()

        then: "The query throws an exception"
        def e = thrown(InvalidDataAccessResourceUsageException)
        e.message == 'Joins cannot be used in a DELETE or UPDATE operation'

    }

    void "Test build update property natural ordering and hibernate compatible"() {
        given: "Some criteria"
        DetachedCriteria criteria = new DetachedCriteria(Person).build {
            eq 'firstName', 'Bob'
        }

        when: "A jpa query is built"
        def builder = new JpaQueryBuilder(manager.session.mappingContext.getPersistentEntity(Person.name), criteria.criteria)
        builder.hibernateCompatible = true
        def queryInfo = builder.buildUpdate(firstName: 'Bob updated', age: 30)

        then: "The query is valid"
        queryInfo.query != null
        queryInfo.query == 'UPDATE org.apache.grails.data.testing.tck.domains.Person person SET person.age=:p1, person.firstName=:p2 WHERE (person.firstName=:p3)'
        queryInfo.parameters == [30, "Bob updated", "Bob"]
    }

    void "Test build update property natural ordering"() {
        given: "Some criteria"
        DetachedCriteria criteria = new DetachedCriteria(Person).build {
            eq 'firstName', 'Bob'
        }

        when: "A jpa query is built"
        def builder = new JpaQueryBuilder(manager.session.mappingContext.getPersistentEntity(Person.name), criteria.criteria)
        def queryInfo = builder.buildUpdate(firstName: 'Bob updated', age: 30)

        then: "The query is valid"
        queryInfo.query != null
        queryInfo.query == 'UPDATE org.apache.grails.data.testing.tck.domains.Person person SET person.age=:p1, person.firstName=:p2 WHERE (person.firstName=:p3)'
        queryInfo.parameters == [30, "Bob updated", "Bob"]
    }

    void "Test build update"() {
        given: "Some criteria"
        DetachedCriteria criteria = new DetachedCriteria(Person).build {
            eq 'firstName', 'Bob'
        }

        when: "A jpa query is built"
        def builder = new JpaQueryBuilder(manager.session.mappingContext.getPersistentEntity(Person.name), criteria.criteria)
        def queryInfo = builder.buildUpdate(age: 30)

        then: "The query is valid"
        queryInfo.query != null
        queryInfo.query == 'UPDATE org.apache.grails.data.testing.tck.domains.Person person SET person.age=:p1 WHERE (person.firstName=:p2)'
        queryInfo.parameters == [30, "Bob"]
    }

    void "Test build delete"() {
        given: "Some criteria"
        DetachedCriteria criteria = new DetachedCriteria(Person).build {
            eq 'firstName', 'Bob'
        }

        when: "A jpa query is built"
        def builder = new JpaQueryBuilder(manager.session.mappingContext.getPersistentEntity(Person.name), criteria.criteria)
        def queryInfo = builder.buildDelete()

        then: "The query is valid"
        queryInfo.query != null
        queryInfo.query == 'DELETE FROM org.apache.grails.data.testing.tck.domains.Person person WHERE (person.firstName=:p1)'
        queryInfo.parameters == ["Bob"]
    }

    void "Test build simple select hibernate compatible"() {
        given: "Some criteria"
        DetachedCriteria criteria = new DetachedCriteria(Person).build {
            eq 'firstName', 'Bob'
        }

        when: "A jpa query is built"
        def builder = new JpaQueryBuilder(manager.session.mappingContext.getPersistentEntity(Person.name), criteria.criteria)
        builder.hibernateCompatible = true
        def query = builder.buildSelect().query

        then: "The query is valid"
        query != null
        query == 'SELECT DISTINCT person FROM org.apache.grails.data.testing.tck.domains.Person AS person WHERE (person.firstName=:p1)'
    }

    void "Test build simple select"() {
        given: "Some criteria"
        DetachedCriteria criteria = new DetachedCriteria(Person).build {
            eq 'firstName', 'Bob'
        }

        when: "A jpa query is built"
        def builder = new JpaQueryBuilder(manager.session.mappingContext.getPersistentEntity(Person.name), criteria.criteria)
        def query = builder.buildSelect().query

        then: "The query is valid"
        query != null
        query == 'SELECT DISTINCT person FROM org.apache.grails.data.testing.tck.domains.Person AS person WHERE (person.firstName=:p1)'
    }

    void "Test build select with or"() {
        given: "Some criteria"
        DetachedCriteria criteria = new DetachedCriteria(Person).build {
            or {
                eq 'firstName', 'Bob'
                eq 'firstName', 'Fred'
            }
        }

        when: "A jpa query is built"
        def builder = new JpaQueryBuilder(manager.session.mappingContext.getPersistentEntity(Person.name), criteria.criteria)
        final queryInfo = builder.buildSelect()

        then: "The query is valid"
        queryInfo.query != null
        queryInfo.query == 'SELECT DISTINCT person FROM org.apache.grails.data.testing.tck.domains.Person AS person WHERE ((person.firstName=:p1 OR person.firstName=:p2))'
        queryInfo.parameters == ['Bob', 'Fred']

    }

    void "Test build DELETE with an empty criteria or build {}"() {
        given:
        DetachedCriteria criteria = new DetachedCriteria(Person).build {}

        when: "A jpa query is built"
        def builder = new JpaQueryBuilder(manager.session.mappingContext.getPersistentEntity(Person.name), criteria.criteria)
        final queryInfo = builder.buildDelete()

        then: "The query is valid"
        queryInfo.query != null
        queryInfo.query == 'DELETE FROM org.apache.grails.data.testing.tck.domains.Person person'
        queryInfo.parameters == []
    }


    void "Test build SELECT with an empty criteria or build {}"() {
        given:
        DetachedCriteria criteria = new DetachedCriteria(Person).build {}

        when: "A jpa query is built"
        def builder = new JpaQueryBuilder(manager.session.mappingContext.getPersistentEntity(Person.name), criteria.criteria)
        final queryInfo = builder.buildSelect()

        then: "The query is valid"
        queryInfo.query != null
        queryInfo.query == 'SELECT DISTINCT person FROM org.apache.grails.data.testing.tck.domains.Person AS person'
        queryInfo.parameters == null
    }

    void "Test build UPDATE with an empty criteria or build {}"() {
        given:
        DetachedCriteria criteria = new DetachedCriteria(Person).build {}

        when: "A jpa query is built"
        def builder = new JpaQueryBuilder(manager.session.mappingContext.getPersistentEntity(Person.name), criteria.criteria)
        final queryInfo = builder.buildUpdate(firstName: "Fred")

        then: "The query is valid"
        queryInfo.query != null
        queryInfo.query == 'UPDATE org.apache.grails.data.testing.tck.domains.Person person SET person.firstName=:p1'
        queryInfo.parameters == ["Fred"]
    }

    void "Test build UPDATE with FunctionCallingCriterion ILike, In values, In subquery, and null checks"() {
        given:
        def personEntity = manager.session.mappingContext.getPersistentEntity(Person.name)
        def subCriteria = new DetachedCriteria(Person).build {
            eq('lastName', 'Simpson')
        }.distinct('firstName')

        when: "FunctionCallingCriterion with ILike and multi-value In"
        def criteria1 = [
            new org.grails.datastore.mapping.query.criteria.FunctionCallingCriterion('upper', new org.grails.datastore.mapping.query.Query.ILike('firstName', 'bob%')),
            new org.grails.datastore.mapping.query.criteria.FunctionCallingCriterion('lower', new org.grails.datastore.mapping.query.Query.In('lastName', ['smith', 'jones']))
        ]
        def queryInfo1 = new JpaQueryBuilder(personEntity, criteria1).buildUpdate(age: 40)

        then:
        queryInfo1.query == 'UPDATE org.apache.grails.data.testing.tck.domains.Person person SET person.age=:p1 WHERE (lower(upper(person.firstName)) like lower(:p2) AND lower(person.lastName) IN (:p3,:p4))'
        queryInfo1.parameters == [40, 'bob%', 'smith', 'jones']

        when: "FunctionCallingCriterion with In subquery followed by another criterion"
        def criteria2 = [
            new org.grails.datastore.mapping.query.criteria.FunctionCallingCriterion('upper', new org.grails.datastore.mapping.query.Query.In('firstName', subCriteria)),
            new org.grails.datastore.mapping.query.criteria.FunctionCallingCriterion('upper', new org.grails.datastore.mapping.query.Query.Equals('lastName', 'SMITH'))
        ]
        def queryInfo2 = new JpaQueryBuilder(personEntity, criteria2).buildUpdate(age: 50)

        then:
        queryInfo2.query == 'UPDATE org.apache.grails.data.testing.tck.domains.Person person SET person.age=:p1 WHERE (upper(person.firstName) IN (SELECT person1.firstName FROM org.apache.grails.data.testing.tck.domains.Person person1 WHERE person1.lastName=:p2) AND upper(person.lastName) = :p3)'
        queryInfo2.parameters == [50, 'Simpson', 'SMITH']
    }
}
