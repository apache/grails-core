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
package org.grails.orm.hibernate

import javax.sql.DataSource

import grails.gorm.annotation.Entity
import org.grails.orm.hibernate.cfg.Settings
import org.springframework.core.Ordered
import org.springframework.orm.hibernate5.SessionHolder
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

@Issue('https://github.com/apache/grails-core/issues/16783')
class WithNewSessionNonLazyPoolSpec extends Specification {

    @AutoCleanup
    HibernateDatastore datastore

    org.apache.tomcat.jdbc.pool.DataSource tomcatDataSource
    org.hibernate.Session requestSession

    void setup() {
        Map config = [
            (Settings.SETTING_DB_CREATE): 'create-drop',
            'dataSource.url': 'jdbc:h2:mem:nonlazyPoolDB;LOCK_TIMEOUT=10000',
            'dataSource.lazy': false,
            'dataSource.pooled': true,
            'dataSource.properties.maxActive': '1',
            'dataSource.properties.maxWait': '500'
        ]
        datastore = new HibernateDatastore(config, NonLazyAuthor, NonLazyBook)
        DataSource ds = datastore.connectionSources.defaultConnectionSource.dataSource
        tomcatDataSource = (org.apache.tomcat.jdbc.pool.DataSource) ds.targetDataSource

        requestSession = datastore.sessionFactory.openSession()
        org.springframework.transaction.support.TransactionSynchronizationManager.bindResource(
                datastore.sessionFactory, new org.springframework.orm.hibernate5.SessionHolder(requestSession))
    }

