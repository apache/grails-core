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

import datasources.Application
import example.BookService
import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.*
import spock.lang.*
import example.Book
import ds2.Book as SecondBook

@Integration(applicationClass = Application)
@Rollback
class MultipleDataSourcesSpec extends Specification {

    BookService bookService

    void "Test multiple data source persistence"() {
        when:
            new Book(title:"One").save(flush:true)
            new Book(title:"Two").save(flush:true)
            SecondBook.withTransaction {
                new SecondBook(title:"Three").save(flush:true)
            }

        then:
            Book.count() == 2
            SecondBook.withTransaction(readOnly: true) { SecondBook.count() } == 1
            SecondBook.withTransaction(readOnly: true)  { SecondBook.secondary.count() } == 1
    }

    void "test BookService does NOT throw NoUniqueBeanDefinitionException when multiple dataSources are configured"() {
        expect:
        bookService != null
    }
}
