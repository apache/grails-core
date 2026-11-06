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
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.TenantId;
import org.grails.datastore.mapping.model.types.ToMany;
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore;
import org.grails.datastore.mapping.multitenancy.exceptions.TenantException;
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
                validationEvent.addCascadeListener(associatedObject ->
                        assignTenantIdOfAssociatedObject(hibernateDatastore, associatedObject));
            }
        }
    }

    // Called with an associated object once its beforeValidate callback has run, and before its constraints are
    // checked. The object is given the tenant id it is inserted or updated with. One that has a tenant id and is
    // not saved with its owner keeps it, and is passed again once every callback of the validation has run, as one
    // of them may have changed it. Returns whether the object was given a tenant id.
    private boolean assignTenantIdOfAssociatedObject(AbstractHibernateDatastore hibernateDatastore, Object associatedObject) {
        ValidationEvent cascaded = new ValidationEvent(hibernateDatastore, associatedObject);
        PersistentEntity entity = cascaded.getEntity();
        if (entity == null || !entity.isMultiTenant()) {
            return false;
        }
        TenantId tenantId = entity.getTenantId();
        boolean hasTenantId = tenantId != null && cascaded.getEntityAccess().getProperty(tenantId.getName()) != null;
        if (hasTenantId && !isSaved(hibernateDatastore, entity, cascaded.getEntityObject())) {
            return false;
        }
        handleEvent(cascaded);
        return true;
    }

    // Whether Hibernate writes the properties of the object as its owner is saved. It writes none of a read-only
    // object. For another object that tracks its changes this is the check of GrailsEntityDirtinessStrategy, with
    // which Hibernate decides whether to update an object of the session, and the check for a changed collection.
    // An object without a tenant id is not asked: Hibernate tracks the changes of a new object from the save on,
    // before it is inserted.
    private static boolean isSaved(AbstractHibernateDatastore hibernateDatastore, PersistentEntity entity, Object object) {
        if (isReadOnly(hibernateDatastore, object)) {
            return false;
        }
        return !(object instanceof DirtyCheckable) || ((DirtyCheckable) object).hasChanged() ||
                DirtyCheckingSupport.areEmbeddedDirty(entity, object) || hasDirtyCollection(entity, object);
    }

    // An object that is read-only in the session, such as one that read(id) or a read-only query has returned, is
    // not updated although it has changed. Hibernate looks for the changes of a modifiable object only, and updates
    // no more than the version of a read-only object whose collection has changed.
    private static boolean isReadOnly(AbstractHibernateDatastore hibernateDatastore, Object object) {
        if (!hibernateDatastore.hasCurrentSession()) {
            return false;
        }
        SessionImplementor session = (SessionImplementor) hibernateDatastore.getSessionFactory().getCurrentSession();
        EntityEntry entry = session.getPersistenceContext().getEntry(object);
        return entry != null && !entry.isModifiableEntity();
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

