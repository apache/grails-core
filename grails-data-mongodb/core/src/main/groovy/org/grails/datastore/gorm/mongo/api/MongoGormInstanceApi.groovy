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

package org.grails.datastore.gorm.mongo.api

import groovy.transform.CompileStatic

import org.grails.datastore.gorm.GormInstanceApi
import org.grails.datastore.gorm.GormRegistry
import org.grails.datastore.gorm.mongo.transactions.MongoTransactionContext
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.model.MappingContext

/**
 * MongoDB-specific instance API that ensures all operations are flushed
 *
 * @author Graeme Rocher
 * @since 8.0
 */
@CompileStatic
class MongoGormInstanceApi<D> extends GormInstanceApi<D> {

    MongoGormInstanceApi(Class<D> persistentClass, Datastore datastore) {
        super(persistentClass, datastore)
    }

    MongoGormInstanceApi(Class<D> persistentClass, Datastore datastore, GormRegistry registry) {
        super(persistentClass, datastore, registry)
    }

    MongoGormInstanceApi(Class<D> persistentClass, MappingContext mappingContext, org.grails.datastore.gorm.DatastoreResolver datastoreResolver) {
        super(persistentClass, mappingContext, datastoreResolver)
    }

    MongoGormInstanceApi(Class<D> persistentClass, MappingContext mappingContext, org.grails.datastore.gorm.DatastoreResolver datastoreResolver, GormRegistry registry) {
        super(persistentClass, mappingContext, datastoreResolver, registry)
    }

    @Override
    D save(D instance) {
        save(instance, [:])
    }

    @Override
    D save(D instance, boolean validate) {
        save(instance, [validate: validate])
    }

    @Override
    D save(D instance, Map arguments) {
        // Only force flush outside active transactions.
        // Inside a transaction, immediate flush breaks rollback semantics.
        if (!arguments?.containsKey('flush') && shouldAutoFlushByDefault()) {
            arguments = (arguments ?: [:]) + [flush: true]
        }
        return super.save(instance, arguments)
    }

    protected boolean shouldAutoFlushByDefault() {
        !MongoTransactionContext.isRollbackAwareActive()
    }
}
