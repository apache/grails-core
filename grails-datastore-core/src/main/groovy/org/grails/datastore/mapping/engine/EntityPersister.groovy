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
package org.grails.datastore.mapping.engine

import java.sql.Timestamp

import groovy.transform.CompileStatic
import org.springframework.context.ApplicationEventPublisher

import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.SessionImplementor
import org.grails.datastore.mapping.engine.event.PostDeleteEvent
import org.grails.datastore.mapping.engine.event.PostInsertEvent
import org.grails.datastore.mapping.engine.event.PostLoadEvent
import org.grails.datastore.mapping.engine.event.PostUpdateEvent
import org.grails.datastore.mapping.engine.event.PreDeleteEvent
import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.grails.datastore.mapping.engine.event.PreLoadEvent
import org.grails.datastore.mapping.engine.event.PreUpdateEvent
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.PropertyMapping
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.proxy.ProxyFactory
import org.grails.datastore.mapping.reflect.EntityReflector

/**
 * A {@link org.grails.datastore.mapping.engine.Persister} specifically for persisting PersistentEntity instances.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
abstract class EntityPersister implements Persister {

    private final PersistentEntity persistentEntity
    private final MappingContext mappingContext
    protected final Session session
    protected final ApplicationEventPublisher publisher
    protected final EntityReflector reflector
    protected ProxyFactory proxyFactory

    EntityPersister(MappingContext mappingContext, PersistentEntity entity,
              Session session, ApplicationEventPublisher publisher) {
        this.persistentEntity = entity
        this.mappingContext = mappingContext
        this.session = session
        this.publisher = publisher
        this.reflector = mappingContext.getEntityReflector(entity)
    }

    Session getSession() {
        return session
    }

    @SuppressWarnings('unchecked')
    Object proxy(Serializable key) {
        PersistentEntity entity = getPersistentEntity()
        PersistentProperty identity = entity.getIdentity()
        Serializable proxyKey = key
        if (identity != null) {
            proxyKey = (Serializable) mappingContext.getConversionService().convert(key, identity.getType())
        }
        return getProxyFactory().createProxy(session, entity.getJavaClass(), proxyKey)
    }

    ProxyFactory getProxyFactory() {
        if (proxyFactory == null) {
            proxyFactory = mappingContext.getProxyFactory()
        }
        return proxyFactory
    }

    /**
     * @return The MappingContext instance
     */
    MappingContext getMappingContext() {
        return mappingContext
    }

    /**
     * @return The PersistentEntity instance
     */
    PersistentEntity getPersistentEntity() {
        return persistentEntity
    }

    @SuppressWarnings('rawtypes')
    Class getType() {
        return persistentEntity.getJavaClass()
    }

    /**
     * Obtains an objects identifer
     * @param obj The object
     * @return The identifier or null if it doesn't have one
     */
    Serializable getObjectIdentifier(Object obj) {
        if (obj == null) {
            return null
        }
        final ProxyFactory pf = getProxyFactory()
        if (pf.isProxy(obj)) {
            return pf.getIdentifier(obj)
        }
        if (persistentEntity.getJavaClass() == obj.getClass()) {
            return reflector.getIdentifier(obj)
        }
        else {
            EntityPersister persister = (EntityPersister) getSession().getPersister(obj)
            if (persister != null) {
                return persister.getObjectIdentifier(obj)
            }
            else {
                return null
            }
        }
    }

    @Override
    Serializable insert(Object obj) {
        if (!persistentEntity.isInstance(obj)) {
            final Persister persister = getSession().getPersister(obj)
            if (persister == null) {
                throw new IllegalArgumentException('Object [' + obj +
                        '] is not an instance supported by the persister for class [' +
                        getType().getName() + ']')
            }

            return persister.persist(obj)
        }

        return persistEntity(getPersistentEntity(), obj, true)
    }

    /**
     * Subclasses should override to support explicit inserts
     * @param entity The entity
     * @param obj The object
     * @param isInsert Whether it is an insert
     * @return The id
     */
    protected Serializable persistEntity(PersistentEntity entity, Object obj, boolean isInsert) {
        return persistEntity(entity, obj)
    }

    /**
     * Obtains an objects identifer
     * @param obj The object
     */
    void setObjectIdentifier(Object obj, Serializable id) {
        createEntityAccess(getPersistentEntity(), obj).setIdentifier(id)
    }

    /**
     * Persists an object returning the identifier
     *
     * @param obj The object to persist
     * @return The identifer
     */
    final Serializable persist(Object obj) {
        if (!persistentEntity.isInstance(obj)) {
            final Persister persister = getSession().getPersister(obj)
            if (persister == null) {
                throw new IllegalArgumentException('Object [' + obj +
                     '] is not an instance supported by the persister for class [' +
                     getType().getName() + ']')
            }

            return persister.persist(obj)
        }

        return persistEntity(getPersistentEntity(), obj)
    }

    List<Serializable> persist(@SuppressWarnings('rawtypes') Iterable objs) {
        return persistEntities(getPersistentEntity(), objs)
    }

    List<Object> retrieveAll(Iterable<Serializable> keys) {
        return retrieveAllEntities(getPersistentEntity(), keys)
    }

    List<Object> retrieveAll(Serializable[] keys) {
        return retrieveAllEntities(getPersistentEntity(), keys)
    }

    protected abstract List<Object> retrieveAllEntities(PersistentEntity pe, Serializable[] keys)

    Object getCurrentVersion(final EntityAccess ea) {
        Object currentVersion = ea.getProperty(GormProperties.VERSION)
        if (Number.isAssignableFrom(ea.getPropertyType(GormProperties.VERSION))) {
            currentVersion = currentVersion != null ? ((Number) currentVersion).longValue() : currentVersion
        }
        return currentVersion
    }

    protected abstract List<Object> retrieveAllEntities(PersistentEntity pe, Iterable<Serializable> keys)

    protected abstract List<Serializable> persistEntities(PersistentEntity pe, @SuppressWarnings('rawtypes') Iterable objs)

    final Object retrieve(Serializable key) {
        if (key == null) {
            return null
        }

        PersistentEntity entity = getPersistentEntity()

        Object o = retrieveEntity(entity, key)
        if (o == null) {
            return null
        }

        return o
    }

    /**
     * Retrieve a PersistentEntity for the given mappingContext and key
     *
     * @param pe The entity
     * @param key The key
     * @return The object or null if it doesn't exist
     */
    protected abstract Object retrieveEntity(PersistentEntity pe, Serializable key)

    protected boolean isAssignedId(PersistentEntity persistentEntity) {
        boolean assignedId = false
        PersistentProperty identity = persistentEntity.getIdentity()
        if (identity != null) {
            PropertyMapping mapping = identity.getMapping()
            if (mapping != null) {
                Property p = mapping.getMappedForm()
                assignedId = p != null && 'assigned' == p.getGenerator()
            }
        }
        return assignedId
    }

    /**
     * Persist the given persistent entity
     *
     * @param pe The PersistentEntity
     * @param obj
     * @return The generated key
     */
    protected abstract Serializable persistEntity(PersistentEntity pe, Object obj)

    final void delete(@SuppressWarnings('rawtypes') Iterable objects) {
        if (objects == null) {
            return
        }

        deleteEntities(getPersistentEntity(), objects)
    }

    void delete(Object obj) {
        if (obj == null) {
            return
        }

        deleteEntity(getPersistentEntity(), obj)
    }

    protected abstract void deleteEntity(PersistentEntity pe, Object obj)

    protected abstract void deleteEntities(PersistentEntity pe, @SuppressWarnings('rawtypes') Iterable objects)

    protected EntityAccess createEntityAccess(PersistentEntity pe, Object obj) {
        final SessionImplementor si = (SessionImplementor) getSession()
        return si.createEntityAccess(pe, obj)
    }

    protected Object newEntityInstance(PersistentEntity persistentEntity) {
        Object o = persistentEntity.newInstance()
        publisher.publishEvent(new PreLoadEvent(session.getDatastore(), getPersistentEntity(),
                createEntityAccess(persistentEntity, o)))
        return o
    }

    /**
     * Fire the beforeInsert even on an entityAccess object and return true if the operation should be cancelled
     * @param persistentEntity The entity
     * @param entityAccess The entity access
     * @return true if the operation should be cancelled
     */
    boolean cancelInsert(final PersistentEntity persistentEntity, final EntityAccess entityAccess) {
        PreInsertEvent event = new PreInsertEvent(session.getDatastore(), persistentEntity, entityAccess)
        publisher.publishEvent(event)
        return event.isCancelled()
    }

    void firePostInsertEvent(final PersistentEntity persistentEntity, final EntityAccess entityAccess) {
        publisher.publishEvent(new PostInsertEvent(
                session.getDatastore(), persistentEntity, entityAccess))
    }

    /**
     * Fire the beforeUpdate event on an entityAccess object and return true if the operation should be cancelled
     * @param persistentEntity The entity
     * @param entityAccess The entity access
     * @return true if the operation should be cancelled
     */
    boolean cancelUpdate(final PersistentEntity persistentEntity, final EntityAccess entityAccess) {
        PreUpdateEvent event = new PreUpdateEvent(session.getDatastore(), persistentEntity, entityAccess)
        publisher.publishEvent(event)
        return event.isCancelled()
    }

    /**
     * Fire the beforeDelete event on an entityAccess object and return true if the operation should be cancelled
     * @param persistentEntity The entity
     * @param entityAccess The entity access
     * @return true if the operation should be cancelled
     */
    boolean cancelDelete(final PersistentEntity persistentEntity, final EntityAccess entityAccess) {
        PreDeleteEvent event = new PreDeleteEvent(session.getDatastore(), persistentEntity, entityAccess)
        publisher.publishEvent(event)
        return event.isCancelled()
    }

    /**
     * Fire the beforeDelete event on an entityAccess object and return true if the operation should be cancelled
     * @param persistentEntity The entity
     * @param entityAccess The entity access
     * @return true if the operation should be cancelled
     */
    boolean cancelLoad(final PersistentEntity persistentEntity, final EntityAccess entityAccess) {
        PreLoadEvent event = new PreLoadEvent(session.getDatastore(), persistentEntity, entityAccess)
        publisher.publishEvent(event)
        return event.isCancelled()
    }

    void firePostUpdateEvent(final PersistentEntity persistentEntity, final EntityAccess entityAccess) {
        publisher.publishEvent(new PostUpdateEvent(
                session.getDatastore(), persistentEntity, entityAccess))
    }

    void firePostDeleteEvent(final PersistentEntity persistentEntity, final EntityAccess entityAccess) {
        publisher.publishEvent(new PostDeleteEvent(
                session.getDatastore(), persistentEntity, entityAccess))
    }

    void firePreLoadEvent(final PersistentEntity persistentEntity, final EntityAccess entityAccess) {
        publisher.publishEvent(new PreLoadEvent(
                session.getDatastore(), persistentEntity, entityAccess))
    }

    void firePostLoadEvent(final PersistentEntity persistentEntity, final EntityAccess entityAccess) {
        publisher.publishEvent(new PostLoadEvent(
                session.getDatastore(), persistentEntity, entityAccess))
    }

    boolean isVersioned(final EntityAccess ea) {
        if (ea != null && !ea.getPersistentEntity().isVersioned()) {
            return false
        }

        Class<?> type = ea.getPropertyType(GormProperties.VERSION)
        return Number.isAssignableFrom(type) || Date.isAssignableFrom(type)
    }

    void incrementVersion(final EntityAccess ea) {
        incrementEntityVersion(ea)
    }

    static void incrementEntityVersion(EntityAccess ea) {
        final String versionName = ea.getPersistentEntity().getVersion().getName()
        if (Number.isAssignableFrom(ea.getPropertyType(versionName))) {
            Number currentVersion = (Number) ea.getProperty(versionName)
            if (currentVersion == null) {
                currentVersion = 0L
            }
            ea.setProperty(versionName, currentVersion.longValue() + 1)
        }
        else {
            setDateVersionInternal(versionName, ea)
        }
    }

    protected void setVersion(final EntityAccess ea) {
        final String versionName = ea.getPersistentEntity().getVersion().getName()
        if (Number.isAssignableFrom(ea.getPropertyType(versionName))) {
            ea.setProperty(versionName, 0)
        }
        else {
            setDateVersion(ea)
        }
    }

    protected void setDateVersion(final EntityAccess ea) {
        setDateVersionInternal(ea.getPersistentEntity().getVersion().getName(), ea)
    }

    private static void setDateVersionInternal(String versionName, EntityAccess ea) {
        if (Timestamp.isAssignableFrom(ea.getPropertyType(versionName))) {
            ea.setProperty(versionName, new Timestamp(System.currentTimeMillis()))
        }
        else {
            ea.setProperty(versionName, new Date())
        }
    }

}
