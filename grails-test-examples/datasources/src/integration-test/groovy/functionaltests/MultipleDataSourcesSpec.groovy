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
import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import spock.lang.*
import ds1.Book
import ds2.Book as SecondBook

@Integration(applicationClass = Application)
@Rollback
class MultipleDataSourcesSpec extends Specification {


    void "Test multiple data source persistence"() {
        given: "initial counts"
            def initialPrimaryCount = Book.count()
            def initialSecondaryCount
            SecondBook.withTransaction {
                initialSecondaryCount = SecondBook.count()
            }
            
        when:
            new Book(title:"One").save(flush:true)
            new Book(title:"Two").save(flush:true)
            SecondBook.withTransaction {
                new SecondBook(title:"Three").save(flush:true)    
            }
            
        then:
            Book.count() == initialPrimaryCount + 2
            SecondBook.withTransaction { SecondBook.count() } == initialSecondaryCount + 1
            SecondBook.secondary.withTransaction { SecondBook.secondary.count() } == initialSecondaryCount + 1
    }
}
