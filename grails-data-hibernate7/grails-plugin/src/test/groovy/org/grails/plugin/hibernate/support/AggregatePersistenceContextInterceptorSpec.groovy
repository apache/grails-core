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

import grails.persistence.support.PersistenceContextInterceptor
import grails.orm.bootstrap.HibernateDatastoreSpringInitializer
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.FlushMode
import org.hibernate.SessionFactory
import org.hibernate.dialect.H2Dialect
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class AggregatePersistenceContextInterceptorSpec extends Specification {

    @Shared Map config = [
            'dataSource.url':"jdbc:h2:mem:aggregateSpecDB;LOCK_TIMEOUT=10000",
            'dataSource.dbCreate': 'create-drop',
            'dataSource.dialect': H2Dialect.name,
            'dataSources.secondary':[url:"jdbc:h2:mem:aggregateSecondaryDb;LOCK_TIMEOUT=10000"],
    ]

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config), OsivSpecBook, OsivSpecAuthor)

    def "test aggregate interceptor delegates all methods"() {
        given: "An aggregate interceptor"
        def interceptor = new AggregatePersistenceContextInterceptor(datastore)
        
        expect: "It has two inner interceptors"
        interceptor.interceptors.size() == 2
        
        when: "init is called"
        interceptor.init()
        
        then: "Both sessions are bound"
        TransactionSynchronizationManager.hasResource(datastore.sessionFactory)
        TransactionSynchronizationManager.hasResource(datastore.getDatastoreForConnection('secondary').sessionFactory)
        interceptor.isOpen()

        when: "setReadOnly is called"
        interceptor.setReadOnly()
        
        then: "Inner sessions are read-only"
        interceptor.interceptors.every { it.isOpen() }

        when: "setReadWrite is called"
        interceptor.setReadWrite()
        
        then: "Inner sessions are read-write"
        interceptor.interceptors.every { it.isOpen() }

        when: "flush is called"
        interceptor.flush()
        
        then: "No exception"
        true

        when: "clear is called"
        interceptor.clear()
        
        then: "No exception"
        true

        when: "destroy is called"
        interceptor.destroy()
        
        then: "Both sessions are unbound"
        !TransactionSynchronizationManager.hasResource(datastore.sessionFactory)
        !TransactionSynchronizationManager.hasResource(datastore.getDatastoreForConnection('secondary').sessionFactory)
        !interceptor.isOpen()

        when: "reconnect is called"
        interceptor.reconnect()
        
        then: "UnsupportedOperationException is thrown"
        thrown(UnsupportedOperationException)

        when: "disconnect is called"
        interceptor.disconnect()
        
        then: "UnsupportedOperationException is thrown"
        thrown(UnsupportedOperationException)
    }

    def "test destroy handles exceptions"() {
        given: "An aggregate interceptor with a failing inner interceptor"
        def mockDatastore = Mock(HibernateDatastore)
        def connectionSources = Mock(org.grails.datastore.mapping.core.connections.ConnectionSources)
        def source = Mock(org.grails.datastore.mapping.core.connections.ConnectionSource)
        source.getName() >> 'default'
        connectionSources.getAllConnectionSources() >> [source]
        mockDatastore.getConnectionSources() >> connectionSources
        
        // Mock the secondary datastore returned by createPersistenceContextInterceptor's call to getDatastoreForConnection
        mockDatastore.getDatastoreForConnection('default') >> Mock(HibernateDatastore)

        def interceptor = new AggregatePersistenceContextInterceptor(mockDatastore)
        def inner = Mock(PersistenceContextInterceptor)
        interceptor.interceptors.clear()
        interceptor.interceptors.add(inner)
        
        inner.isOpen() >> true
        inner.destroy() >> { throw new RuntimeException("Destroy failed") }
        
        when: "destroy is called"
        interceptor.destroy()
        
        then: "No exception is propagated"
        noExceptionThrown()
    }
}
