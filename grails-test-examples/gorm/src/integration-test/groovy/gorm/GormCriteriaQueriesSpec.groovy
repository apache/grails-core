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
import spock.lang.Unroll

import grails.gorm.DetachedCriteria
import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
/**
 * Tests for GORM Criteria Queries - both createCriteria() and DetachedCriteria.
 *
 * Criteria queries provide a type-safe, programmatic way to build
 * complex queries without writing HQL strings.
 */
@Rollback
@Integration(applicationClass = Application)
class GormCriteriaQueriesSpec extends Specification {

    def setup() {
        // Clean up and create fresh test data
        Book.executeUpdate('delete from Book')
        Author.executeUpdate('delete from Author')

        def kingAuthor = new Author(name: 'Stephen King', email: 'stephen@king.com').save(flush: true)
        def clancyAuthor = new Author(name: 'Tom Clancy', email: 'tom@clancy.com').save(flush: true)
        def grishamAuthor = new Author(name: 'John Grisham', email: 'john@grisham.com').save(flush: true)

        // Stephen King books
        new Book(title: 'The Stand', isbn: '1234567890', pageCount: 1153, price: 19.99, inStock: true, author: kingAuthor).save(flush: true)
        new Book(title: 'It', isbn: '0987654321', pageCount: 1138, price: 18.99, inStock: true, author: kingAuthor).save(flush: true)
        new Book(title: 'The Shining', isbn: '1122334455', pageCount: 447, price: 14.99, inStock: false, author: kingAuthor).save(flush: true)

        // Tom Clancy books
        new Book(title: 'The Hunt for Red October', isbn: '2233445566', pageCount: 387, price: 16.99, inStock: true, author: clancyAuthor).save(flush: true)
        new Book(title: 'Patriot Games', isbn: '3344556677', pageCount: 540, price: 15.99, inStock: false, author: clancyAuthor).save(flush: true)

        // John Grisham books
        new Book(title: 'The Firm', isbn: '4455667788', pageCount: 421, price: 13.99, inStock: true, author: grishamAuthor).save(flush: true)
        new Book(title: 'A Time to Kill', isbn: '5566778899', pageCount: 515, price: 14.99, inStock: true, author: grishamAuthor).save(flush: true)

        // Book without author
        new Book(title: 'Anonymous Work', pageCount: 100, price: 9.99, inStock: true).save(flush: true)
    }

    // ============================================
    // Basic Criteria Queries with createCriteria()
    // ============================================

    void "test simple criteria list"() {
        when: "listing all books with criteria"
        def results = Book.createCriteria().list {
            // No restrictions - returns all
        }

        then: "all books returned"
        results.size() == 8
    }

    void "test criteria with eq restriction"() {
        when: "finding books with exact title match"
        def results = Book.createCriteria().list {
            eq('title', 'The Stand')
        }

        then: "exact match found"
        results.size() == 1
        results[0].title == 'The Stand'
    }

    void "test criteria with like restriction"() {
        when: "finding books with title starting with 'The'"
        def results = Book.createCriteria().list {
            like('title', 'The%')
        }

        then: "matching books found"
        results.size() == 4  // The Stand, The Shining, The Hunt..., The Firm
        results.every { it.title.startsWith('The') }
    }

    void "test criteria with ilike (case insensitive)"() {
        when: "finding books with case insensitive match"
        def results = Book.createCriteria().list {
            ilike('title', 'THE%')
        }

        then: "matching books found regardless of case"
        results.size() == 4
    }

    void "test criteria with between restriction"() {
        when: "finding books with page count in range"
        def results = Book.createCriteria().list {
            between('pageCount', 400, 600)
        }

        then: "books in range found"
        results.size() == 4  // The Shining(447), Patriot Games(540), The Firm(421), A Time to Kill(515)
        results.every { it.pageCount >= 400 && it.pageCount <= 600 }
    }

    void "test criteria with gt/lt restrictions"() {
        when: "finding expensive books over 15.00"
        def results = Book.createCriteria().list {
            gt('price', 15.00)
        }

        then: "expensive books found"
        results.size() == 4  // Stand(19.99), It(18.99), Hunt(16.99), Patriot(15.99)
        results.every { it.price > 15.00 }
    }

