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

import grails.gorm.rx.api.RxGormInstanceOperations
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.datastore.rx.RxDatastoreClient
import rx.Observable
import rx.Single

/**
 * Bridge to the implementation of the instance method level operations
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class RxGormInstanceApi<D> implements RxGormInstanceOperations<D> {

    final PersistentEntity entity
    final RxDatastoreClient datastoreClient
    final EntityReflector entityReflector

    RxGormInstanceApi(PersistentEntity entity, RxDatastoreClient datastoreClient) {
        this.entity = entity
        this.datastoreClient = datastoreClient
        this.entityReflector = datastoreClient.mappingContext.getEntityReflector(entity)
    }

    @Override
    Observable<D> save(D instance, Map arguments = Collections.emptyMap()) {
        datastoreClient.persist(instance, arguments)
    }

    @Override
    Observable<D> insert(D instance, Map arguments = Collections.emptyMap()) {
        datastoreClient.insert(instance, arguments)
    }

    @Override
    Serializable ident(D instance) {
        entityReflector.getIdentifier(instance)
    }

    @Override
    Observable<Boolean> delete(D instance) {
        delete(instance, Collections.emptyMap())
    }

    @Override
    Observable<Boolean> delete(D instance, Map arguments) {
        datastoreClient.delete(instance, arguments)
    }
}
