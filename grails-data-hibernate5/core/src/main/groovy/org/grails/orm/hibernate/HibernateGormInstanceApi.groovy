/*
 * Copyright 2013 the original author or authors.
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
package org.grails.orm.hibernate

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil
import org.hibernate.engine.spi.EntityEntry
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.persister.entity.EntityPersister
import org.hibernate.tuple.NonIdentifierAttribute

/**
 * The implementation of the GORM instance API contract for Hibernate.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class HibernateGormInstanceApi<D> extends AbstractHibernateGormInstanceApi<D> {

    protected InstanceApiHelper instanceApiHelper

    HibernateGormInstanceApi(Class<D> persistentClass, HibernateDatastore datastore, ClassLoader classLoader) {
        super(persistentClass, datastore, classLoader, null)
        hibernateTemplate = new GrailsHibernateTemplate(sessionFactory, datastore)
        instanceApiHelper = new InstanceApiHelper((GrailsHibernateTemplate)hibernateTemplate)
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
        SessionImplementor session = (SessionImplementor)sessionFactory.currentSession
        def entry = findEntityEntry(instance, session)
        if (!entry || !entry.loadedState) {
            return false
        }

        EntityPersister persister = entry.persister
        Object[] values = persister.getPropertyValues(instance)
        def dirtyProperties = findDirty(persister, values, entry, instance, session)
        if(dirtyProperties == null) {
            return false
        }
        else {
            int fieldIndex = persister.getEntityMetamodel().getProperties().findIndexOf { NonIdentifierAttribute attribute -> fieldName == attribute.name }
            return fieldIndex in dirtyProperties
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
        SessionImplementor session = (SessionImplementor)sessionFactory.currentSession
        def entry = findEntityEntry(instance, session)
        if (!entry || !entry.loadedState) {
            return false
        }
        EntityPersister persister = entry.persister
        Object[] currentState = persister.getPropertyValues(instance)
        def dirtyPropertyIndexes = findDirty(persister, currentState, entry, instance, session)
        return dirtyPropertyIndexes != null
    }

    /**
     * Obtains a list of property names that are dirty
     *
     * @param instance The instance
     * @return A list of property names that are dirty
     */

    @CompileDynamic
    List getDirtyPropertyNames(D instance) {
        SessionImplementor session = (SessionImplementor)sessionFactory.currentSession
        def entry = findEntityEntry(instance, session)
        if (!entry || !entry.loadedState) {
            return []
        }

        EntityPersister persister = entry.persister
        Object[] currentState = persister.getPropertyValues(instance)
        int[] dirtyPropertyIndexes = findDirty(persister, currentState, entry, instance, session)
        List<String> names = []
        def entityProperties = persister.getEntityMetamodel().getProperties()
        for (index in dirtyPropertyIndexes) {
            names.add entityProperties[index].name
        }
        return names
    }

    /**
     * Gets the original persisted value of a field.
     *
     * @param fieldName The field name
     * @return The original persisted value
     */
    Object getPersistentValue(D instance, String fieldName) {
        SessionImplementor session = (SessionImplementor)sessionFactory.currentSession
        def entry = findEntityEntry(instance, session, false)
        if (!entry || !entry.loadedState) {
            return null
        }

        EntityPersister persister = entry.persister
        int fieldIndex = persister.getEntityMetamodel().getProperties().findIndexOf {
            NonIdentifierAttribute attribute -> fieldName == attribute.name
        }
        return fieldIndex == -1 ? null : entry.loadedState[fieldIndex]
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
        GrailsHibernateUtil.setObjectToReadWrite(target, sessionFactory)
    }

    @Override
    void setObjectToReadOnly(Object target) {
        GrailsHibernateUtil.setObjectToReadyOnly(target, sessionFactory)
    }
}
