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
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.GormInstanceApi
import org.grails.datastore.gorm.GormStaticApi
import org.grails.datastore.gorm.GormValidationApi
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.model.PersistentEntity
import org.springframework.transaction.PlatformTransactionManager

/**
 * A {@link GormEnhancer} for Hibernate.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class HibernateGormEnhancer extends GormEnhancer {

    protected final Map<String, HibernateDatastore> datastoresByConnectionSource

    HibernateGormEnhancer(HibernateDatastore datastore, PlatformTransactionManager transactionManager, ConnectionSourceSettings settings) {
        this(datastore, transactionManager, settings, Collections.<String, HibernateDatastore>emptyMap())
    }

    HibernateGormEnhancer(HibernateDatastore datastore, PlatformTransactionManager transactionManager, ConnectionSourceSettings settings, Map<String, HibernateDatastore> datastoresByConnectionSource) {
        super(datastore, transactionManager, settings)
        this.datastoresByConnectionSource = datastoresByConnectionSource
        for (entry in datastoresByConnectionSource.entrySet()) {
            if (entry.key != ConnectionSource.DEFAULT) {
                registry.registerDatastore(entry.key, entry.value)
            }
        }
    }

    @Override
    void close() throws IOException {
        super.close()
        for (child in datastoresByConnectionSource.values()) {
            if (child != datastore) {
                registry.removeDatastore(child)
            }
        }
    }

    @Override
    public List<String> allQualifiers(Datastore datastore, PersistentEntity entity) {
        List<String> qualifiers = new ArrayList<>(super.allQualifiers(datastore, entity))
        if (qualifiers.contains(ConnectionSource.ALL)) {
            qualifiers.remove(ConnectionSource.ALL)
            qualifiers.addAll(datastoresByConnectionSource.keySet())
        }
        return qualifiers.unique()
    }

    @Override
    protected <D> GormStaticApi<D> getStaticApi(Class<D> cls, org.grails.datastore.gorm.DatastoreResolver resolver, String qualifier) {
        HibernateDatastore hibernateDatastore = (HibernateDatastore) datastore
        org.grails.datastore.gorm.DatastoreResolver dynamicResolver = new org.grails.datastore.gorm.DatastoreResolver() {
            @Override Datastore resolve() { org.grails.datastore.gorm.GormEnhancer.findDatastore(cls, qualifier) }
        }
        new HibernateGormStaticApi<D>(
                cls,
                hibernateDatastore.mappingContext,
                createDynamicFinders(resolver, hibernateDatastore.mappingContext),
                dynamicResolver,
                qualifier,
                hibernateDatastore.mappingContext.getMappingFactory().getClass().getClassLoader()
        )
    }

    @Override
    protected <D> GormInstanceApi<D> getInstanceApi(Class<D> cls, org.grails.datastore.gorm.DatastoreResolver resolver) {
        HibernateDatastore hibernateDatastore = (HibernateDatastore) datastore
        org.grails.datastore.gorm.DatastoreResolver dynamicResolver = new org.grails.datastore.gorm.DatastoreResolver() {
            @Override Datastore resolve() { org.grails.datastore.gorm.GormEnhancer.findDatastore(cls, null) }
        }
        new HibernateGormInstanceApi<D>(cls, hibernateDatastore.mappingContext, dynamicResolver, hibernateDatastore.mappingContext.getMappingFactory().getClass().getClassLoader())
    }

    @Override
    protected <D> GormValidationApi<D> getValidationApi(Class<D> cls, org.grails.datastore.gorm.DatastoreResolver resolver) {
        HibernateDatastore hibernateDatastore = (HibernateDatastore) datastore
        org.grails.datastore.gorm.DatastoreResolver dynamicResolver = new org.grails.datastore.gorm.DatastoreResolver() {
            @Override Datastore resolve() { org.grails.datastore.gorm.GormEnhancer.findDatastore(cls, null) }
        }
        new HibernateGormValidationApi<D>(cls, hibernateDatastore.mappingContext, dynamicResolver, hibernateDatastore.mappingContext.getMappingFactory().getClass().getClassLoader())
    }

    @Override
    protected void registerConstraints(Datastore datastore) {
        // no-op
    }
}
