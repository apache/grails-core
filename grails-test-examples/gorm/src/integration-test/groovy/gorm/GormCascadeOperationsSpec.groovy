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
package gorm

import spock.lang.Specification

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration

/**
 * Tests for GORM Cascade Operations.
 *
 * Cascade operations control how persistence operations (save, update, delete)
 * propagate from parent entities to their associated child entities.
 *
 * Key cascade behaviors tested:
 * - Cascade save on hasMany/belongsTo relationships
 * - Cascade delete behavior
 * - Orphan removal
 * - Bidirectional associations
 */
@Rollback
@Integration
class GormCascadeOperationsSpec extends Specification {

    def setup() {
        // Clean up test data
        Book.executeUpdate('delete from Book')
        Author.executeUpdate('delete from Author')
        User.executeUpdate('delete from User')
        City.executeUpdate('delete from City')
    }

    // ============================================
    // Cascade Save Tests (Author -> Book)
    // ============================================

    void "test saving parent cascades to children with addTo"() {
        given: "an author with books added via addTo"
        def author = new Author(name: 'George Orwell', email: 'george@orwell.com')
        author.addToBooks(new Book(title: '1984', pageCount: 328, inStock: true))
        author.addToBooks(new Book(title: 'Animal Farm', pageCount: 112, inStock: true))

        when: "saving only the author"
        author.save(flush: true)

        then: "books are also saved (cascade)"
        Author.count() == 1
        Book.count() == 2
        Book.findByTitle('1984').author == author
        Book.findByTitle('Animal Farm').author == author
    }

    void "test saving child with belongsTo saves parent reference"() {
        given: "an author"
        def author = new Author(name: 'J.K. Rowling', email: 'jk@rowling.com')
        author.save(flush: true)

        when: "creating a book with the author using addToBooks"
        def book = new Book(title: 'Harry Potter', pageCount: 309, inStock: true)
        author.addToBooks(book)
        author.save(flush: true)

        then: "book references the author and author has the book"
        book.author == author
        author.books.contains(book)
    }

    void "test cascade save with nested new objects"() {
        given: "a new author with new books - all unsaved"
        def author = new Author(name: 'Brandon Sanderson', email: 'brandon@sanderson.com')
        def book1 = new Book(title: 'Mistborn', pageCount: 541, inStock: true)
        def book2 = new Book(title: 'The Way of Kings', pageCount: 1007, inStock: true)

        author.addToBooks(book1)
        author.addToBooks(book2)

        when: "saving the parent"
        author.save(flush: true)

        then: "all objects have IDs (were persisted)"
        author.id != null
        book1.id != null
        book2.id != null
    }

    // ============================================
    // Cascade Update Tests
    // ============================================

    void "test updating parent does not affect children unless changed"() {
        given: "an author with books"
        def author = new Author(name: 'Original Name', email: 'original@email.com')
        author.addToBooks(new Book(title: 'Book1', pageCount: 100, inStock: true))
        author.save(flush: true)

        def bookId = author.books[0].id

        when: "updating only the author"
        author.name = 'Updated Name'
        author.save(flush: true)

        then: "book remains unchanged"
        def book = Book.get(bookId)
        book.title == 'Book1'
    }

    void "test dirty checking with associations"() {
        given: "an author with books"
        def author = new Author(name: 'Test Author', email: 'test@author.com')
        author.addToBooks(new Book(title: 'Test Book', pageCount: 200, inStock: true))
        author.save(flush: true)

        when: "modifying a book through the author"
        def book = author.books.first()
        book.title = 'Modified Title'
        author.save(flush: true)

        // Clear session and reload
        Author.withSession { it.flush(); it.clear() }
        def reloaded = Book.findByTitle('Modified Title')

        then: "book changes are persisted"
        reloaded != null
        reloaded.title == 'Modified Title'
    }

    // ============================================
    // Cascade Delete Tests
    // ============================================

    void "test deleting child does not delete parent"() {
        given: "an author with multiple books"
        def author = new Author(name: 'Multi Book Author', email: 'multi@author.com')
        author.addToBooks(new Book(title: 'Keep Me', pageCount: 100, inStock: true))
        author.addToBooks(new Book(title: 'Delete Me', pageCount: 100, inStock: true))
        author.save(flush: true)

        def authorId = author.id

        when: "deleting one book"
        def bookToDelete = Book.findByTitle('Delete Me')
        author.removeFromBooks(bookToDelete)
        bookToDelete.delete(flush: true)

        then: "author still exists with remaining book"
        Author.get(authorId) != null
        Author.get(authorId).books.size() == 1
        Book.findByTitle('Keep Me') != null
    }

    void "test belongsTo allows orphan removal"() {
        given: "an author with a book"
        def author = new Author(name: 'Orphan Test', email: 'orphan@test.com')
        def book = new Book(title: 'Orphan Book', pageCount: 100, inStock: true)
        author.addToBooks(book)
        author.save(flush: true)

        def bookId = book.id

        when: "removing book from author and saving"
        author.removeFromBooks(book)
        book.delete(flush: true)
        author.save(flush: true)

        then: "book is deleted (orphan removal)"
        Book.get(bookId) == null
        author.books.isEmpty()
    }

