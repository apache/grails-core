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

import grails.mongodb.MongoEntity
import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.bson.types.ObjectId

/**
 * Created by graemerocher on 22/04/14.
 */
class AggregateMethodSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {
    void setupSpec() {
        manager.domainClasses.addAll([City])
    }

    void "Test aggregate method"() {
        given: "Some test data"
        new City(city: "San Francisco", state: "CA", pop: 3000).save()
        new City(city: "LA", state: "CA", pop: 5000).save()
        new City(city: "Dallas", state: "TX", pop: 4000).save()
        new City(city: "Austin", state: "CA", pop: 15000).save()
        new City(city: "Sacramento", state: "CA", pop: 1000).save(flush: true)
        manager.session.clear()

        when: "An aggregation query is executed"
        def results = City.aggregate([
                ['$match': [pop: ['$gte': 1200]]]
        ])

        then: "The results are correct"
        results.size() == 4
        !results.find { it.city == "Sacramento" }
    }
}

@Entity
class City implements MongoEntity<City> {
    ObjectId id
    String city
    String state
    int pop
}
