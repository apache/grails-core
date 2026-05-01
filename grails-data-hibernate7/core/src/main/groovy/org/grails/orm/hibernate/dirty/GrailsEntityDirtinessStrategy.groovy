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
package org.grails.orm.hibernate.dirty

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.dirty.checking.DirtyCheckingSupport
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Embedded
import org.hibernate.CustomEntityDirtinessStrategy
import org.hibernate.CustomEntityDirtinessStrategy.AttributeChecker
import org.hibernate.CustomEntityDirtinessStrategy.AttributeInformation
import org.hibernate.CustomEntityDirtinessStrategy.DirtyCheckContext
import org.hibernate.Hibernate
import org.hibernate.Session
import org.hibernate.engine.spi.EntityEntry
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.engine.spi.Status
import org.hibernate.persister.entity.EntityPersister
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.grails.datastore.gorm.GormEnhancer

/**
 * Implementation of the {@link CustomEntityDirtinessStrategy} interface for Grails
 *
 * @author James Kleeh
 * @author Graeme Rocher
 *
 * @since 6.0.3
 */
@CompileStatic
class GrailsEntityDirtinessStrategy implements CustomEntityDirtinessStrategy {

    protected static final Logger LOG = LoggerFactory.getLogger(GrailsEntityDirtinessStrategy)

    @Override
    boolean canDirtyCheck(Object entity, EntityPersister persister, Session session) {
        return entity instanceof DirtyCheckable
    }

    @Override
    boolean isDirty(Object entity, EntityPersister persister, Session session) {
        DirtyCheckable dirtyCheckable = cast(entity)
        boolean dirty = !session.contains(entity) || dirtyCheckable.hasChanged() || DirtyCheckingSupport.areEmbeddedDirty(GormEnhancer.findEntity(Hibernate.getClass(entity)), entity)
        return dirty
    }

    @Override
    void resetDirty(Object entity, EntityPersister persister, Session session) {
        if (canDirtyCheck(entity, persister, session)) {
            cast(entity).trackChanges()
            try {
                PersistentEntity persistentEntity = GormEnhancer.findEntity(Hibernate.getClass(entity))
                if (persistentEntity != null) {
                    resetDirtyEmbeddedObjects(persistentEntity, entity, persister, session)
                }
            } catch (IllegalStateException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(e.message, e)
                }
            }
        }
    }

    private void resetDirtyEmbeddedObjects(PersistentEntity persistentEntity,
                                           Object entity,
                                           EntityPersister persister,
                                           Session session) {

        if (DirtyCheckingSupport.areEmbeddedDirty(persistentEntity, entity)) {
            final associations = persistentEntity.getEmbedded()
            for (Embedded a in associations) {
                final value = a.reader.read(entity)
                resetDirty(value, persister, session)
            }
        }
    }

    @Override
    void findDirty(Object entity, EntityPersister persister, Session session, DirtyCheckContext dirtyCheckContext) {
        if (!(entity instanceof DirtyCheckable)) return

        SessionImplementor si = (SessionImplementor) session
        Status status = getStatus(session, entity)
        DirtyCheckable dirtyCheckable = cast(entity)

        dirtyCheckContext.doDirtyChecking({ AttributeInformation info ->
            // new object not yet in session — always dirty
            if (status == null) {
                return true
            }

            // session is read-only, so no need to check
            if (status == Status.READ_ONLY) {
                return false
            }

            final String propertyName = info.getName()
            if (dirtyCheckable.hasChanged(propertyName)) {
                return true
            }

            final PersistentEntity persistentEntity = GormEnhancer.findEntity(Hibernate.getClass(entity))
            if (persistentEntity != null) {
                final PersistentProperty property = persistentEntity.getPropertyByName(propertyName)
                if (property instanceof Embedded) {
                    final Object embeddedValue = ((Embedded) property).reader.read(entity)
                    if (embeddedValue instanceof DirtyCheckable && ((DirtyCheckable) embeddedValue).hasChanged()) {
                        return true
                    }
                }
            }

            return false
        } as AttributeChecker)
    }

    private Status getStatus(Session session, Object entity) {
        SessionImplementor si = (SessionImplementor) session
        EntityEntry entry = si.getPersistenceContext().getEntry(entity)
        return entry != null ? entry.getStatus() : null
    }

    private static DirtyCheckable cast(Object entity) {
        return (DirtyCheckable) entity
    }
}
