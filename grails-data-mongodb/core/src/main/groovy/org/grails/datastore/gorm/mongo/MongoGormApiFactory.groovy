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
package org.grails.datastore.gorm.mongo

import groovy.transform.CompileStatic

import org.grails.datastore.gorm.DefaultGormApiFactory
import org.grails.datastore.gorm.DatastoreResolver
import org.grails.datastore.gorm.GormInstanceApi
import org.grails.datastore.gorm.GormRegistry
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.gorm.mongo.api.MongoGormInstanceApi
import org.grails.datastore.gorm.mongo.api.MongoStaticApi
import org.grails.datastore.mapping.model.MappingContext

/**
 * MongoDB-specific factory for creating GORM API objects.
 * Extends the default factory to create MongoStaticApi instead of the generic GormStaticApi,
 * allowing MongoDB-specific query operations and optimizations.
 *
 * @since 8.0.0
 */
@CompileStatic
class MongoGormApiFactory extends DefaultGormApiFactory {

    @Override
    <D> MongoStaticApi<D> createStaticApi(Class<D> persistentClass,
                                          MappingContext mappingContext,
                                          DatastoreResolver resolver,
                                          String qualifier,
                                          GormRegistry registry) {
        List<FinderMethod> finders = createDynamicFinders(resolver, mappingContext)
        return new MongoStaticApi<D>(persistentClass, mappingContext, finders, resolver, qualifier)
    }

    @Override
    <D> GormInstanceApi<D> createInstanceApi(Class<D> persistentClass,
                                             MappingContext mappingContext,
                                             DatastoreResolver resolver,
                                             GormRegistry registry,
                                             boolean failOnError,
                                             boolean markDirty) {
        GormInstanceApi<D> instanceApi = new MongoGormInstanceApi<D>(persistentClass, mappingContext, resolver, registry)
        instanceApi.failOnError = failOnError
        instanceApi.markDirty = markDirty
        return instanceApi
    }
}
