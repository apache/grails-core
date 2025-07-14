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
package org.grails.datastore.gorm.multitenancy;

import grails.gorm.multitenancy.Tenants;
import org.grails.datastore.gorm.GormEnhancer;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.connections.ConnectionSource;
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent;
import org.grails.datastore.mapping.engine.event.PersistenceEventListener;
import org.grails.datastore.mapping.engine.event.PreInsertEvent;
import org.grails.datastore.mapping.engine.event.PreUpdateEvent;
import org.grails.datastore.mapping.engine.event.ValidationEvent;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.types.TenantId;
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore;
import org.grails.datastore.mapping.multitenancy.exceptions.TenantException;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.event.PreQueryEvent;
import org.springframework.context.ApplicationEvent;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

/**
 * An event listener that hooks into persistence events to enable discriminator based multi tenancy (ie {@link org.grails.datastore.mapping.multitenancy.MultiTenancySettings.MultiTenancyMode#DISCRIMINATOR}
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public class MultiTenantEventListener implements PersistenceEventListener {

    public static final List<Class<? extends ApplicationEvent>> SUPPORTED_EVENTS = Arrays.asList(PreQueryEvent.class, ValidationEvent.class, PreInsertEvent.class, PreUpdateEvent.class);
    protected final Datastore datastore;

    public MultiTenantEventListener(Datastore datastore) {
        this.datastore = datastore;
    }

    @Override
    public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        return SUPPORTED_EVENTS.contains(eventType);
    }

    @Override
    public boolean supportsSourceType(Class<?> sourceType) {
        return Datastore.class.isAssignableFrom(sourceType);
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        Class<? extends ApplicationEvent> eventClass = event.getClass();
        if (supportsEventType(eventClass)) {
            Datastore datastore = (Datastore) event.getSource();
            if (event instanceof PreQueryEvent) {
                PreQueryEvent preQueryEvent = (PreQueryEvent) event;
                Query query = preQueryEvent.getQuery();

                PersistentEntity entity = query.getEntity();
                if (entity.isMultiTenant()) {
                    if (datastore == null) {
                        datastore = GormEnhancer.findDatastore(entity.getJavaClass());
                    }
                    if (supportsSourceType(datastore.getClass()) && this.datastore.equals(datastore)) {
                        TenantId tenantId = entity.getTenantId();
                        if (tenantId != null) {
                            Serializable currentId;

                            if (datastore instanceof MultiTenantCapableDatastore) {
                                currentId = Tenants.currentId((MultiTenantCapableDatastore) datastore);
                            } else {
                                currentId = Tenants.currentId(datastore.getClass());
                            }
                            query.eq(tenantId.getName(), currentId);
                        }
                    }
                }
            } else if ((event instanceof ValidationEvent) || (event instanceof PreInsertEvent) || (event instanceof PreUpdateEvent)) {
                AbstractPersistenceEvent preInsertEvent = (AbstractPersistenceEvent) event;
                PersistentEntity entity = preInsertEvent.getEntity();
                if (entity.isMultiTenant()) {
                    TenantId tenantId = entity.getTenantId();
                    if (datastore == null) {
                        datastore = GormEnhancer.findDatastore(entity.getJavaClass());
                    }
                    if (supportsSourceType(datastore.getClass()) && this.datastore.equals(datastore)) {
                        Serializable currentId;

                        if (datastore instanceof MultiTenantCapableDatastore) {
                            currentId = Tenants.currentId((MultiTenantCapableDatastore) datastore);
                        } else {
                            currentId = Tenants.currentId(datastore.getClass());
                        }
                        if (currentId != null) {
                            try {
                                if (currentId == ConnectionSource.DEFAULT) {
                                    currentId = (Serializable) preInsertEvent.getEntityAccess().getProperty(tenantId.getName());
                                }
                                preInsertEvent.getEntityAccess().setProperty(tenantId.getName(), currentId);
                            } catch (Exception e) {
                                throw new TenantException("Could not assigned tenant id [" + currentId + "] to property [" + tenantId + "], probably due to a type mismatch. You should return a type from the tenant resolver that matches the property type of the tenant id!: " + e.getMessage(), e);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getOrder() {
        return DEFAULT_ORDER;
    }
}