    void cleanup() {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.hasResource(datastore.sessionFactory)) {
            org.springframework.transaction.support.TransactionSynchronizationManager.unbindResource(datastore.sessionFactory)
        }
        requestSession?.close()
        datastore?.close()
    }

    void "withNewSession works with non-lazy single connection pool after initial transaction disconnects session"() {
        given:
        NonLazyAuthor.withTransaction { NonLazyAuthor.count() }
        requestSession.disconnect()
        assert tomcatDataSource.pool.active == 0

        when: "withNewSession executes a query"
        NonLazyBook.withNewSession { NonLazyAuthor.count() }

        then: "single connection pool does not exhaust and active connections return to zero"
        tomcatDataSource.pool.active == 0
    }

    void "withNewSession with inner transaction works with non-lazy single connection pool"() {
        given:
        NonLazyAuthor.withTransaction { NonLazyAuthor.count() }
        requestSession.disconnect()
        assert tomcatDataSource.pool.active == 0

        when: "calling withNewSession with inner transaction that saves"
        NonLazyBook.withNewSession {
            NonLazyBook.withTransaction {
                new NonLazyAuthor(name: 'x').save(flush: true)
            }
        }

        then: "active connections return to zero and write is persisted"
        tomcatDataSource.pool.active == 0
        NonLazyAuthor.count() == 1
    }

    void "withNewSession with inner transaction works under SUPPORTS transaction with non-lazy single connection pool"() {
        given:
        NonLazyAuthor.withTransaction { NonLazyAuthor.count() }
        requestSession.disconnect()
        assert tomcatDataSource.pool.active == 0

        when: "calling withNewSession under SUPPORTS transaction with inner transaction"
        NonLazyBook.withTransaction(propagationBehavior: TransactionDefinition.PROPAGATION_SUPPORTS, readOnly: true) {
            NonLazyBook.withNewSession {
                NonLazyBook.withTransaction {
                    new NonLazyAuthor(name: 'x').save(flush: true)
                }
            }
        }

        then: "active connections return to zero and write is persisted"
        tomcatDataSource.pool.active == 0
        NonLazyAuthor.count() == 1
    }

    void "lazy loading in inner transaction with non-lazy pool drops closed connection on restore"() {
        given: "a seeded author and book"
        NonLazyAuthor author = null
        NonLazyAuthor.withTransaction {
            author = new NonLazyAuthor(name: 'Stephen King').save(flush: true)
            new NonLazyBook(title: 'The Stand', author: author).save(flush: true)
        }

        and: "disconnecting the request session via an initial transaction"
        NonLazyAuthor.withTransaction { NonLazyAuthor.count() }
        requestSession.disconnect()
        assert tomcatDataSource.pool.active == 0

        when: "calling withNewSession where inner transaction fetches book and lazy-loads association"
        NonLazyBook.withNewSession {
            NonLazyBook.withTransaction {
                NonLazyBook book = NonLazyBook.first()
                assert book.author.name == 'Stephen King'
                new NonLazyAuthor(name: 'Tenar').save(flush: true)
            }
        }

        then: "the request-bound session remains usable and pool active connections return to zero"
        tomcatDataSource.pool.active == 0
        NonLazyBook.count() == 1
        NonLazyAuthor.count() == 2
    }

    @Unroll
    void "a #hook callback of the inner transaction that lazy loads the outer session leaves it a usable connection with a non-lazy pool"() {
        given:
        NonLazyBook.withTransaction {
            NonLazyAuthor author = new NonLazyAuthor(name: 'Stephen King').save(flush: true)
            new NonLazyBook(title: 'The Stand', author: author).save(flush: true)
        }
        NonLazyBook book = NonLazyBook.withTransaction { NonLazyBook.first() }
        requestSession.disconnect()
        Closure load = { assert book.author.name == 'Stephen King' }
        String cbHook = hook
        int cbOrder = callbackOrder

        when: 'the callback reads the author of the request session while the transaction completes'
        NonLazyBook.withNewSession {
            NonLazyBook.withNewSession {
                NonLazyBook.withTransaction {
                    NonLazyBook.count()
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        int getOrder() {
                            cbOrder
                        }

                        @Override
                        void beforeCommit(boolean readOnly) {
                            if (cbHook == 'beforeCommit') {
                                load()
                            }
                        }

                        @Override
                        void beforeCompletion() {
                            if (cbHook == 'beforeCompletion') {
                                load()
                            }
                        }

                        @Override
                        void afterCommit() {
                            if (cbHook == 'afterCommit') {
                                load()
                            }
                        }

                        @Override
                        void afterCompletion(int status) {
                            if (cbHook == 'afterCompletion') {
                                load()
                            }
                        }
                    })
                }
            }
        }

        then: 'the next query of the request session does not use the connection the transaction closed'
        requestSession.createQuery('from NonLazyAuthor', NonLazyAuthor).list().size() == 1

        where:
        hook               | callbackOrder
        'beforeCommit'     | Ordered.LOWEST_PRECEDENCE
        'beforeCompletion' | Ordered.LOWEST_PRECEDENCE
        'beforeCompletion' | Ordered.HIGHEST_PRECEDENCE
        'afterCommit'      | Ordered.LOWEST_PRECEDENCE
        'afterCompletion'  | Ordered.LOWEST_PRECEDENCE
        'afterCompletion'  | Ordered.HIGHEST_PRECEDENCE
    }

    void "an outer session is usable after the inner transaction ends inside the scope with a non-lazy pool"() {
        given: "a seeded author and book, and a request session holding the book"
        NonLazyAuthor.withTransaction {
            NonLazyAuthor author = new NonLazyAuthor(name: 'Stephen King').save(flush: true)
            new NonLazyBook(title: 'The Stand', author: author).save(flush: true)
        }
        NonLazyBook book = requestSession.createQuery('from NonLazyBook', NonLazyBook).list().first()
        requestSession.disconnect()
        assert tomcatDataSource.pool.active == 0

        when: "the inner transaction lazy-loads on the outer session and the outer session is queried afterwards in the scope"
        int seen = NonLazyBook.withNewSession {
            NonLazyBook.withTransaction {
                assert book.author.name == 'Stephen King'
            }
            requestSession.createQuery('from NonLazyAuthor', NonLazyAuthor).list().size()
        }

        then: "the outer session got a connection of its own and none is held after the scope"
        seen == 1
        tomcatDataSource.pool.active == 0
    }

    void "an outer session is usable after the inner transaction ends inside a SUPPORTS scope with a non-lazy pool"() {
        given:
        NonLazyAuthor.withTransaction {
            NonLazyAuthor author = new NonLazyAuthor(name: 'Stephen King').save(flush: true)
            new NonLazyBook(title: 'The Stand', author: author).save(flush: true)
        }
        NonLazyBook book = requestSession.createQuery('from NonLazyBook', NonLazyBook).list().first()
        requestSession.disconnect()

        when:
        int seen = NonLazyBook.withTransaction(propagationBehavior: TransactionDefinition.PROPAGATION_SUPPORTS, readOnly: true) {
            NonLazyBook.withNewSession {
                NonLazyBook.withTransaction {
                    assert book.author.name == 'Stephen King'
                }
                requestSession.createQuery('from NonLazyAuthor', NonLazyAuthor).list().size()
            }
        }

        then:
        seen == 1
        requestSession.createQuery('from NonLazyAuthor', NonLazyAuthor).list().size() == 1
    }

    void "withNewSession runs and rebinds holder when outer request session was closed before scope"() {
        given: "a closed outer request session with holder still bound"
        SessionHolder boundHolder =
                (SessionHolder) TransactionSynchronizationManager.getResource(datastore.sessionFactory)
        requestSession.close()
        assert !requestSession.isOpen()

        when: "running withNewSession"
        int count = NonLazyAuthor.withNewSession {
            NonLazyAuthor.count()
        }

        then: "the closure executes and the closed holder is restored"
        count == 0
        TransactionSynchronizationManager.getResource(datastore.sessionFactory).is(boundHolder)
    }
}

@Entity
class NonLazyAuthor {
    String name
}

@Entity
class NonLazyBook {
    String title
    NonLazyAuthor author
    static constraints = {
        author nullable: true
    }
}
