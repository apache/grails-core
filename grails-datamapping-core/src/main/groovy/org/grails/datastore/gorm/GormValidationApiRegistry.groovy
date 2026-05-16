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
import org.grails.datastore.mapping.core.connections.ConnectionSource

@CompileStatic
class GormValidationApiRegistry extends AbstractGormApiRegistry<GormValidationApi> {

    GormValidationApiRegistry(GormRegistry registry) {
        super(registry)
    }

    @Override
    protected GormValidationApi qualify(GormValidationApi api, String qualifier) {
        return api.forQualifier(qualifier)
    }

    <D> GormValidationApi<D> findValidationApi(Class<D> entity, String qualifier = null) {
        String className = className(entity)
        GormValidationApi api = get(className)
        if (api == null) {
            throw stateException(entity)
        }

        String normalizedQualifier = registry.normalizeQualifier(qualifier)
        if (!ConnectionSource.DEFAULT.equals(normalizedQualifier)) {
            return api.forQualifier(normalizedQualifier)
        }
        return (GormValidationApi<D>) api
    }
}
