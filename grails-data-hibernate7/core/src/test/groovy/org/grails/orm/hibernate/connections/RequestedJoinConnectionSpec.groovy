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
package org.grails.orm.hibernate.connections

import jakarta.persistence.criteria.JoinType
import org.hibernate.Hibernate
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import grails.gorm.annotation.Entity
import org.grails.orm.hibernate.HibernateDatastore

class RequestedJoinConnectionSpec extends Specification {

    @Shared
    @AutoCleanup
    HibernateDatastore datastore = new HibernateDatastore([
            'dataSource.url': 'jdbc:h2:mem:requestedJoinDefault;DB_CLOSE_DELAY=-1',
            'dataSource.dbCreate': 'create-drop',
            'dataSources.secondary.url': 'jdbc:h2:mem:requestedJoinSecondary;DB_CLOSE_DELAY=-1',
            'dataSources.secondary.dbCreate': 'create-drop'
    ], RequestedConnectionBook, RequestedConnectionAuthor)

    void setupSpec() {
        RequestedConnectionBook.withNewTransaction {
            final author = new RequestedConnectionAuthor(name: 'Default author').save(failOnError: true)
            new RequestedConnectionBook(title: 'Default book', author: author).save(failOnError: true, flush: true)
        }
        RequestedConnectionBook.secondary.withNewTransaction {
            final author = new RequestedConnectionAuthor(name: 'Secondary author').secondary.save(failOnError: true)
            new RequestedConnectionBook(title: 'Secondary book', author: author).secondary.save(failOnError: true)
            new RequestedConnectionBook(title: 'Secondary unlinked').secondary.save(failOnError: true, flush: true)
        }
    }

    void 'a named list with #fetch and paging #paged retains its connection'() {
        given:
        final arguments = [fetch: [author: fetch]]
        if (paged) {
            arguments.max = 10
        }

        when:
        final results = withBothConnections {
            final page = RequestedConnectionBook.secondary.list(arguments)
            if (paged) {
                page.totalCount
            }
            page
        }

        then:
        results*.title.sort() == (fetch == JoinType.INNER ? ['Secondary book'] : ['Secondary book', 'Secondary unlinked'])
        !paged || results.totalCount == results.size()
        results.findAll { it.author != null }.every { Hibernate.isInitialized(it.author) }

        where:
        [fetch, paged] << [[JoinType.LEFT, JoinType.INNER], [false, true]].combinations()
    }

    void 'a nested fetch mode retains the named connection'() {
        when:
        final results = withBothConnections {
            final page = RequestedConnectionBook.secondary.list(fetch: ['author.books': 'join'], max: 10)
            page.totalCount
            page
        }

        then:
        results*.title.sort() == ['Secondary book', 'Secondary unlinked']
        results.totalCount == 2
        Hibernate.isInitialized(results.find { it.author != null }.author.books)
    }

    void 'a named #method with a requested join retains its connection'() {
        when:
        final result = withBothConnections {
            RequestedConnectionBook.secondary."$method"([title: 'Secondary book'], [fetch: [author: JoinType.INNER]])
        }

        then:
        (result instanceof List ? result*.title : [result?.title]) == ['Secondary book']

        where:
        method << ['findWhere', 'findAllWhere']
    }

    void 'a bag fetch with a sort retains the named connection'() {
        when:
        final results = withBothConnections {
            final page = RequestedConnectionAuthor.secondary.list(fetch: [books: 'join'], sort: 'name', max: 10)
            page.totalCount
            page
        }

        then:
        results*.name == ['Secondary author']
        results.totalCount == 1
        Hibernate.isInitialized(results.first().books)
        results.first().books*.title == ['Secondary book']
    }

    void 'criteria fallbacks respect the scoped connection and an explicit default override'() {
        expect:
        withBothConnections {
            assert RequestedConnectionBook.list(fetch: [author: JoinType.INNER])*.title == ['Secondary book']
            assert RequestedConnectionBook.'default'.list(fetch: [author: JoinType.INNER])*.title == ['Default book']
            true
        }
    }

    private static Object withBothConnections(Closure work) {
        RequestedConnectionBook.withNewSession {
            RequestedConnectionBook.secondary.withNewSession {
                work.call()
            }
        }
    }
}

@Entity
class RequestedConnectionBook {
    String title
    RequestedConnectionAuthor author
    static belongsTo = [RequestedConnectionAuthor]
    static mapping = { datasource 'ALL' }
    static constraints = { author nullable: true }
}

@Entity
class RequestedConnectionAuthor {
    String name
    Collection<RequestedConnectionBook> books
    static hasMany = [books: RequestedConnectionBook]
    static mapping = { datasource 'ALL' }
}
