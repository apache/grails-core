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

import grails.testing.gorm.DomainUnitTest
import spock.lang.Issue
import spock.lang.Specification

/**
 */
class BookSpec extends Specification implements DomainUnitTest<Book> {

    void "Test validating a book"() {
        expect:"The book validates"
            new Book(title:"The Stand").validate()
    }

    @Issue('apache/grails-core#10079')
    void 'Test that auto-timestamp properties are excluded from mass property binding'() {
        given:
        def book = new Book()

        when:
        book.properties = [title: 'Some Title',
                           dateCreated: 'some value',
                           lastUpdated: 'some value']

        then:
        !book.hasErrors()
        book.title == 'Some Title'
        book.dateCreated == null
        book.lastUpdated == null
    }
}