    void "test criteria with ge/le restrictions"() {
        when: "finding books with price >= 15.00 and <= 17.00"
        def results = Book.createCriteria().list {
            ge('price', 15.00)
            le('price', 17.00)
        }

        then: "books in price range found"
        results.every { it.price >= 15.00 && it.price <= 17.00 }
    }

    void "test criteria with isNull"() {
        when: "finding books without ISBN"
        def results = Book.createCriteria().list {
            isNull('isbn')
        }

        then: "books without ISBN found"
        results.size() == 1
        results[0].title == 'Anonymous Work'
    }

    void "test criteria with isNotNull"() {
        when: "finding books with ISBN"
        def results = Book.createCriteria().list {
            isNotNull('isbn')
        }

        then: "books with ISBN found"
        results.size() == 7
    }

    void "test criteria with in list"() {
        when: "finding specific books by title"
        def results = Book.createCriteria().list {
            inList('title', ['The Stand', 'It', 'The Firm'])
        }

        then: "specified books found"
        results.size() == 3
        results*.title.containsAll(['The Stand', 'It', 'The Firm'])
    }

    // ============================================
    // Logical Operators (and/or/not)
    // ============================================

    void "test criteria with or"() {
        when: "finding books that are either cheap OR out of stock"
        def results = Book.createCriteria().list {
            or {
                lt('price', 12.00)
                eq('inStock', false)
            }
        }

        then: "matching books found"
        results.size() == 3  // Anonymous(9.99), The Shining(out), Patriot Games(out)
    }

    void "test criteria with nested and/or"() {
        when: "finding in-stock books that are either cheap or short"
        def results = Book.createCriteria().list {
            eq('inStock', true)
            or {
                lt('price', 14.00)
                lt('pageCount', 500)
            }
        }

        then: "matching books found"
        results.every { it.inStock == true && (it.price < 14.00 || it.pageCount < 500) }
    }

    void "test criteria with not"() {
        when: "finding books NOT by Stephen King"
        def results = Book.createCriteria().list {
            not {
                author {
                    eq('name', 'Stephen King')
                }
            }
        }

        then: "non-King books found"
        // This includes books without author and other authors
        results.every { it.author?.name != 'Stephen King' }
    }

    // ============================================
    // Ordering and Pagination
    // ============================================

    void "test criteria with order"() {
        when: "listing books ordered by title"
        def results = Book.createCriteria().list {
            order('title', 'asc')
        }

        then: "books are ordered"
        results.size() == 8
        for (int i = 0; i < results.size() - 1; i++) {
            assert results[i].title <= results[i + 1].title
        }
    }

    void "test criteria with multiple order clauses"() {
        when: "ordering by inStock desc, then price asc"
        def results = Book.createCriteria().list {
            order('inStock', 'desc')  // true first
            order('price', 'asc')
        }

        then: "books are correctly ordered"
        // In-stock books come first, then ordered by price
        def inStockBooks = results.findAll { it.inStock }
        def outOfStockBooks = results.findAll { !it.inStock }

        results.takeWhile { it.inStock }.size() == inStockBooks.size()
    }

    void "test criteria with pagination"() {
        when: "paginating results"
        def page1 = Book.createCriteria().list(max: 3, offset: 0) {
            order('title', 'asc')
        }
        def page2 = Book.createCriteria().list(max: 3, offset: 3) {
            order('title', 'asc')
        }

        then: "pages are correctly split"
        page1.size() == 3
        page2.size() == 3
        page1.intersect(page2).isEmpty()
    }

    // ============================================
    // Association Queries
    // ============================================

    void "test criteria with association"() {
        when: "finding books by author name"
        def results = Book.createCriteria().list {
            author {
                eq('name', 'Stephen King')
            }
        }

        then: "King's books found"
        results.size() == 3
        results.every { it.author.name == 'Stephen King' }
    }

    void "test criteria with association property"() {
        when: "finding books by active author"
        def results = Book.createCriteria().list {
            author {
                eq('active', true)
            }
        }

        then: "books by active authors found"
        results.size() == 7  // All books with authors (authors are active by default)
    }

