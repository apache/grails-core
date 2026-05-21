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
package org.grails.orm.hibernate.support;

import java.util.Set;

import org.hibernate.Hibernate;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.Status;
import org.hibernate.event.spi.FlushEntityEvent;
import org.hibernate.event.spi.FlushEntityEventListener;
import org.hibernate.persister.entity.EntityPersister;

import org.grails.datastore.gorm.events.AutoTimestampEventListener;
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;

/**
 * A Hibernate {@link FlushEntityEventListener} that ensures auto-timestamp properties
 * (e.g., {@code lastUpdated}) are set on the entity BEFORE Hibernate computes dirty
 * properties during the flush phase.
 *
 * <p>When {@code dynamicUpdate = true}, Hibernate generates a SQL UPDATE that only
 * includes columns marked as dirty. Dirty properties are computed during the flush phase
 * (via {@code FlushEntityEvent}), before {@code PreUpdateEvent} fires. Setting
 * {@code lastUpdated} in a {@code PreUpdateEventListener} is therefore too late — the
 * column is excluded from the dynamic SQL even though its value was updated in the state
 * array.</p>
 *
 * <p>This listener is registered as a <em>prepended</em> listener so it runs before
 * {@code DefaultFlushEntityEventListener}. It sets {@code lastUpdated} directly on the
 * entity instance, so the subsequent dirty check includes it in the dirty-property set.</p>
 */
public class GormAutoTimestampFlushEntityEventListener implements FlushEntityEventListener {

    private final AutoTimestampEventListener autoTimestampEventListener;
    private final MappingContext mappingContext;

    public GormAutoTimestampFlushEntityEventListener(
            AutoTimestampEventListener autoTimestampEventListener,
            MappingContext mappingContext) {
        this.autoTimestampEventListener = autoTimestampEventListener;
        this.mappingContext = mappingContext;
    }

    @Override
    public void onFlushEntity(FlushEntityEvent event) throws HibernateException {
        final Object entity = event.getEntity();
        final EntityEntry entry = event.getEntityEntry();

        // Only handle managed entities being updated, not new inserts or deletes
        if (entry.getStatus() != Status.MANAGED) {
            return;
        }

        // No loadedState means this is a new entity (INSERT path), not an UPDATE
        final Object[] loadedState = entry.getLoadedState();
        if (loadedState == null) {
            return;
        }

        // Resolve the GORM PersistentEntity for this entity class
        final Class<?> entityClass = Hibernate.getClass(entity);
        final PersistentEntity persistentEntity = mappingContext.getPersistentEntity(entityClass.getName());
        if (persistentEntity == null) {
            return;
        }

        // Respect autoTimestamp = false mappings
        if (persistentEntity.getMapping().getMappedForm() != null
                && !persistentEntity.getMapping().getMappedForm().isAutoTimestamp()) {
            return;
        }

        // Skip entities that have no lastUpdated property registered
        final Set<String> lastUpdatedProps =
                autoTimestampEventListener.getLastUpdatedPropertyNames(persistentEntity.getName());
        if (lastUpdatedProps == null || lastUpdatedProps.isEmpty()) {
            return;
        }

        // Perform the dirty check to avoid triggering spurious UPDATEs on clean entities
        final EntityPersister persister = entry.getPersister();
        final Object[] currentState = persister.getValues(entity);
        final int[] dirtyProps =
                persister.findDirty(currentState, loadedState, entity, event.getSession());
        if (dirtyProps == null || dirtyProps.length == 0) {
            return;
        }

        // Entity IS dirty — set lastUpdated on the entity BEFORE DefaultFlushEntityEventListener
        // reads the entity values and computes the dirty-property set.
        autoTimestampEventListener.beforeUpdate(
                persistentEntity,
                mappingContext.createEntityAccess(persistentEntity, entity));

        // GrailsEntityDirtinessStrategy uses DirtyCheckable.hasChanged() to determine dirty properties.
        // Since ea.setProperty() bypasses the entity setter, we must explicitly mark lastUpdated as
        // dirty so that GrailsEntityDirtinessStrategy.findDirty() includes it in the dirty set, which
        // in turn ensures dynamicUpdate=true SQL includes the last_updated column.
        if (entity instanceof DirtyCheckable) {
            for (String prop : lastUpdatedProps) {
                ((DirtyCheckable) entity).markDirty(prop);
            }
        }
    }
}
