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
package grails.gorm.tests

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import org.grails.datastore.gorm.GormEntity

/**
 * Regression tests for H7 "Found two representations of same collection" error.
 *
 * H7 enforces strict collection identity — after an entity is persisted and
 * managed by the session, calling addTo* and then save(flush:true) must not
 * replace the Hibernate-tracked PersistentCollection with a plain collection.
 */
class AddToManagedEntitySpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([CascadeAuthor, CascadeBook])
    }

    void cleanup() {
        CascadeBook.withNewTransaction {
            CascadeBook.executeUpdate('delete from CascadeBook', [:])
            CascadeAuthor.executeUpdate('delete from CascadeAuthor', [:])
        }
    }

    void "addTo* then save(flush:true) on an already-persisted author does not throw two representations error"() {
        given: "an author that is already persisted (managed by session)"
        def author = new CascadeAuthor(name: 'J.K. Rowling').save(flush: true)

        when: "adding a book to the managed author and flushing"
        def book = new CascadeBook(title: 'Harry Potter')
        author.addToBooks(book)
        author.save(flush: true)

        then: "no exception is thrown and the relationship is persisted"
        noExceptionThrown()
        CascadeBook.count() == 1
        CascadeBook.findByTitle('Harry Potter').author.id == author.id
        author.books.contains(book)
    }

    void "addTo* then save(flush:true) with multiple books on managed author works"() {
        given: "a persisted author"
        def author = new CascadeAuthor(name: 'Brandon Sanderson').save(flush: true)

        when: "adding multiple books to the managed author"
        5.times { i ->
            author.addToBooks(new CascadeBook(title: "Book ${i}"))
        }
        author.save(flush: true)

        then:
        noExceptionThrown()
        CascadeBook.count() == 5
    }

    void "modifying a book through a managed author and flushing does not throw"() {
        given: "a persisted author with books"
        def author = new CascadeAuthor(name: 'Test Author')
        author.addToBooks(new CascadeBook(title: 'Original Title'))
        author.save(flush: true)

        when: "modifying a book and saving the author again"
        author.books.first().title = 'Modified Title'
        author.save(flush: true)

        CascadeAuthor.withSession { it.flush(); it.clear() }

        then:
        noExceptionThrown()
        CascadeBook.findByTitle('Modified Title') != null
    }

    void "removeFrom then save(flush:true) on managed author works"() {
        given: "a persisted author with a book"
        def author = new CascadeAuthor(name: 'Orphan Author')
        def book = new CascadeBook(title: 'Orphan Book')
        author.addToBooks(book)
        author.save(flush: true)
        def bookId = book.id

        when:
        author.removeFromBooks(book)
        book.delete(flush: true)
        author.save(flush: true)

        then:
        noExceptionThrown()
        CascadeBook.get(bookId) == null
        author.books.isEmpty()
    }
}

@Entity
class CascadeAuthor implements HibernateEntity<CascadeAuthor> {
    String name
    Set<CascadeBook> books
    static hasMany = [books: CascadeBook]
    static constraints = {
        name blank: false
    }
}

@Entity
class CascadeBook implements HibernateEntity<CascadeBook> {
    String title
    CascadeAuthor author
    static belongsTo = [author: CascadeAuthor]
    static constraints = {
        title blank: false
    }
}
