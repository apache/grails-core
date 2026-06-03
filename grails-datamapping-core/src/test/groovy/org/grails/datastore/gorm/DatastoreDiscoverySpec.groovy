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
package org.grails.datastore.gorm

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import spock.lang.Specification

class DatastoreDiscoverySpec extends Specification {

    void "test register and get datastore"() {
        given:
        def discovery = new DatastoreDiscovery()
        def datastore = Stub(Datastore)

        when:
        discovery.registerDatastore("ds1", datastore)

        then:
        discovery.getDatastore("ds1") == datastore
        discovery.allDatastores.contains(datastore)
    }

    void "test initializeDatastore registers type and default qualifier"() {
        given:
        def discovery = new DatastoreDiscovery()
        def datastore = Stub(Datastore)

        when:
        discovery.initializeDatastore(datastore)

        then:
        discovery.getDefaultDatastore() == datastore
        discovery.datastoresByType.get(datastore.getClass()) == datastore
    }

    void "test removeDatastore removes completely"() {
        given:
        def discovery = new DatastoreDiscovery()
        def datastore = Stub(Datastore)

        when:
        discovery.initializeDatastore(datastore)
        discovery.registerDatastoreByQualifier("ds1", datastore)
        discovery.removeDatastore(datastore)

        then:
        discovery.allDatastores.isEmpty()
        discovery.datastoresByQualifier.isEmpty()
        discovery.datastoresByType.isEmpty()
    }

    void "test normalizeEntityKey and normalizeQualifier"() {
        given:
        def discovery = new DatastoreDiscovery()

        expect:
        discovery.normalizeQualifier(null) == ConnectionSource.DEFAULT
        discovery.normalizeQualifier("   ") == ConnectionSource.DEFAULT
        discovery.normalizeQualifier("ds1") == "ds1"

        discovery.normalizeEntityKey(DummyEntity) == DummyEntity.name
        discovery.normalizeEntityKey(DummyEntity.name) == DummyEntity.name
        discovery.normalizeEntityKey(null) == null
    }

    static class DummyEntity {
        Long id
    }
}
