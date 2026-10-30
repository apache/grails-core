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
package org.grails.gorm.rx.api

import org.grails.datastore.gorm.events.ConfigurableApplicationEventPublisher
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.event.ValidationEvent
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.rx.RxDatastoreClient
import spock.lang.Specification

class RxGormValidationApiSpec extends Specification {

    private static class TestEntity {
    }

    PersistentEntity persistentEntity
    EntityAccess entityAccess
    ConfigurableApplicationEventPublisher eventPublisher
    RxDatastoreClient datastoreClient
    RxGormValidationApi<TestEntity> api

    void setup() {
        persistentEntity = Stub(PersistentEntity) {
            getJavaClass() >> TestEntity
        }
        entityAccess = Stub(EntityAccess)
        MappingContext mappingContext = Stub(MappingContext) {
            getPersistentEntity(TestEntity.name) >> persistentEntity
            getEntityValidator(persistentEntity) >> null
            createEntityAccess(persistentEntity, _) >> entityAccess
        }
        eventPublisher = Mock(ConfigurableApplicationEventPublisher)
        datastoreClient = Stub(RxDatastoreClient) {
            getMappingContext() >> mappingContext
            getEventPublisher() >> eventPublisher
        }
        api = new RxGormValidationApi<TestEntity>(persistentEntity, datastoreClient)
    }

    void "getDatastoreClient exposes the client supplied to the constructor"() {
        expect:
        api.datastoreClient.is(datastoreClient)
    }

    void "validate publishes a ValidationEvent built from the datastore client and mapping context"() {
        given:
        TestEntity instance = new TestEntity()

        when:
        boolean result = api.validate(instance)

        then:
        result
        1 * eventPublisher.publishEvent({ ValidationEvent event ->
            event.source.is(datastoreClient) &&
                    event.entity.is(persistentEntity) &&
                    event.entityAccess.is(entityAccess) &&
                    event.validatedFields == null
        })
    }

    void "validate with fields includes the validated fields on the published event"() {
        given:
        TestEntity instance = new TestEntity()

        when:
        boolean result = api.validate(instance, ['name'])

        then:
        result
        1 * eventPublisher.publishEvent({ ValidationEvent event -> event.validatedFields == ['name'] })
    }
}
