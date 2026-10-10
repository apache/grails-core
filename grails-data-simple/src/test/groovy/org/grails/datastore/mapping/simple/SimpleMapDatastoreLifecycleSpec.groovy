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
package org.grails.datastore.mapping.simple

import org.springframework.transaction.PlatformTransactionManager
import spock.lang.Specification

import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.events.DefaultApplicationEventPublisher
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.FixedTenantResolver
import org.grails.datastore.mapping.transactions.DatastoreTransactionManager
import org.grails.datastore.mapping.transactions.Transaction

class SimpleMapDatastoreLifecycleSpec extends Specification {

    void 'a datastore exposes its backing map, indices and transaction manager'() {
        given:
        SimpleMapDatastore datastore = new SimpleMapDatastore(SmlBook)

        expect:
        datastore.backingMap != null
        datastore.indices != null
        datastore.transactionManager instanceof DatastoreTransactionManager
        datastore.transactionManager instanceof PlatformTransactionManager
        datastore.connectionSources.defaultConnectionSource.name == ConnectionSource.DEFAULT
        datastore.multiTenancyMode == MultiTenancySettings.MultiTenancyMode.NONE
        datastore.tenantResolver != null
        datastore.getDatastoreForTenantId(null).is(datastore)
        datastore.applicationEventPublisher instanceof DefaultApplicationEventPublisher
        datastore.mappingContext.getPersistentEntity(SmlBook.name) != null

        when:
        datastore.backingMap.put('x', [:])
        datastore.indices.put('y', 1)
        datastore.clearData()

        then:
        datastore.backingMap.isEmpty()
        datastore.indices.isEmpty()

        cleanup:
        datastore.close()
    }

    void 'sessions are backed by the datastore map and use no-op transactions'() {
        given:
        SimpleMapDatastore datastore = new SimpleMapDatastore(SmlBook)

        when:
        Session session = datastore.connect()
        Transaction transaction = session.beginTransaction()

        then:
        session instanceof SimpleMapSession
        ((SimpleMapSession) session).backingMap.is(datastore.backingMap)
        session.nativeInterface.is(datastore.backingMap)
        !session.isPendingAlready(new SmlBook())
        session.getPersister(SmlBook) != null
        session.getPersister(String) == null
        transaction.active
        transaction.nativeTransaction.is(transaction)
        session.hasTransaction()

        when:
        transaction.setTimeout(5)
        transaction.commit()
        transaction.rollback()

        then:
        noExceptionThrown()

        cleanup:
        session.disconnect()
        datastore.close()
    }

    void 'schema multi tenancy adds child datastores per tenant'() {
        given:
        Map config = [(Settings.SETTING_MULTI_TENANCY_MODE): MultiTenancySettings.MultiTenancyMode.SCHEMA,
                      (Settings.SETTING_MULTI_TENANT_RESOLVER): new FixedTenantResolver('t1')]
        SimpleMapDatastore datastore = new SimpleMapDatastore(DatastoreUtils.createPropertyResolver(config), new DefaultApplicationEventPublisher(), SmlBook)

        when:
        datastore.addTenantForSchema('t1')
        datastore.addTenantForSchema(ConnectionSource.DEFAULT)

        then:
        datastore.multiTenancyMode == MultiTenancySettings.MultiTenancyMode.DATABASE
        datastore.tenantResolver instanceof FixedTenantResolver
        datastore.getDatastoreForTenantId('t1') instanceof SimpleMapDatastore
        !datastore.getDatastoreForTenantId('t1').is(datastore)
        datastore.getDatastoreForConnection('t1').is(datastore.getDatastoreForTenantId('t1'))
        datastore.getDatastoreForConnection(ConnectionSource.DEFAULT).is(datastore)
        !datastore.getDatastoreForTenantId('t1').backingMap.is(datastore.backingMap)

        when:
        Session bound = datastore.withNewSession('t1') { Session s -> s }

        then:
        bound.datastore.is(datastore.getDatastoreForTenantId('t1'))
        !bound.connected || !DatastoreUtils.hasSession(datastore.getDatastoreForTenantId('t1'))

        cleanup:
        datastore.close()
    }

    void 'discriminator multi tenancy always resolves to the same datastore'() {
        given:
        Map config = [(Settings.SETTING_MULTI_TENANCY_MODE): MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
                      (Settings.SETTING_MULTI_TENANT_RESOLVER): new FixedTenantResolver('t1')]
        SimpleMapDatastore datastore = new SimpleMapDatastore(DatastoreUtils.createPropertyResolver(config), new DefaultApplicationEventPublisher(), SmlBook)

        expect:
        datastore.multiTenancyMode == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR
        datastore.getDatastoreForTenantId('anything').is(datastore)

        cleanup:
        datastore.close()
    }

}

@Entity
class SmlBook {

    Long id
    String title

}
