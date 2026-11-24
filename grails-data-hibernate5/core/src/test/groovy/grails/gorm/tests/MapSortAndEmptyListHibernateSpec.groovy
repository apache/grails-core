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
import grails.orm.HibernateCriteriaBuilder
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
        manager.domainClasses.addAll([MsPublisher, MsAuthor, MsBook, MsHomeAuthor, MsHomeBook, MsMappedSortBook])
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

    void "a map sort that goes through an embedded component of an association orders by it on the join of the association"() {
        given:
        MsHomeAuthor ann = new MsHomeAuthor(name: 'ann', home: new MsHome(city: 'rome')).save(failOnError: true)
        MsHomeAuthor bob = new MsHomeAuthor(name: 'bob', home: new MsHome(city: 'oslo')).save(failOnError: true)
        MsHomeAuthor cat = new MsHomeAuthor(name: 'cat', home: new MsHome(city: 'oslo')).save(failOnError: true)
        new MsHomeBook(title: 'one', author: ann).save(failOnError: true)
        new MsHomeBook(title: 'two', author: bob).save(failOnError: true)
        new MsHomeBook(title: 'three', author: cat).save(flush: true, failOnError: true)
        manager.session.clear()

        expect:
        MsHomeBook.createCriteria().list(sort: ['author.home.city': 'asc', 'author.name': 'desc']) { }*.title == ['three', 'two', 'one']
        MsHomeBook.createCriteria().list(sort: ['author.name': 'asc', 'author.home.city': 'asc']) { }*.title == ['one', 'two', 'three']
        MsHomeBook.createCriteria().list(sort: ['author.home.city': 'desc', 'author.name': 'asc']) { gt('id', 0L) }*.title == ['one', 'two', 'three']
        MsHomeBook.createCriteria().list(sort: ['author.home.city': 'asc', title: 'asc']) { }*.title == ['three', 'two', 'one']
    }

    void "a map sort of several properties of one association, given to a finder that takes an example or a map of properties, joins it once"() {
        given:
        MsAuthor ann = MsAuthor.findByName('ann')
        Map byAuthor = ['author.name': 'asc', 'author.active': 'desc', title: 'desc']
        Map nested = ['author.publisher.name': 'asc', 'author.publisher.country': 'asc', 'author.name': 'asc', title: 'asc']

        expect:
        MsBook.findAllWhere([author: ann], [sort: byAuthor])*.title == ['beta', 'alpha']
        MsBook.findAllWhere([author: ann], [sort: nested])*.title == ['alpha', 'beta']
        MsBook.findAll(new MsBook(), [sort: byAuthor])*.title == ['beta', 'alpha', 'zeta', 'omega']
        MsBook.findAll(new MsBook(), [sort: nested])*.title == ['zeta', 'alpha', 'beta', 'omega']
        MsBook.findWhere([author: ann], [sort: nested]).title == 'alpha'
        MsBook.find(new MsBook(), [sort: nested]).title == 'zeta'
    }

    void "a default sort of the mapping on two properties of one association joins it once, for the finders and for list"() {
        given:
        MsAuthor ann = MsAuthor.findByName('ann')
        new MsMappedSortBook(title: 'two', author: MsAuthor.findByName('bob')).save(failOnError: true)
        new MsMappedSortBook(title: 'one', author: ann).save(flush: true, failOnError: true)
        manager.session.clear()

        expect:
        MsMappedSortBook.list()*.title == ['one', 'two']
        MsMappedSortBook.findAllWhere([author: ann])*.title == ['one']
        MsMappedSortBook.findAll(new MsMappedSortBook())*.title == ['one', 'two']
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

    private static boolean inOrder(List<MsBook> books, Map<String, String> sort) {
        (0..<(books.size() - 1)).every { int i ->
            int result = 0
            sort.each { String property, String direction ->
                if (result == 0) {
                    result = valueAt(books[i], property) <=> valueAt(books[i + 1], property)
                    if (direction == 'desc') {
                        result = -result
                    }
                }
            }
            result <= 0
        }
    }

    @Unroll
    void "the nested map sort lists the #size books that #context in order"() {
        given:
        Map<String, String> sortMap = ['author.name': 'asc', 'author.publisher.name': 'desc']

        when:
        List<MsBook> found = query.call(sortMap)

        then: 'the books that the query keeps, sorted by the author and then by the publisher of the author'
        found.size() == size
        inOrder(found, sortMap)

        where:
        [context, size, query] << [
                ['have an author with an active flag', 3, { Map arg -> MsBook.createCriteria().list(sort: arg) { author { eq('active', true) } } }],
                ['have an author, in an empty block', 4, { Map arg -> MsBook.createCriteria().list(sort: arg) { author { } } }],
                ['have an author, in an or over two blocks', 4, { Map arg -> MsBook.createCriteria().list(sort: arg) { or { author { eq('active', true) }; author { eq('active', false) } } } }],
                ['are by a publisher in the us', 1, { Map arg -> MsBook.createCriteria().list(sort: arg) { author { publisher { eq('country', 'us') } } } }],
                ['have an active author, through an alias of the closure', 3, { Map arg -> MsBook.createCriteria().list(sort: arg) { createAlias('author', 'a'); eq('a.active', true) } }],
                ['have an author, through an inner join', 4, { Map arg -> MsBook.createCriteria().list(sort: arg) { author(org.hibernate.sql.JoinType.INNER_JOIN.joinTypeValue) { isNotNull('name') } } }],
                ['have an author, through a left join', 4, { Map arg -> MsBook.createCriteria().list(sort: arg) { createAlias('author', 'a', org.hibernate.sql.JoinType.LEFT_OUTER_JOIN.joinTypeValue) } }],
                ['have an author, fetched', 4, { Map arg -> MsBook.createCriteria().list(sort: arg, fetch: [author: 'join']) { gt('id', 0L) } }],
                ['have an author, paged', 4, { Map arg -> MsBook.createCriteria().list(sort: arg, max: 10) { author { isNotNull('name') } } }],
                ['are listed with ignoreCase off', 4, { Map arg -> MsBook.createCriteria().list(sort: arg, ignoreCase: false) { gt('id', 0L) } }],
                ['are listed with an order as well', 4, { Map arg -> MsBook.createCriteria().list(sort: arg, order: 'desc') { gt('id', 0L) } }],
                ['have an active author, in a where query', 3, { Map arg -> MsBook.where { author.active == true }.list(sort: arg) }],
                ['are by a publisher in the us, in a where query', 1, { Map arg -> MsBook.where { author.publisher.country == 'us' }.list(sort: arg) }],
                ['have a publisher, in a where query with nested blocks', 4, { Map arg -> MsBook.where { author { publisher { name != null } } }.list(sort: arg) }],
                ['have an active author, in findAll', 3, { Map arg -> MsBook.findAll(sort: arg) { author.active == true } }],
                ['have an author, in a dynamic finder', 4, { Map arg -> MsBook.findAllByAuthorIsNotNull(sort: arg) }],
                ['are on the second page of two', 2, { Map arg -> MsBook.createCriteria().list(sort: arg, max: 2, offset: 1) { gt('id', 0L) } }]
        ].collect { List row -> [row[0], row[1], row[2]] }
    }

    @Unroll
    void "a sort by #sort of a query whose condition already joined the author lists its #size books in order"() {
        when:
        List<MsBook> found = query.call(sort)

        then:
        found.size() == size
        inOrder(found, [(sort): direction])

        where:
        [sort, direction, size, query] << [
                ['author.name', 'asc', 3, { String arg -> MsBook.where { author.active == true }.list(sort: arg) }],
                ['author.name', 'desc', 3, { String arg -> MsBook.where { author.active == true }.list(sort: arg, order: 'desc') }],
                ['author.name', 'asc', 3, { String arg -> MsBook.findAll(sort: arg) { author.active == true } }],
                ['author.publisher.name', 'asc', 1, { String arg -> MsBook.where { author.publisher.country == 'us' }.list(sort: arg) }],
                ['author.name', 'asc', 1, { String arg -> MsBook.where { author.publisher.country == 'us' }.list(sort: arg) }],
                ['author.publisher.name', 'asc', 3, { String arg -> MsBook.where { author.active == true }.list(sort: arg) }],
                ['author.name', 'asc', 3, { String arg -> MsBook.where { author.active == true }.list(sort: arg, max: 10) }]
        ].collect { List row -> [row[0], row[1], row[2], row[3]] }
    }

    void "a map sort on a criteria builder constructed on its own sorts like the one of the domain class"() {
        given:
        Map<Map, List<String>> expectedBySort = [
                ([title: 'asc'])             : ['alpha', 'beta', 'zeta'],
                ([title: 'desc'])            : ['zeta', 'beta', 'alpha'],
                ([title: 'DESC'])            : ['zeta', 'beta', 'alpha'],
                ([title: 'asc', id: 'desc']) : ['alpha', 'beta', 'zeta'],
                ([id: 'desc', title: 'asc']) : ['alpha', 'beta', 'zeta']
        ]
        List<String> wrong = []

        when:
        expectedBySort.each { Map sort, List<String> expected ->
            Closure block = { ne('title', 'omega') }
            List standalone = new HibernateCriteriaBuilder(MsBook, manager.sessionFactory).list(sort: sort, block)*.title
            List ofClass = MsBook.createCriteria().list(sort: sort, block)*.title
            if (standalone != expected || ofClass != expected) {
                wrong << "$sort: expected $expected, standalone $standalone, of the class $ofClass".toString()
            }
        }
        List byIdFirst = new HibernateCriteriaBuilder(MsBook, manager.sessionFactory).list(sort: [id: 'desc', title: 'asc']) { }*.title
        List allTitles = new HibernateCriteriaBuilder(MsBook, manager.sessionFactory).list(sort: [title: 'desc']) { }*.title

        then:
        wrong == []
        byIdFirst == MsBook.createCriteria().list(sort: [id: 'desc']) { }*.title
        byIdFirst != allTitles
        allTitles == ['zeta', 'omega', 'beta', 'alpha']
    }

    void "a negated empty list on a collection of strings matches the books without elements"() {
        expect:
        MsBook.createCriteria().list { not { inList('tags', []) }; order('title') }*.title == ['alpha', 'beta', 'omega', 'zeta']
    }

    void "a negated empty list on one side of a negated or matches nothing, and leaving the side out would change that"() {
        expect:
        MsBook.createCriteria().list { not { or { not { inList('title', []) }; eq('title', 'alpha') } } }*.title == []
        MsBook.createCriteria().list { not { or { eq('title', 'alpha') } }; order('title') }*.title == ['beta', 'omega', 'zeta']
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

    @Unroll
    void "a map sort with #form is refused"() {
        when:
        MsBook.list(sort: sort)

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('title')

        where:
        form                | sort
        'a direction text'  | [title: 'sideways']
        'a number'          | [title: 1]
        'a null direction'  | [title: null]
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

class MsHome {
    String city
}

@Entity
class MsHomeAuthor {
    String name
    MsHome home
    static embedded = ['home']
}

@Entity
class MsHomeBook {
    String title
    MsHomeAuthor author
}

@Entity
class MsMappedSortBook {

    String title
    MsAuthor author

    static mapping = {
        sort 'author.name': 'asc', 'author.active': 'desc'
    }
}
