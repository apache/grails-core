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

import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.datastore.rx.RxDatastoreClient
import rx.Observable
import spock.lang.Specification

class RxGormInstanceApiSpec extends Specification {

    private static class TestEntity {
    }

    RxDatastoreClient datastoreClient
    EntityReflector entityReflector
    RxGormInstanceApi<TestEntity> api

    void setup() {
        entityReflector = Mock(EntityReflector)
        MappingContext mappingContext = Stub(MappingContext) {
            getEntityReflector(_) >> entityReflector
        }
        datastoreClient = Mock(RxDatastoreClient) {
            getMappingContext() >> mappingContext
        }
        PersistentEntity entity = Stub(PersistentEntity)
        api = new RxGormInstanceApi<TestEntity>(entity, datastoreClient)
    }

    void "save persists the instance using the default arguments"() {
        given:
        TestEntity instance = new TestEntity()
        Observable<TestEntity> saved = Observable.just(instance)

        when:
        Observable<TestEntity> result = api.save(instance)

        then:
        1 * datastoreClient.persist(instance, [:]) >> saved
        result.is(saved)
    }

    void "save persists the instance with the given arguments"() {
        given:
        TestEntity instance = new TestEntity()
        Map arguments = [flush: true]
        Observable<TestEntity> saved = Observable.just(instance)

        when:
        Observable<TestEntity> result = api.save(instance, arguments)

        then:
        1 * datastoreClient.persist(instance, arguments) >> saved
        result.is(saved)
    }

    void "insert inserts the instance using the default arguments"() {
        given:
        TestEntity instance = new TestEntity()
        Observable<TestEntity> inserted = Observable.just(instance)

        when:
        Observable<TestEntity> result = api.insert(instance)

        then:
        1 * datastoreClient.insert(instance, [:]) >> inserted
        result.is(inserted)
    }

    void "insert inserts the instance with the given arguments"() {
        given:
        TestEntity instance = new TestEntity()
        Map arguments = [flush: true]
        Observable<TestEntity> inserted = Observable.just(instance)

        when:
        Observable<TestEntity> result = api.insert(instance, arguments)

        then:
        1 * datastoreClient.insert(instance, arguments) >> inserted
        result.is(inserted)
    }

    void "ident returns the identifier obtained from the entity reflector"() {
        given:
        TestEntity instance = new TestEntity()

        when:
        Serializable id = api.ident(instance)

        then:
        1 * entityReflector.getIdentifier(instance) >> 42L
        id == 42L
    }

    void "delete with no arguments delegates to the datastore client with empty arguments"() {
        given:
        TestEntity instance = new TestEntity()

        when:
        Observable<Boolean> result = api.delete(instance)

        then:
        1 * datastoreClient.delete(instance, [:]) >> Observable.just(true)
        result.toBlocking().single()
    }

    void "delete with arguments delegates to the datastore client"() {
        given:
        TestEntity instance = new TestEntity()
        Map arguments = [flush: true]

        when:
        Observable<Boolean> result = api.delete(instance, arguments)

        then:
        1 * datastoreClient.delete(instance, arguments) >> Observable.just(false)
        !result.toBlocking().single()
    }
}
