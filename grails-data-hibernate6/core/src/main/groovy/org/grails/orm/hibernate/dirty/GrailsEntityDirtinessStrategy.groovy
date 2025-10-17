/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.orm.hibernate.dirty

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.dirty.checking.DirtyCheckingSupport
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.model.types.Embedded
import org.hibernate.CustomEntityDirtinessStrategy
import org.hibernate.Hibernate
import org.hibernate.Session
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.engine.spi.Status
import org.hibernate.persister.entity.EntityPersister
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * A class to customize Hibernate dirtiness based on Grails {@link DirtyCheckable} interface
 *
 * @author James Kleeh
 * @author Graeme Rocher
 *
 * @since 6.0.3
 */
@CompileStatic
class GrailsEntityDirtinessStrategy implements CustomEntityDirtinessStrategy {

    protected static final Logger LOG = LoggerFactory.getLogger(GrailsEntityDirtinessStrategy.class)

    @Override
    boolean canDirtyCheck(Object entity, EntityPersister persister, Session session) {
        return entity instanceof DirtyCheckable
    }

    @Override
    boolean isDirty(Object entity, EntityPersister persister, Session session) {
        !session.contains(entity) || cast(entity).hasChanged() || DirtyCheckingSupport.areEmbeddedDirty(GormEnhancer.findEntity(Hibernate.getClass(entity)), entity)
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
    void findDirty(Object entity, EntityPersister persister, Session session, CustomEntityDirtinessStrategy.DirtyCheckContext dirtyCheckContext) {
        Status status = getStatus(session, entity)
        if(entity instanceof DirtyCheckable) {
            dirtyCheckContext.doDirtyChecking(
                    new CustomEntityDirtinessStrategy.AttributeChecker() {
                        @Override
                        boolean isDirty(CustomEntityDirtinessStrategy.AttributeInformation attributeInformation) {
                            String propertyName = attributeInformation.name
                            if(status != null) {
                                if(status == Status.MANAGED) {
                                    // perform dirty check
                                    DirtyCheckable dirtyCheckable = cast(entity)
                                    if(GormProperties.LAST_UPDATED == propertyName) {
                                        return dirtyCheckable.hasChanged()
                                    }
                                    else {
                                        if(dirtyCheckable.hasChanged(propertyName)) {
                                            return true
                                        }
                                        else {
                                            PersistentEntity gormEntity = GormEnhancer.findEntity(Hibernate.getClass(entity))
                                            PersistentProperty prop = gormEntity.getPropertyByName(attributeInformation.name)
                                            if(prop instanceof Embedded) {
                                                def val = prop.reader.read(entity)
                                                if( val instanceof DirtyCheckable ) {
                                                    return ((DirtyCheckable)val).hasChanged()
                                                }
                                                else {
                                                    return false
                                                }
                                            }
                                            else {
                                                return false
                                            }
                                        }
                                    }
                                }
                                else {
                                    // either deleted or in a state that cannot be regarded as dirty
                                    return false
                                }
                            }
                            else {
                                // a new object not within the session
                                return true
                            }
                        }
                    }
            )
        }
    }

    @CompileDynamic
    Status getStatus(Session session, Object entity) {
        SessionImplementor si = (SessionImplementor) session
        return si.getPersistenceContext().getEntry(entity)?.getStatus()
    }

    private DirtyCheckable cast(Object entity) {
        return DirtyCheckable.class.cast(entity)
    }
}
