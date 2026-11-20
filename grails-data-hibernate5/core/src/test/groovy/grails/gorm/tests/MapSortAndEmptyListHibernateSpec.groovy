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

package grails.gorm.tests

import grails.gorm.annotation.Entity
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.Issue
import spock.lang.Unroll

/**
 * A map of sort properties and an empty in list, tried against the blocks of the same query: a sort on an association
 * the query's block already joined reuses that join, and an empty list is an explicit false before any join is made.
 */
@Issue('https://github.com/apache/grails-core/issues/17008')
class MapSortAndEmptyListHibernateSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([MsPublisher, MsAuthor, MsBook])
    }

    void setup() {
        MsPublisher north = new MsPublisher(name: 'north', country: 'us').save(failOnError: true)
        MsPublisher south = new MsPublisher(name: 'south', country: 'de').save(failOnError: true)
        MsAuthor ann = new MsAuthor(name: 'ann', active: true, publisher: south).save(failOnError: true)
        MsAuthor bob = new MsAuthor(name: 'bob', active: true, publisher: north).save(failOnError: true)
        MsAuthor cat = new MsAuthor(name: 'cat', active: false, publisher: south).save(failOnError: true)
        new MsBook(title: 'zeta', author: bob, tags: ['x', 'y']).save(failOnError: true)
        new MsBook(title: 'beta', author: ann).save(failOnError: true)
        new MsBook(title: 'alpha', author: ann, tags: ['x']).save(failOnError: true)
        new MsBook(title: 'omega', author: cat).save(flush: true, failOnError: true)
        manager.session.clear()
    }

    void "a map sort on an association the block already joined reuses the join"() {
        expect:
        MsBook.createCriteria().list(sort: ['author.name': 'asc', title: 'desc']) {
            author { eq('active', true) }
        }*.title == ['beta', 'alpha', 'zeta']
    }

    void "a map sort keeps the order of its properties across joined and plain ones"() {
        expect:
        MsBook.createCriteria().list(sort: [title: 'asc', 'author.name': 'desc']) {
            author { eq('active', true) }
        }*.title == ['alpha', 'beta', 'zeta']

        and: 'the joined property first'
        MsBook.createCriteria().list(sort: ['author.name': 'desc', title: 'asc']) {
            author { eq('active', true) }
        }*.title == ['zeta', 'alpha', 'beta']
    }

    void "a map sort on an association without a block of its own still joins it"() {
        expect:
        MsBook.createCriteria().list(sort: ['author.name': 'desc', title: 'asc']) {
            gt('id', 0L)
        }*.title == ['omega', 'zeta', 'alpha', 'beta']
    }

    void "a map sort of two properties of the same association joins it once"() {
        expect:
        MsBook.list(sort: ['author.name': 'asc', 'author.active': 'desc'])*.author*.name == ['ann', 'ann', 'bob', 'cat']
        MsBook.createCriteria().list(sort: ['author.active': 'desc', 'author.name': 'desc', title: 'asc']) {
            gt('id', 0L)
        }*.title == ['zeta', 'alpha', 'beta', 'omega']
    }

    private static Object valueAt(Object book, String path) {
        path.split('\\.').inject(book) { Object current, String name -> current[name] }
    }

    private static List<String> sortedTitles(Map<String, String> sort) {
        List<MsBook> books = MsBook.list()
        books.sort(false) { MsBook a, MsBook b ->
            int result = 0
            sort.each { String property, String direction ->
                if (result == 0) {
                    result = valueAt(a, property) <=> valueAt(b, property)
                    if (direction == 'desc') {
                        result = -result
                    }
                }
            }
            result
        }*.title
    }

    @Unroll
    void "a map sort #sort of a query with #context lists the books in that order"() {
        given:
        Map<String, String> sortMap = new LinkedHashMap<String, String>(sort)
        List<String> expected = sortedTitles(sortMap)

        when:
        List<MsBook> found = query.call(sortMap)

        then:
        found*.title == expected

        where:
        [sort, context, query] << [
                [
                        ['author.name': 'asc', 'author.active': 'desc'],
                        ['author.name': 'asc', 'author.publisher.name': 'desc'],
                        ['author.publisher.name': 'desc', 'author.name': 'asc'],
                        ['author.publisher.name': 'asc', 'author.publisher.country': 'desc', title: 'asc'],
                        [title: 'desc', 'author.publisher.name': 'asc', 'author.name': 'desc'],
                        ['author.publisher.country': 'asc', 'author.active': 'asc', title: 'asc']
                ],
                [
                        ['no block of its own', { Map arg -> MsBook.createCriteria().list(sort: arg) { gt('id', 0L) } }],
                        ['a block on the author', { Map arg -> MsBook.createCriteria().list(sort: arg) { author { isNotNull('name') } } }],
                        ['a block on the author and its publisher', { Map arg ->
                            MsBook.createCriteria().list(sort: arg) { author { publisher { isNotNull('name') } } }
                        }],
                        ['paged list', { Map arg -> MsBook.list(sort: arg, max: 10) }],
                        ['where query', { Map arg -> MsBook.where { title != null }.list(sort: arg) }],
                        ['findAll with a closure', { Map arg -> MsBook.findAll(sort: arg) { title != null } }],
                        ['dynamic finder', { Map arg -> MsBook.findAllByTitleIsNotNull(sort: arg) }],
                        ['paged where query', { Map arg -> MsBook.where { title != null }.list(sort: arg, max: 10) }]
                ]
        ].combinations().collect { List row -> [row[0], row[1][0], row[1][1]] }
    }

    void "a negated empty list on a collection of strings matches the books without elements"() {
        expect:
        MsBook.createCriteria().list { not { inList('tags', []) }; order('title') }*.title == ['alpha', 'beta', 'omega', 'zeta']
    }

    void "an empty list on a collection of strings matches nothing"() {
        expect:
        MsBook.createCriteria().list { inList('tags', []) } == []
    }

    @Unroll
    void "an empty array: #form"() {
        expect:
        query.call() == expected

        where:
        form                                    | query                                                                                                    | expected
        'a plain property'                      | { -> MsBook.createCriteria().list { inList('title', [] as Object[]) }*.title }                         | []
        'a plain property, negated'             | { -> MsBook.createCriteria().list { not { inList('title', [] as Object[]) }; order('title') }*.title } | ['alpha', 'beta', 'omega', 'zeta']
        'a collection of strings, negated'      | { -> MsBook.createCriteria().list { not { inList('tags', [] as Object[]) }; order('title') }*.title }  | ['alpha', 'beta', 'omega', 'zeta']
        'a collection of strings, with values'  | { -> MsBook.createCriteria().list { inList('tags', ['y'] as Object[]) }*.title }                        | ['zeta']
    }
}

@Entity
class MsPublisher {
    String name
    String country
}

@Entity
class MsAuthor {
    String name
    Boolean active
    MsPublisher publisher
}

@Entity
class MsBook {
    String title
    MsAuthor author
    static hasMany = [tags: String]
    static constraints = {
        author nullable: true
    }
}
