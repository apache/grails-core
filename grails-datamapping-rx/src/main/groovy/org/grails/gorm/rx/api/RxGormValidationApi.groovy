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

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormValidationApi
import org.grails.datastore.mapping.engine.event.ValidationEvent
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.rx.RxDatastoreClient
/**
 * RxGORM version of validation API
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class RxGormValidationApi<D> extends GormValidationApi<D>{

    final RxDatastoreClient datastoreClient
    RxGormValidationApi(PersistentEntity entity, RxDatastoreClient datastoreClient) {
        super(entity.javaClass, datastoreClient.mappingContext, datastoreClient.eventPublisher)
        this.datastoreClient = datastoreClient
    }

    @Override
    protected ValidationEvent createValidationEvent(Object target) {
        return new ValidationEvent(datastoreClient, persistentEntity, mappingContext.createEntityAccess(persistentEntity, target) )
    }
}
