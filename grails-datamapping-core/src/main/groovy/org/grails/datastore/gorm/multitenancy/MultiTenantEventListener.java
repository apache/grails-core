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

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import org.springframework.context.ApplicationEvent;

import grails.gorm.multitenancy.Tenants;
import org.grails.datastore.gorm.GormEnhancer;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable;
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

/**
 * An event listener that hooks into persistence events to enable discriminator based multi tenancy (ie {@link org.grails.datastore.mapping.multitenancy.MultiTenancySettings.MultiTenancyMode#DISCRIMINATOR}
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public class MultiTenantEventListener implements PersistenceEventListener {
    protected final Datastore datastore;
    public static final List<Class<? extends ApplicationEvent>> SUPPORTED_EVENTS = Arrays.asList(PreQueryEvent.class, ValidationEvent.class, PreInsertEvent.class, PreUpdateEvent.class);

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
        handleEvent(event);
        if (event instanceof ValidationEvent && this.datastore.equals(event.getSource())) {
            ValidationEvent validationEvent = (ValidationEvent) event;
            if (validationEvent.isDeepValidate()) {
                // The associated objects that are validated with this one get no event of their own, so the
                // validator calls back with each of them
                validationEvent.addCascadeListener(this::assignTenantIdOfAssociatedObject);
            }
        }
    }

    // Called with an associated object once its beforeValidate callback has run, and before its constraints are
    // checked. The object is given the tenant id it is inserted or updated with. One that has a tenant id and is
    // not saved with its owner keeps it, and is passed again once every callback of the validation has run, as one
    // of them may have changed it. Returns whether the object was given a tenant id.
    private boolean assignTenantIdOfAssociatedObject(Object associatedObject) {
        ValidationEvent cascaded = new ValidationEvent(this.datastore, associatedObject);
        PersistentEntity entity = cascaded.getEntity();
        if (entity == null || !entity.isMultiTenant()) {
            return false;
        }
        TenantId tenantId = entity.getTenantId();
        Object object = cascaded.getEntityObject();
        Object previous = tenantId != null ? cascaded.getEntityAccess().getProperty(tenantId.getName()) : null;
        boolean saved = isSaved(object);
        if (previous != null && !saved) {
            return false;
        }
        handleEvent(cascaded);
        if (tenantId != null && saved) {
            markChanged(object, tenantId.getName(), previous, cascaded.getEntityAccess().getProperty(tenantId.getName()));
        }
        return true;
    }

    // A datastore that writes the changed properties of an object alone, as GORM for MongoDB does, stores the tenant
    // id that this listener assigns as the object is updated, since it takes the properties that a listener sets
    // then as changed. The tenant id is now set here, before that, so it is recorded as a change of an object that
    // is updated. An object that is not updated is not touched, or it would be updated because of this, and neither
    // is one that is changed as a whole, all of which is written.
    private static void markChanged(Object object, String tenantIdName, Object previous, Object assigned) {
        if (object instanceof DirtyCheckable) {
            DirtyCheckable dirtyCheckable = (DirtyCheckable) object;
            boolean changedAsAWhole = dirtyCheckable.hasChanged() && dirtyCheckable.listDirtyPropertyNames().isEmpty();
            if (!changedAsAWhole) {
                dirtyCheckable.markDirty(tenantIdName, assigned, previous);
            }
        }
    }

    // Whether the object is inserted or updated with its owner. For an object that tracks its changes this is the
    // answer of the session, which updates an object only when it is dirty. An object without a tenant id is not
    // asked: the in-memory datastore does not read the tenant id of a loaded object back.
    private boolean isSaved(Object object) {
        return !(object instanceof DirtyCheckable) || this.datastore.getCurrentSession().isDirty(object);
    }

    private void handleEvent(ApplicationEvent event) {
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
                            }
                            else {
                                currentId = Tenants.currentId(datastore.getClass());
                            }
                            query.eq(tenantId.getName(), currentId);
                        }
                    }
                }
            }
            else if ((event instanceof ValidationEvent) || (event instanceof PreInsertEvent) || (event instanceof PreUpdateEvent)) {
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
                        }
                        else {
                            currentId = Tenants.currentId(datastore.getClass());
                        }
                        if (currentId != null) {
                            try {
                                if (Tenants.isWithoutId(datastore)) {
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

