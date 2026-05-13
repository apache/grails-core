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

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.reflect.NameUtils
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore
import org.springframework.transaction.PlatformTransactionManager

/**
 * Instance-based resolver for GORM APIs and datastores.
 *
 * @since 8.0.0
 */
@CompileStatic
class GormApiResolver {

    private final GormRegistry registry

    GormApiResolver(GormRegistry registry) {
        this.registry = registry
    }

    <D> GormStaticApi<D> findStaticApi(Class<D> entity, String qualifier = null) {
        String className = NameUtils.getClassName(entity)
        GormStaticApi api = registry.getStaticApi(className)
        if (api == null) {
            throw stateException(entity)
        }

        if (qualifier != null && qualifier != ConnectionSource.DEFAULT) {
            return api.forQualifier(qualifier)
        }
        return (GormStaticApi<D>) api
    }

    <D> GormInstanceApi<D> findInstanceApi(Class<D> entity, String qualifier = null) {
        String className = NameUtils.getClassName(entity)
        GormInstanceApi api = registry.getInstanceApi(className)
        if (api == null) {
            throw stateException(entity)
        }

        if (qualifier != null && qualifier != ConnectionSource.DEFAULT) {
            return api.forQualifier(qualifier)
        }
        return (GormInstanceApi<D>) api
    }

    <D> GormValidationApi<D> findValidationApi(Class<D> entity, String qualifier = null) {
        String className = NameUtils.getClassName(entity)
        GormValidationApi api = registry.getValidationApi(className)
        if (api == null) {
            throw stateException(entity)
        }

        if (qualifier != null && qualifier != ConnectionSource.DEFAULT) {
            return api.forQualifier(qualifier)
        }
        return (GormValidationApi<D>) api
    }

    Datastore findDatastore(Class entity, String qualifier = null) {
        return GormEnhancer.findDatastore(entity, qualifier, registry)
    }

    Datastore findDatastoreByType(Class<? extends Datastore> datastoreType) {
        Datastore datastore = registry.datastoresByType.get(datastoreType)
        if (datastore == null) {
            throw new IllegalStateException("No GORM implementation configured for type [$datastoreType]. Ensure GORM has been initialized correctly")
        }
        return datastore
    }

    Datastore findSingleDatastore() {
        return GormEnhancer.findSingleDatastore(registry)
    }

    PlatformTransactionManager findSingleTransactionManager() {
        Datastore ds = findSingleDatastore()
        if (ds instanceof TransactionCapableDatastore) {
            return ((TransactionCapableDatastore) ds).transactionManager
        }
        return null
    }

    PlatformTransactionManager findSingleTransactionManager(String connectionName) {
        Datastore ds = findDatastore(null, connectionName)
        if (ds instanceof TransactionCapableDatastore) {
            return ((TransactionCapableDatastore) ds).transactionManager
        }
        return null
    }

    PlatformTransactionManager findTransactionManager(Class entity, String qualifier = null) {
        Datastore ds = findDatastore(entity, qualifier)
        if (ds instanceof TransactionCapableDatastore) {
            return ((TransactionCapableDatastore) ds).transactionManager
        }
        return null
    }

    PersistentEntity findEntity(Class entity, String qualifier = null) {
        String resolvedQualifier = qualifier ?: GormEnhancer.findTenantId(entity, registry)
        return findDatastore(entity, resolvedQualifier)?.mappingContext?.getPersistentEntity(entity.name)
    }

    private static IllegalStateException stateException(Class entity) {
        return new IllegalStateException("No GORM implementation configured for class [${entity.name}]. Ensure GORM has been initialized correctly")
    }
}
