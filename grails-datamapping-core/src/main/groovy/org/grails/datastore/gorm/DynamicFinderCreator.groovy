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
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.model.MappingContext

/**
 * Creates dynamic finders for GORM datastores.
 * Extracted from GormRegistry to improve single responsibility and unit testability.
 *
 * @author Walter Duque de Estrada
 * @since 8.0.0
 */
@CompileStatic
@SuppressWarnings(['unused', 'DuplicatedCode'])
class DynamicFinderCreator {

    private final GormRegistry registry

    DynamicFinderCreator(GormRegistry registry) {
        this.registry = registry
    }

    List<FinderMethod> createDynamicFinders(Datastore targetDatastore) {
        return createDynamicFinders({ -> targetDatastore } as DatastoreResolver, targetDatastore.getMappingContext())
    }

    List<FinderMethod> createDynamicFinders(DatastoreResolver resolver, MappingContext mappingContext) {
        Datastore ds = resolver.resolve()
        return ds ? registry.getApiFactory(ds).createDynamicFinders(resolver, mappingContext) : []
    }

    DatastoreResolver createClassDatastoreResolver(Class cls, String qualifier = ConnectionSource.DEFAULT) {
        String normalizedQualifier = registry.normalizeQualifier(qualifier)
        return { -> registry.apiResolver.findDatastore(cls, normalizedQualifier) } as DatastoreResolver
    }
}
