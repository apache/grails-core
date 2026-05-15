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
import org.grails.datastore.gorm.finders.CountByFinder
import org.grails.datastore.gorm.finders.FindAllByBooleanFinder
import org.grails.datastore.gorm.finders.FindAllByFinder
import org.grails.datastore.gorm.finders.FindByBooleanFinder
import org.grails.datastore.gorm.finders.FindByFinder
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.gorm.finders.FindOrCreateByFinder
import org.grails.datastore.gorm.finders.FindOrSaveByFinder
import org.grails.datastore.gorm.finders.ListOrderByFinder
import org.grails.datastore.mapping.model.MappingContext

/**
 * Default core factory for GORM API object creation.
 *
 * @since 8.0.0
 */
@CompileStatic
class DefaultGormApiFactory implements GormApiFactory {

    @Override
    <D> GormStaticApi<D> createStaticApi(Class<D> persistentClass,
                                         MappingContext mappingContext,
                                         DatastoreResolver resolver,
                                         String qualifier,
                                         GormRegistry registry) {
        List<FinderMethod> finders = createDynamicFinders(resolver, mappingContext)
        return new GormStaticApi<D>(persistentClass, mappingContext, finders, resolver, qualifier, registry)
    }

    @Override
    <D> GormInstanceApi<D> createInstanceApi(Class<D> persistentClass,
                                             MappingContext mappingContext,
                                             DatastoreResolver resolver,
                                             GormRegistry registry,
                                             boolean failOnError,
                                             boolean markDirty) {
        GormInstanceApi<D> instanceApi = new GormInstanceApi<D>(persistentClass, mappingContext, resolver, registry)
        instanceApi.failOnError = failOnError
        instanceApi.markDirty = markDirty
        return instanceApi
    }

    @Override
    <D> GormValidationApi<D> createValidationApi(Class<D> persistentClass,
                                                 MappingContext mappingContext,
                                                 DatastoreResolver resolver,
                                                 GormRegistry registry) {
        return new GormValidationApi<D>(persistentClass, mappingContext, resolver, registry)
    }

    @Override
    List<FinderMethod> createDynamicFinders(DatastoreResolver datastoreResolver, MappingContext mappingContext) {
        [new FindOrCreateByFinder(datastoreResolver, mappingContext),
         new FindOrSaveByFinder(datastoreResolver, mappingContext),
         new FindByFinder(datastoreResolver, mappingContext),
         new FindAllByFinder(datastoreResolver, mappingContext),
         new FindAllByBooleanFinder(datastoreResolver, mappingContext),
         new FindByBooleanFinder(datastoreResolver, mappingContext),
         new CountByFinder(datastoreResolver, mappingContext),
         new ListOrderByFinder(datastoreResolver, mappingContext)] as List<FinderMethod>
    }
}
