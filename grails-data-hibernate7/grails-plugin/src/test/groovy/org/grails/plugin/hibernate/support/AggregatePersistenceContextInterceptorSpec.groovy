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
package org.grails.plugin.hibernate.support

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.support.hibernate7.SessionHolder
import org.hibernate.FlushMode
import org.hibernate.SessionFactory
import org.hibernate.dialect.H2Dialect
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class AggregatePersistenceContextInterceptorSpec extends Specification {

    @Shared Map config = [
            'dataSource.url':"jdbc:h2:mem:aggPciSpecDB;LOCK_TIMEOUT=10000",
            'dataSource.dbCreate': 'create-drop',
            'dataSource.dialect': H2Dialect.name,
            'hibernate.flush.mode': 'COMMIT',
            'hibernate.hbm2ddl.auto': 'create-drop',
            'dataSources.secondary':[url:"jdbc:h2:mem:aggPciSecondaryDB;LOCK_TIMEOUT=10000"],
    ]

    @Shared @AutoCleanup HibernateDatastore datastore =
            new HibernateDatastore(DatastoreUtils.createPropertyResolver(config), AggPciBook, AggPciAuthor)

    SessionFactory defaultSessionFactory = datastore.sessionFactory
    SessionFactory secondarySessionFactory = datastore.getDatastoreForConnection('secondary').sessionFactory

    def setup() {
        unbindIfBound(defaultSessionFactory)
        unbindIfBound(secondarySessionFactory)
    }

    def cleanup() {
        unbindIfBound(defaultSessionFactory)
        unbindIfBound(secondarySessionFactory)
    }

    private static void unbindIfBound(SessionFactory sf) {
        if (TransactionSynchronizationManager.hasResource(sf)) {
            TransactionSynchronizationManager.unbindResource(sf)
        }
    }

    def "init and destroy open and close a session for every configured data source"() {
        given: "an aggregate interceptor built from a multi-datasource HibernateDatastore"
        def interceptor = new AggregatePersistenceContextInterceptor(datastore)

        expect: "no session bound for either datasource initially"
        !TransactionSynchronizationManager.hasResource(defaultSessionFactory)
        !TransactionSynchronizationManager.hasResource(secondarySessionFactory)

        when: "init is called"
        interceptor.init()

        then: "a session is bound for both the default and secondary datasource"
        TransactionSynchronizationManager.getResource(defaultSessionFactory) instanceof SessionHolder
        TransactionSynchronizationManager.getResource(secondarySessionFactory) instanceof SessionHolder

        when: "destroy is called"
        interceptor.destroy()

        then: "both sessions are unbound"
        !TransactionSynchronizationManager.hasResource(defaultSessionFactory)
        !TransactionSynchronizationManager.hasResource(secondarySessionFactory)
    }

    def "isOpen reports true while at least one delegate session is open"() {
        given: "an aggregate interceptor"
        def interceptor = new AggregatePersistenceContextInterceptor(datastore)

        expect: "not open before init"
        !interceptor.isOpen()

        when: "init is called"
        interceptor.init()

        then: "isOpen reports true"
        interceptor.isOpen()

        when: "destroy is called"
        interceptor.destroy()

        then: "isOpen reports false again"
        !interceptor.isOpen()
    }

    def "flush and clear propagate to every configured data source"() {
        given: "an aggregate interceptor with an open session on each datasource"
        def interceptor = new AggregatePersistenceContextInterceptor(datastore)
        interceptor.init()

        when: "an entity is saved on each datasource and flush/clear are called"
        new AggPciBook(title: 'default-book').save()
        new AggPciAuthor(name: 'secondary-author').save()
        interceptor.flush()
        interceptor.clear()
        interceptor.destroy()

        then: "no exception occurs and both entities are persisted"
        noExceptionThrown()
        AggPciBook.withNewSession { AggPciBook.findByTitle('default-book') != null }
        AggPciAuthor.withNewSession { AggPciAuthor.findByName('secondary-author') != null }

        cleanup:
        AggPciBook.withTransaction { AggPciBook.list()*.delete(flush: true) }
        AggPciAuthor.withTransaction { AggPciAuthor.list()*.delete(flush: true) }
    }

    def "setReadOnly and setReadWrite propagate the flush mode to every configured data source"() {
        given: "an aggregate interceptor with an open session on each datasource"
        def interceptor = new AggregatePersistenceContextInterceptor(datastore)
        interceptor.init()

        when: "setReadOnly is called"
        interceptor.setReadOnly()

        then: "both sessions switch to manual flushing"
        sessionFlushMode(defaultSessionFactory) == FlushMode.MANUAL
        sessionFlushMode(secondarySessionFactory) == FlushMode.MANUAL

        when: "setReadWrite is called"
        interceptor.setReadWrite()

        then: "both sessions switch back to automatic flushing"
        sessionFlushMode(defaultSessionFactory) == FlushMode.AUTO
        sessionFlushMode(secondarySessionFactory) == FlushMode.AUTO

        cleanup:
        interceptor.destroy()
    }

    private static FlushMode sessionFlushMode(SessionFactory sf) {
        ((SessionHolder) TransactionSynchronizationManager.getResource(sf)).session.hibernateFlushMode
    }

    def "disconnect propagates the unsupported-operation failure from the underlying Hibernate 7 interceptor"() {
        given: "an aggregate interceptor with an open session on each datasource"
        def interceptor = new AggregatePersistenceContextInterceptor(datastore)
        interceptor.init()

        when: "disconnect is called"
        interceptor.disconnect()

        then: "the underlying per-datasource interceptor's unsupported-operation failure propagates"
        thrown(UnsupportedOperationException)

        cleanup:
        interceptor.destroy()
    }

    def "reconnect propagates the unsupported-operation failure from the underlying Hibernate 7 interceptor"() {
        given: "an aggregate interceptor with an open session on each datasource"
        def interceptor = new AggregatePersistenceContextInterceptor(datastore)
        interceptor.init()

        when: "reconnect is called"
        interceptor.reconnect()

        then: "the underlying per-datasource interceptor's unsupported-operation failure propagates"
        thrown(UnsupportedOperationException)

        cleanup:
        interceptor.destroy()
    }
}

@Entity
class AggPciBook {
    String title
}

@Entity
class AggPciAuthor {
    String name

    static mapping = {
        datasource 'secondary'
    }
}
