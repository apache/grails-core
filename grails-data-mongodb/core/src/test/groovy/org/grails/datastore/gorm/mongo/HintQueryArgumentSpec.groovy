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

import com.mongodb.MongoException
import com.mongodb.MongoQueryException
import grails.gorm.CriteriaBuilder
import grails.gorm.DetachedCriteria
import grails.gorm.tests.Person
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

class HintQueryArgumentSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses += [Person]
    }

    void "Test that hints work on criteria queries"() {
        when: "A criteria query is created with a hint"
        CriteriaBuilder c = Person.createCriteria()
        c.list {
            eq 'firstName', 'Bob'
            arguments hint: ["firstName": 1]
        }

        then: "The query contains the hint"
        c.query.@queryArguments == [hint: ['firstName': 1]]

        when: "A dynamic finder uses a hint"
        def results = Person.findAllByFirstName("Bob", [hint: "firstName"])
        // just to trigger the query
        for (e in results) {
        }

        then: "The hint is used"
        MongoException exception = thrown()
        exception instanceof MongoQueryException
        ((MongoQueryException) exception).message.contains('BadValue')

        when: "A dynamic finder uses a hint"
        results = Person.findAllByFirstName("Bob", [hint: ["firstName": 1]])

        then: "The hint is used"
        results.size() == 0
    }

    void "Test that hints work on detached criteria queries"() {
        when: "A criteria query is created with a hint"
        DetachedCriteria<Person> detachedCriteria = new DetachedCriteria<>(Person)
        detachedCriteria = detachedCriteria.build {
            eq 'firstName', 'Bob'
        }

        def results = detachedCriteria.list(hint: ["firstName": "blah"])
        // just to trigger the query
        for (e in results) {
        }
        then: "The hint is used"
        MongoException exception = thrown()
        exception instanceof MongoQueryException
        exception.message.contains('BadValue')
    }
}
