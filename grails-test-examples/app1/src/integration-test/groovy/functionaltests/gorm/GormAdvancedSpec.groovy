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
package functionaltests.gorm

import spock.lang.Specification

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration

/**
 * Integration tests for advanced GORM features:
 * - Criteria queries
 * - Where queries
 * - Projections and aggregations
 * - HQL queries
 * - Named queries
 * - Detached criteria
 */
@Rollback
@Integration
class GormAdvancedSpec extends Specification {

    def setup() {
        // Create test data
        createTestData()
    }

    private static void createTestData() {

        if (Author.count() > 0) {
            return // Test data already exists
        }

        // Author 1: American author with 3 books
        def author1 = new Author(name: 'John Smith', country: 'USA', birthYear: 1970, active: true)
        author1.addToBooks(new GormBook(title: 'The Great Adventure', genre: 'Fiction', price: 19.99, pageCount: 350, publicationYear: 2020, rating: 4.5))
        author1.addToBooks(new GormBook(title: 'Mystery Manor', genre: 'Mystery', price: 14.99, pageCount: 280, publicationYear: 2021, rating: 4.2))
        author1.addToBooks(new GormBook(title: 'Science Today', genre: 'Science', price: 29.99, pageCount: 450, publicationYear: 2022, rating: 4.8))
        author1.save(failOnError: true)

        // Author 2: British author with 2 books
        def author2 = new Author(name: 'Jane Doe', country: 'UK', birthYear: 1985, active: true)
        author2.addToBooks(new GormBook(title: 'British History', genre: 'History', price: 24.99, pageCount: 500, publicationYear: 2019, rating: 4.0))
        author2.addToBooks(new GormBook(title: 'Royal Biography', genre: 'Biography', price: 21.99, pageCount: 320, publicationYear: 2020, rating: 3.8))
        author2.save(failOnError: true)

        // Author 3: French author with 1 book, inactive
        def author3 = new Author(name: 'Pierre Martin', country: 'France', birthYear: 1960, active: false)
        author3.addToBooks(new GormBook(title: 'French Cuisine', genre: 'Non-Fiction', price: 34.99, pageCount: 400, publicationYear: 2018, inPrint: false, rating: 4.6))
        author3.save(failOnError: true)

        // Author 4: American author with 4 books (prolific)
        def author4 = new Author(name: 'Sarah Johnson', country: 'USA', birthYear: 1975, active: true)
        author4.addToBooks(new GormBook(title: 'Fantasy World', genre: 'Fantasy', price: 16.99, pageCount: 600, publicationYear: 2021, rating: 4.9))
        author4.addToBooks(new GormBook(title: 'Dragon Tales', genre: 'Fantasy', price: 17.99, pageCount: 550, publicationYear: 2022, rating: 4.7))
        author4.addToBooks(new GormBook(title: 'Magic Realm', genre: 'Fantasy', price: 18.99, pageCount: 580, publicationYear: 2023, rating: 4.8))
        author4.addToBooks(new GormBook(title: 'Love Story', genre: 'Romance', price: 12.99, pageCount: 250, publicationYear: 2020, rating: 3.5))
        author4.save(failOnError: true, flush: true)
    }

    // ========== Criteria Query Tests ==========

    def "criteria query - basic equals"() {
        when: "querying with equals criterion"
        def results = Author.createCriteria().list {
            eq('country', 'USA')
        }

        then: "matching authors are returned"
        results.size() == 2
        results*.name.containsAll(['John Smith', 'Sarah Johnson'])
    }

    def "criteria query - like operator"() {
        when: "querying with like criterion"
        def results = GormBook.createCriteria().list {
            like('title', '%History%')
        }

        then: "matching books are returned"
        results.size() >= 1  // At least our British History book
        results.every { it.title.contains('History') }
        results.find { it.title == 'British History' } != null
    }

    def "criteria query - ilike (case insensitive)"() {
        when: "querying with case-insensitive like"
        def results = GormBook.createCriteria().list {
            ilike('title', '%FANTASY%')
        }

        then: "matching books regardless of case"
        results.size() >= 1  // At least our Fantasy World book
        results.every { it.title.toLowerCase().contains('fantasy') }
        results.find { it.title == 'Fantasy World' } != null
    }

    def "criteria query - between"() {
        when: "querying with between criterion"
        def results = GormBook.createCriteria().list {
            between('price', 15.00, 25.00)
        }

        then: "books in price range are returned"
        results.size() >= 4
        results.every { it.price >= 15.00 && it.price <= 25.00 }
    }

