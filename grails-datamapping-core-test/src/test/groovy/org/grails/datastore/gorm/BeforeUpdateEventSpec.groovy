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

package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

import spock.lang.Issue

class BeforeUpdateEventSpec extends GormDatastoreSpec {

    @Issue('GRAILS-8916')
    void "Test beforeUpdate event doesn't cause test failure"() {
        when:"An entity is saved that has a beforeUpdate event"
            BeforeUpdateBook b = new BeforeUpdateBook()
            b.save(failOnError:true)
            BeforeUpdateAuthor a = new BeforeUpdateAuthor()
            a.save(failOnError:true)

            a.book = b
            a.save(failOnError:true)

        then:"The association index is persisted correctly"
            a.id == BeforeUpdateAuthor.findByBook(b).id
    }

    @Issue('GRAILS-8977')
    void "Test beforeUpdate event doesn't cause test failure when setting association to null"() {
        when:"A domain model is created"
            BeforeUpdateBook b = new BeforeUpdateBook()
            b.save(failOnError:true)

            BeforeUpdateAuthor a = new BeforeUpdateAuthor()
            a.save(failOnError:true)

            a.book = b
            a.save(failOnError:true)
        then:"The domain model is valid"
            assert a.id == BeforeUpdateAuthor.findByBook(b).id

        when:"An association is set to null"
            a.book = null
            a.save(failOnError:true)

        then:"It can be queried"
            assert a.id == BeforeUpdateAuthor.findByBookIsNull().id
    }

    @Override
    List getDomainClasses() {
        [BeforeUpdateAuthor, BeforeUpdateBook]
    }
}

@Entity
class BeforeUpdateBook {
    Long id
    static hasMany = [authors:BeforeUpdateAuthor]
}

@Entity
class BeforeUpdateAuthor {
    Long id
    BeforeUpdateBook book

    def beforeUpdate() {}

    static constraints = {
        book(nullable:true)
    }
}
