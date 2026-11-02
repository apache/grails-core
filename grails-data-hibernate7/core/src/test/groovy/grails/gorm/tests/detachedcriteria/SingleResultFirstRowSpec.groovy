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
package grails.gorm.tests.detachedcriteria

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import grails.gorm.tests.HibernateGormDatastoreSpec

class SingleResultFirstRowSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.registerDomainClasses(FirstRowEntity)
    }

    private void seed() {
        (1..3).each { new FirstRowEntity(name: "n${it}", amount: it).save(flush: true) }
    }

    void "a criteria get returns the first row when several match"() {
        given:
        seed()

        when:
        FirstRowEntity ascending = FirstRowEntity.createCriteria().get {
            gt('amount', 0)
            order('amount', 'asc')
        }
        FirstRowEntity descending = FirstRowEntity.createCriteria().get {
            gt('amount', 0)
            order('amount', 'desc')
        }

        then:
        ascending.name == 'n1'
        descending.name == 'n3'
    }

    void "a where query get and find return the first row when several match"() {
        given:
        seed()

        when:
        FirstRowEntity got = FirstRowEntity.where { amount > 0 }.order('amount', 'desc').get()
        FirstRowEntity found = FirstRowEntity.where { amount > 0 }.order('amount', 'asc').find()

        then:
        got.name == 'n3'
        found.name == 'n1'
    }

    void "get and find return null when nothing matches"() {
        given:
        seed()

        when:
        FirstRowEntity got = FirstRowEntity.where { amount > 99 }.get()

        then:
        got == null
    }
}

@Entity
class FirstRowEntity implements HibernateEntity<FirstRowEntity> {
    String name
    Integer amount
}
