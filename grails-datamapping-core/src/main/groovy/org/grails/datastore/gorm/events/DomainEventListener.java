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
package org.grails.datastore.gorm.events;

import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.NonNull;

import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.ReflectionUtils;

import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.connections.ConnectionSourcesProvider;
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable;
import org.grails.datastore.mapping.engine.EntityAccess;
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent;
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEventListener;
import org.grails.datastore.mapping.engine.event.PostDeleteEvent;
import org.grails.datastore.mapping.engine.event.PostInsertEvent;
import org.grails.datastore.mapping.engine.event.PostLoadEvent;
import org.grails.datastore.mapping.engine.event.PostUpdateEvent;
import org.grails.datastore.mapping.engine.event.PreDeleteEvent;
import org.grails.datastore.mapping.engine.event.PreInsertEvent;
import org.grails.datastore.mapping.engine.event.PreLoadEvent;
import org.grails.datastore.mapping.engine.event.PreUpdateEvent;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.config.GormProperties;

/**
 * An event listener that provides support for GORM domain events.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DomainEventListener extends AbstractPersistenceEventListener
       implements MappingContext.Listener {

    private final Map<PersistentEntity, Map<String, Method>> entityEvents = new ConcurrentHashMap<>();

    public static final String EVENT_BEFORE_INSERT = "beforeInsert";
    private static final String EVENT_BEFORE_UPDATE = "beforeUpdate";
    private static final String EVENT_BEFORE_DELETE = "beforeDelete";
    private static final String EVENT_BEFORE_LOAD = "beforeLoad";
    private static final String EVENT_AFTER_INSERT = "afterInsert";
    private static final String EVENT_AFTER_UPDATE = "afterUpdate";
    private static final String EVENT_AFTER_DELETE = "afterDelete";
    private static final String EVENT_AFTER_LOAD = "afterLoad";

    private static final List<String> REFRESH_EVENTS = Arrays.asList(
            EVENT_BEFORE_INSERT, EVENT_BEFORE_UPDATE, EVENT_BEFORE_DELETE);

    private final boolean autowireEntities;

    public DomainEventListener(final Datastore datastore) {
        super(datastore);

        for (PersistentEntity entity : datastore.getMappingContext().getPersistentEntities()) {
            createEventCaches(entity);
        }

        datastore.getMappingContext().addMappingContextListener(this);
        if (datastore instanceof ConnectionSourcesProvider<?, ?>) {
            autowireEntities = ((ConnectionSourcesProvider<?, ?>) datastore).getConnectionSources().getDefaultConnectionSource().getSettings().isAutowire();
        }
        else {
            autowireEntities = false;
        }
    }

    protected DomainEventListener(ConnectionSourcesProvider<?, ?> connectionSourcesProvider, final MappingContext mappingContext) {
        super(null);

        for (PersistentEntity entity : mappingContext.getPersistentEntities()) {
            createEventCaches(entity);
        }
        autowireEntities = connectionSourcesProvider.getConnectionSources().getDefaultConnectionSource().getSettings().isAutowire();
        mappingContext.addMappingContextListener(this);
    }

    @Override
    protected void onPersistenceEvent(final AbstractPersistenceEvent event) {
        switch (event.getEventType()) {
            case PreInsert:
                if (!beforeInsert(event.getEntity(), event.getEntityAccess())) {
                    event.cancel();
                }
                break;
            case PostInsert:
                afterInsert(event.getEntity(), event.getEntityAccess());
                break;
            case PreUpdate:
                if (!beforeUpdate(event.getEntity(), event.getEntityAccess())) {
                    event.cancel();
                }
                break;
            case PostUpdate:
                afterUpdate(event.getEntity(), event.getEntityAccess());
                break;
            case PreDelete:
                if (!beforeDelete(event.getEntity(), event.getEntityAccess())) {
                    event.cancel();
                }
                break;
            case PostDelete:
                afterDelete(event.getEntity(), event.getEntityAccess());
                break;
            case PreLoad:
                beforeLoad(event.getEntity(), event.getEntityAccess());
                break;
            case PostLoad:
                afterLoad(event.getEntity(), event.getEntityAccess());
                break;
            default:
                break;
        }
    }

    public boolean beforeInsert(final PersistentEntity entity, final EntityAccess ea) {
        if (entity.isVersioned()) {
            try {
                setVersion(ea);
            }
            catch (RuntimeException e) {
                // TODO
            }
        }

        return invokeEvent(EVENT_BEFORE_INSERT, entity, ea);
    }

    /**
     * @deprecated the {@code event} parameter is unused; use {@link #beforeInsert(PersistentEntity, EntityAccess)} instead. Scheduled for removal in 9.0.
     */
    @Deprecated
    public boolean beforeInsert(final PersistentEntity entity, final EntityAccess ea, @SuppressWarnings("unused") PreInsertEvent event) {
        return beforeInsert(entity, ea);
    }

    protected void setVersion(final EntityAccess ea) {
        final Class<?> versionType = ea.getPersistentEntity().getVersion().getType();
        if (Number.class.isAssignableFrom(versionType)) {
            ea.setProperty(GormProperties.VERSION, 0);
        }
        else if (Timestamp.class.isAssignableFrom(versionType)) {
            ea.setProperty(GormProperties.VERSION, new Timestamp(System.currentTimeMillis()));
        }
        else if (Date.class.isAssignableFrom(versionType)) {
            ea.setProperty(GormProperties.VERSION, new Date());
        }
    }

    public boolean beforeUpdate(final PersistentEntity entity, final EntityAccess ea) {
        return invokeEvent(EVENT_BEFORE_UPDATE, entity, ea);
    }

    /**
     * @deprecated the {@code event} parameter is unused; use {@link #beforeUpdate(PersistentEntity, EntityAccess)} instead. Scheduled for removal in 9.0.
     */
    @Deprecated
    public boolean beforeUpdate(final PersistentEntity entity, final EntityAccess ea, @SuppressWarnings("unused") PreUpdateEvent event) {
        return beforeUpdate(entity, ea);
    }

    public boolean beforeDelete(final PersistentEntity entity, final EntityAccess ea) {
        return invokeEvent(EVENT_BEFORE_DELETE, entity, ea);
    }

    /**
     * @deprecated the {@code event} parameter is unused; use {@link #beforeDelete(PersistentEntity, EntityAccess)} instead. Scheduled for removal in 9.0.
     */
    @Deprecated
    public boolean beforeDelete(final PersistentEntity entity, final EntityAccess ea, @SuppressWarnings("unused") PreDeleteEvent event) {
        return beforeDelete(entity, ea);
    }

    public void beforeLoad(final PersistentEntity entity, final EntityAccess ea) {
        invokeEvent(EVENT_BEFORE_LOAD, entity, ea);
    }

    /**
     * @deprecated the {@code event} parameter is unused; use {@link #beforeLoad(PersistentEntity, EntityAccess)} instead. Scheduled for removal in 9.0.
     */
    @Deprecated
    public void beforeLoad(final PersistentEntity entity, final EntityAccess ea, @SuppressWarnings("unused") PreLoadEvent event) {
        beforeLoad(entity, ea);
    }

    public void afterDelete(final PersistentEntity entity, final EntityAccess ea) {
        invokeEvent(EVENT_AFTER_DELETE, entity, ea);
    }

    /**
     * @deprecated the {@code event} parameter is unused; use {@link #afterDelete(PersistentEntity, EntityAccess)} instead. Scheduled for removal in 9.0.
     */
    @Deprecated
    public void afterDelete(final PersistentEntity entity, final EntityAccess ea, @SuppressWarnings("unused") PostDeleteEvent event) {
        afterDelete(entity, ea);
    }

    public void afterInsert(final PersistentEntity entity, final EntityAccess ea) {
        activateDirtyChecking(ea);
        invokeEvent(EVENT_AFTER_INSERT, entity, ea);
    }

    /**
     * @deprecated the {@code event} parameter is unused; use {@link #afterInsert(PersistentEntity, EntityAccess)} instead. Scheduled for removal in 9.0.
     */
    @Deprecated
    public void afterInsert(final PersistentEntity entity, final EntityAccess ea, @SuppressWarnings("unused") PostInsertEvent event) {
        afterInsert(entity, ea);
    }

    private void activateDirtyChecking(EntityAccess ea) {
        Object e = ea.getEntity();
        if (e instanceof DirtyCheckable) {
            ((DirtyCheckable) e).trackChanges();
        }
    }

    public void afterUpdate(final PersistentEntity entity, final EntityAccess ea) {
        activateDirtyChecking(ea); // reset dirty checking
        invokeEvent(EVENT_AFTER_UPDATE, entity, ea);
    }

    /**
     * @deprecated the {@code event} parameter is unused; use {@link #afterUpdate(PersistentEntity, EntityAccess)} instead. Scheduled for removal in 9.0.
     */
    @Deprecated
    public void afterUpdate(final PersistentEntity entity, final EntityAccess ea, @SuppressWarnings("unused") PostUpdateEvent event) {
        afterUpdate(entity, ea);
    }

    public void afterLoad(final PersistentEntity entity, final EntityAccess ea) {
        activateDirtyChecking(ea);
        if (autowireEntities || (entity != null && entity.getMapping().getMappedForm().isAutowire())) {
            autowireBeanProperties(ea.getEntity());
        }
        invokeEvent(EVENT_AFTER_LOAD, entity, ea);
    }

    /**
     * @deprecated the {@code event} parameter is unused; use {@link #afterLoad(PersistentEntity, EntityAccess)} instead. Scheduled for removal in 9.0.
     */
    @Deprecated
    public void afterLoad(final PersistentEntity entity, final EntityAccess ea, @SuppressWarnings("unused") PostLoadEvent event) {
        afterLoad(entity, ea);
    }

    protected void autowireBeanProperties(final Object entity) {
        ConfigurableApplicationContext applicationContext = datastore.getApplicationContext();
        if (applicationContext != null) {
            applicationContext.getAutowireCapableBeanFactory().autowireBeanProperties(
                    entity, AutowireCapableBeanFactory.AUTOWIRE_BY_NAME, false);
        }
    }

    /**
     * {@inheritDoc}
     * @see org.grails.datastore.mapping.model.MappingContext.Listener#persistentEntityAdded(
     *     org.grails.datastore.mapping.model.PersistentEntity)
     */
    public void persistentEntityAdded(PersistentEntity entity) {
        createEventCaches(entity);
    }

    /**
     * {@inheritDoc}
     * @see org.springframework.context.event.SmartApplicationListener#supportsEventType(
     *     java.lang.Class)
     */
    public boolean supportsEventType(@NonNull Class<? extends ApplicationEvent> eventType) {
        return AbstractPersistenceEvent.class.isAssignableFrom(eventType);
    }

    private boolean invokeEvent(String eventName, PersistentEntity entity, EntityAccess ea) {
        final Map<String, Method> events = entityEvents.get(entity);
        if (events == null) {
            return true;
        }

        final Method eventMethod = events.get(eventName);
        if (eventMethod == null) {
            return true;
        }

        final Object result;
        if (ea != null) {
            result = ReflectionUtils.invokeMethod(eventMethod, ea.getEntity());
        }
        else {
            result = null;
        }

        boolean booleanResult = (result instanceof Boolean) ? (Boolean) result : true;
        if (ea != null && booleanResult && REFRESH_EVENTS.contains(eventName)) {
            ea.refresh();
        }
        return booleanResult;
    }

    private void createEventCaches(PersistentEntity entity) {
        Class<?> javaClass = entity.getJavaClass();
        final ConcurrentHashMap<String, Method> events = new ConcurrentHashMap<>();
        entityEvents.put(entity, events);

        findAndCacheEvent(EVENT_BEFORE_INSERT, javaClass, events);
        findAndCacheEvent(EVENT_BEFORE_UPDATE, javaClass, events);
        findAndCacheEvent(EVENT_BEFORE_DELETE, javaClass, events);
        findAndCacheEvent(EVENT_BEFORE_LOAD, javaClass, events);
        findAndCacheEvent(EVENT_AFTER_INSERT, javaClass, events);
        findAndCacheEvent(EVENT_AFTER_UPDATE, javaClass, events);
        findAndCacheEvent(EVENT_AFTER_DELETE, javaClass, events);
        findAndCacheEvent(EVENT_AFTER_LOAD, javaClass, events);
    }

    private void findAndCacheEvent(String event, Class<?> javaClass, Map<String, Method> events) {
        final Method method = ReflectionUtils.findMethod(javaClass, event);
        if (method != null) {
            events.put(event, method);
        }
    }
}
