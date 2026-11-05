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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.TenantId;
import org.grails.datastore.mapping.model.types.ToMany;
import org.grails.datastore.mapping.model.types.ToOne;
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore;
import org.grails.datastore.mapping.multitenancy.exceptions.TenantException;
import org.grails.datastore.mapping.proxy.ProxyHandler;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.event.PreQueryEvent;
import org.grails.datastore.mapping.reflect.EntityReflector;

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
            // The associated objects that are validated with this one get no event of their own, so each is given
            // the tenant id it is inserted or updated with here, before it is validated
            for (ValidationEvent cascaded : cascadedValidationEvents((ValidationEvent) event)) {
                handleEvent(cascaded);
            }
        }
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

    /**
     * Finds the associated objects that are validated and saved with the object of the given event: the ones its
     * validation cascades to, directly or through another associated object, that are new or have changes. They are
     * validated with that object, without a {@link ValidationEvent} of their own, so a listener that assigns the
     * tenant id of an object before it is validated handles the returned events as well.
     *
     * <p>A validation that does not cascade, see {@link ValidationEvent#isDeepValidate()}, returns no events. Neither
     * does an associated object that has a tenant id and no changes: it is validated, but it is not saved, so it
     * keeps the tenant id it has.</p>
     *
     * @param event The event of the object that is validated
     * @return An event for each associated entity instance that is validated and saved with that object
     * @since 7.0.18
     */
    public static List<ValidationEvent> cascadedValidationEvents(ValidationEvent event) {
        PersistentEntity entity = event.getEntity();
        Object source = event.getSource();
        if (entity == null || !(source instanceof Datastore) || !event.isDeepValidate()) {
            return Collections.emptyList();
        }
        Object entityObject = event.getEntityObject();
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        visited.add(entityObject);
        List<Object> associatedObjects = new ArrayList<>();
        collectCascadedObjects(entity, entityObject, null, visited, associatedObjects);

        List<ValidationEvent> events = new ArrayList<>(associatedObjects.size());
        for (Object associatedObject : associatedObjects) {
            ValidationEvent cascaded = new ValidationEvent((Datastore) source, associatedObject);
            if (cascaded.getEntity() != null && !keepsTenantId(cascaded)) {
                events.add(cascaded);
            }
        }
        return events;
    }

    // An object without changes is not updated, so the tenant id it has is not replaced. One without a tenant id is
    // still given one, as it can be without changes too: Hibernate tracks the changes of a new object from the save
    // on, before it is inserted, and the in-memory datastore does not read the tenant id of a loaded object back.
    private static boolean keepsTenantId(ValidationEvent event) {
        Object object = event.getEntityObject();
        if (!(object instanceof DirtyCheckable) || ((DirtyCheckable) object).hasChanged()) {
            return false;
        }
        TenantId tenantId = event.getEntity().getTenantId();
        return tenantId == null || event.getEntityAccess().getProperty(tenantId.getName()) != null;
    }

    // Follows PersistentEntityValidator: the same associations, and the same order of checks for an associated
    // object, so that the objects found are the ones it validates. An association or an object that is not loaded
    // yet is left alone.
    private static void collectCascadedObjects(PersistentEntity entity, Object object, Association inverseSide,
                                               Set<Object> visited, List<Object> associatedObjects) {
        List<Association> associations = entity.getAssociations();
        if (associations == null || associations.isEmpty()) {
            return;
        }
        MappingContext mappingContext = entity.getMappingContext();
        ProxyHandler proxyHandler = mappingContext.getProxyHandler();
        if (proxyHandler == null) {
            return;
        }
        EntityReflector reflector = mappingContext.getEntityReflector(entity);
        for (Association association : associations) {
            if (association.getAssociatedEntity() == null || association == inverseSide) {
                continue;
            }
            if (association instanceof ToOne) {
                Object associatedObject = reflector.getProperty(object, association.getName());
                // An association that does not cascade to the object does not mark it as visited, so that another
                // association that refers to the same object can still cascade to it
                if (isLoaded(associatedObject, proxyHandler) && association.doesCascadeValidate(associatedObject)) {
                    collectCascadedObject(association, associatedObject, visited, associatedObjects);
                }
            }
            else if (association instanceof ToMany && association.doesCascadeValidate(null)) {
                Object value = reflector.getProperty(object, association.getName());
                if (!isLoaded(value, proxyHandler)) {
                    continue;
                }
                Collection<?> values = Collections.emptyList();
                if (value instanceof Collection) {
                    values = (Collection<?>) value;
                }
                else if (value instanceof Map) {
                    values = ((Map<?, ?>) value).values();
                }
                for (Object associatedObject : values) {
                    if (isLoaded(associatedObject, proxyHandler)) {
                        collectCascadedObject(association, associatedObject, visited, associatedObjects);
                    }
                }
            }
        }
    }

    private static void collectCascadedObject(Association association, Object associatedObject,
                                              Set<Object> visited, List<Object> associatedObjects) {
        if (visited.add(associatedObject) && association.doesCascadeValidate(associatedObject)) {
            associatedObjects.add(associatedObject);
            Association inverseSide = association.isBidirectional() ? association.getInverseSide() : null;
            collectCascadedObjects(association.getAssociatedEntity(), associatedObject, inverseSide, visited, associatedObjects);
        }
    }

    private static boolean isLoaded(Object object, ProxyHandler proxyHandler) {
        return object != null && proxyHandler.isInitialized(object);
    }
}

