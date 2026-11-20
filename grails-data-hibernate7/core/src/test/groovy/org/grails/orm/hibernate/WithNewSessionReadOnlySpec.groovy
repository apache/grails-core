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
package org.grails.orm.hibernate

import grails.gorm.annotation.Entity
import org.grails.orm.hibernate.cfg.Settings
import org.hibernate.Session
import org.grails.orm.hibernate.support.hibernate7.SessionHolder
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Specification

/**
 * With the open-session read-only setting on and a session bound for the request, withNewSession hands its closure a
 * session that must stay writable: the closure commits its own write transaction on it.
 */
@Issue('https://github.com/apache/grails-core/issues/16571')
class WithNewSessionReadOnlySpec extends Specification {

    @AutoCleanup
    HibernateDatastore datastore

    Session requestSession

    void setup() {
        datastore = new HibernateDatastore([
            (Settings.SETTING_DB_CREATE): 'create-drop',
            'dataSource.url': 'jdbc:h2:mem:withNewSessionReadOnly;LOCK_TIMEOUT=10000',
            'hibernate.osiv.readonly': true
        ], ReadOnlyScopeBook)
        assert datastore.osivReadOnly
        ReadOnlyScopeBook.withNewSession { Session session ->
            session.beginTransaction()
            session.persist(new ReadOnlyScopeBook(title: 'before'))
            session.transaction.commit()
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

    void "a change the closure commits on the new session is kept"() {
        when:
        ReadOnlyScopeBook.withNewSession { Session session ->
            session.beginTransaction()
            ReadOnlyScopeBook book = session.createQuery("from ReadOnlyScopeBook b where b.title = 'before'", ReadOnlyScopeBook).uniqueResult()
            book.title = 'after'
            session.transaction.commit()
        }
        int changed = ReadOnlyScopeBook.withNewSession { Session session ->
            session.createQuery("select count(b) from ReadOnlyScopeBook b where b.title = 'after'", Long).uniqueResult().intValue()
        }

        then:
        changed == 1
    }
}

@Entity
class ReadOnlyScopeBook {
    String title
}
