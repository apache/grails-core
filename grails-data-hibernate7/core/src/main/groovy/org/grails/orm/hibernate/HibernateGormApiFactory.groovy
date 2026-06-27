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
package org.grails.orm.hibernate

import groovy.transform.CompileStatic

import org.grails.datastore.gorm.DatastoreResolver
import org.grails.datastore.gorm.DefaultGormApiFactory
import org.grails.datastore.gorm.GormApiFactory
import org.grails.datastore.gorm.GormInstanceApi
import org.grails.datastore.gorm.GormRegistry
import org.grails.datastore.gorm.GormStaticApi
import org.grails.datastore.gorm.GormValidationApi
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.mapping.model.MappingContext

/**
 * {@link GormApiFactory} that produces Hibernate 7-specific API instances, ensuring that
 * {@code withNewSession} and related lifecycle methods use the Hibernate {@code SessionFactory}
 * binding rather than the generic GORM {@code DatastoreUtils} path.
 */
@CompileStatic
class HibernateGormApiFactory implements GormApiFactory {

    private final ClassLoader classLoader

    HibernateGormApiFactory(ClassLoader classLoader) {
        this.classLoader = classLoader
    }

    @Override
    <D> GormStaticApi<D> createStaticApi(Class<D> persistentClass, MappingContext mappingContext, DatastoreResolver resolver, String qualifier, GormRegistry registry) {
        HibernateDatastore hds = (HibernateDatastore) resolver.resolve()
        List<FinderMethod> finders = new DefaultGormApiFactory().createDynamicFinders(resolver, mappingContext)
        return new HibernateGormStaticApi<D>(persistentClass, hds, finders, classLoader, hds.getTransactionManager(), qualifier)
    }

    @Override
    <D> GormInstanceApi<D> createInstanceApi(Class<D> persistentClass, MappingContext mappingContext, DatastoreResolver resolver, GormRegistry registry, boolean failOnError, boolean markDirty) {
        HibernateDatastore hds = (HibernateDatastore) resolver.resolve()
        GormInstanceApi<D> instanceApi = new HibernateGormInstanceApi<D>(persistentClass, hds, classLoader)
        instanceApi.failOnError = failOnError
        instanceApi.markDirty = markDirty
        return instanceApi
    }

    @Override
    <D> GormValidationApi<D> createValidationApi(Class<D> persistentClass, MappingContext mappingContext, DatastoreResolver resolver, GormRegistry registry) {
        HibernateDatastore hds = (HibernateDatastore) resolver.resolve()
        return new HibernateGormValidationApi<D>(persistentClass, hds, classLoader)
    }

    @Override
    List<FinderMethod> createDynamicFinders(DatastoreResolver datastoreResolver, MappingContext mappingContext) {
        new DefaultGormApiFactory().createDynamicFinders(datastoreResolver, mappingContext)
    }
}
