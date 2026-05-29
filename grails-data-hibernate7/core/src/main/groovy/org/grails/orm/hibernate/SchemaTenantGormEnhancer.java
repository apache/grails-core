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
package org.grails.orm.hibernate;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.transaction.PlatformTransactionManager;

import grails.gorm.MultiTenant;
import org.grails.datastore.gorm.jdbc.schema.SchemaHandler;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.multitenancy.AllTenantsResolver;
import org.grails.datastore.mapping.multitenancy.TenantResolver;
import org.grails.orm.hibernate.connections.HibernateConnectionSource;

/**
 * A {@link HibernateGormEnhancer} for SCHEMA multi-tenancy mode that resolves all tenant qualifiers
 * from either the registered {@link AllTenantsResolver} or the available schema names on the data source.
 */
public class SchemaTenantGormEnhancer extends HibernateGormEnhancer {

    private final HibernateConnectionSource defaultConnectionSource;
    private final TenantResolver tenantResolver;
    private final SchemaHandler schemaHandler;
    private final Map<String, HibernateDatastore> datastoresByConnectionSource;

    public SchemaTenantGormEnhancer(
            Datastore datastore,
            PlatformTransactionManager transactionManager,
            HibernateConnectionSource defaultConnectionSource,
            TenantResolver tenantResolver,
            SchemaHandler schemaHandler,
            Map<String, HibernateDatastore> datastoresByConnectionSource) {
        super(datastore, transactionManager, defaultConnectionSource.getSettings());
        this.defaultConnectionSource = defaultConnectionSource;
        this.tenantResolver = tenantResolver;
        this.schemaHandler = schemaHandler;
        this.datastoresByConnectionSource = datastoresByConnectionSource;
        // super() calls registerEntity → allQualifiers before our fields are set.
        // Re-register now that all fields are initialized so schema qualifiers are wired correctly.
        for (PersistentEntity entity : datastore.getMappingContext().getPersistentEntities()) {
            registerEntity(entity);
        }
    }

    @Override
    public List<String> allQualifiers(Datastore datastore, PersistentEntity entity) {
        List<String> allQualifiers = super.allQualifiers(datastore, entity);
        // Guard against being called from super() before our fields are initialized.
        if (defaultConnectionSource == null) {
            return allQualifiers;
        }
        if (MultiTenant.class.isAssignableFrom(entity.getJavaClass())) {
            if (tenantResolver instanceof AllTenantsResolver allTenantsResolver) {
                Iterable<Serializable> tenantIds = allTenantsResolver.resolveTenantIds();
                for (Serializable id : tenantIds) {
                    allQualifiers.add(id.toString());
                }
            } else {
                Collection<String> schemaNames =
                        schemaHandler.resolveSchemaNames(defaultConnectionSource.getDataSource());
                for (String schemaName : schemaNames) {
                    if (HibernateDatastore.INFORMATION_SCHEMA.equals(schemaName) || HibernateDatastore.PUBLIC_SCHEMA.equals(schemaName)) continue;
                    for (String connectionName : datastoresByConnectionSource.keySet()) {
                        if (schemaName.equalsIgnoreCase(connectionName)) {
                            allQualifiers.add(connectionName);
                        }
                    }
                }
            }
        }
        return allQualifiers;
    }
}
