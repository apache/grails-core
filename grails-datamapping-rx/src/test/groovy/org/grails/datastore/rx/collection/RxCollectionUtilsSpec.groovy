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
package org.grails.datastore.rx.collection

import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PropertyMapping
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.rx.internal.RxDatastoreClientImplementor
import org.grails.datastore.rx.query.QueryState
import org.grails.datastore.rx.query.RxQuery
import org.grails.gorm.rx.api.RxGormEnhancer
import org.grails.gorm.rx.api.RxGormInstanceApi
import rx.Observable
import spock.lang.Specification

class RxCollectionUtilsSpec extends Specification {

    private static class TestChild {
    }

    void cleanup() {
        RxGormEnhancer.close()
    }

    void "createConcreteCollection with a foreign key builds a #expected.simpleName for association type #type.simpleName"() {
        given:
        PersistentEntity entity = createAssociatedEntity()
        RxDatastoreClientImplementor client = registerEntity(entity)
        Association association = createAssociation(entity, type)
        Query query = Stub(Query, additionalInterfaces: [RxQuery])
        query.findAll() >> Observable.empty()
        client.createQuery(_, _) >> query

        when:
        Collection collection = RxCollectionUtils.createConcreteCollection(association, 1L, new QueryState())

        then:
        expected.isInstance(collection)
        collection.datastoreClient.is(client)

        where:
        type      | expected
        SortedSet | RxPersistentSortedSet
        List      | RxPersistentList
        Set       | RxPersistentSet
    }

    void "createConcreteCollection with an initializer query builds a #expected.simpleName for association type #type.simpleName"() {
        given:
        PersistentEntity entity = createAssociatedEntity()
        RxDatastoreClientImplementor client = registerEntity(entity)
        Association association = createAssociation(entity, type)
        Query initializerQuery = Stub(Query, additionalInterfaces: [RxQuery])
        initializerQuery.findAll() >> Observable.empty()

        when:
        Collection collection = RxCollectionUtils.createConcreteCollection(association, (Query) initializerQuery, new QueryState())

        then:
        expected.isInstance(collection)
        collection.datastoreClient.is(client)

        where:
        type      | expected
        SortedSet | RxPersistentSortedSet
        List      | RxPersistentList
        Set       | RxPersistentSet
    }

    private PersistentEntity createAssociatedEntity() {
        Stub(PersistentEntity) {
            getJavaClass() >> TestChild
            getName() >> TestChild.name
            getMapping() >> Stub(ClassMapping) {
                getMappedForm() >> null
            }
        }
    }

    private Association createAssociation(PersistentEntity associatedEntity, Class type) {
        Stub(Association) {
            getType() >> type
            getAssociatedEntity() >> associatedEntity
            getInverseSide() >> Stub(Association) {
                getName() >> 'owner'
            }
            getMapping() >> Stub(PropertyMapping) {
                getMappedForm() >> Stub(Property) {
                    isLazy() >> false
                }
            }
        }
    }

    private RxDatastoreClientImplementor registerEntity(PersistentEntity entity) {
        RxDatastoreClientImplementor client = Stub(RxDatastoreClientImplementor)
        RxGormInstanceApi instanceApi = Stub(RxGormInstanceApi)
        instanceApi.getDatastoreClient() >> client
        client.createInstanceApi(_, _) >> instanceApi
        RxGormEnhancer.registerEntity(entity, client)
        client
    }
}
