package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import spock.lang.Issue

/*
 * Copyright 2014 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @author graemerocher
 */
@Issue('https://github.com/grails/grails-core/issues/2674')
class FindByDomainInListSpec extends GormDatastoreSpec{

    void "Test fetch books by author"() {
        given:
        def author = new BookAuthor(name: "Aaron")
        author.books = [] as Set
        author.books << new AuthorBook(title: "Twilight", author: author)
        author.books << new AuthorBook(title: "Harry Potter", author: author)
        author.save(flush: true, failOnError: true)
        session.clear()
        when:
        def books = AuthorBook.withCriteria {
            inList 'author', BookAuthor.list()
        }

        then:
        AuthorBook.count() == 2
        books
        books.size() == 2
    }

    @Override
    List getDomainClasses() {
        [BookAuthor, AuthorBook]
    }
}

@Entity
class BookAuthor {
    Long id
    String name

    Set books
    static hasMany = [books: AuthorBook]

    static constraints = {
        name blank: false
    }
}

@Entity
class AuthorBook {

    Long id
    String title

    BookAuthor author
    static belongsTo = [author:BookAuthor]
}