    // ============================================
    // Projections
    // ============================================

    void "test criteria with count projection"() {
        when: "counting books"
        def count = Book.createCriteria().get {
            projections {
                count()
            }
        }

        then: "count returned"
        count == 8
    }

    void "test criteria with sum projection"() {
        when: "summing page counts"
        def totalPages = Book.createCriteria().get {
            projections {
                sum('pageCount')
            }
        }

        then: "sum calculated"
        totalPages == 1153 + 1138 + 447 + 387 + 540 + 421 + 515 + 100  // 4701
    }

    void "test criteria with avg projection"() {
        when: "calculating average price"
        def avgPrice = Book.createCriteria().get {
            projections {
                avg('price')
            }
        }

        then: "average calculated"
        avgPrice != null
        avgPrice > 0
    }

    void "test criteria with min/max projection"() {
        when: "finding min and max prices"
        def result = Book.createCriteria().get {
            projections {
                min('price')
                max('price')
            }
        }

        then: "min and max found"
        result[0] == 9.99   // Anonymous Work
        result[1] == 19.99  // The Stand
    }

    void "test criteria with property projection"() {
        when: "selecting only titles"
        def titles = Book.createCriteria().list {
            projections {
                property('title')
            }
            order('title')
        }

        then: "only titles returned"
        titles.size() == 8
        titles.every { it instanceof String }
    }

    void "test criteria with distinct projection"() {
        when: "getting distinct authors"
        def authorNames = Book.createCriteria().list {
            projections {
                distinct('author')
            }
        }

        then: "distinct authors returned"
        // 3 authors + null for anonymous book
        authorNames.findAll { it != null }.size() == 3
    }

    void "test criteria with groupProperty projection"() {
        when: "grouping by inStock status with count"
        def results = Book.createCriteria().list {
            projections {
                groupProperty('inStock')
                count()
            }
        }

        then: "grouped results returned"
        results.size() == 2  // true and false
        def stockedCount = results.find { it[0] == true }[1]
        def outOfStockCount = results.find { it[0] == false }[1]
        stockedCount == 6
        outOfStockCount == 2
    }

    // ============================================
    // DetachedCriteria Tests
    // ============================================

    void "test detached criteria basic usage"() {
        given: "a detached criteria"
        def criteria = new DetachedCriteria(Book).build {
            eq('inStock', true)
        }

        when: "executing the criteria"
        def results = criteria.list()

        then: "results match"
        results.size() == 6
        results.every { it.inStock == true }
    }

    void "test detached criteria with where clause"() {
        given: "a detached criteria with where"
        def criteria = Book.where {
            price >= 15.00
        }

        when: "listing results"
        def results = criteria.list()

        then: "matching books found"
        results.every { it.price >= 15.00 }
    }

    void "test detached criteria chaining"() {
        given: "chained detached criteria"
        def baseCriteria = new DetachedCriteria(Book).build {
            eq('inStock', true)
        }
        def refinedCriteria = baseCriteria.build {
            gt('price', 14.00)
        }

        when: "executing chained criteria"
        def baseResults = baseCriteria.list()
        def refinedResults = refinedCriteria.list()

        then: "chaining works correctly"
        baseResults.size() > refinedResults.size()
        refinedResults.every { it.inStock == true && it.price > 14.00 }
    }

    void "test detached criteria as reusable query"() {
        given: "a reusable detached criteria"
        def expensiveBooks = new DetachedCriteria(Book).build {
            gt('price', 17.00)
        }

        expect: "criteria can be reused multiple times"
        expensiveBooks.count() == 2
        expensiveBooks.list().size() == 2
        expensiveBooks.get() != null  // Gets first match
    }

    void "test detached criteria with update"() {
        given: "a detached criteria"
        def outOfStockBooks = new DetachedCriteria(Book).build {
            eq('inStock', false)
        }

        when: "batch updating"
        int updated = outOfStockBooks.updateAll(inStock: true)
        Book.withSession { it.flush(); it.clear() }

        then: "updates applied"
        updated == 2
        Book.findAllByInStock(false).size() == 0
    }

