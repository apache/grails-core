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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import spock.lang.Specification
import ds1.Book
import ds2.Book as SecondBook

import javax.sql.DataSource

/**
 * Integration tests for datasource switching and multiple datasource operations.
 * Tests dynamic datasource selection, isolation between datasources,
 * and various GORM operations across different datasources.
 */
@Integration(applicationClass = Application)
@Rollback
class DatasourceSwitchingSpec extends Specification {

    @Autowired
    @Qualifier('dataSource')
    DataSource primaryDataSource

    @Autowired
    @Qualifier('dataSource_secondary')
    DataSource secondaryDataSource

    // ========== Datasource Isolation Tests ==========

    def "data in primary datasource is isolated from secondary"() {
        given: "initial counts"
        def initialPrimaryCount = Book.count()
        def initialSecondaryCount = SecondBook.withTransaction { SecondBook.count() }

        when: "creating books in primary datasource with unique titles"
        def title1 = "Primary Book ${UUID.randomUUID()}"
        def title2 = "Primary Book ${UUID.randomUUID()}"
        new Book(title: title1).save(flush: true)
        new Book(title: title2).save(flush: true)

        and: "creating books in secondary datasource with unique title"
        def secTitle = "Secondary Book ${UUID.randomUUID()}"
        SecondBook.withTransaction {
            new SecondBook(title: secTitle).save(flush: true)
        }

        then: "primary datasource has 2 more books"
        Book.count() == initialPrimaryCount + 2
        Book.findByTitle(title1) != null
        Book.findByTitle(title2) != null

        and: "secondary datasource has 1 more book"
        SecondBook.withTransaction { SecondBook.count() } == initialSecondaryCount + 1
        SecondBook.withTransaction { SecondBook.findByTitle(secTitle) } != null

        and: "cross-datasource isolation: primary books not in secondary"
        SecondBook.withTransaction { SecondBook.findByTitle(title1) } == null

        and: "cross-datasource isolation: secondary books not in primary"
        Book.findByTitle(secTitle) == null
    }

    def "same entity name in different packages uses different datasources"() {
        when: "saving to primary"
        def primaryBook = new Book(title: "Same Name Test - Primary").save(flush: true)

        and: "saving to secondary"
        def secondaryBook
        SecondBook.withTransaction {
            secondaryBook = new SecondBook(title: "Same Name Test - Secondary").save(flush: true)
        }

        then: "both books are saved to their respective datasources"
        primaryBook.id != null
        secondaryBook != null

        and: "they exist in their respective datasources"
        Book.findByTitle("Same Name Test - Primary") != null
        SecondBook.withTransaction { SecondBook.findByTitle("Same Name Test - Secondary") } != null

        and: "they don't exist in the other datasource"
        Book.findByTitle("Same Name Test - Secondary") == null
        SecondBook.withTransaction { SecondBook.findByTitle("Same Name Test - Primary") } == null
    }

    // ========== CRUD Operations on Multiple Datasources ==========

    def "CRUD operations work independently on each datasource"() {
        given: "a book in primary datasource"
        def primaryBook = new Book(title: "CRUD Primary").save(flush: true)

        and: "a book in secondary datasource"
        def secondaryBookId
        SecondBook.withTransaction {
            secondaryBookId = new SecondBook(title: "CRUD Secondary").save(flush: true).id
        }

        when: "updating primary book"
        primaryBook.title = "CRUD Primary Updated"
        primaryBook.save(flush: true)

        and: "updating secondary book"
        SecondBook.withTransaction {
            def book = SecondBook.get(secondaryBookId)
            book.title = "CRUD Secondary Updated"
            book.save(flush: true)
        }

        then: "primary book is updated"
        Book.get(primaryBook.id).title == "CRUD Primary Updated"

        and: "secondary book is updated"
        SecondBook.withTransaction { SecondBook.get(secondaryBookId).title } == "CRUD Secondary Updated"

        when: "deleting from secondary"
        SecondBook.withTransaction {
            SecondBook.get(secondaryBookId).delete(flush: true)
        }

        then: "secondary book is deleted"
        SecondBook.withTransaction { SecondBook.get(secondaryBookId) } == null

        and: "primary book still exists"
        Book.get(primaryBook.id) != null
    }

    // ========== Query Operations Across Datasources ==========

    def "dynamic finders work on each datasource independently"() {
        given: "books with same titles in both datasources"
        new Book(title: "FindMe").save(flush: true)
        new Book(title: "IgnoreMe").save(flush: true)

        SecondBook.withTransaction {
            new SecondBook(title: "FindMe").save(flush: true)
            new SecondBook(title: "DifferentOne").save(flush: true)
        }

        expect: "findByTitle finds in correct datasource"
        Book.findByTitle("FindMe") != null
        Book.findByTitle("DifferentOne") == null

        SecondBook.withTransaction { SecondBook.findByTitle("FindMe") } != null
        SecondBook.withTransaction { SecondBook.findByTitle("IgnoreMe") } == null
    }

