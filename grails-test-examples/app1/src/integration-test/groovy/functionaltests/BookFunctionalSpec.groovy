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

import functionaltests.pages.BookCreatePage
import functionaltests.pages.BookListPage
import functionaltests.pages.BookShowPage

import grails.gorm.transactions.Rollback
import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration
import spock.lang.Issue

@Integration
class BookFunctionalSpec extends ContainerGebSpec {

    void "Test that when the /viewBooks URL is hit it redirects to the book list"() {
        expect:
        to(BookListPage)
    }

    void "Test that a book was created in the Bootstrap class"() {
        expect:
        to(BookShowPage, 1)
    }

    void "Test that switching language results in correct encodings"() {
        when: 'The show page is rendered in german'
        def page = to(BookShowPage, 1, lang: 'de')

        then: 'The language is correct'
        page.createButton.text() == 'Book anlegen'
        page.deleteButton.attr('value') == 'Löschen'
    }

    @Rollback
    @Issue('10965')
    void "When creating a book the params are not on the url"() {
        when: 'Creating a book'
        to(BookCreatePage).createBook('The Stand')

        then: 'The show book view is rendered'
        at(BookShowPage)

        and: 'The params are not on the url'
        !currentUrl.contains('title')
        !currentUrl.contains('create')
    }
}
