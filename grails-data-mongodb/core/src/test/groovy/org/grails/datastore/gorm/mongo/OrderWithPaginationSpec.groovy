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

import grails.gorm.tests.Plant
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.Issue

/**
 * Created by graemerocher on 14/03/14.
 */
class OrderWithPaginationSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses += [Plant]
    }

    @Issue('GPMONGODB-241')
    void "Test that a criteria query with pagination parameters works correctly"() {
        given: "Some test data"
        new Plant(name: "Lettuce").save()
        new Plant(name: "Eggplant").save()
        new Plant(name: "Cabbage").save()
        new Plant(name: "Kiwi").save()
        new Plant(name: "Tomato").save(flush: true)
        manager.session.clear()

        when: "A criteria query with pagination parameters is used"
        def c = Plant.createCriteria()
        def results = c.list(max: 2, offset: 0) {
            eq 'goesInPatch', false
            order 'name'
        }

        then: "The results are correct"
        results.size() == 2
        results[0].name == 'Cabbage'
        results[1].name == 'Eggplant'

    }
}