    def "findAll with criteria works on each datasource"() {
        given: "books in both datasources"
        new Book(title: "Alpha Book").save(flush: true)
        new Book(title: "Beta Book").save(flush: true)
        new Book(title: "Gamma Book").save(flush: true)

        SecondBook.withTransaction {
            new SecondBook(title: "Delta Book").save(flush: true)
            new SecondBook(title: "Epsilon Book").save(flush: true)
        }

        when: "querying primary datasource"
        def primaryBooks = Book.findAllByTitleLike("%Book")

        and: "querying secondary datasource"
        def secondaryBooks
        SecondBook.withTransaction {
            secondaryBooks = SecondBook.findAllByTitleLike("%Book")
        }

        then: "correct results from each datasource"
        primaryBooks.size() == 3
        secondaryBooks.size() == 2
    }

    def "where queries work on each datasource"() {
        given: "books in both datasources"
        new Book(title: "Where Test A").save(flush: true)
        new Book(title: "Where Test B").save(flush: true)

        SecondBook.withTransaction {
            new SecondBook(title: "Where Test C").save(flush: true)
        }

        when: "using where query on primary"
        def primaryResults = Book.where { title =~ 'Where Test%' }.list()

        and: "using where query on secondary"
        def secondaryResults
        SecondBook.withTransaction {
            secondaryResults = SecondBook.where { title =~ 'Where Test%' }.list()
        }

        then: "correct results from each datasource"
        primaryResults.size() == 2
        secondaryResults.size() == 1
    }

    // ========== Transaction Behavior Tests ==========

    def "transactions are independent between datasources"() {
        given: "initial counts"
        def initialPrimaryCount = Book.count()

        when: "adding to primary in a transaction"
        Book.withTransaction {
            new Book(title: "Transaction Test Primary").save(flush: true)
        }

        and: "adding to secondary in a separate transaction"
        SecondBook.withTransaction {
            new SecondBook(title: "Transaction Test Secondary").save(flush: true)
        }

        then: "primary count increased by 1"
        Book.count() == initialPrimaryCount + 1

        and: "secondary has the new book"
        SecondBook.withTransaction { SecondBook.count() } >= 1
    }

    def "rollback in one datasource does not affect other"() {
        given: "unique id for this test"
        def uniqueId = UUID.randomUUID().toString()
        def primaryTitle = "Rollback Test Primary ${uniqueId}"
        def secondaryTitle = "Rollback Test Secondary ${uniqueId}"

        when: "adding to primary in separate new transaction (will be committed)"
        Book.withNewTransaction {
            new Book(title: primaryTitle).save(flush: true)
        }

        and: "secondary new transaction that fails and rolls back"
        try {
            SecondBook.withNewTransaction { status ->
                new SecondBook(title: secondaryTitle).save(flush: true)
                status.setRollbackOnly()
            }
        } catch (ignored) {
            // Expected - transaction marked rollback-only
        }

        then: "primary book was saved (in its own committed transaction)"
        Book.withNewTransaction { Book.findByTitle(primaryTitle) } != null

        and: "secondary book was rolled back"
        SecondBook.withNewTransaction { SecondBook.findByTitle(secondaryTitle) } == null
    }

    // ========== Datasource API Tests ==========

    def "secondary namespace API provides access to secondary datasource"() {
        when: "using secondary namespace"
        SecondBook.secondary.withTransaction {
            new SecondBook(title: "Namespace Test").save(flush: true)
        }

        then: "book exists via secondary namespace"
        SecondBook.secondary.withTransaction { SecondBook.secondary.count() } >= 1
    }

    def "count operations work correctly on each datasource"() {
        given: "known number of books in each datasource"
        3.times { i ->
            new Book(title: "Count Test Primary ${i}").save(flush: true)
        }

        2.times { i ->
            SecondBook.withTransaction {
                new SecondBook(title: "Count Test Secondary ${i}").save(flush: true)
            }
        }

        expect: "counts reflect only books in respective datasources"
        Book.countByTitleLike("Count Test Primary%") == 3
        SecondBook.withTransaction { SecondBook.countByTitleLike("Count Test Secondary%") } == 2
    }

    // ========== Datasource Connection Verification ==========

    def "datasources are properly configured and distinct"() {
        expect: "both datasources are available"
        primaryDataSource != null
        secondaryDataSource != null

        and: "they are different instances"
        !primaryDataSource.is(secondaryDataSource)
    }