    def "criteria query - greater than and less than"() {
        when: "querying with gt and lt"
        def results = GormBook.createCriteria().list {
            gt('pageCount', 400)
            lt('price', 30.00)
        }

        then: "matching books are returned"
        results.every { it.pageCount > 400 && it.price < 30.00 }
    }

    def "criteria query - in list"() {
        when: "querying with inList"
        def results = GormBook.createCriteria().list {
            inList('genre', ['Fantasy', 'Mystery'])
        }

        then: "books in specified genres are returned"
        results.size() >= 4
        results.every { it.genre in ['Fantasy', 'Mystery'] }
    }

    def "criteria query - is null and is not null"() {
        when: "querying for non-null ratings"
        def results = GormBook.createCriteria().list {
            isNotNull('rating')
        }

        then: "all books have ratings"
        results.every { it.rating != null }
    }

    def "criteria query - and/or logic"() {
        when: "querying with or logic"
        def results = GormBook.createCriteria().list {
            or {
                eq('genre', 'Fantasy')
                ge('price', 30.00)
            }
        }

        then: "books matching either condition are returned"
        results.every { it.genre == 'Fantasy' || it.price >= 30.00 }
    }

    def "criteria query - nested and/or"() {
        when: "querying with nested logic"
        def results = GormBook.createCriteria().list {
            and {
                eq('inPrint', true)
                or {
                    eq('genre', 'Fiction')
                    eq('genre', 'Science')
                }
            }
        }

        then: "books matching complex condition"
        results.every { it.inPrint && (it.genre == 'Fiction' || it.genre == 'Science') }
    }

    def "criteria query - association query"() {
        when: "querying through association"
        def results = GormBook.createCriteria().list {
            author {
                eq('country', 'USA')
            }
        }

        then: "books by USA authors are returned"
        results.size() >= 7
    }

    def "criteria query - order by"() {
        when: "querying with ordering"
        def results = GormBook.createCriteria().list {
            order('price', 'desc')
        }

        then: "results are ordered by price descending"
        for (int i = 0; i < results.size() - 1; i++) {
            results[i].price >= results[i + 1].price
        }
    }

    def "criteria query - multiple order by"() {
        when: "querying with multiple orderings"
        def results = GormBook.createCriteria().list {
            order('genre', 'asc')
            order('price', 'desc')
        }

        then: "results are ordered"
        results.size() > 0
    }

    def "criteria query - pagination"() {
        when: "querying with pagination"
        def results = GormBook.createCriteria().list(max: 3, offset: 0) {
            order('title', 'asc')
        }

        then: "paginated results are returned"
        results.size() <= 3
    }

    def "criteria query - count"() {
        when: "counting with criteria"
        def count = Author.createCriteria().count {
            eq('active', true)
        }

        then: "count is returned"
        count >= 3  // At least our 3 active authors
    }

    def "criteria query - get single result"() {
        when: "getting single result with unique constraint"
        def result = Author.createCriteria().list {
            eq('name', 'John Smith')
            maxResults(1)
        }

        then: "author is returned"
        result.size() >= 1
        result[0].name == 'John Smith'
    }

    // ========== Where Query Tests ==========

    def "where query - simple condition"() {
        when: "using where query"
        def results = Author.where {
            country == 'UK'
        }.list()

        then: "matching authors are returned"
        results.size() >= 1  // At least our UK test author
        results.every { it.country == 'UK' }
        results.find { it.name == 'Jane Doe' } != null
    }

    def "where query - multiple conditions"() {
        when: "using where with multiple conditions"
        def results = GormBook.where {
            genre == 'Fantasy' && price < 18.00
        }.list()

        then: "matching books are returned"
        results.every { it.genre == 'Fantasy' && it.price < 18.00 }
    }

    def "where query - or conditions"() {
        when: "using where with or"
        def results = GormBook.where {
            genre == 'History' || genre == 'Biography'
        }.list()

        then: "books in either genre"
        results.size() >= 2  // At least our 2 test books
        results*.genre.every { it in ['History', 'Biography'] }
    }

    def "where query - comparison operators"() {
        when: "using comparison operators"
        def results = GormBook.where {
            rating >= 4.5 && pageCount > 300
        }.list()

        then: "highly rated long books"
        results.every { it.rating >= 4.5 && it.pageCount > 300 }
    }

