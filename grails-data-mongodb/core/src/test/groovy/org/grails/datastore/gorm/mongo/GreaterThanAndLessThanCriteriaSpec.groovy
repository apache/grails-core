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

class GreaterThanAndLessThanCriteriaSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {
    void setupSpec() {
        manager.addAllDomainClasses([GTBook])
    }

    @Issue('GPMONGODB-180')
    void "Test that gt and lt criterion work together"() {
        given: "some books with publication dates in the last 2 days"
        new GTBook(title: 'The Cross and the Switchblade', published: new Date() - 7).save(flush: true)
        new GTBook(title: 'The Firm', published: new Date() + 1).save(flush: true)

        when: "lt and gt are used in the same query"
        def books = GTBook.createCriteria().list {
            gt('published', new Date())
            lt('published', new Date() + 5)
        }

        then: "The correct results are returned"
        1 == books.size()
    }
}

@Entity
class GTBook {
    Long id
    String title
    Date published
}
