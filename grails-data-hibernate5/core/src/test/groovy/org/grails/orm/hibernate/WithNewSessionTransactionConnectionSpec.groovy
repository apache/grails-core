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

import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager

import grails.gorm.annotation.Entity
import org.grails.orm.hibernate.cfg.Settings
import org.hibernate.Session
import org.springframework.jdbc.datasource.ConnectionHolder
import org.springframework.orm.hibernate5.SessionHolder
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

/**
 * A lazy data source with a pool of two connections, so that REQUIRES_NEW has a second one: a transaction that completes
 * inside withNewSession closes the connection it bound, and an outer session may be disconnected only when it holds that
 * connection. An outer session that holds the connection of a transaction of its own, or runs while the transaction that
 * would complete is suspended, keeps its connection. What is committed is read through a JDBC connection of its own,
 * because a query through the datastore can get the connection that still holds an uncommitted row.
 */
@Issue('https://github.com/apache/grails-core/issues/16783')
class WithNewSessionTransactionConnectionSpec extends Specification {

    static final String URL = 'jdbc:h2:mem:txConnectionDB;LOCK_TIMEOUT=10000'

    @AutoCleanup
    HibernateDatastore datastore

    org.hibernate.Session requestSession

    void setup() {
        datastore = new HibernateDatastore([
                (Settings.SETTING_DB_CREATE)       : 'create-drop',
                'dataSource.url'                   : URL,
                'dataSource.username'              : 'sa',
                'dataSource.password'              : '',
                'dataSource.lazy'                  : true,
                'dataSource.pooled'                : true,
                'dataSource.properties.maxActive'  : '2',
                'dataSource.properties.maxWait'    : '1000'
        ], LazyPoolAuthor, LazyPoolBook)
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

    private static int committedAuthors(String name) {
        Connection connection = DriverManager.getConnection(URL, 'sa', '')
        try {
            def rs = connection.prepareStatement("select count(*) from lazy_pool_author where name = '${name}'").executeQuery()
            rs.next()
            return rs.getInt(1)
        }
        finally {
            connection.close()
        }
    }

    private LazyPoolBook disconnectedBook() {
        LazyPoolBook book = LazyPoolBook.withTransaction { LazyPoolBook.first() }
        requestSession.disconnect()
        book
    }

    @Unroll
    void "a transaction of withNewSession that reads the outer session and then runs a #propagation scope commits, and the outer session stays usable"() {
        given:
        LazyPoolBook book = disconnectedBook()

        when: 'the outer session lazy loads with the connection of the enclosing transaction, and a nested scope suspends it'
        LazyPoolBook.withNewSession {
            LazyPoolAuthor.withTransaction {
                new LazyPoolAuthor(name: 'Tenar').save(flush: true)
                assert book.author.name == 'Stephen King'
                LazyPoolAuthor.withTransaction(propagationBehavior: behavior) { }
                assert LazyPoolAuthor.count() >= 2
            }
        }

        then: 'the row of the transaction is committed, and the outer session still answers'
        committedAuthors('Tenar') == 1
        requestSession.createQuery('from LazyPoolAuthor', LazyPoolAuthor).list().size() == 2

        where:
        propagation     | behavior
        'NOT_SUPPORTED' | TransactionDefinition.PROPAGATION_NOT_SUPPORTED
        'REQUIRES_NEW'  | TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    void "an outer session with a transaction of its own commits it, after a transaction ran in withNewSession"() {
        when:
        LazyPoolBook.withNewSession {
            org.hibernate.Transaction transaction = requestSession.beginTransaction()
            requestSession.persist(new LazyPoolAuthor(name: 'Mary'))
            requestSession.flush()
            LazyPoolAuthor.withTransaction { LazyPoolAuthor.count() }
            transaction.commit()
        }

        then: 'the connection of the transaction of the outer session was not handed back to the pool'
        committedAuthors('Mary') == 1
    }

    void "the new session of withNewSession is closed when cleaning up what it left bound throws"() {
        given:
        GrailsHibernateTemplate template = new GrailsHibernateTemplate(datastore.sessionFactory, datastore, 0)
        Session inner = null
        Connection failing = (Connection) Proxy.newProxyInstance(Connection.classLoader, [Connection] as Class[]) { Object proxy, java.lang.reflect.Method method, Object[] args ->
            if (method.name == 'isClosed') {
                throw new IllegalStateException('the connection cannot say whether it is closed')
            }
            null
        }

        when: 'the closure binds a connection holder whose connection fails when asked'
        template.executeWithNewSession { Session session ->
            inner = session
            TransactionSynchronizationManager.bindResource(template.dataSource, new ConnectionHolder(failing))
            null
        }

        then: 'the failure is reported, the new session is closed, and the outer session is bound again'
        thrown(IllegalStateException)
        inner != null
        !inner.open
        ((SessionHolder) TransactionSynchronizationManager.getResource(datastore.sessionFactory)).session.is(requestSession)
        !TransactionSynchronizationManager.hasResource(template.dataSource)
    }
}
