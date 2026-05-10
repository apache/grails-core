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

import org.springframework.transaction.PlatformTransactionManager

import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.GormInstanceApi
import org.grails.datastore.gorm.GormStaticApi
import org.grails.datastore.gorm.GormValidationApi
import org.grails.datastore.gorm.DatastoreResolver
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.model.PersistentEntity

/**
 * Extended GORM Enhancer that fills out the remaining GORM for Hibernate methods
 * and implements string-based query support via HQL.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class HibernateGormEnhancer extends GormEnhancer {

    private final PlatformTransactionManager transactionManager

    HibernateGormEnhancer(HibernateDatastore datastore, PlatformTransactionManager transactionManager) {
        super(datastore)
        this.transactionManager = transactionManager
    }

    HibernateGormEnhancer(Datastore datastore, PlatformTransactionManager transactionManager, ConnectionSourceSettings settings) {
        super(datastore, transactionManager, settings)
        this.transactionManager = transactionManager
    }

    @Override
    protected <D> GormStaticApi<D> getStaticApi(Class<D> cls, DatastoreResolver resolver, String qualifier) {
        HibernateDatastore hibernateDatastore = (HibernateDatastore) datastore
        new HibernateGormStaticApi<D>(
                cls,
                hibernateDatastore.mappingContext,
                createDynamicFinders(resolver, hibernateDatastore.mappingContext),
                resolver,
                qualifier,
                cls.classLoader
        )
    }

    @Override
    protected <D> GormInstanceApi<D> getInstanceApi(Class<D> cls, DatastoreResolver resolver) {
        HibernateDatastore hibernateDatastore = (HibernateDatastore) datastore
        new HibernateGormInstanceApi<D>(cls, hibernateDatastore.mappingContext, resolver, cls.classLoader)
    }

    @Override
    protected <D> GormValidationApi<D> getValidationApi(Class<D> cls, DatastoreResolver resolver) {
        HibernateDatastore hibernateDatastore = (HibernateDatastore) datastore
        new GormValidationApi<D>(cls, hibernateDatastore.mappingContext, resolver)
    }

    @Override
    void registerEntity(PersistentEntity entity) {
        System.out.println("DEBUG: HibernateGormEnhancer.registerEntity called for " + entity.getName());
        HibernateDatastore hds = (HibernateDatastore) datastore
        String defaultConnectionName = hds.connectionSources.defaultConnectionSource.name
        
        // Register datastore for this qualifier
        if (org.grails.datastore.mapping.core.connections.ConnectionSourcesSupport.usesConnectionSource(entity, defaultConnectionName)) {
            registry.registerEntityDatastore(entity.name, defaultConnectionName, hds)
        }
        if (ConnectionSource.DEFAULT.equals(defaultConnectionName) && org.grails.datastore.mapping.core.connections.ConnectionSourcesSupport.usesConnectionSource(entity, ConnectionSource.ALL)) {
            registry.registerEntityDatastore(entity.name, ConnectionSource.ALL, hds)
        }

        // Only register APIs for the PREFERRED connection source to avoid overwriting
        List<String> names = org.grails.datastore.mapping.core.connections.ConnectionSourcesSupport.getConnectionSourceNames(entity)
        String preferred = names.isEmpty() ? ConnectionSource.DEFAULT : names.get(0)
        
        boolean isAll = ConnectionSource.ALL.equals(preferred)
        if (defaultConnectionName.equals(preferred) || (ConnectionSource.DEFAULT.equals(defaultConnectionName) && isAll)) {
            String apiQualifier = isAll ? ConnectionSource.DEFAULT : preferred
            
            // We use a dynamic resolver that delegates to GormEnhancer.findDatastore
            // so that the API instance remains valid even if datastores are restarted (common in TCK)
            DatastoreResolver resolver = new DatastoreResolver() {
                @Override Datastore resolve() {
                    org.grails.datastore.gorm.GormEnhancer.findDatastore(entity.javaClass, apiQualifier)
                }
            }
            
            GormStaticApi staticApi = getStaticApi(entity.javaClass, resolver, apiQualifier)
            GormInstanceApi instanceApi = getInstanceApi(entity.javaClass, resolver)
            GormValidationApi validationApi = getValidationApi(entity.javaClass, resolver)
            
            // Overwrite existing APIs in the registry. This is safe because our new APIs are dynamic.
            registry.registerApi(entity.name, staticApi, instanceApi, validationApi)
        }

        addStaticMethods(entity)
        addInstanceMethods(entity, false)
    }

    @Override
    protected void registerConstraints(Datastore datastore) {
        // no-op
    }
}
