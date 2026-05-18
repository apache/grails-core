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
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.model.MappingContext

@CompileStatic
class GormInstanceApiRegistry extends AbstractGormApiRegistry<GormInstanceApi> {

    GormInstanceApiRegistry(GormRegistry registry) {
        super(registry)
    }

    @Override
    protected GormInstanceApi qualify(GormInstanceApi api, String qualifier) {
        Class persistentClass = api.persistentClass
        Datastore datastore = registry.apiResolver.findDatastore(persistentClass, qualifier)
        if (datastore == null) {
            return api
        }
        MappingContext mappingContext = datastore.mappingContext
        DatastoreResolver resolver = registry.createClassDatastoreResolver(persistentClass, qualifier)
        return registry.getApiFactory(datastore).createInstanceApi(persistentClass, mappingContext, resolver, registry, api.failOnError, api.markDirty)
    }

    <D> GormInstanceApi<D> findInstanceApi(Class<D> entity, String qualifier = null) {
        String className = className(entity)
        GormInstanceApi api = get(className, qualifier)
        if (api == null) {
            throw stateException(entity)
        }
        return (GormInstanceApi<D>) api
    }
}
