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

package org.grails.gorm.rx.events

import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.rx.RxDatastoreClient
import spock.lang.Specification

class AutoTimestampEventListenerSpec extends Specification {

    PersistentEntity buildEntity() {
        PersistentProperty dateCreatedProperty = Stub(PersistentProperty) {
            getName() >> 'dateCreated'
            getType() >> Date
        }
        PersistentProperty lastUpdatedProperty = Stub(PersistentProperty) {
            getName() >> 'lastUpdated'
            getType() >> Date
        }
        ClassMapping classMapping = Stub(ClassMapping) {
            getMappedForm() >> null
        }
        Stub(PersistentEntity) {
            isInitialized() >> true
            getName() >> 'test.Book'
            getMapping() >> classMapping
            getPersistentProperties() >> [dateCreatedProperty, lastUpdatedProperty]
        }
    }

    void "registers itself with the mapping context on construction"() {
        given:
        MappingContext mappingContext = Mock(MappingContext) {
            getPersistentEntities() >> []
        }
        RxDatastoreClient datastoreClient = Stub(RxDatastoreClient) {
            getMappingContext() >> mappingContext
        }

        when:
        new AutoTimestampEventListener(datastoreClient)

        then:
        1 * mappingContext.addMappingContextListener(_)
    }

    void "sets auto timestamp properties when the event source is the owning datastore client"() {
        given:
        PersistentEntity entity = buildEntity()
        MappingContext mappingContext = Stub(MappingContext) {
            getPersistentEntities() >> [entity]
        }
        RxDatastoreClient datastoreClient = Stub(RxDatastoreClient) {
            getMappingContext() >> mappingContext
        }
        AutoTimestampEventListener listener = new AutoTimestampEventListener(datastoreClient)
        EntityAccess entityAccess = Mock(EntityAccess) {
            getPropertyType('dateCreated') >> Date
            getPropertyType('lastUpdated') >> Date
        }

        when:
        listener.onApplicationEvent(new PreInsertEvent(datastoreClient, entity, entityAccess))

        then:
        1 * entityAccess.setProperty('dateCreated', _ as Date)
        1 * entityAccess.setProperty('lastUpdated', _ as Date)
    }

    void "ignores events whose source is not the owning datastore client"() {
        given:
        PersistentEntity entity = buildEntity()
        MappingContext mappingContext = Stub(MappingContext) {
            getPersistentEntities() >> [entity]
        }
        RxDatastoreClient datastoreClient = Stub(RxDatastoreClient) {
            getMappingContext() >> mappingContext
        }
        AutoTimestampEventListener listener = new AutoTimestampEventListener(datastoreClient)
        EntityAccess entityAccess = Mock(EntityAccess)

        when:
        listener.onApplicationEvent(new PreInsertEvent(new Object(), entity, entityAccess))

        then:
        0 * entityAccess.setProperty(_, _)
    }

    void "ignores events raised by a different datastore client instance"() {
        given:
        PersistentEntity entity = buildEntity()
        MappingContext mappingContext = Stub(MappingContext) {
            getPersistentEntities() >> [entity]
        }
        RxDatastoreClient datastoreClient = Stub(RxDatastoreClient) {
            getMappingContext() >> mappingContext
        }
        RxDatastoreClient otherDatastoreClient = Stub(RxDatastoreClient) {
            getMappingContext() >> mappingContext
        }
        AutoTimestampEventListener listener = new AutoTimestampEventListener(datastoreClient)
        EntityAccess entityAccess = Mock(EntityAccess)

        when:
        listener.onApplicationEvent(new PreInsertEvent(otherDatastoreClient, entity, entityAccess))

        then:
        0 * entityAccess.setProperty(_, _)
    }

    void "supports the source type matching the owning datastore client's class"() {
        given:
        MappingContext mappingContext = Stub(MappingContext) {
            getPersistentEntities() >> []
        }
        RxDatastoreClient datastoreClient = Stub(RxDatastoreClient) {
            getMappingContext() >> mappingContext
        }
        AutoTimestampEventListener listener = new AutoTimestampEventListener(datastoreClient)

        expect:
        listener.supportsSourceType(datastoreClient.getClass())
        !listener.supportsSourceType(String)
    }
}
