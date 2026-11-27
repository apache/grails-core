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

/**
 * A lazy data source with a pool of one connection: withNewSession must not hold a second connection for the outer
 * session, or a transaction in the closure that then reads the outer session waits for a connection that never comes.
 */
@Issue('https://github.com/apache/grails-core/issues/16783')
class WithNewSessionLazyPoolSpec extends Specification {

    @AutoCleanup
    HibernateDatastore datastore

    org.apache.tomcat.jdbc.pool.DataSource tomcatDataSource
    org.hibernate.Session requestSession

    void setup() {
        Map config = [
            (Settings.SETTING_DB_CREATE): 'create-drop',
            'dataSource.url': 'jdbc:h2:mem:lazyPoolDB;LOCK_TIMEOUT=10000',
            'dataSource.lazy': true,
            'dataSource.pooled': true,
            'dataSource.properties.maxActive': '1',
            'dataSource.properties.maxWait': '500'
        ]
        datastore = new HibernateDatastore(config, LazyPoolAuthor, LazyPoolBook)
        DataSource ds = datastore.connectionSources.defaultConnectionSource.dataSource
        Object target = ds
        while (!(target instanceof org.apache.tomcat.jdbc.pool.DataSource)) {
            target = ((org.springframework.jdbc.datasource.DelegatingDataSource) target).targetDataSource
        }
        tomcatDataSource = (org.apache.tomcat.jdbc.pool.DataSource) target
        LazyPoolAuthor.withTransaction {
            LazyPoolAuthor author = new LazyPoolAuthor(name: 'Stephen King').save(flush: true)
            new LazyPoolBook(title: 'The Stand', author: author).save(flush: true)
        }
        requestSession = datastore.sessionFactory.openSession()
        TransactionSynchronizationManager.bindResource(datastore.sessionFactory, new SessionHolder(requestSession))
    }

    void cleanup() {
        if (TransactionSynchronizationManager.hasResource(datastore.sessionFactory)) {
            TransactionSynchronizationManager.unbindResource(datastore.sessionFactory)
        }
        requestSession?.close()
    }

    void "a transaction in withNewSession that reads the outer session does not need a second connection"() {
        given: 'the outer session holds a book whose author is lazy, and no connection'
        LazyPoolBook book = LazyPoolBook.withTransaction { LazyPoolBook.first() }
        requestSession.disconnect()
        assert tomcatDataSource.pool.active == 0

        when: 'a SUPPORTS scope runs a transaction in withNewSession and then reads the outer session'
        String name = LazyPoolBook.withTransaction(propagationBehavior: TransactionDefinition.PROPAGATION_SUPPORTS, readOnly: true) {
            LazyPoolBook.withNewSession {
                LazyPoolBook.withTransaction {
                    LazyPoolBook.count()
                    book.author.name
                }
            }
        }

        then: 'the pool of one served both'
        name == 'Stephen King'
        tomcatDataSource.pool.active == 0
    }

    void "an outer session that lazy loads as the first use in the inner transaction is left without a connection"() {
        given:
        LazyPoolBook book = LazyPoolBook.withTransaction { LazyPoolBook.first() }
        requestSession.disconnect()

        when:
        LazyPoolBook.withNewSession {
            LazyPoolBook.withTransaction {
                assert book.author.name == 'Stephen King'
                new LazyPoolAuthor(name: 'Tenar').save(flush: true)
            }
        }

        then: 'the next query of the outer session gets a connection of its own'
        requestSession.createQuery('from LazyPoolAuthor', LazyPoolAuthor).list().size() == 2
    }

    void "an outer session that lazy loads after sql ran in the inner transaction is left without a connection"() {
        given:
        LazyPoolBook book = LazyPoolBook.withTransaction { LazyPoolBook.first() }
        requestSession.disconnect()

        when:
        LazyPoolBook.withNewSession {
            LazyPoolBook.withTransaction {
                new LazyPoolAuthor(name: 'Tenar').save(flush: true)
                assert book.author.name == 'Stephen King'
            }
        }

        then:
        requestSession.createQuery('from LazyPoolAuthor', LazyPoolAuthor).list().size() == 2
    }

    void "an outer session is usable after the inner transaction ends but inside the scope"() {
        given:
        LazyPoolBook book = LazyPoolBook.withTransaction { LazyPoolBook.first() }
        requestSession.disconnect()

        when:
        int seen = LazyPoolBook.withNewSession {
            LazyPoolBook.withTransaction {
                assert book.author.name == 'Stephen King'
            }
            requestSession.createQuery('from LazyPoolAuthor', LazyPoolAuthor).list().size()
        }

        then:
        seen == 1
        requestSession.createQuery('from LazyPoolAuthor', LazyPoolAuthor).list().size() == 1
    }

    void "an outer session is usable after the inner transaction ends inside a SUPPORTS scope"() {
        given:
        LazyPoolBook book = LazyPoolBook.withTransaction { LazyPoolBook.first() }
        requestSession.disconnect()

        when:
        int seen = LazyPoolBook.withTransaction(propagationBehavior: TransactionDefinition.PROPAGATION_SUPPORTS, readOnly: true) {
            LazyPoolBook.withNewSession {
                LazyPoolBook.withTransaction {
                    assert book.author.name == 'Stephen King'
                }
                requestSession.createQuery('from LazyPoolAuthor', LazyPoolAuthor).list().size()
            }
        }

        then:
        seen == 1
        requestSession.createQuery('from LazyPoolAuthor', LazyPoolAuthor).list().size() == 1
    }

    @Unroll
    void "a #hook callback of the inner transaction that lazy loads the outer session leaves it a usable connection"() {
        given:
        LazyPoolBook book = LazyPoolBook.withTransaction { LazyPoolBook.first() }
        requestSession.disconnect()
        Closure load = { assert book.author.name == 'Stephen King' }
        String cbHook = hook
        int cbOrder = callbackOrder

        when: 'the callback reads the author of the request session while the transaction completes'
        LazyPoolBook.withNewSession {
            LazyPoolBook.withNewSession {
                LazyPoolBook.withTransaction {
                    LazyPoolBook.count()
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
        requestSession.createQuery('from LazyPoolAuthor', LazyPoolAuthor).list().size() == 1

        where:
        hook               | callbackOrder
        'beforeCommit'     | Ordered.LOWEST_PRECEDENCE
        'beforeCompletion' | Ordered.LOWEST_PRECEDENCE
        'beforeCompletion' | Ordered.HIGHEST_PRECEDENCE
        'afterCommit'      | Ordered.LOWEST_PRECEDENCE
        'afterCompletion'  | Ordered.LOWEST_PRECEDENCE
        'afterCompletion'  | Ordered.HIGHEST_PRECEDENCE
    }

    void "an outer session that lazy loads outside any transaction in the scope keeps no connection after it"() {
        given:
        LazyPoolBook book = LazyPoolBook.withTransaction { LazyPoolBook.first() }
        requestSession.disconnect()

        when:
        LazyPoolBook.withNewSession {
            assert book.author.name == 'Stephen King'
        }

        then:
        tomcatDataSource.pool.active == 0
    }
}

@Entity
class LazyPoolAuthor {
    String name
}

@Entity
class LazyPoolBook {
    String title
    LazyPoolAuthor author
}
