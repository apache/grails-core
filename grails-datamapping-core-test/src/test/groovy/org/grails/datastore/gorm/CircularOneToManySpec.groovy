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

class CircularOneToManySpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {
    void setupSpec() {
        manager.domainClasses.addAll([CircularAuthor, CircularBook])
    }

    // GRAILS-10984
    void "Test that a circular one-to-many with two entities persists correctly"() {
        given: "an author and a book"
        def author = new CircularAuthor(name: 'John Doe')
        def book = new CircularBook(name: 'Divergent')

        when: "the book is added to the author"
        author.addToBooks(book)
        author.save(flush: true, failOnError: true)

        and: "the author is added to the book"
        book.addToFavoriteAuthors(author)
        book.save(flush: true, failOnError: true)

        then: "everything saves correctly"
        author.id
        book.id
        author.books.size() == 1
        book.favoriteAuthors.size() == 1
        author.favoriteBook == book
        book.author == author
    }
}

@Entity
class CircularAuthor {
    Long id
    String name
    CircularBook favoriteBook

    static hasMany = [ books: CircularBook ]
    static mappedBy = [ books: 'author' ]
    static constraints = { favoriteBook nullable: true }
}

@Entity
class CircularBook {
    Long id
    String name

    static belongsTo = [author: CircularAuthor]
    static hasMany = [favoriteAuthors: CircularAuthor]
    static mappedBy = [favoriteAuthors: 'favoriteBook']
}
