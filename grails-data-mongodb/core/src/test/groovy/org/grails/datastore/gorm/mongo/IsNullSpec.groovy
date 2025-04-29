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

import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.Issue

class IsNullSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([Elephant, Trunk])
    }

    @Issue('GPMONGODB-164')
    void "Test isNull works in a criteria query"() {
        given: "Some test data"
        new Elephant(name: "Dumbo").save(validate: false)
        new Elephant(name: "Big Daddy", trunk: new Trunk(length: 10).save()).save(flush: true, validate: false)
        manager.session.clear()

        when: "A entity is queried with isNull"
        def results = Elephant.withCriteria {
            isNull 'trunk'
        }

        then: "The correct results are returned"
        results.size() == 1
        results[0].name == "Dumbo"

        when: "A entity is queried with isNotNull"
        results = Elephant.withCriteria {
            isNotNull 'trunk'
        }

        then: "The correct results are returned"
        results.size() == 1
        results[0].name == "Big Daddy"
    }

    @Issue('GPMONGODB-164')
    void "Test isNull works in a dynamic finder"() {
        given: "Some test data"
        new Elephant(name: "Dumbo").save(validate: false)
        new Elephant(name: "Big Daddy", trunk: new Trunk(length: 10).save()).save(flush: true, validate: false)
        manager.session.clear()

        when: "A entity is queried with isNull"
        def results = Elephant.findAllByTrunkIsNull()

        then: "The correct results are returned"
        results.size() == 1
        results[0].name == "Dumbo"

        when: "A entity is queried with isNotNull"
        results = Elephant.findAllByTrunkIsNotNull()

        then: "The correct results are returned"
        results.size() == 1
        results[0].name == "Big Daddy"
    }
}

@Entity
class Elephant {
    Long id
    String name
    Trunk trunk
    static mapping = {
        trunk nullable: true
    }
}

@Entity
class Trunk {
    Long id
    int length
}
