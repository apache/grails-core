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
package org.grails.datastore.gorm.mongo

import com.mongodb.MongoQueryException
import com.mongodb.ReadConcern
import com.mongodb.WriteConcern
import grails.gorm.CriteriaBuilder
import grails.gorm.DetachedCriteria
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.datastore.mapping.mongo.MongoCodecSession
import spock.lang.Ignore

/**
 * Created by graemerocher on 03/02/2017.
 */
class ReadConcernArgumentSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses += [grails.gorm.tests.Person]
    }

    @Ignore
    void "Test that read concern work on criteria queries"() {
        when: "A criteria query is created with a hint"
        CriteriaBuilder c = grails.gorm.tests.Person.createCriteria()
        c.list {
            eq 'firstName', 'Bob'
            arguments readConcern: ReadConcern.MAJORITY
        }

        then: "The query contains the hint"
        MongoQueryException exception = thrown()
        exception.message.contains('Query failed with error code 148')
        c.query.@queryArguments == [readConcern: ReadConcern.MAJORITY]

        when: "A dynamic finder uses a hint"
        def results = grails.gorm.tests.Person.findAllByFirstName("Bob", [readConcern: ReadConcern.MAJORITY])

        then: "The read concern is used"
        MongoQueryException exception2 = thrown()
        exception2.message.contains('Query failed with error code 148')
    }

    @Ignore
    void "Test that hints work on detached criteria queries"() {
        when: "A criteria query is created with a hint"
        DetachedCriteria<grails.gorm.tests.Person> detachedCriteria = new DetachedCriteria<>(grails.gorm.tests.Person)
        detachedCriteria = detachedCriteria.build {
            eq 'firstName', 'Bob'
        }

        def results = detachedCriteria.list(readConcern: ReadConcern.MAJORITY)
        for (e in results) {
        } // just to trigger the query
        then: "The hint is used"
        MongoQueryException exception2 = thrown()
        exception2.message.contains('Query failed with error code 148')
    }

    void "Test save with write concern"() {
        when:
        grails.gorm.tests.Person.withSession { MongoCodecSession session ->
            new grails.gorm.tests.Person(firstName: "Bob", lastName: "Smith").save(validate: false)
            session.flush(WriteConcern.MAJORITY)
        }

        then:
        grails.gorm.tests.Person.count() == 1

    }
}
