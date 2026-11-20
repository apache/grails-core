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

package functionaltests

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration

import spock.lang.*

/**
 */
@Integration(applicationClass = Application)
@Rollback
class BookIntegrationSpec extends Specification {

    void "create book and save"() {
        given:
        def book = new Book(title:"Create The Stand")
        when:
        book.save(flush: true)

        then:
        Book.list()?.size() == old(Book.list()?.size()) + 1
    }

    void "test transaction rolled back from previous test"() {
        expect:
            Book.countByTitle("Create The Stand") == 0
    }

    void "create book and save with where"() {
        given:
        def book = new Book(title:title).save(flush: true)

        expect:
        book != null
        where:
        title        | count
        "The Stand"  | 2
    }


    def "test toUpperCase"() {
        given:
        def result = value.toUpperCase()

        expect:
        result == expectedResult

        where:
        value | expectedResult
        'King Crimson' | 'KING CRIMSON'
        'Riverside'    | 'RIVERSIDE'
    }
}