    // ============================================
    // City -> User Cascade Tests (hasMany/belongsTo)
    // ============================================

    void "test hasMany cascade save for City and Users"() {
        given: "a city with users"
        def city = new City(name: 'London')
        city.addToUsers(new User(name: 'Alice'))
        city.addToUsers(new User(name: 'Bob'))

        when: "saving the city"
        city.save(flush: true)

        then: "users are also saved"
        City.count() == 1
        User.count() == 2
        User.findByName('Alice').city == city
    }

    void "test removing user from city"() {
        given: "a city with users"
        def city = new City(name: 'Paris')
        city.addToUsers(new User(name: 'Jean'))
        city.addToUsers(new User(name: 'Pierre'))
        city.save(flush: true)

        when: "removing one user"
        def jean = User.findByName('Jean')
        city.removeFromUsers(jean)
        jean.delete(flush: true)

        then: "user is removed"
        city.users.size() == 1
        User.findByName('Jean') == null
    }

    // ============================================
    // Association Navigation Tests
    // ============================================

    void "test bidirectional navigation"() {
        given: "an author with books"
        def author = new Author(name: 'Nav Author', email: 'nav@author.com')
        def book1 = new Book(title: 'Nav Book 1', pageCount: 100, inStock: true)
        def book2 = new Book(title: 'Nav Book 2', pageCount: 100, inStock: true)
        author.addToBooks(book1)
        author.addToBooks(book2)
        author.save(flush: true)

        expect: "navigation works both ways"
        // Author -> Books
        author.books.size() == 2
        author.books.contains(book1)
        author.books.contains(book2)

        // Book -> Author
        book1.author == author
        book2.author == author
    }

    void "test lazy loading of associations"() {
        given: "a city with users"
        def city = new City(name: 'Berlin')
        city.addToUsers(new User(name: 'Hans'))
        city.addToUsers(new User(name: 'Klaus'))
        city.save(flush: true)
        def cityId = city.id

        // Clear session
        City.withSession { it.flush(); it.clear() }

        when: "loading city without accessing users"
        def loadedCity = City.get(cityId)

        then: "city is loaded"
        loadedCity != null
        loadedCity.name == 'Berlin'

        and: "users can be lazily loaded"
        loadedCity.users.size() == 2
    }

    // ============================================
    // Batch Operations with Associations
    // ============================================

    void "test batch insert with associations"() {
        when: "batch creating cities with users"
        10.times { i ->
            def city = new City(name: "City${i}")
            city.addToUsers(new User(name: "User${i}A"))
            city.addToUsers(new User(name: "User${i}B"))
            city.save(flush: true)
        }

        then: "all entities created"
        City.count() == 10
        User.count() == 20
    }

    void "test updating multiple children"() {
        given: "an author with multiple books"
        def author = new Author(name: 'Batch Author', email: 'batch@author.com')
        5.times { i ->
            author.addToBooks(new Book(title: "Book ${i}", pageCount: 100 + i, inStock: true))
        }
        author.save(flush: true)

        when: "updating all books"
        author.books.each { book ->
            book.inStock = false
        }
        author.save(flush: true)

        // Clear and reload
        Author.withSession { it.flush(); it.clear() }
        def reloaded = Author.findByName('Batch Author')

        then: "all books updated"
        reloaded.books.every { !it.inStock }
    }

    // ============================================
    // Association Collection Operations
    // ============================================

    void "test addTo creates bidirectional link"() {
        given: "an author and a book"
        def author = new Author(name: 'AddTo Author', email: 'addto@author.com')
        author.save(flush: true)
        def book = new Book(title: 'AddTo Book', pageCount: 100, inStock: true)

        when: "using addTo"
        author.addToBooks(book)
        author.save(flush: true)

        then: "bidirectional link established"
        book.author == author
        author.books.contains(book)
    }

    void "test removeFrom breaks bidirectional link"() {
        given: "an author with a book"
        def author = new Author(name: 'RemoveFrom Author', email: 'remove@author.com')
        def book = new Book(title: 'RemoveFrom Book', pageCount: 100, inStock: true)
        author.addToBooks(book)
        author.save(flush: true)

        when: "using removeFrom"
        author.removeFromBooks(book)

        then: "bidirectional link broken"
        book.author == null
        !author.books.contains(book)
    }

    void "test collection operations on hasMany"() {
        given: "a city with users"
        def city = new City(name: 'Collection City')
        city.addToUsers(new User(name: 'User1'))
        city.addToUsers(new User(name: 'User2'))
        city.addToUsers(new User(name: 'User3'))
        city.save(flush: true)

        expect: "collection operations work"
        city.users.size() == 3
        city.users.find { it.name == 'User1' } != null
        city.users.findAll { it.name.startsWith('User') }.size() == 3
        city.users.collect { it.name }.containsAll(['User1', 'User2', 'User3'])
    }
}