    def "datasources use correct connection URLs"() {
        when: "getting connection metadata"
        def primaryUrl
        def secondaryUrl

        primaryDataSource.connection.withCloseable { conn ->
            primaryUrl = conn.metaData.URL
        }

        secondaryDataSource.connection.withCloseable { conn ->
            secondaryUrl = conn.metaData.URL
        }

        then: "URLs point to different databases"
        primaryUrl.contains('devDb')
        secondaryUrl.contains('secondDb')
        primaryUrl != secondaryUrl
    }

    // ========== Batch Operations ==========

    def "batch insert works on each datasource"() {
        when: "batch inserting into primary"
        Book.withTransaction {
            (1..5).each { i ->
                new Book(title: "Batch Primary ${i}").save()
            }
        }

        and: "batch inserting into secondary"
        SecondBook.withTransaction {
            (1..3).each { i ->
                new SecondBook(title: "Batch Secondary ${i}").save()
            }
        }

        then: "all books are saved"
        Book.countByTitleLike("Batch Primary%") == 5
        SecondBook.withTransaction { SecondBook.countByTitleLike("Batch Secondary%") } == 3
    }

    def "bulk delete works on each datasource"() {
        given: "books in both datasources"
        (1..4).each { i ->
            new Book(title: "Delete Me ${i}").save(flush: true)
        }

        SecondBook.withTransaction {
            (1..2).each { i ->
                new SecondBook(title: "Delete Me ${i}").save(flush: true)
            }
        }

        when: "bulk deleting from primary"
        Book.where { title =~ 'Delete Me%' }.deleteAll()

        then: "primary books are deleted"
        Book.countByTitleLike("Delete Me%") == 0

        and: "secondary books still exist"
        SecondBook.withTransaction { SecondBook.countByTitleLike("Delete Me%") } == 2
    }

    def "executeQuery routes to secondary datasource"() {
        given: "books in both datasources"
        def primaryTitle = "Primary Query ${UUID.randomUUID()}"
        def secondaryTitle = "Secondary Query ${UUID.randomUUID()}"
        new Book(title: primaryTitle).save(flush: true)
        SecondBook.withTransaction {
            new SecondBook(title: secondaryTitle).save(flush: true)
        }

        when: "executing query on secondary"
        def results
        SecondBook.withTransaction {
            results = SecondBook.executeQuery("from ds2.Book where title = :t", [t: secondaryTitle])
        }

        then: "only secondary data is returned"
        results.size() == 1
        results.first().title == secondaryTitle
    }

    def "withCriteria routes to secondary datasource"() {
        given: "books in both datasources"
        def primaryTitle = "Primary Criteria ${UUID.randomUUID()}"
        def secondaryTitle = "Secondary Criteria ${UUID.randomUUID()}"
        new Book(title: primaryTitle).save(flush: true)
        SecondBook.withTransaction {
            new SecondBook(title: secondaryTitle).save(flush: true)
        }

        when: "executing criteria on secondary"
        def results
        SecondBook.withTransaction {
            results = SecondBook.withCriteria {
                eq('title', secondaryTitle)
            }
        }

        then: "only secondary data is returned"
        results.size() == 1
        results.first().title == secondaryTitle
    }

    def "createCriteria routes to secondary datasource"() {
        given: "books in both datasources"
        def primaryTitle = "Primary CreateCriteria ${UUID.randomUUID()}"
        def secondaryTitle = "Secondary CreateCriteria ${UUID.randomUUID()}"
        new Book(title: primaryTitle).save(flush: true)
        SecondBook.withTransaction {
            new SecondBook(title: secondaryTitle).save(flush: true)
        }

        when: "executing createCriteria on secondary"
        def results
        SecondBook.withTransaction {
            results = SecondBook.createCriteria().list {
                eq('title', secondaryTitle)
            }
        }

        then: "only secondary data is returned"
        results.size() == 1
        results.first().title == secondaryTitle
    }

    def "executeUpdate routes to secondary datasource"() {
        given: "books in both datasources"
        def primaryTitle = "Primary Update ${UUID.randomUUID()}"
        def secondaryTitle = "Secondary Update ${UUID.randomUUID()}"
        def updatedTitle = "Secondary Updated ${UUID.randomUUID()}"
        new Book(title: primaryTitle).save(flush: true)
        SecondBook.withTransaction {
            new SecondBook(title: secondaryTitle).save(flush: true)
        }

        when: "executing update on secondary"
        int updated
        SecondBook.withTransaction {
            updated = SecondBook.executeUpdate("update ds2.Book set title = :newTitle where title = :oldTitle", [newTitle: updatedTitle, oldTitle: secondaryTitle])
        }

        then: "only secondary data is updated"
        updated == 1
        SecondBook.withTransaction { SecondBook.findByTitle(updatedTitle) } != null
        Book.findByTitle(updatedTitle) == null
    }
}
