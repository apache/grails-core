/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.plugin.hibernate.support

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.SessionFactory
import org.hibernate.dialect.H2Dialect
import org.grails.orm.hibernate.support.hibernate7.SessionHolder
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class HibernatePersistenceContextInterceptorSpec extends Specification {

    @Shared Map config = [
            'dataSource.url': 'jdbc:h2:mem:hpciSpecDB;LOCK_TIMEOUT=10000',
            'dataSource.dbCreate': 'create-drop',
            'dataSource.dialect': H2Dialect.name,
            'hibernate.flush.mode': 'COMMIT',
            'hibernate.hbm2ddl.auto': 'create-drop',
    ]

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config), HpciBook)

    def setup() {
        SessionFactory sf = datastore.sessionFactory
        if (TransactionSynchronizationManager.hasResource(sf)) {
            TransactionSynchronizationManager.unbindResource(sf)
        }
    }

    def cleanup() {
        SessionFactory sf = datastore.sessionFactory
        if (TransactionSynchronizationManager.hasResource(sf)) {
            TransactionSynchronizationManager.unbindResource(sf)
        }
    }

    def 'test init and destroy with real objects'() {
        given: 'A persistence context interceptor'
        def interceptor = new HibernatePersistenceContextInterceptor()
        interceptor.setHibernateDatastore(datastore)
        SessionFactory sf = datastore.sessionFactory

        expect: 'No session bound initially'
        !TransactionSynchronizationManager.hasResource(sf)

        when: 'init is called'
        interceptor.init()

        then: 'a session is bound'
        TransactionSynchronizationManager.hasResource(sf)
        TransactionSynchronizationManager.getResource(sf) instanceof SessionHolder

        when: 'destroy is called'
        interceptor.destroy()

        then: 'the session is unbound'
        !TransactionSynchronizationManager.hasResource(sf)
    }

    def 'test nesting init and destroy'() {
        given: 'A persistence context interceptor'
        def interceptor = new HibernatePersistenceContextInterceptor()
        interceptor.setHibernateDatastore(datastore)
        SessionFactory sf = datastore.sessionFactory

        when: 'init is called twice'
        interceptor.init()
        interceptor.init()

        then: 'a session is bound'
        TransactionSynchronizationManager.hasResource(sf)

        when: 'destroy is called once'
        interceptor.destroy()

        then: 'the session remains bound due to nesting'
        TransactionSynchronizationManager.hasResource(sf)

        when: 'destroy is called again'
        interceptor.destroy()

        then: 'the session is finally unbound'
        !TransactionSynchronizationManager.hasResource(sf)
    }

    def 'test flush and clear'() {
        given: 'A persistence context interceptor and a manually-bound Hibernate session'
        def interceptor = new HibernatePersistenceContextInterceptor()
        interceptor.setHibernateDatastore(datastore)
        def sf = datastore.sessionFactory
        def nativeSession = sf.openSession()
        TransactionSynchronizationManager.bindResource(sf, new SessionHolder(nativeSession))

        when: 'Operations are called within the session context'
        interceptor.init()
        interceptor.clear()
        interceptor.flush()
        interceptor.destroy()

        then: 'no exception occurs'
        noExceptionThrown()

        cleanup:
        if (TransactionSynchronizationManager.hasResource(sf)) {
            TransactionSynchronizationManager.unbindResource(sf)
        }
        nativeSession.close()
    }
}

@Entity
class HpciBook {

    String title
}