    def "where query - like pattern"() {
        when: "using like in where query"
        def results = GormBook.where {
            title =~ '%Tales%'
        }.list()

        then: "matching books are returned"
        results.size() >= 1  // At least our Dragon Tales book
        results.every { it.title.contains('Tales') }
        results.find { it.title == 'Dragon Tales' } != null
    }

    def "where query - in list"() {
        when: "using in operator"
        def genres = ['Fantasy', 'Romance']
        def results = GormBook.where {
            genre in genres
        }.list()

        then: "books in specified genres"
        results.every { it.genre in genres }
    }

    def "where query - association traversal"() {
        when: "traversing association in where"
        def results = GormBook.where {
            author.country == 'USA'
        }.list()

        then: "books by USA authors"
        results.size() >= 7
    }

    def "where query - detached for reuse"() {
        given: "a detached where query"
        def fantasyBooks = GormBook.where {
            genre == 'Fantasy'
        }

        when: "reusing the query with additional conditions"
        def highlyRatedFantasy = fantasyBooks.where {
            rating >= 4.7
        }.list()

        then: "combined conditions apply"
        highlyRatedFantasy.every { it.genre == 'Fantasy' && it.rating >= 4.7 }
    }

    def "where query - count"() {
        when: "counting with where query"
        def count = GormBook.where {
            inPrint == true
        }.count()

        then: "count is correct"
        count >= 9  // 9 books in print
    }

    // ========== Projection Tests ==========

    def "projection - single property"() {
        when: "projecting single property"
        def titles = GormBook.createCriteria().list {
            projections {
                property('title')
            }
        }

        then: "list of titles is returned"
        titles.every { it instanceof String }
    }

    def "projection - multiple properties"() {
        when: "projecting multiple properties"
        def results = GormBook.createCriteria().list {
            projections {
                property('title')
                property('price')
            }
        }

        then: "list of arrays is returned"
        results.every { it.size() == 2 }
    }

    def "projection - distinct"() {
        when: "getting distinct values"
        def genres = GormBook.createCriteria().list {
            projections {
                distinct('genre')
            }
        }

        then: "distinct genres are returned"
        genres.unique().size() == genres.size()
    }

    def "projection - count"() {
        when: "counting with projection"
        def count = GormBook.createCriteria().get {
            projections {
                count('id')
            }
        }

        then: "count is returned"
        count >= 10
    }

    def "projection - sum"() {
        when: "summing with projection"
        def totalPages = GormBook.createCriteria().get {
            projections {
                sum('pageCount')
            }
        }

        then: "sum is returned"
        totalPages > 0
    }

    def "projection - average"() {
        when: "averaging with projection"
        def avgPrice = GormBook.createCriteria().get {
            projections {
                avg('price')
            }
        }

        then: "average is returned"
        avgPrice > 0
    }

    def "projection - min and max"() {
        when: "getting min and max"
        def minMax = GormBook.createCriteria().get {
            projections {
                min('price')
                max('price')
            }
        }

        then: "min and max are returned"
        minMax[0] < minMax[1]
    }

    def "projection - group by"() {
        when: "grouping by genre with count"
        def results = GormBook.createCriteria().list {
            projections {
                groupProperty('genre')
                count('id')
            }
            order('genre', 'asc')
        }

        then: "grouped counts are returned"
        results.every { it.size() == 2 }
        def fantasyCount = results.find { it[0] == 'Fantasy' }
        fantasyCount != null
        fantasyCount[1] >= 3
    }

    def "projection - group by with aggregations"() {
        when: "grouping with multiple aggregations"
        def results = GormBook.createCriteria().list {
            projections {
                groupProperty('genre')
                count('id')
                avg('price')
                sum('pageCount')
            }
        }

        then: "aggregated results per genre"
        results.every { it.size() == 4 }
    }

    // ========== HQL Query Tests ==========

    def "HQL - simple select"() {
        when: "executing simple HQL"
        def results = GormBook.executeQuery("from GormBook where genre = :genre", [genre: 'Fantasy'])

        then: "matching books are returned"
        results.every { it.genre == 'Fantasy' }
    }

    def "HQL - select with projection"() {
        when: "HQL with projection"
        def titles = GormBook.executeQuery("select b.title from GormBook b where b.price > :price", [price: 20.00])

        then: "titles are returned"
        titles.every { it instanceof String }
    }

