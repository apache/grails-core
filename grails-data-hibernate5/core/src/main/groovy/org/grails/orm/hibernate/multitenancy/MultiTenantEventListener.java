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

package org.grails.orm.hibernate.multitenancy;

import java.io.Serializable;
import java.util.List;

import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.SessionImplementor;

import org.springframework.context.ApplicationEvent;

import grails.gorm.multitenancy.Tenants;
import org.grails.datastore.gorm.GormEnhancer;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable;
import org.grails.datastore.mapping.dirty.checking.DirtyCheckingSupport;
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent;
import org.grails.datastore.mapping.engine.event.PersistenceEventListener;
import org.grails.datastore.mapping.engine.event.PreInsertEvent;
import org.grails.datastore.mapping.engine.event.PreUpdateEvent;
import org.grails.datastore.mapping.engine.event.ValidationEvent;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.TenantId;
import org.grails.datastore.mapping.model.types.ToMany;
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore;
import org.grails.datastore.mapping.multitenancy.exceptions.TenantException;
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.event.PreQueryEvent;
import org.grails.datastore.mapping.reflect.EntityReflector;
import org.grails.orm.hibernate.AbstractHibernateDatastore;

/**
 * An event listener that hooks into persistence events to enable discriminator based multi tenancy (ie {@link org.grails.datastore.mapping.multitenancy.MultiTenancySettings.MultiTenancyMode#DISCRIMINATOR}
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public class MultiTenantEventListener implements PersistenceEventListener {
    @Override
    public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        return org.grails.datastore.gorm.multitenancy.MultiTenantEventListener.SUPPORTED_EVENTS.contains(eventType);
    }

    @Override
    public boolean supportsSourceType(Class<?> sourceType) {
        return AbstractHibernateDatastore.class.isAssignableFrom(sourceType);
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        handleEvent(event);
        if (event instanceof ValidationEvent && supportsSourceType(event.getSource().getClass())) {
            ValidationEvent validationEvent = (ValidationEvent) event;
            if (validationEvent.isDeepValidate()) {
                // The associated objects that are validated with this one get no event of their own, so the
                // validator calls back with each of them
                AbstractHibernateDatastore hibernateDatastore = (AbstractHibernateDatastore) event.getSource();
                validationEvent.addCascadeListener((associatedObject, association) ->
                        assignTenantIdOfAssociatedObject(hibernateDatastore, associatedObject));
            }
        }
    }

    // Called with an associated object once its beforeValidate callback has run, and before its constraints are
    // checked. The object is given the tenant id it is inserted or updated with. One that is not saved with its owner
    // keeps the tenant id it has, or its lack of one, and is passed again once every callback of the validation has
    // run, as one of them may have changed it. Without a current tenant the object is left alone, and gets its tenant
    // id as it is inserted or updated, as before. Returns whether the object was given a tenant id.
    private boolean assignTenantIdOfAssociatedObject(AbstractHibernateDatastore hibernateDatastore, Object associatedObject) {
        ValidationEvent cascaded = new ValidationEvent(hibernateDatastore, associatedObject);
        PersistentEntity entity = cascaded.getEntity();
        if (entity == null || !entity.isMultiTenant()) {
            return false;
        }
        if (!isSaved(hibernateDatastore, entity, cascaded.getEntityObject())) {
            return false;
        }
        try {
            handleEvent(cascaded);
        }
        catch (TenantNotFoundException e) {
            return false;
        }
        return true;
    }

    // Whether Hibernate writes the properties of the object as its owner is saved. It writes none of an object that
    // is read-only in the session, such as one that read(id) or a read-only query has returned: Hibernate looks for
    // the changes of a modifiable object only, and updates no more than the version of a read-only object whose
    // collection has changed. It inserts an object of the session that is not in the database yet, whose insert
    // waits for the flush, as its identifier comes from a sequence for example. For another object that tracks its
    // changes this is the check of GrailsEntityDirtinessStrategy, with which Hibernate decides whether to update an
    // object of the session, and the check for a changed collection. A new object that is not in the session yet has
    // no tracked changes, so it counts as changed.
    private static boolean isSaved(AbstractHibernateDatastore hibernateDatastore, PersistentEntity entity, Object object) {
        EntityEntry entry = entryOf(hibernateDatastore, object);
        if (entry != null) {
            if (!entry.isModifiableEntity()) {
                return false;
            }
            if (!entry.isExistsInDatabase()) {
                return true;
            }
        }
        return !(object instanceof DirtyCheckable) || hasStoredChange(entity, (DirtyCheckable) object) ||
                DirtyCheckingSupport.areEmbeddedDirty(entity, object) || hasDirtyCollection(entity, object);
    }

    // The entry of the object in the current session, if it is in one
    private static EntityEntry entryOf(AbstractHibernateDatastore hibernateDatastore, Object object) {
        if (!hibernateDatastore.hasCurrentSession()) {
            return null;
        }
        SessionImplementor session = (SessionImplementor) hibernateDatastore.getSessionFactory().getCurrentSession();
        return session.getPersistenceContext().getEntry(object);
    }

    // Whether the object has a change that makes Hibernate update it. GrailsEntityDirtinessStrategy updates an object
    // when a property that Hibernate maps has changed, and any changed object that has a lastUpdated property. A
    // change to a property that it does not map, such as an untyped one, makes no update. An object that has changed
    // without a changed property is new, or is changed as a whole.
    private static boolean hasStoredChange(PersistentEntity entity, DirtyCheckable object) {
        if (!object.hasChanged()) {
            return false;
        }
        List<String> changed = object.listDirtyPropertyNames();
        if (changed.isEmpty() || entity.getPropertyByName(GormProperties.LAST_UPDATED) != null) {
            return true;
        }
        for (String name : changed) {
            if (entity.getPropertyByName(name) != null) {
                return true;
            }
        }
        return false;
    }

    // Hibernate updates a versioned object when one of its collections has changed, to increment its version
    private static boolean hasDirtyCollection(PersistentEntity entity, Object object) {
        if (!entity.isVersioned()) {
            return false;
        }
        EntityReflector reflector = entity.getReflector();
        for (Association association : entity.getAssociations()) {
            if (association instanceof ToMany) {
                Object value = reflector.getProperty(object, association.getName());
                if (value instanceof PersistentCollection && ((PersistentCollection) value).isDirty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void handleEvent(ApplicationEvent event) {
        if (supportsEventType(event.getClass())) {
            Datastore hibernateDatastore = (Datastore) event.getSource();
            if (event instanceof PreQueryEvent) {
                PreQueryEvent preQueryEvent = (PreQueryEvent) event;
                Query query = preQueryEvent.getQuery();

                PersistentEntity entity = query.getEntity();
                if (hibernateDatastore == null) {
                    hibernateDatastore = GormEnhancer.findDatastore(entity.getJavaClass());
                }
                if (supportsSourceType(hibernateDatastore.getClass())) {
                    ((AbstractHibernateDatastore) hibernateDatastore).enableMultiTenancyFilter(entity);
                }
            }
            else if ((event instanceof ValidationEvent) || (event instanceof PreInsertEvent) || (event instanceof PreUpdateEvent)) {
                AbstractPersistenceEvent preInsertEvent = (AbstractPersistenceEvent) event;
                PersistentEntity entity = preInsertEvent.getEntity();
                if (entity.isMultiTenant()) {
                    TenantId tenantId = entity.getTenantId();
                    if (hibernateDatastore == null) {
                        hibernateDatastore = GormEnhancer.findDatastore(entity.getJavaClass());
                    }
                    if (supportsSourceType(hibernateDatastore.getClass())) {
                        Serializable currentId;

                        if (hibernateDatastore instanceof MultiTenantCapableDatastore) {
                            currentId = Tenants.currentId((MultiTenantCapableDatastore) hibernateDatastore);
                        }
                        else {
                            currentId = Tenants.currentId(hibernateDatastore.getClass());
                        }
                        if (currentId != null) {
                            try {
                                if (Tenants.isWithoutId(hibernateDatastore)) {
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

