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

import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.model.MappingContext
import spock.lang.Specification

class DynamicFinderCreatorSpec extends Specification {

    void "test create dynamic finders for datastore"() {
        given:
        def registry = GormRegistry.instance
        registry.reset()

        def creator = registry.dynamicFinderCreator
        def mappingContext = Stub(MappingContext)
        def datastore = Stub(Datastore) {
            getMappingContext() >> mappingContext
        }
        def apiFactory = Mock(GormApiFactory)
        def finder = Stub(FinderMethod)

        when:
        registry.registerApiFactory(datastore.getClass(), apiFactory)
        registry.initializeDatastore(datastore)
        def finders = creator.createDynamicFinders(datastore)

        then:
        1 * apiFactory.createDynamicFinders(_, mappingContext) >> [finder]
        finders == [finder]

        cleanup:
        registry.reset()
    }

    void "test create class datastore resolver"() {
        given:
        def registry = GormRegistry.instance
        registry.reset()

        def creator = registry.dynamicFinderCreator
        def datastore = Stub(Datastore)

        when:
        registry.initializeDatastore(datastore)
        registry.registerEntityDatastore(DummyEntity.name, ConnectionSource.DEFAULT, datastore)
        def resolver = creator.createClassDatastoreResolver(DummyEntity)

        then:
        resolver.resolve() == datastore

        cleanup:
        registry.reset()
    }

    static class DummyEntity {
        Long id
    }
}
