/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.data.testing.tck.tests

import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.WqBiAuthor
import org.apache.grails.data.testing.tck.domains.WqBiBook
import org.apache.grails.data.testing.tck.domains.WqBookAuthor
import org.apache.grails.data.testing.tck.domains.WqFoo
import org.apache.grails.data.testing.tck.domains.WqGroupedItem
import org.apache.grails.data.testing.tck.domains.WqPhrase
import org.apache.grails.data.testing.tck.domains.WqRole
import org.apache.grails.data.testing.tck.domains.WqRoleUser
import org.apache.grails.data.testing.tck.domains.WqScientificBook
import org.apache.grails.data.testing.tck.domains.WqSentence
import org.apache.grails.data.testing.tck.domains.WqStudent
import org.apache.grails.data.testing.tck.domains.WqThing
import org.apache.grails.data.testing.tck.domains.WqUserRole
import org.apache.grails.data.testing.tck.domains.WqWord
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Requires

/**
 * Regression tests for historical where-query issues, verified against every
 * GORM implementation.
 */
class WhereQueryIssueVerificationSpec extends GrailsDataTckSpec {

    void setupSpec() {
        manager.registerDomainClasses(
                WqFoo, WqWord, WqPhrase, WqSentence,
                WqScientificBook, WqBookAuthor,
                WqThing,
                WqUserRole, WqRoleUser, WqRole,
                WqStudent,
                WqGroupedItem,
                WqBiBook, WqBiAuthor
        )
    }

    @Issue('https://github.com/apache/grails-core/issues/14596')
    void 'where-query returning wrong result if expression not assigned to variable'() {
        given: 'a Foo with bar set to a non-null value'
        new WqFoo(bar: 'something').save(flush: true)

        when: 'querying inline for records where bar == null'
        long inlineCount = WqFoo.where { bar == null }.count()

        and: 'querying with variable assignment for records where bar == null'
        def criteria = WqFoo.where { bar == null }
        long variableCount = criteria.count()

        then: 'both should return 0 since no Foo has bar == null'
        inlineCount == 0
        variableCount == 0
    }

    @Issue('https://github.com/apache/grails-core/issues/14596')
    void 'where-query inline vs variable produces same result with matching records'() {
        given: 'Foos with null and non-null bar values'
        new WqFoo(bar: null).save(flush: true)
        new WqFoo(bar: 'something').save(flush: true)

        when: 'querying inline'
        long inlineCount = WqFoo.where { bar == null }.count()

        and: 'querying with variable'
        def criteria = WqFoo.where { bar == null }
        long variableCount = criteria.count()

        then: 'both should return 1'
        inlineCount == 1
        variableCount == 1
    }

    // Restricting by a nested association path requires join support: MongoDB rejects
    // join queries and the simple in-memory datastore cannot traverse two levels.
    @Requires({ Boolean.getBoolean('hibernate5.gorm.suite') || Boolean.getBoolean('hibernate7.gorm.suite') })
    @Issue('https://github.com/apache/grails-core/issues/14622')
    void 'where-query with multi-level association restriction produces correct result'() {
        given: 'a sentence -> phrase -> word hierarchy'
        def sentence = new WqSentence(text: 'Hello World').save(flush: true)
        def phrase1 = new WqPhrase(text: 'Hello', sentence: sentence).save(flush: true)
        def phrase2 = new WqPhrase(text: 'World', sentence: sentence).save(flush: true)
        new WqWord(text: 'Hel', phrase: phrase1).save(flush: true)
        new WqWord(text: 'lo', phrase: phrase1).save(flush: true)
        new WqWord(text: 'Wor', phrase: phrase2).save(flush: true)
        new WqWord(text: 'ld', phrase: phrase2).save(flush: true)

        when: 'querying words by sentence via multi-level association'
        def words = WqWord.where {
            phrase.sentence == sentence
        }.list()

        then: 'all 4 words belonging to the sentence should be returned'
        words.size() == 4
    }

    @Issue('https://github.com/apache/grails-core/issues/14480')
    void 'countBy dynamic finder works correctly with where queries'() {
        given: 'scientific and non-scientific books by different authors'
        def author1 = new WqBookAuthor(name: 'Author A').save(flush: true)
        def author2 = new WqBookAuthor(name: 'Author B').save(flush: true)
        new WqScientificBook(title: 'Science 1', scientific: true, author: author1).save(flush: true)
        new WqScientificBook(title: 'Science 2', scientific: true, author: author1).save(flush: true)
        new WqScientificBook(title: 'Novel 1', scientific: false, author: author1).save(flush: true)
        new WqScientificBook(title: 'Science 3', scientific: true, author: author2).save(flush: true)

        when: 'using countByAuthor on a where query filtering scientific books'
        def scientificBooks = WqScientificBook.where { scientific == true }
        long findCount = scientificBooks.findAllByAuthor(author1).size()
        long countResult = scientificBooks.countByAuthor(author1)

        then: 'countByAuthor should match findAllByAuthor count'
        findCount == 2
        countResult == 2
    }

