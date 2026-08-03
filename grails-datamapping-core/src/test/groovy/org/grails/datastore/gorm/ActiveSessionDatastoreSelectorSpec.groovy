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
package org.grails.datastore.gorm

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import org.grails.datastore.mapping.transactions.SessionHolder
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.Specification

class ActiveSessionDatastoreSelectorSpec extends Specification {

    void setup() {
        GormRegistry.reset()
    }

    void cleanup() {
        GormRegistry.reset()
    }

    void 'select returns active datastore when it matches the registered class datastore'() {
        given:
        def selector = new ActiveSessionDatastoreSelector()
        GormRegistry registry = GormRegistry.instance
        Datastore activeDatastore = Mock(Datastore) {
            hasCurrentSession() >> true
        }
        registry.registerDatastore(ConnectionSource.DEFAULT, activeDatastore)

        expect:
        selector.select(registry, TestEntity.name).is(activeDatastore)
    }

    void 'select returns the only active datastore when no class name is supplied'() {
        given:
        def selector = new ActiveSessionDatastoreSelector()
        GormRegistry registry = GormRegistry.instance
        Datastore activeDatastore = Mock(Datastore) {
            hasCurrentSession() >> true
        }
        registry.registerDatastoreByType(activeDatastore)

        expect:
        selector.select(registry, null).is(activeDatastore)
    }

    void 'select returns null when no active datastore matches'() {
        given:
        def selector = new ActiveSessionDatastoreSelector()
        GormRegistry registry = GormRegistry.instance
        registry.registerDatastore(ConnectionSource.DEFAULT, Mock(Datastore))

        expect:
        selector.select(registry, TestEntity.name) == null
    }

    void 'the discovery scan does not disturb the thread-bound sessions of the datastores it probes'() {
        given: "two real datastores, only one of which has a session bound on this thread"
        def selector = new ActiveSessionDatastoreSelector()
        GormRegistry registry = GormRegistry.instance
        def probed = new SimpleMapDatastore(TestEntity)
        def owner = new SimpleMapDatastore(TestEntity)
        registry.registerDatastore(ConnectionSource.DEFAULT, owner)
        registry.registerDatastore('probed', probed)
        Session probedSession = probed.connect()
        DatastoreUtils.bindSession(probedSession)
        SessionHolder probedHolder = (SessionHolder) TransactionSynchronizationManager.getResource(probed)

        when: "routing asks every registered datastore whether it holds a session"
        selector.select(registry, TestEntity.name)

        then: "the probed datastore's binding survives the probe — a routing decision for one entity must not unbind another datastore's holder"
        TransactionSynchronizationManager.getResource(probed).is(probedHolder)
        probed.hasCurrentSession()

        cleanup:
        DatastoreUtils.unbindSession(probedSession)
        probed.close()
        owner.close()
    }

    @Entity
    static class TestEntity {
        String name
    }
}