    void "test detached criteria with delete"() {
        given: "a detached criteria for cheap books"
        def cheapBooks = new DetachedCriteria(Book).build {
            lt('price', 10.00)
        }
        def initialCount = Book.count()

        when: "batch deleting"
        int deleted = cheapBooks.deleteAll()

        then: "deletions applied"
        deleted == 1  // Anonymous Work
        Book.count() == initialCount - 1
    }

    // ============================================
    // HQL Queries
    // ============================================

    void "test basic HQL query"() {
        when: "executing HQL query with no parameters (use Map overload for plain strings)"
        def results = Book.executeQuery("from Book where inStock = true", [:])

        then: "results returned"
        results.size() == 6
    }

    void "test HQL with named parameters"() {
        when: "executing HQL with named params"
        def results = Book.executeQuery(
            'from Book b where b.price > :minPrice and b.inStock = :stock',
            [minPrice: 15.00, stock: true]
        )

        then: "results match parameters"
        results.every { it.price > 15.00 && it.inStock == true }
    }

    void "test HQL with positional parameters"() {
        when: "executing HQL with named parameter as alternative to positional"
        def results = Book.executeQuery(
            'from Book b where b.title like :pattern',
            [pattern: 'The%']
        )

        then: "results match"
        results.size() == 4
        results.every { it.title.startsWith('The') }
    }

    void "test HQL with pagination"() {
        when: "executing paginated HQL"
        def results = Book.executeQuery(
            'from Book b order by b.title',
            [max: 3, offset: 0]
        )

        then: "pagination applied"
        results.size() == 3
    }

    void "test HQL join query"() {
        when: "executing HQL join"
        def results = Book.executeQuery(
            'select b from Book b join b.author a where a.name = :authorName',
            [authorName: 'Stephen King']
        )

        then: "join works"
        results.size() == 3
        results.every { it.author.name == 'Stephen King' }
    }

    void "test HQL aggregate functions"() {
        when: "executing HQL aggregates"
        def result = Book.executeQuery(
            'select count(b), avg(b.price), max(b.pageCount) from Book b', [:]
        )[0]

        then: "aggregates calculated"
        result[0] == 8      // count
        result[1] != null   // avg price
        result[2] == 1153   // max page count
    }

    void "test HQL group by"() {
        when: "executing HQL group by"
        def results = Book.executeQuery(
            'select a.name, count(b) from Book b join b.author a group by a.name order by count(b) desc', [:]
        )

        then: "grouped results"
        results.size() == 3
        results[0][0] == 'Stephen King'  // Has most books
        results[0][1] == 3
    }

    void "test executeUpdate for bulk operations"() {
        when: "executing bulk update"
        int updated = Book.executeUpdate(
            'update Book b set b.price = b.price * 1.1 where b.inStock = true', [:]
        )

        then: "bulk update applied"
        updated == 6  // All in-stock books
    }

    // ============================================
    // Named Queries (where closures as class properties)
    // ============================================

    void "test where query as subquery"() {
        given: "a where query used as subquery"
        def expensiveBookIds = Book.where { price > 17.00 }.id()

        when: "using in main query"
        def results = Book.where {
            id in expensiveBookIds
        }.list()

        then: "subquery works"
        results.size() == 2
        results.every { it.price > 17.00 }
    }

    @Unroll
    void "test criteria with different comparison operators: #description"() {
        when: "executing criteria with #description"
        def results = Book.createCriteria().list(closure)

        then: "expected count matches"
        results.size() == expectedCount

        where:
        description          | closure                                        | expectedCount
        "eq"                 | { eq('inStock', true) }                        | 6
        "ne"                 | { ne('inStock', true) }                        | 2
        "gt"                 | { gt('price', 15.00) }                         | 4
        "ge"                 | { ge('price', 15.00) }                         | 4   // Stand(19.99), It(18.99), Hunt(16.99), Patriot(15.99)
        "lt"                 | { lt('pageCount', 500) }                       | 4   // Shining(447), Hunt(387), Firm(421), Anonymous(100)
        "le"                 | { le('pageCount', 500) }                       | 4   // Same as lt since no book has exactly 500 pages
    }
}
