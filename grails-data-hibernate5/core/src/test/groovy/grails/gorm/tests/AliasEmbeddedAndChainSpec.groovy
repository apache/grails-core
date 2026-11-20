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

import org.hibernate.QueryException
import org.hibernate.SessionFactory
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import grails.orm.HibernateCriteriaBuilder
import org.grails.orm.hibernate.HibernateDatastore

/**
 * A path that createAlias is given may go through an embedded component to an association inside it, whether the
 * builder was made with or without a datastore and whether the call is made on the query or inside an association
 * block, and an alias of a join that another alias names may be used to join further.
 */
@Rollback
class AliasEmbeddedAndChainSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(AcCountry, AcClub, AcOwner, AcPet, AcShelf)
    @Shared PlatformTransactionManager transactionManager = datastore.transactionManager

    void setup() {
        AcCountry c1 = new AcCountry(name: 'C1').save(failOnError: true)
        AcCountry c2 = new AcCountry(name: 'C2').save(failOnError: true)
        AcClub k1 = new AcClub(name: 'K1').save(failOnError: true)
        AcClub k2 = new AcClub(name: 'K2').save(failOnError: true)
        AcOwner a = new AcOwner(name: 'A', club: k1).save(failOnError: true)
        AcOwner b = new AcOwner(name: 'B', club: k2).save(failOnError: true)
        AcPet ann = new AcPet(name: 'Ann', owner: a, address: new AcAddress(street: 's1', country: c1)).save(failOnError: true)
        AcPet bob = new AcPet(name: 'Bob', owner: b, address: new AcAddress(street: 's2', country: c2)).save(failOnError: true)
        new AcShelf(name: 'one', pet: ann).save(failOnError: true)
        new AcShelf(name: 'two', pet: bob).save(flush: true, failOnError: true)
        datastore.sessionFactory.currentSession.clear()
    }

    void cleanup() {
        AcShelf.withNewTransaction {
            AcShelf.executeUpdate('delete from AcShelf')
            AcPet.executeUpdate('delete from AcPet')
            AcOwner.executeUpdate('delete from AcOwner')
            AcClub.executeUpdate('delete from AcClub')
            AcCountry.executeUpdate('delete from AcCountry')
        }
    }

    void 'a path through an embedded component, on a builder made without a datastore'() {
        given:
        SessionFactory sessionFactory = datastore.sessionFactory

        when:
        def found = new HibernateCriteriaBuilder(AcPet, sessionFactory).list {
            createAlias('address.country', 'c')
            eq('c.name', 'C1')
        }

        then:
        found*.name == ['Ann']
    }

    void 'a path through an embedded component, inside an association block'() {
        expect:
        AcShelf.withCriteria {
            pet {
                createAlias('address.country', 'c')
                eq('c.name', 'C1')
            }
        }*.name == ['one']
    }

    void 'a second alias that is declared for a path that has one is refused, as Hibernate 5 joins a path once'() {
        when:
        AcPet.withCriteria {
            createAlias('owner', 'o')
            createAlias('owner', 'o2')
            createAlias('o2.club', 'c')
            eq('c.name', 'K1')
        }

        then:
        QueryException e = thrown()
        e.message.contains('duplicate association path')
    }

    void 'a second alias inside a block is refused the same way'() {
        when:
        AcPet.withCriteria {
            createAlias('owner', 'o')
            createAlias('owner', 'ownerAgain')
            eq('ownerAgain.name', 'A')
        }

        then:
        thrown(QueryException)
    }
}

@Entity
class AcCountry {
    String name
}

@Entity
class AcClub {
    String name
}

@Entity
class AcOwner {
    String name
    AcClub club
}

class AcAddress {
    String street
    AcCountry country
}

@Entity
class AcPet {
    String name
    AcOwner owner
    AcAddress address
    static embedded = ['address']
}

@Entity
class AcShelf {
    String name
    AcPet pet
}
