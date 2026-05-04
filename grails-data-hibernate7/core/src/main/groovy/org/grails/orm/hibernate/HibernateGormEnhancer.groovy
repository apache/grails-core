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
import org.grails.datastore.gorm.DatastoreResolver
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.hibernate.engine.spi.SessionFactoryImplementor
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

    @Deprecated
    HibernateGormEnhancer(HibernateDatastore datastore, PlatformTransactionManager transactionManager) {
        this(datastore, transactionManager, datastore.connectionSources.defaultConnectionSource.settings)
    }

    HibernateGormEnhancer(HibernateDatastore datastore, PlatformTransactionManager transactionManager, ConnectionSourceSettings settings) {
        this(datastore, transactionManager, settings, Collections.<String, HibernateDatastore>emptyMap())
    }

    HibernateGormEnhancer(HibernateDatastore datastore, PlatformTransactionManager transactionManager, ConnectionSourceSettings settings, Map<String, HibernateDatastore> datastoresByConnectionSource) {
        super(datastore, transactionManager, settings)
        this.datastoresByConnectionSource = datastoresByConnectionSource
        if (datastore instanceof ChildHibernateDatastore) {
            registry.removeDatastoreFromDiscovery(datastore)
        }
    }

    @Override
    void close() throws IOException {
        super.close()
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

    protected <D> GormStaticApi<D> getStaticApi(Class<D> cls, String qualifier) {
        getStaticApi(cls, getDatastoreResolver(cls, qualifier), qualifier)
    }

    protected <D> GormInstanceApi<D> getInstanceApi(Class<D> cls, String qualifier) {
        getInstanceApi(cls, getDatastoreResolver(cls, qualifier))
    }

    protected <D> GormValidationApi<D> getValidationApi(Class<D> cls, String qualifier) {
        getValidationApi(cls, getDatastoreResolver(cls, qualifier))
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
                hibernateDatastore.mappingContext.getMappingFactory().getClass().getClassLoader()
        )
    }

    @Override
    protected <D> GormInstanceApi<D> getInstanceApi(Class<D> cls, DatastoreResolver resolver) {
        HibernateDatastore hibernateDatastore = (HibernateDatastore) datastore
        new HibernateGormInstanceApi<D>(cls, hibernateDatastore.mappingContext, resolver, hibernateDatastore.mappingContext.getMappingFactory().getClass().getClassLoader())
    }

    @Override
    protected <D> GormValidationApi<D> getValidationApi(Class<D> cls, DatastoreResolver resolver) {
        HibernateDatastore hibernateDatastore = (HibernateDatastore) datastore
        new HibernateGormValidationApi<D>(cls, hibernateDatastore.mappingContext, resolver, hibernateDatastore.mappingContext.getMappingFactory().getClass().getClassLoader())
    }

    protected DatastoreResolver getDatastoreResolver(Class cls, String qualifier) {
        HibernateDatastore hibernateDatastore = (HibernateDatastore) datastore
        HibernateGormEnhancer self = this
        new DatastoreResolver() {
            @Override Datastore resolve() {
                if (ConnectionSource.DEFAULT.equals(qualifier)) {
                    org.grails.datastore.mapping.multitenancy.MultiTenancySettings.MultiTenancyMode mode = hibernateDatastore.getMultiTenancyMode()
                    if (mode != org.grails.datastore.mapping.multitenancy.MultiTenancySettings.MultiTenancyMode.NONE) {
                        Serializable tenantId = grails.gorm.multitenancy.Tenants.currentId((org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore)hibernateDatastore)
                        System.err.println "RESOLVE for ${cls.name} mode: ${mode} tenantId: ${tenantId}"
                        if (tenantId != null && !ConnectionSource.DEFAULT.equals(tenantId.toString())) {
                            Datastore ds = hibernateDatastore.getDatastoreForTenantId(tenantId)
                            System.err.println "ROUTED to child ds: ${ds}"
                            return ds
                        }
                    }
                    return self.resolveOwningDatastore(cls)
                }
                Datastore ds = hibernateDatastore.getDatastoreForConnection(qualifier)
                if (ds != null) return ds
                return org.grails.datastore.gorm.GormEnhancer.findDatastore(cls, qualifier)
            }
        }
    }

    protected Datastore resolveOwningDatastore(Class cls) {
        HibernateDatastore hds = (HibernateDatastore) datastore
        PersistentEntity entity = hds.getMappingContext().getPersistentEntity(cls.name)
        if (entity != null && !org.grails.datastore.mapping.core.connections.ConnectionSourcesSupport.usesConnectionSource(entity, ConnectionSource.DEFAULT)) {
            Map<String, HibernateDatastore> byConn = datastoresByConnectionSource
            if (byConn != null && !byConn.isEmpty()) {
                for (HibernateDatastore sub : byConn.values()) {
                    if (sub != hds) {
                        SessionFactoryImplementor subSfi = (SessionFactoryImplementor) sub.sessionFactory
                        if (subSfi.mappingMetamodel.findEntityDescriptor(cls) != null) {
                            return sub
                        }
                    }
                }
            }
        }
        return GormEnhancer.findDatastore(cls, ConnectionSource.DEFAULT)
    }

    @Override
    void registerEntity(PersistentEntity entity) {
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
            GormStaticApi staticApi = getStaticApi(entity.javaClass, apiQualifier)
            GormInstanceApi instanceApi = getInstanceApi(entity.javaClass, apiQualifier)
            GormValidationApi validationApi = getValidationApi(entity.javaClass, apiQualifier)
            registry.registerApi(entity.name, staticApi, instanceApi, validationApi)
        }
    }

    @Override
    protected void registerConstraints(Datastore datastore) {
        // no-op
    }
}