    @Issue('https://github.com/apache/grails-core/issues/11202')
    void 'where queries in tests filter results correctly'() {
        given: 'two things with different names'
        new WqThing(name: 'thing 1').save(flush: true)
        new WqThing(name: 'thing 2').save(flush: true)

        when: 'querying with where inline'
        def inlineResult = WqThing.where { name == 'thing 1' }.list()

        then: 'where query filters correctly'
        inlineResult.size() == 1

        when: 'querying from a closure'
        def queryClosure = { -> WqThing.where { name == 'thing 1' } }
        def closureResult = queryClosure.call().list()

        then: 'closure-based where query filters correctly'
        closureResult.size() == 1
    }

    @Issue('https://github.com/apache/grails-core/issues/14636')
    void 'many-to-many queries with sorting do not throw exception'() {
        given: 'users and roles in a many-to-many relationship'
        def role1 = new WqRole(name: 'ADMIN').save(flush: true)
        def role2 = new WqRole(name: 'USER').save(flush: true)
        def user1 = new WqRoleUser(username: 'alice').save(flush: true)
        def user2 = new WqRoleUser(username: 'bob').save(flush: true)
        new WqUserRole(user: user1, role: role1).save(flush: true)
        new WqUserRole(user: user2, role: role2).save(flush: true)
        new WqUserRole(user: user1, role: role2).save(flush: true)

        when: 'querying UserRole by role and sorting by user.username'
        def results = WqUserRole.where {
            role == role1
        }.list(sort: 'user.username')

        then: 'no exception is thrown and results are correct'
        noExceptionThrown()
        results.size() == 1
        results[0].user.username == 'alice'
    }

    // The simple in-memory datastore does not support querying basic collection types.
    @IgnoreIf({ Boolean.getBoolean('simple.gorm.suite') })
    @Issue('https://github.com/apache/grails-core/issues/14610')
    void 'querying association with basic collection types works'() {
        given: 'students with basic collection type (hasMany String)'
        def s1 = new WqStudent(name: 'Alice', email: 'alice@test.com').save(flush: true)
        s1.addToSchools('School1')
        s1.addToSchools('School2')
        s1.save(flush: true)

        def s2 = new WqStudent(name: 'Bob', email: 'bob@test.com').save(flush: true)
        s2.addToSchools('School2')
        s2.addToSchools('School3')
        s2.save(flush: true)

        when: 'querying students by school using criteria'
        def emails = WqStudent.createCriteria().list {
            'in'('schools', ['School1'])
            projections {
                property 'email'
            }
        }

        then: 'the query works without error'
        noExceptionThrown()
        emails.contains('alice@test.com')
    }

    // groupProperty projections are only implemented by the Hibernate datastores.
    @Requires({ Boolean.getBoolean('hibernate5.gorm.suite') || Boolean.getBoolean('hibernate7.gorm.suite') })
    @Issue('https://github.com/apache/grails-core/issues/14569')
    void 'count() gives correct results with projection in where query'() {
        given: 'items with different groupings'
        (1..12).each { new WqGroupedItem(itemGroup: 1, itemValue: "a${it}").save() }
        (1..16).each { new WqGroupedItem(itemGroup: 2, itemValue: "b${it}").save() }
        (1..9).each { new WqGroupedItem(itemGroup: 3, itemValue: "c${it}").save() }
        (1..18).each { new WqGroupedItem(itemGroup: 4, itemValue: "d${it}").save() }
        (1..5).each { new WqGroupedItem(itemGroup: 5, itemValue: "e${it}").save(flush: true) }

        when: 'creating a where query with groupProperty and count projections'
        def c = WqGroupedItem.where {
            projections {
                groupProperty 'itemGroup'
                count()
            }
        }
        def groups = c.list()

        then: 'list returns the correct number of groups'
        groups.size() == 5

        and: 'count returns the number of groups, not the count from first projection row'
        c.count() == 5
    }

    // MongoDB rejects the association criteria here as an unsupported join query.
    @IgnoreIf({ Boolean.getBoolean('mongodb.gorm.suite') })
    @Issue('https://github.com/apache/grails-core/issues/14600')
    void 'findAllBy works with bidirectional hasMany relation'() {
        given: 'authors with books in a bidirectional hasMany'
        def author1 = new WqBiAuthor(name: 'Stephen King').save(flush: true)
        def book1 = new WqBiBook(title: 'IT').save(flush: true)
        def book2 = new WqBiBook(title: 'The Shining').save(flush: true)
        author1.addToBooks(book1)
        author1.addToBooks(book2)
        book1.addToAuthors(author1)
        book2.addToAuthors(author1)
        author1.save(flush: true)

        when: 'using withCriteria to find books by author'
        def books = WqBiBook.withCriteria {
            authors {
                'in'('id', [author1.id])
            }
        }

        then: 'books are found without error'
        noExceptionThrown()
        books.size() == 2
    }
}
