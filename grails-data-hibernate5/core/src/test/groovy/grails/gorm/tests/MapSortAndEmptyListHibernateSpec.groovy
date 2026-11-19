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
        manager.domainClasses.addAll([MsAuthor, MsBook])
    }

    void setup() {
        MsAuthor ann = new MsAuthor(name: 'ann', active: true).save(failOnError: true)
        MsAuthor bob = new MsAuthor(name: 'bob', active: true).save(failOnError: true)
        MsAuthor cat = new MsAuthor(name: 'cat', active: false).save(failOnError: true)
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
class MsAuthor {
    String name
    Boolean active
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
