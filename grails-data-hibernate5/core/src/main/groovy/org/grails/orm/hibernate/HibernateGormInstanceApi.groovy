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
package org.grails.orm.hibernate

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import org.grails.orm.hibernate.support.HibernateRuntimeUtils
import org.hibernate.Session
import org.hibernate.engine.spi.EntityEntry
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.persister.entity.EntityPersister
import org.hibernate.tuple.NonIdentifierAttribute

import org.grails.orm.hibernate.cfg.GrailsHibernateUtil
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.gorm.DatastoreResolver
import org.grails.datastore.gorm.GormInstanceApi
import org.grails.datastore.mapping.core.Datastore

/**
 * The implementation of the GORM instance API contract for Hibernate.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class HibernateGormInstanceApi<D> extends AbstractHibernateGormInstanceApi<D> {

    protected final ClassLoader classLoader

    HibernateGormInstanceApi(Class<D> persistentClass, HibernateDatastore datastore, ClassLoader classLoader) {
        super(persistentClass, datastore, classLoader)
        this.classLoader = classLoader
    }

    HibernateGormInstanceApi(Class<D> persistentClass, MappingContext mappingContext, DatastoreResolver datastoreResolver, ClassLoader classLoader) {
        super(persistentClass, mappingContext, datastoreResolver, classLoader)
        this.classLoader = classLoader
    }

    @Override
    GormInstanceApi<D> forQualifier(String qualifier) {
        Datastore ds = getDatastore()
        if (ds == null) return this

        org.grails.datastore.gorm.DatastoreResolver resolver = new org.grails.datastore.gorm.DatastoreResolver() {
            @Override Datastore resolve() { org.grails.datastore.gorm.GormEnhancer.findDatastore(persistentClass, qualifier) }
        }
        return new HibernateGormInstanceApi<D>(persistentClass, ds.mappingContext, resolver, classLoader)
    }

    protected InstanceApiHelper getInstanceApiHelper() {
        new InstanceApiHelper((GrailsHibernateTemplate) getHibernateTemplate())
    }

    /**
     * Checks whether a field is dirty
     *
     * @param instance The instance
     * @param fieldName The name of the field
     *
     * @return true if the field is dirty
     */

    @CompileDynamic
    boolean isDirty(D instance, String fieldName) {
        getHibernateTemplate().execute { Object session ->
            SessionImplementor sessionImplementor = (SessionImplementor) session
            def entry = findEntityEntry(instance, sessionImplementor)
            if (!entry || !entry.loadedState) {
                return false
            }

            EntityPersister persister = entry.persister
            Object[] values = persister.getPropertyValues(instance)
            def dirtyProperties = findDirty(persister, values, entry, instance, sessionImplementor)
            if (dirtyProperties == null) {
                return false
            }
            else {
                int fieldIndex = persister.getEntityMetamodel().getProperties().findIndexOf { NonIdentifierAttribute attribute -> fieldName == attribute.name }
                return fieldIndex in dirtyProperties
            }
        }
    }

    @CompileDynamic // required for Hibernate 5.2 compatibility
    private def findDirty(EntityPersister persister, Object[] values, EntityEntry entry, D instance, SessionImplementor session) {
        persister.findDirty(values, entry.loadedState, instance, session)
    }

    /**
     * Checks whether an entity is dirty
     *
     * @param instance The instance
     * @return true if it is dirty
     */
    @CompileDynamic
    boolean isDirty(D instance) {
        getHibernateTemplate().execute { Object session ->
            SessionImplementor sessionImplementor = (SessionImplementor) session
            def entry = findEntityEntry(instance, sessionImplementor)
            if (!entry || !entry.loadedState) {
                return false
            }
            EntityPersister persister = entry.persister
            Object[] currentState = persister.getPropertyValues(instance)
            def dirtyPropertyIndexes = findDirty(persister, currentState, entry, instance, sessionImplementor)
            return dirtyPropertyIndexes != null
        }
    }

    /**
     * Obtains a list of property names that are dirty
     *
     * @param instance The instance
     * @return A list of property names that are dirty
     */

    @CompileDynamic
    List getDirtyPropertyNames(D instance) {
        getHibernateTemplate().execute { Object session ->
            SessionImplementor sessionImplementor = (SessionImplementor) session
            def entry = findEntityEntry(instance, sessionImplementor)
            if (!entry || !entry.loadedState) {
                return []
            }

            EntityPersister persister = entry.persister
            Object[] currentState = persister.getPropertyValues(instance)
            int[] dirtyPropertyIndexes = findDirty(persister, currentState, entry, instance, sessionImplementor)
            List<String> names = []
            def entityProperties = persister.getEntityMetamodel().getProperties()
            for (index in dirtyPropertyIndexes) {
                names.add(entityProperties[index].name)
            }
            return names
        }
    }

    /**
     * Gets the original persisted value of a field.
     *
     * @param fieldName The field name
     * @return The original persisted value
     */
    Object getPersistentValue(D instance, String fieldName) {
        getHibernateTemplate().execute { Object session ->
            SessionImplementor sessionImplementor = (SessionImplementor) session
            def entry = findEntityEntry(instance, sessionImplementor, false)
            if (!entry || !entry.loadedState) {
                return null
            }

            EntityPersister persister = entry.persister
            int fieldIndex = persister.getEntityMetamodel().getProperties().findIndexOf {
                NonIdentifierAttribute attribute -> fieldName == attribute.name
            }
            return fieldIndex == -1 ? null : entry.loadedState[fieldIndex]
        }
    }

    protected EntityEntry findEntityEntry(D instance, SessionImplementor session, boolean forDirtyCheck = true) {
        def entry = session.persistenceContext.getEntry(instance)
        if (!entry) {
            return null
        }

        if (forDirtyCheck && !entry.requiresDirtyCheck(instance) && entry.loadedState) {
            return null
        }

        return entry
    }

    @Override
    void setObjectToReadWrite(Object target) {
        GrailsHibernateUtil.setObjectToReadWrite(target, getHibernateDatastore().getSessionFactory())
    }

    @Override
    void setObjectToReadOnly(Object target) {
        GrailsHibernateUtil.setObjectToReadyOnly(target, getHibernateDatastore().getSessionFactory())
    }

    @Override
    protected D performUpsert(D target, boolean shouldFlush) {
        getHibernateTemplate().execute { Object session ->
            if (((Session)session).contains(target)) {
                if (shouldFlush) {
                    ((Session)session).flush()
                }
                return target
            } else {
                org.grails.datastore.mapping.model.PersistentEntity identityEntity = getGormPersistentEntity()
                PersistentProperty identityProperty = identityEntity.identity
                if (identityProperty == null) {
                    // composite ID
                    ((Session)session).saveOrUpdate(target)
                } else {
                    Serializable id = (Serializable) org.codehaus.groovy.runtime.InvokerHelper.getProperty(target, identityProperty.name)
                    if (id == null || HibernateRuntimeUtils.isInsertActive()) {
                        ((Session)session).save target
                    } else {
                        ((Session)session).update target
                    }
                }
                if (shouldFlush) {
                    ((Session)session).flush()
                }
                return target
            }
        }
    }
}
