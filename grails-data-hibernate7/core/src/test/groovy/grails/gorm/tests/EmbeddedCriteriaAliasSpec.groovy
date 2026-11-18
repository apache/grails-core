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
 *    Unless required by applicable law or agreed to in writing,
 *    software distributed under the License is distributed on an
 *    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *    KIND, either express or implied.  See the License for the
 *    specific language governing permissions and limitations
 *    under the License.
 */
package grails.gorm.tests

import grails.gorm.annotation.Entity

class EmbeddedCriteriaAliasSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(EmbeddedCriteriaAliasOwner)
    }

    void 'plain embedded components support dotted criteria with nested junction #nestedJunction and counts and pagination'() {
        given:
        new EmbeddedCriteriaAliasOwner(name: 'A', address: new EmbeddedCriteriaAliasAddress(
                geo: new EmbeddedCriteriaAliasGeo(city: 'London'))).save(failOnError: true)
        new EmbeddedCriteriaAliasOwner(name: 'B', address: new EmbeddedCriteriaAliasAddress(
                geo: new EmbeddedCriteriaAliasGeo(city: 'London'))).save(failOnError: true)
        new EmbeddedCriteriaAliasOwner(name: 'C', address: new EmbeddedCriteriaAliasAddress(
                geo: new EmbeddedCriteriaAliasGeo(city: 'Paris'))).save(flush: true, failOnError: true)
        def criteria = {
            address {
                if (nestedJunction) {
                    and {
                        eq('geo.city', 'London')
                    }
                } else {
                    eq('geo.city', 'London')
                }
            }
        }

        expect:
        EmbeddedCriteriaAliasOwner.withCriteria(criteria)*.name.toSet() == ['A', 'B'].toSet()
        EmbeddedCriteriaAliasOwner.createCriteria().count(criteria) == 2L

        when:
        def page = EmbeddedCriteriaAliasOwner.createCriteria().list(max: 1, sort: 'name', order: 'asc', criteria)

        then:
        page*.name == ['A']
        page.totalCount == 2L

        where:
        nestedJunction << [false, true]
    }
}

@Entity
class EmbeddedCriteriaAliasOwner {
    String name
    EmbeddedCriteriaAliasAddress address

    static embedded = ['address']
}

class EmbeddedCriteriaAliasAddress {
    EmbeddedCriteriaAliasGeo geo

    static embedded = ['geo']
}

class EmbeddedCriteriaAliasGeo {
    String city
}