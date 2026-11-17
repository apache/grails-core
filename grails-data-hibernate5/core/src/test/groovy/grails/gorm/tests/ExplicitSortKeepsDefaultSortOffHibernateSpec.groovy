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

import spock.lang.Issue

import grails.gorm.annotation.Entity
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * A sort that the call gives replaces the default sort of the mapping, whether it is a string or a map, and a map
 * of sort properties joins what the query has not joined and reuses what it has, down to the start of its path.
 */
@Issue('https://github.com/apache/grails-core/issues/14653')
class ExplicitSortKeepsDefaultSortOffHibernateSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([EsPublisher, EsAuthor, EsBook])
    }

    private void seed() {
        EsPublisher zed = new EsPublisher(name: 'zed').save(failOnError: true)
        EsPublisher abe = new EsPublisher(name: 'abe').save(failOnError: true)
        EsAuthor first = new EsAuthor(name: 'amy', publisher: zed).save(failOnError: true)
        EsAuthor second = new EsAuthor(name: 'bea', publisher: abe).save(failOnError: true)
        new EsBook(title: 'c', author: first).save(failOnError: true)
        new EsBook(title: 'b', author: second).save(failOnError: true)
        new EsBook(title: 'a').save(flush: true, failOnError: true)
        manager.session.clear()
    }

    void 'a map sort does not bring back the default sort through an association, so a book without an author stays'() {
        given:
        seed()

        expect: 'the mapping sorts by author.name, and the sort the call gives replaces it'
        EsBook.createCriteria().list(sort: [title: 'asc']) { }*.title == ['a', 'b', 'c']
    }

    void 'a string sort on a joined association does not bring back the default sort either'() {
        given:
        seed()

        expect:
        EsBook.createCriteria().list(sort: 'author.name', order: 'desc') {
            author {
                isNotNull('name')
            }
        }*.title == ['b', 'c']
    }

    void 'a map sort through a path of which the query joined the start reuses that join'() {
        given:
        seed()

        expect:
        EsBook.createCriteria().list(sort: ['author.publisher.name': 'asc']) {
            author {
                isNotNull('name')
            }
        }*.title == ['b', 'c']
    }

    void 'a string sort through a path of which the query joined the start reuses that join'() {
        given:
        seed()

        expect:
        EsBook.createCriteria().list(sort: 'author.publisher.name', order: 'desc') {
            author {
                isNotNull('name')
            }
        }*.title == ['c', 'b']
    }

    void 'a map sort through a path of which the query joined the whole path reuses that join'() {
        given:
        seed()

        expect:
        EsBook.createCriteria().list(sort: ['author.publisher.name': 'desc']) {
            author {
                publisher {
                    isNotNull('name')
                }
            }
        }*.title == ['c', 'b']
    }
}

@Entity
class EsPublisher {
    String name
}

@Entity
class EsAuthor {
    String name
    EsPublisher publisher
}

@Entity
class EsBook {
    String title
    EsAuthor author

    static constraints = {
        author(nullable: true)
    }

    static mapping = {
        sort 'author.name'
    }
}
