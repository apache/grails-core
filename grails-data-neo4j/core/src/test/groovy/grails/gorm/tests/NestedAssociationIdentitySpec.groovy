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

import grails.gorm.annotation.Entity
import org.apache.grails.data.neo4j.core.Neo4jGormDatastoreSpec
import grails.neo4j.mapping.MappingBuilder
import spock.lang.Issue

/**
 * A comparison with an association inside an association block tests the node the association leads to, so it
 * uses the identity of the associated entity, not the one of the entity that owns the association.
 */
@Issue('https://github.com/apache/grails-core/pull/16836')
class NestedAssociationIdentitySpec extends Neo4jGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(IdMapOrder, IdMapCustomer, IdMapAccount)
    }

    private Map seed() {
        IdMapAccount gold = new IdMapAccount(label: 'gold').save(flush: true, failOnError: true)
        IdMapAccount basic = new IdMapAccount(label: 'basic').save(flush: true, failOnError: true)
        IdMapCustomer goldCustomer = new IdMapCustomer(name: 'g', account: gold).save(flush: true, failOnError: true)
        IdMapCustomer basicCustomer = new IdMapCustomer(name: 'b', account: basic).save(flush: true, failOnError: true)
        new IdMapOrder(ref: 'o-gold', customer: goldCustomer).save(flush: true, failOnError: true)
        new IdMapOrder(ref: 'o-basic', customer: basicCustomer).save(flush: true, failOnError: true)
        IdMapOrder.withSession { it.clear() }
        [gold: gold, basic: basic]
    }

    void "an equality on an association in a block uses the identity of the associated entity"() {
        given: 'a customer with a snowflake id, whose account has a native id'
        Map accounts = seed()

        when:
        List<IdMapOrder> orders = IdMapOrder.createCriteria().list {
            customer {
                eq('account', accounts.gold)
            }
        }

        then:
        orders*.ref == ['o-gold']
    }

    void "a not-equal comparison on an association in a block uses the identity of the associated entity"() {
        given:
        Map accounts = seed()

        when:
        List<IdMapOrder> orders = IdMapOrder.createCriteria().list {
            customer {
                ne('account', accounts.gold)
            }
        }

        then:
        orders*.ref == ['o-basic']
    }

    void "the same comparison on the entity itself still finds the row"() {
        given:
        Map accounts = seed()

        expect:
        IdMapCustomer.createCriteria().list { eq('account', accounts.basic) }*.name == ['b']
    }
}

@Entity
class IdMapAccount {
    String label
}

@Entity
class IdMapCustomer {
    String name
    IdMapAccount account

    static mapping = MappingBuilder.node {
        id(generator: 'snowflake')
    }
}

@Entity
class IdMapOrder {
    String ref
    IdMapCustomer customer
}
