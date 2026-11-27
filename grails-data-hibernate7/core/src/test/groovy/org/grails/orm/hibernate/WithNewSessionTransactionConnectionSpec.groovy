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

import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet

import org.grails.orm.hibernate.cfg.Settings
import org.grails.orm.hibernate.support.hibernate7.SessionHolder
import org.hibernate.Session
import org.hibernate.Transaction
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.resource.jdbc.spi.LogicalConnectionImplementor
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

/**
 * A transaction that begins inside withNewSession disconnects the outer (request) session before it completes, and only
 * when that session holds the connection of the transaction that is completing. A session that holds another connection,
 * such as the one of its own transaction, keeps it, and the transaction itself is not cut off in the middle by a
 * connection that went back to the pool. The pool has two connections, as REQUIRES_NEW needs a second one, and what was
 * committed is read through a connection of its own, as a query through the datastore could get the connection that
 * still holds an uncommitted row and pass for the wrong reason.
 */
@Issue('https://github.com/apache/grails-core/issues/16571')
class WithNewSessionTransactionConnectionSpec extends Specification {

    static final String URL = 'jdbc:h2:mem:txConnectionDB;LOCK_TIMEOUT=10000'

    static final String USER = 'committer'

    static final String PASSWORD = 'committer-password'

    @AutoCleanup
    HibernateDatastore datastore

    Session requestSession

    void setup() {
        datastore = new HibernateDatastore([
            (Settings.SETTING_DB_CREATE): 'create-drop',
            'dataSource.url': URL,
            'dataSource.username': USER,
            'dataSource.password': PASSWORD,
            'dataSource.lazy': true,
            'dataSource.pooled': true,
            'dataSource.properties.maxActive': '2',
            'dataSource.properties.maxWait': '2000'
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

    private LazyPoolBook bookOfADisconnectedRequestSession() {
        LazyPoolBook book = LazyPoolBook.withTransaction { LazyPoolBook.first() }
        LogicalConnectionImplementor logicalConnection = ((SessionImplementor) requestSession).jdbcCoordinator.logicalConnection
        if (logicalConnection.isOpen() && logicalConnection.isPhysicallyConnected()) {
            logicalConnection.manualDisconnect()
        }
        book
    }

    private static int committedAuthors(String name) {
        Connection connection = DriverManager.getConnection(URL, USER, PASSWORD)
        try {
            PreparedStatement statement = connection.prepareStatement('select count(*) from lazy_pool_author where name = ?')
            statement.setString(1, name)
            ResultSet rows = statement.executeQuery()
            rows.next()
            return rows.getInt(1)
        }
        finally {
            connection.close()
        }
    }

    @Unroll
    void "a transaction in withNewSession commits when a nested #scope scope suspends it and the request session lazy loads on its connection"() {
        given: 'a request session that holds a book whose author is lazy, and no connection'
        LazyPoolBook book = bookOfADisconnectedRequestSession()

        when: 'the transaction saves a row, the request session lazy loads, and a nested scope suspends the transaction'
        LazyPoolBook.withNewSession {
            LazyPoolBook.withTransaction {
                new LazyPoolAuthor(name: 'committed').save(flush: true)
                assert book.author.name == 'Stephen King'
                LazyPoolBook.withTransaction(propagationBehavior: propagation) { }
                LazyPoolBook.count()
            }
        }

        then: 'the row is committed'
        committedAuthors('committed') == 1

        where:
        scope          | propagation
        'NOT_SUPPORTED' | TransactionDefinition.PROPAGATION_NOT_SUPPORTED
        'REQUIRES_NEW'  | TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    void "a request session that begins a transaction of its own in withNewSession keeps its connection when a transaction there ends"() {
        given: 'a request session that holds no connection when the scope begins'
        bookOfADisconnectedRequestSession()

        when: 'the scope has the request session begin a transaction, persist a row and flush it, a transaction begins and ends in the scope, and the request session commits'
        LazyPoolBook.withNewSession {
            Transaction transaction = requestSession.beginTransaction()
            requestSession.persist(new LazyPoolAuthor(name: 'of the request'))
            requestSession.flush()
            LazyPoolBook.withTransaction { LazyPoolBook.count() }
            transaction.commit()
        }

        then: 'the row of the request session is committed'
        committedAuthors('of the request') == 1
    }
}
