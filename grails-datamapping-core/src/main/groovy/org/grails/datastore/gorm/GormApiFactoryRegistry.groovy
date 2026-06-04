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

import java.util.concurrent.ConcurrentHashMap
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.core.Datastore

/**
 * Registry and resolver for datastore-specific {@link GormApiFactory} implementations.
 *
 * @author Walter Duque de Estrada
 * @since 8.0.0
 */
@CompileStatic
class GormApiFactoryRegistry {

    private final GormApiFactory defaultApiFactory = new DefaultGormApiFactory()
    private final Map<Class, GormApiFactory> apiFactoriesByDatastoreType = new ConcurrentHashMap<>()

    /**
     * Registers a custom GormApiFactory for a specific datastore type.
     */
    void registerApiFactory(Class datastoreType, GormApiFactory factory) {
        if (datastoreType != null && factory != null) {
            apiFactoriesByDatastoreType.put(datastoreType, factory)
        }
    }

    /**
     * Gets the custom GormApiFactory registered for the datastore type, or falls back to the default.
     */
    GormApiFactory getApiFactory(Datastore datastore) {
        if (datastore == null) {
            return defaultApiFactory
        }
        GormApiFactory factory = apiFactoriesByDatastoreType.get(datastore.getClass())
        if (factory == null) {
            for (Map.Entry<Class, GormApiFactory> entry in apiFactoriesByDatastoreType.entrySet()) {
                if (entry.key.isInstance(datastore)) {
                    return entry.value
                }
            }
            return defaultApiFactory
        }
        return factory
    }

    /**
     * Clears all registered API factories.
     */
    void clear() {
        apiFactoriesByDatastoreType.clear()
    }
}
