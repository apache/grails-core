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

import groovy.transform.CompileStatic
import org.springframework.transaction.PlatformTransactionManager
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore

/**
 * Resolves transaction managers for GORM connection qualifiers and entity classes.
 * Extracted from GormRegistry to improve single responsibility and unit testability.
 *
 * @author Walter Duque de Estrada
 * @since 8.0.0
 */
@CompileStatic
@SuppressWarnings(['unused', 'DuplicatedCode'])
class TransactionResolver {

    private final GormRegistry registry

    TransactionResolver(GormRegistry registry) {
        this.registry = registry
    }

    PlatformTransactionManager findSingleTransactionManager(String qualifier = ConnectionSource.DEFAULT) {
        Datastore ds = registry.getDatastoreByString((String) null, qualifier)
        resolveTransactionManager(ds)
    }

    PlatformTransactionManager findTransactionManager(Class entityClass, String qualifier = ConnectionSource.DEFAULT) {
        Datastore ds = registry.getDatastore(entityClass, qualifier) ?: registry.apiResolver.findDatastore(entityClass, qualifier)
        resolveTransactionManager(ds)
    }

    private PlatformTransactionManager resolveTransactionManager(Datastore ds) {
        if (ds == null) {
            if (registry.defaultDatastore == null) {
                throw new IllegalStateException('No GORM implementations configured. Ensure GORM has been initialized correctly')
            }
            return null
        }
        ds instanceof TransactionCapableDatastore ? ((TransactionCapableDatastore) ds).getTransactionManager() : null
    }
}
