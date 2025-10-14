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
package org.grails.datastore.gorm.mongo.api

import groovy.transform.CompileStatic

import org.bson.Document

import org.grails.datastore.gorm.GormInstanceApi
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.SessionImplementor
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.EntityPersister
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.model.types.Basic
import org.grails.datastore.mapping.model.types.Simple
import org.grails.datastore.mapping.model.types.ToOne
import com.mongodb.DBRef

/**
 * Mongo-specific instance API that aligns isDirty semantics with Hibernate by
 * comparing the current state to the session snapshot instead of relying solely
 * on DirtyCheckable setters.
 */
@CompileStatic
class MongoGormInstanceApi<D> extends GormInstanceApi<D> {

    MongoGormInstanceApi(Class<D> persistentClass, Datastore datastore) {
        super(persistentClass, datastore)
    }

    @Override
    boolean isDirty(D instance) {
        // Delegate to the Session implementation which compares against cached entry
        execute({ Session session ->
            session.isDirty(instance)
        } as org.grails.datastore.mapping.core.SessionCallback<Boolean>)
    }

    @Override
    boolean isDirty(D instance, String fieldName) {
        if (instance == null || fieldName == null) return false

        // Prefer session snapshot comparison like HibernateGormInstanceApi
        execute({ Session session ->
            final EntityPersister persister = (EntityPersister) session.getPersister(instance)
            if (persister == null) return false

            final Serializable id = persister.getObjectIdentifier(instance)
            if (id == null) return false

            final PersistentEntity entity = persister.getPersistentEntity()
            final PersistentProperty property = entity.getPropertyByName(fieldName)
            if (property == null) return false

            // Obtain the cached native entry (snapshot) from the session
            final SessionImplementor<Document> si = (SessionImplementor<Document>) session
            final Document cached = (Document) si.getCachedEntry(entity, id, true)
            if (cached == null) return false

            // Compute the key used in the native entry
            String key = property.getMapping()?.getMappedForm()?.getTargetName()
            if (key == null) key = property.getName()

            // Current value via EntityAccess to avoid proxies
            final EntityAccess access = session.getMappingContext().createEntityAccess(entity, instance)
            final Object currentValue = access.getProperty(fieldName)

            // Old value from cached native entry
            final Object oldValue = cached.get(key)

            return !valuesEqual(oldValue, currentValue, key)
        } as org.grails.datastore.mapping.core.SessionCallback<Boolean>)
    }

    @Override
    List getDirtyPropertyNames(D instance) {
        if (instance == null) return Collections.emptyList()

        execute({ Session session ->
            final EntityPersister persister = (EntityPersister) session.getPersister(instance)
            if (persister == null) return Collections.<String>emptyList()

            final Serializable id = persister.getObjectIdentifier(instance)
            if (id == null) return Collections.<String>emptyList()

            final PersistentEntity entity = persister.getPersistentEntity()
            final SessionImplementor<Document> si = (SessionImplementor<Document>) session
            final Document cached = (Document) si.getCachedEntry(entity, id, true)
            if (cached == null) return Collections.<String>emptyList()

            final EntityAccess access = session.getMappingContext().createEntityAccess(entity, instance)
            List<String> dirty = []

            for (PersistentProperty prop : entity.getPersistentProperties()) {
                // skip id
                if (prop.name == entity.identity?.name) continue

                String key = prop.getMapping()?.getMappedForm()?.getTargetName()
                if (key == null) key = prop.getName()

                Object currentValue
                Object oldValue = cached.get(key)

                if (prop instanceof ToOne) {
                    def assocVal = access.getProperty(prop.name)
                    def oldId = (oldValue instanceof DBRef) ? ((DBRef) oldValue).getId() : oldValue
                    if (assocVal == null) {
                        currentValue = null
                    } else {
                        def ae = ((ToOne) prop).associatedEntity
                        def idVal = ae?.reflector?.getIdentifier(assocVal)
                        currentValue = idVal
                    }
                    if (!valuesEqual(oldId, currentValue, key)) {
                        dirty << prop.name
                    }
                    continue
                }

                if (prop instanceof Simple || prop instanceof Basic) {
                    currentValue = access.getProperty(prop.name)
                    if (!valuesEqual(oldValue, currentValue, key)) {
                        dirty << prop.name
                    }
                    continue
                }
                // For other kinds (embedded/collections), fall back to DirtyCheckable if available
                def v = access.getProperty(prop.name)
                if (v instanceof org.grails.datastore.mapping.dirty.checking.DirtyCheckable) {
                    if (((org.grails.datastore.mapping.dirty.checking.DirtyCheckable) v).hasChanged()) {
                        dirty << prop.name
                    }
                }
            }

            return dirty
        } as org.grails.datastore.mapping.core.SessionCallback<List<String>>)
    }

    private static boolean valuesEqual(Object oldValue, Object currentValue, String propName) {
        if (oldValue === currentValue) return true
        if (oldValue == null || currentValue == null) return false

        if (GormProperties.VERSION.equals(propName)) {
            if (oldValue instanceof Number && currentValue instanceof Number) {
                return ((Number) oldValue).longValue() == ((Number) currentValue).longValue()
            }
            return oldValue.toString() == currentValue.toString()
        }

        if (oldValue instanceof Float && currentValue instanceof Float) {
            return Float.floatToIntBits((Float) oldValue) == Float.floatToIntBits((Float) currentValue)
        }
        if (oldValue instanceof Double && currentValue instanceof Double) {
            return Double.doubleToLongBits((Double) oldValue) == Double.doubleToLongBits((Double) currentValue)
        }

        // Basic equality fallback
        return oldValue == currentValue
    }
}
