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
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.mapping.model.MappingContext

/**
 * Abstract factory for creating GORM API instances.
 *
 * @since 8.0.0
 */
@CompileStatic
interface GormApiFactory {

    <D> GormStaticApi<D> createStaticApi(Class<D> persistentClass,
                                         MappingContext mappingContext,
                                         DatastoreResolver resolver,
                                         String qualifier,
                                         GormRegistry registry)

    <D> GormInstanceApi<D> createInstanceApi(Class<D> persistentClass,
                                             MappingContext mappingContext,
                                             DatastoreResolver resolver,
                                             GormRegistry registry,
                                             boolean failOnError,
                                             boolean markDirty)

    <D> GormValidationApi<D> createValidationApi(Class<D> persistentClass,
                                                 MappingContext mappingContext,
                                                 DatastoreResolver resolver,
                                                 GormRegistry registry)

    List<FinderMethod> createDynamicFinders(DatastoreResolver datastoreResolver, MappingContext mappingContext)
}
