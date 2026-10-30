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
import org.springframework.transaction.TransactionDefinition
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Specification

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
