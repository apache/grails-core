/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.gorm.specs.hibernatequery

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.apache.grails.data.testing.tck.domains.Person
import org.apache.grails.data.testing.tck.domains.Pet
import org.grails.datastore.mapping.query.AssociationQuery
import org.grails.datastore.mapping.query.Query
import org.grails.orm.hibernate.HibernateSession
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.query.HibernateAssociationQuery
import org.grails.orm.hibernate.query.HibernateQuery

class HibernateAssociationQuerySpec extends HibernateGormDatastoreSpec {

    HibernateQuery personQuery
    Person bob

    def setupSpec() {
        manager.addAllDomainClasses([Person, Pet])
    }

    def setup() {
        HibernateDatastore hibernateDatastore = manager.hibernateDatastore
        HibernateSession session = hibernateDatastore.connect() as HibernateSession
        personQuery = new HibernateQuery(session, hibernateDatastore.getMappingContext().getPersistentEntity(Person.typeName))
        bob = new Person(firstName: 'Bob', lastName: 'Builder', age: 50).save(flush: true)
        new Pet(name: 'Lucky', age: 3, owner: bob).save(flush: true)
        new Pet(name: 'Rex', age: 7, owner: bob).save(flush: true)
        def alice = new Person(firstName: 'Alice', lastName: 'Smith', age: 30).save(flush: true)
        new Pet(name: 'Whiskers', age: 2, owner: alice).save(flush: true)
        session.flush()
    }

    def 'createQuery returns a HibernateAssociationQuery for an association property'() {
        when:
        def assocQuery = personQuery.createQuery('pets')

        then:
        assocQuery instanceof HibernateAssociationQuery
        assocQuery instanceof AssociationQuery
        assocQuery.getEntity() != null
        assocQuery.getAssociation() != null
        assocQuery.getAssociation().getName() == 'pets'
    }

    def 'HibernateAssociationQuery collects eq criteria added via add()'() {
        given:
        def assocQuery = personQuery.createQuery('pets') as HibernateAssociationQuery

        when:
        assocQuery.add(new Query.Equals('name', 'Lucky'))

        then:
        assocQuery.getAssociationCriteria().size() == 1
        assocQuery.getAssociationCriteria()[0] instanceof Query.Equals
    }

    def 'HibernateAssociationQuery collects multiple criteria'() {
        given:
        def assocQuery = personQuery.createQuery('pets') as HibernateAssociationQuery

        when:
        assocQuery.add(new Query.Equals('name', 'Lucky'))
        assocQuery.add(new Query.GreaterThan('age', 1))

        then:
        assocQuery.getAssociationCriteria().size() == 2
    }

    def 'HibernateAssociationQuery supports disjunction'() {
        given:
        def assocQuery = personQuery.createQuery('pets') as HibernateAssociationQuery

        when:
        def disj = assocQuery.disjunction()
        assocQuery.add(disj, new Query.Equals('name', 'Lucky'))
        assocQuery.add(disj, new Query.Equals('name', 'Rex'))

        then: 'criteria list contains a Disjunction with both inner criteria'
        def allCriteria = assocQuery.getAssociationCriteria()
        def found = allCriteria.find { it instanceof Query.Disjunction } as Query.Disjunction
        found != null
        found.criteria.size() == 2
    }

    // --- DSL integration tests via withCriteria ---

    def 'withCriteria on association with eq filter returns correct results'() {
        when:
        def results = Person.withCriteria {
            pets {
                eq 'name', 'Lucky'
            }
        }

        then:
        results.size() == 1
        results[0].firstName == 'Bob'
    }

    def 'withCriteria on association with no matching criteria returns empty'() {
        when:
        def results = Person.withCriteria {
            pets {
                eq 'name', 'NoSuchPet'
            }
        }

        then:
        results.isEmpty()
    }

    def 'withCriteria on association filters by multiple criteria'() {
        when:
        def results = Person.withCriteria {
            pets {
                eq 'name', 'Rex'
                gt 'age', 5
            }
        }

        then:
        results.size() == 1
        results[0].firstName == 'Bob'
    }

    def 'withCriteria on association with disjunction returns both matching owners'() {
        when:
        def results = Person.withCriteria {
            pets {
                or {
                    eq 'name', 'Lucky'
                    eq 'name', 'Whiskers'
                }
            }
        } as List<Person>

        then:
        results.size() == 2
        results*.firstName.toSet() == ['Bob', 'Alice'].toSet()
    }

    def 'HibernateAssociationQuery supports negation'() {
        given:
        def assocQuery = personQuery.createQuery('pets') as HibernateAssociationQuery

        when:
        def neg = assocQuery.negation()

        then: 'negation returns a non-null Negation junction'
        neg instanceof Query.Negation
    }
}
