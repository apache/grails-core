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

import grails.gorm.annotation.Entity
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.orm.hibernate.proxy.HibernateProxyHandler
import org.springframework.orm.hibernate5.SessionHolder
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.Issue

/**
 * Verifies that the request-bound session remains valid after a lazy load
 * occurs within a nested withNewSession { withTransaction { ... } } block.
 */
@Issue('https://github.com/apache/grails-core/issues/16783')
class WithNewSessionLazyLoadSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    void setupSpec() {
        manager.isTransactional = false
        manager.domainClasses.addAll([WnsllBook, WnsllAuthor])
    }

    void cleanup() {
        WnsllAuthor.withTransaction {
            WnsllBook.where {}.deleteAll()
            WnsllAuthor.where {}.deleteAll()
        }
        manager.sessionFactory.currentSession.clear()
    }

    void "request-bound session remains usable after lazy load inside withNewSession with inner transaction"() {
        given: "a seeded author and book"
        WnsllAuthor.withTransaction {
            WnsllAuthor author = new WnsllAuthor(name: 'Stephen King').save()
            new WnsllBook(title: 'The Stand', author: author).save()
        }
        manager.sessionFactory.currentSession.clear()

        and: "an active request-bound session in TSM"
        manager.sessionFactory.currentSession
        SessionHolder requestSessionHolder = TransactionSynchronizationManager.getResource(manager.sessionFactory)

        when: "loading an entity in the request session without a transaction"
        WnsllBook book = WnsllBook.first()
        HibernateProxyHandler proxyHandler = new HibernateProxyHandler()
        assert !proxyHandler.isInitialized(book, 'author')

        and: "running and ending a transaction on the request session"
        WnsllAuthor.withTransaction { WnsllAuthor.count() }

        and: "calling withNewSession with an inner transaction that lazy-loads an association on the request session entity"
        WnsllBook.withNewSession {
            WnsllBook.withTransaction {
                assert book.author.name == 'Stephen King'
                new WnsllAuthor(name: 'Tenar').save()
            }
        }

        then: "the request-bound session remains open and usable"
        requestSessionHolder.session.isOpen()
        WnsllBook.count() == 1
        WnsllAuthor.count() == 2
        book.refresh()
        book.title == 'The Stand'
        book.author.name == 'Stephen King'
    }

    void "request-bound session remains usable after nested withNewSession inside SUPPORTS transaction"() {
        given: "a seeded author and book"
        WnsllAuthor.withTransaction {
            WnsllAuthor author = new WnsllAuthor(name: 'Ursula').save()
            new WnsllBook(title: 'Earthsea', author: author).save()
        }
        manager.sessionFactory.currentSession.clear()

        and: "an active request-bound session in TSM"
        manager.sessionFactory.currentSession
        SessionHolder requestSessionHolder = TransactionSynchronizationManager.getResource(manager.sessionFactory)

        when: "disconnecting the request session via an initial transaction"
        WnsllAuthor.withTransaction { WnsllAuthor.count() }

        and: "running a SUPPORTS read-only transaction with a nested withNewSession and inner transaction"
        WnsllBook.withTransaction(propagationBehavior: TransactionDefinition.PROPAGATION_SUPPORTS, readOnly: true) {
            WnsllBook.withNewSession {
                WnsllBook.withTransaction { new WnsllAuthor(name: 'Tenar').save(flush: true) }
            }
            assert WnsllBook.count() == 1
        }

        then: "the request-bound session remains open and usable"
        requestSessionHolder.session.isOpen()
        WnsllBook.count() == 1
        WnsllAuthor.count() == 2
    }

    void "request-bound session remains usable after nested withNewSession inside SUPPORTS transaction without in-scope query"() {
        given: "a seeded author and book"
        WnsllAuthor.withTransaction {
            WnsllAuthor author = new WnsllAuthor(name: 'Ursula').save()
            new WnsllBook(title: 'Earthsea', author: author).save()
        }
        manager.sessionFactory.currentSession.clear()

        and: "an active request-bound session in TSM"
        manager.sessionFactory.currentSession
        SessionHolder requestSessionHolder = TransactionSynchronizationManager.getResource(manager.sessionFactory)

        when: "disconnecting the request session via an initial transaction"
        WnsllAuthor.withTransaction { WnsllAuthor.count() }

        and: "running a SUPPORTS read-only transaction with a nested withNewSession without in-scope query"
        WnsllBook.withTransaction(propagationBehavior: TransactionDefinition.PROPAGATION_SUPPORTS, readOnly: true) {
            WnsllBook.withNewSession { WnsllAuthor.count() }
        }

        then: "the request-bound session remains open and usable after the scope completes"
        requestSessionHolder.session.isOpen()
        WnsllBook.count() == 1
    }

    void "request-bound session remains usable after lazy load inside withNewSession under SUPPORTS transaction"() {
        given: "a seeded author and book"
        WnsllAuthor.withTransaction {
            WnsllAuthor author = new WnsllAuthor(name: 'Ursula').save()
            new WnsllBook(title: 'Earthsea', author: author).save()
        }
        manager.sessionFactory.currentSession.clear()

        and: "an active request-bound session in TSM"
        manager.sessionFactory.currentSession
        SessionHolder requestSessionHolder = TransactionSynchronizationManager.getResource(manager.sessionFactory)

        when: "loading an entity in the request session without a transaction"
        WnsllBook book = WnsllBook.first()
        HibernateProxyHandler proxyHandler = new HibernateProxyHandler()
        assert !proxyHandler.isInitialized(book, 'author')

        and: "disconnecting the request session via an initial transaction"
        WnsllAuthor.withTransaction { WnsllAuthor.count() }

        and: "calling withNewSession under a SUPPORTS scope with an inner transaction that lazy-loads"
        WnsllBook.withTransaction(propagationBehavior: TransactionDefinition.PROPAGATION_SUPPORTS, readOnly: true) {
            WnsllBook.withNewSession {
                WnsllBook.withTransaction {
                    assert book.author.name == 'Ursula'
                    new WnsllAuthor(name: 'Tenar').save(flush: true)
                }
            }
        }

        then: "the request-bound session remains open and usable"
        requestSessionHolder.session.isOpen()
        WnsllBook.count() == 1
        WnsllAuthor.count() == 2
        book.refresh()
        book.title == 'Earthsea'
        book.author.name == 'Ursula'
    }

    void "request-bound session remains usable after nested withNewSession inside SUPPORTS transaction without in-scope query"() {
        given: "an initial transaction to ensure the request session is disconnected"
        manager.sessionFactory.currentSession
        SessionHolder requestSessionHolder = TransactionSynchronizationManager.getResource(manager.sessionFactory)
        WnsllAuthor.withTransaction { WnsllAuthor.count() }

        when: "calling withNewSession under SUPPORTS without touching the outer session in the scope"
        WnsllBook.withTransaction(propagationBehavior: TransactionDefinition.PROPAGATION_SUPPORTS, readOnly: true) {
            WnsllBook.withNewSession {
                WnsllAuthor.count()
            }
        }

        then: "the request session remains usable after the scope completes"
        requestSessionHolder.session.isOpen()
        WnsllBook.count() == 0
    }

    void "active outer transaction commits uncommitted writes when nested withNewSession is executed"() {
        when: "starting an active transaction, inserting without flush, calling withNewSession, and inserting after"
        WnsllAuthor.withTransaction {
            new WnsllAuthor(name: 'Brandon').save()
            WnsllBook.withNewSession {
                assert WnsllBook.count() == 0
            }
            new WnsllAuthor(name: 'Patrick').save()
        }

        then: "both inserted authors are committed and persisted"
        WnsllAuthor.count() == 2
        WnsllAuthor.findByName('Brandon') != null
        WnsllAuthor.findByName('Patrick') != null
    }

    void "request-bound session remains usable when SUPPORTS scope executes nested withNewSession and starts REQUIRED transaction"() {
        given: "a seeded author and book"
        WnsllAuthor.withTransaction {
            WnsllAuthor author = new WnsllAuthor(name: 'Ursula').save()
            new WnsllBook(title: 'Earthsea', author: author).save()
        }
        manager.sessionFactory.currentSession.clear()

        and: "an active request-bound session in TSM"
        manager.sessionFactory.currentSession
        SessionHolder requestSessionHolder = TransactionSynchronizationManager.getResource(manager.sessionFactory)

        when: "disconnecting the request session via an initial transaction"
        WnsllAuthor.withTransaction { WnsllAuthor.count() }

        and: "running a SUPPORTS scope that calls withNewSession, followed by a REQUIRED transaction that suspends the scope"
        WnsllBook.withTransaction(propagationBehavior: TransactionDefinition.PROPAGATION_SUPPORTS, readOnly: true) {
            WnsllBook.withNewSession {
                WnsllAuthor.count()
            }
            WnsllAuthor.withTransaction(propagationBehavior: TransactionDefinition.PROPAGATION_REQUIRED) {
                new WnsllAuthor(name: 'Tenar').save(flush: true)
            }
        }

        then: "the request-bound session remains open and can execute subsequent queries without closed-connection errors"
        requestSessionHolder.session.isOpen()
        WnsllBook.count() == 1
        WnsllAuthor.count() == 2
    }
}

@Entity
class WnsllAuthor {
    String name
}

@Entity
class WnsllBook {
    String title
    WnsllAuthor author
    static constraints = {
        author nullable: true
    }
}
