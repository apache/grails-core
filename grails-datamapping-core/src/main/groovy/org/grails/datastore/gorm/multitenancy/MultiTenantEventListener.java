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
     * Finds the associated objects that the validation of the object of the given event cascades to, directly or
     * through another associated object. They are validated with that object, without a {@link ValidationEvent}
     * of their own, so a listener that assigns the tenant id of an object before it is validated handles the
     * returned events as well.
     *
     * @param event The event of the object that is validated
     * @return An event for each associated entity instance that is validated with that object
     * @since 7.0.18
     */
    public static List<ValidationEvent> cascadedValidationEvents(ValidationEvent event) {
        PersistentEntity entity = event.getEntity();
        Object source = event.getSource();
        if (entity == null || !(source instanceof Datastore)) {
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
            if (cascaded.getEntity() != null) {
                events.add(cascaded);
            }
        }
        return events;
    }

    // Follows the associations that PersistentEntityValidator cascades validation to. An association or an object
    // that is not loaded yet is left alone.
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
            PersistentEntity associatedEntity = association.getAssociatedEntity();
            if (associatedEntity == null || association == inverseSide) {
                continue;
            }
            Collection<?> values = Collections.emptyList();
            if (association instanceof ToOne) {
                values = Collections.singletonList(reflector.getProperty(object, association.getName()));
            }
            else if (association instanceof ToMany && association.doesCascadeValidate(null)) {
                Object value = reflector.getProperty(object, association.getName());
                if (value instanceof Collection && proxyHandler.isInitialized(value)) {
                    values = (Collection<?>) value;
                }
                else if (value instanceof Map && proxyHandler.isInitialized(value)) {
                    values = ((Map<?, ?>) value).values();
                }
            }
            Association nextInverseSide = association.isBidirectional() ? association.getInverseSide() : null;
            for (Object associatedObject : values) {
                if (associatedObject != null && proxyHandler.isInitialized(associatedObject) && visited.add(associatedObject) &&
                        association.doesCascadeValidate(associatedObject)) {
                    associatedObjects.add(associatedObject);
                    collectCascadedObjects(associatedEntity, associatedObject, nextInverseSide, visited, associatedObjects);
                }
            }
        }
    }
}

