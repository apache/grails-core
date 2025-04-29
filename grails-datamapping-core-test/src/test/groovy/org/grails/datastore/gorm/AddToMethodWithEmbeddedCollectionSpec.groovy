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
package org.grails.datastore.gorm

import grails.persistence.Entity
import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

class AddToMethodWithEmbeddedCollectionSpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([Library, LibraryBook])
    }

    private service = new LibraryService()

    void testAddBooks() {
        when:
        LibraryBook book = new LibraryBook(title: "title", author: "me")
        def library = service.addBook(book)

        then:
        library
        library.books.size() == 1
    }

    void testAddBooksInTest() {
        when:
        LibraryBook book = new LibraryBook(title: "title", author: "me")
        def library = new Library()
        library.addToBooks(book)

        then:
        library
        library.books.size() == 1
    }
}

@Entity
class Library {

    static embedded = [
            'books'
    ]

    Set books
    Long id
    static hasMany = [books: LibraryBook]
}

@Entity
class LibraryBook {
    Long id
    String title
    String author
}

class LibraryService {

    Library addBook(LibraryBook book) {
        def library = new Library()
        library.addToBooks(book)
        library.save(failOnError: true)
        return library
    }
}