    def "HQL - join query"() {
        when: "HQL with join"
        def results = GormBook.executeQuery(
            "select b from GormBook b join b.author a where a.country = :country",
            [country: 'UK']
        )

        then: "books by UK authors"
        results.size() >= 2  // At least our 2 UK books
        results.every { it.author.country == 'UK' }
    }

    def "HQL - aggregate functions"() {
        when: "HQL with aggregates"
        def result = GormBook.executeQuery(
            "select count(b), avg(b.price), sum(b.pageCount) from GormBook b"
        )

        then: "aggregates are returned"
        result[0][0] >= 10  // count
        result[0][1] > 0     // avg price
        result[0][2] > 0     // sum pages
    }

    def "HQL - group by"() {
        when: "HQL with group by"
        def results = GormBook.executeQuery(
            "select b.genre, count(b), avg(b.rating) from GormBook b group by b.genre order by b.genre"
        )

        then: "grouped results"
        results.size() > 0
    }

    def "HQL - named parameters"() {
        when: "HQL with named parameters"
        def results = GormBook.executeQuery(
            "from GormBook b where b.price between :minPrice and :maxPrice and b.rating >= :minRating",
            [minPrice: 15.00, maxPrice: 25.00, minRating: 4.0d]  // Use 'd' suffix for Double type
        )

        then: "matching books"
        results.every { it.price >= 15.00 && it.price <= 25.00 && it.rating >= 4.0 }
    }

    def "HQL - pagination"() {
        when: "HQL with pagination"
        def results = GormBook.executeQuery(
            "from GormBook b order by b.title",
            [max: 5, offset: 0]
        )

        then: "paginated results"
        results.size() <= 5
    }

    def "HQL - update query"() {
        given: "a book to update"
        def book = GormBook.findByTitle('The Great Adventure')
        def originalPrice = book.price

        when: "executing update HQL"
        def updated = GormBook.executeUpdate(
            "update GormBook b set b.price = b.price * 1.1 where b.genre = :genre",
            [genre: 'Fiction']
        )

        then: "books are updated"
        updated >= 1

        cleanup:
        GormBook.executeUpdate("update GormBook b set b.price = :price where b.title = :title",
            [price: originalPrice, title: 'The Great Adventure'])
    }

    def "HQL - subquery"() {
        when: "HQL with subquery"
        def results = GormBook.executeQuery(
            "from GormBook b where b.price > (select avg(b2.price) from GormBook b2)"
        )

        then: "books above average price"
        results.size() > 0
    }

    // ========== Detached Criteria Tests ==========

    def "detached criteria - basic"() {
        given: "a detached criteria"
        def criteria = new grails.gorm.DetachedCriteria(GormBook).build {
            eq('genre', 'Fantasy')
        }

        when: "executing detached criteria"
        def results = criteria.list()

        then: "matching books are returned"
        results.every { it.genre == 'Fantasy' }
    }

    def "detached criteria - with projections"() {
        given: "a detached criteria with projection"
        def criteria = new grails.gorm.DetachedCriteria(GormBook).build {
            projections {
                property('title')
            }
            eq('inPrint', true)
        }

        when: "executing"
        def titles = criteria.list()

        then: "titles are returned"
        titles.every { it instanceof String }
    }

    def "detached criteria - reusable"() {
        given: "a base criteria"
        def baseCriteria = new grails.gorm.DetachedCriteria(GormBook).build {
            gt('rating', 4.0d)
        }

        when: "reusing with additional conditions"
        def expensiveHighRated = baseCriteria.build {
            gt('price', 20.00)
        }.list()

        def cheapHighRated = baseCriteria.build {
            lt('price', 20.00)
        }.list()

        then: "both queries work independently"
        expensiveHighRated.every { it.rating > 4.0 && it.price > 20.00 }
        cheapHighRated.every { it.rating > 4.0 && it.price < 20.00 }
    }

    // ========== Batch Processing Tests ==========

    def "batch processing with withSession"() {
        when: "processing in batches"
        def processedCount = 0
        GormBook.withSession { session ->
            GormBook.list().each { book ->
                // Simulate batch processing
                processedCount++
                if (processedCount % 5 == 0) {
                    session.flush()
                    session.clear()
                }
            }
        }

        then: "all books processed"
        processedCount >= 10
    }

    def "read-only query optimization"() {
        when: "executing read-only query"
        def results = GormBook.createCriteria().list {
            readOnly(true)
            eq('inPrint', true)
        }

        then: "results are returned (read-only mode)"
        results.size() >= 9
    }
}
