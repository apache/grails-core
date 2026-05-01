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

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.Nullable;

import org.hibernate.Hibernate;
import org.hibernate.HibernateException;
import org.hibernate.event.internal.DefaultMergeEventListener;
import org.hibernate.event.internal.DefaultPersistEventListener;
import org.hibernate.event.spi.MergeContext;
import org.hibernate.event.spi.MergeEvent;
import org.hibernate.event.spi.MergeEventListener;
import org.hibernate.event.spi.PersistContext;
import org.hibernate.event.spi.PersistEvent;
import org.hibernate.event.spi.PersistEventListener;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostLoadEvent;
import org.hibernate.event.spi.PostLoadEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.event.spi.PreDeleteEvent;
import org.hibernate.event.spi.PreDeleteEventListener;
import org.hibernate.event.spi.PreInsertEvent;
import org.hibernate.event.spi.PreInsertEventListener;
import org.hibernate.event.spi.PreLoadEvent;
import org.hibernate.event.spi.PreLoadEventListener;
import org.hibernate.event.spi.PreUpdateEvent;
import org.hibernate.event.spi.PreUpdateEventListener;
import org.hibernate.jpa.event.spi.CallbackRegistry;
import org.hibernate.jpa.event.spi.CallbackRegistryConsumer;
import org.hibernate.metamodel.mapping.AttributeMapping;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.persister.entity.EntityPersister;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;

import org.grails.datastore.gorm.events.AutoTimestampEventListener;
import org.grails.datastore.gorm.events.ConfigurableApplicationContextEventPublisher;
import org.grails.datastore.gorm.events.ConfigurableApplicationEventPublisher;
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable;
import org.grails.datastore.mapping.engine.ModificationTrackingEntityAccess;
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.types.Embedded;
import org.grails.datastore.mapping.proxy.ProxyHandler;
import org.grails.orm.hibernate.HibernateDatastore;

/**
 * Listens for Hibernate events and publishes corresponding Datastore events.
 *
 * @author Graeme Rocher
 * @author Lari Hotari
 * @author Burt Beckwith
 * @since 1.0
 */
@SuppressWarnings({"PMD.DataflowAnomalyAnalysis", "PMD.NonSerializableClass"})
public class ClosureEventTriggeringInterceptor
        implements Serializable,
                ApplicationContextAware,
                PreLoadEventListener,
                PostLoadEventListener,
                PostInsertEventListener,
                PostUpdateEventListener,
                PostDeleteEventListener,
                PreDeleteEventListener,
                PreUpdateEventListener,
                PreInsertEventListener,
                MergeEventListener,
                PersistEventListener,
                CallbackRegistryConsumer {

    /**
     * @deprecated Use {@link AbstractPersistenceEvent#ONLOAD_EVENT} instead
     */
    @Deprecated
    public static final String ONLOAD_EVENT = AbstractPersistenceEvent.ONLOAD_EVENT;
    /**
     * @deprecated Use {@link AbstractPersistenceEvent#ONLOAD_SAVE} instead
     */
    @Deprecated
    public static final String ONLOAD_SAVE = AbstractPersistenceEvent.ONLOAD_SAVE;
    /**
     * @deprecated Use {@link AbstractPersistenceEvent#BEFORE_LOAD_EVENT} instead
     */
    @Deprecated
    public static final String BEFORE_LOAD_EVENT = AbstractPersistenceEvent.BEFORE_LOAD_EVENT;
    /**
     * @deprecated Use {@link AbstractPersistenceEvent#BEFORE_INSERT_EVENT} instead
     */
    @Deprecated
    public static final String BEFORE_INSERT_EVENT = AbstractPersistenceEvent.BEFORE_INSERT_EVENT;
    /**
     * @deprecated Use {@link AbstractPersistenceEvent#AFTER_INSERT_EVENT} instead
     */
    @Deprecated
    public static final String AFTER_INSERT_EVENT = AbstractPersistenceEvent.AFTER_INSERT_EVENT;
    /**
     * @deprecated Use {@link AbstractPersistenceEvent#BEFORE_UPDATE_EVENT} instead
     */
    @Deprecated
    public static final String BEFORE_UPDATE_EVENT = AbstractPersistenceEvent.BEFORE_UPDATE_EVENT;
    /**
     * @deprecated Use {@link AbstractPersistenceEvent#AFTER_UPDATE_EVENT} instead
     */
    @Deprecated
    public static final String AFTER_UPDATE_EVENT = AbstractPersistenceEvent.AFTER_UPDATE_EVENT;
    /**
     * @deprecated Use {@link AbstractPersistenceEvent#BEFORE_DELETE_EVENT} instead
     */
    @Deprecated
    public static final String BEFORE_DELETE_EVENT = AbstractPersistenceEvent.BEFORE_DELETE_EVENT;
    /**
     * @deprecated Use {@link AbstractPersistenceEvent#AFTER_DELETE_EVENT} instead
     */
    @Deprecated
    public static final String AFTER_DELETE_EVENT = AbstractPersistenceEvent.AFTER_DELETE_EVENT;
    /**
     * @deprecated Use {@link AbstractPersistenceEvent#AFTER_LOAD_EVENT} instead
     */
    @Deprecated
    public static final String AFTER_LOAD_EVENT = AbstractPersistenceEvent.AFTER_LOAD_EVENT;
    //    private final Logger log = LoggerFactory.getLogger(getClass());
    @Serial
    private static final long serialVersionUID = 1;

    private final DefaultPersistEventListener persistEventListener = new DefaultPersistEventListener();
    private final DefaultMergeEventListener mergeEventListener = new DefaultMergeEventListener();
    /** The datastore. */
    protected HibernateDatastore datastore;

    /** The event publisher. */
    protected ConfigurableApplicationEventPublisher eventPublisher;

    private MappingContext mappingContext;
    private ProxyHandler proxyHandler;

    /** Sets the datastore. */
    public void setDatastore(HibernateDatastore datastore) {
        this.datastore = datastore;
        this.mappingContext = datastore.getMappingContext();
        this.proxyHandler = mappingContext.getProxyHandler();
    }

    /** Sets the event publisher. */
    public void setEventPublisher(ConfigurableApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void onMerge(MergeEvent hibernateEvent) throws HibernateException {
        publishMergeEvent(hibernateEvent);
        mergeEventListener.onMerge(hibernateEvent);
    }

    private Object getMergeEntity(MergeEvent hibernateEvent) {
        return Optional.ofNullable(hibernateEvent.getOriginal()).orElse(hibernateEvent.getEntity());
    }

    @Override
    public void onMerge(MergeEvent hibernateEvent, MergeContext copiedAlready) throws HibernateException {
        publishMergeEvent(hibernateEvent);
        mergeEventListener.onMerge(hibernateEvent, copiedAlready);
    }

    private void publishMergeEvent(MergeEvent hibernateEvent) {
        Object entity = getMergeEntity(hibernateEvent);
        if (entity != null && proxyHandler.isInitialized(entity)) {
            activateDirtyChecking(entity);
            org.grails.datastore.mapping.engine.event.MergeEvent grailsEvent =
                    new org.grails.datastore.mapping.engine.event.MergeEvent(this.datastore, entity);
            publishEvent(hibernateEvent, grailsEvent);
        }
    }

    @Override
    public void onPersist(PersistEvent event) throws HibernateException {
        publishPersistEvent(event);
        persistEventListener.onPersist(event);
    }

    @Override
    public void onPersist(PersistEvent event, PersistContext createdAlready) throws HibernateException {
        publishPersistEvent(event);
        persistEventListener.onPersist(event, createdAlready);
    }

    private Object getPersistEntity(PersistEvent hibernateEvent) {
        return hibernateEvent.getObject();
    }

    private void publishPersistEvent(PersistEvent hibernateEvent) {
        Object entity = getPersistEntity(hibernateEvent);
        if (entity != null && proxyHandler.isInitialized(entity)) {
            activateDirtyChecking(entity);
            org.grails.datastore.mapping.engine.event.PersistEvent grailsEvent =
                    new org.grails.datastore.mapping.engine.event.PersistEvent(this.datastore, entity);
            publishEvent(hibernateEvent, grailsEvent);
        }
    }

    @Override
    public void injectCallbackRegistry(CallbackRegistry callbackRegistry) {
        persistEventListener.injectCallbackRegistry(callbackRegistry);
    }

    @Override
    public void onPreLoad(PreLoadEvent hibernateEvent) {
        org.grails.datastore.mapping.engine.event.PreLoadEvent grailsEvent =
                new org.grails.datastore.mapping.engine.event.PreLoadEvent(this.datastore, hibernateEvent.getEntity());
        publishEvent(hibernateEvent, grailsEvent);
    }

    @Override
    public void onPostLoad(PostLoadEvent hibernateEvent) {
        Object entity = hibernateEvent.getEntity();
        activateDirtyChecking(entity);
        publishEvent(
                hibernateEvent, new org.grails.datastore.mapping.engine.event.PostLoadEvent(this.datastore, entity));
    }

    /**
     * Resolves the {@link PersistentEntity} for the given type from the mapping context.
     * Extracted as a protected hook to allow test subclasses to control the returned value.
     */
    protected PersistentEntity resolvePersistentEntity(Class<?> type) {
        return mappingContext.getPersistentEntity(type.getName());
    }

    @Override
    public boolean onPreInsert(PreInsertEvent hibernateEvent) {
        Object entity = hibernateEvent.getEntity();
        Class<?> type = Hibernate.getClass(entity);
        PersistentEntity persistentEntity = resolvePersistentEntity(type);
        AbstractPersistenceEvent grailsEvent;
        ModificationTrackingEntityAccess entityAccess = null;
        if (persistentEntity != null) {
            entityAccess =
                    new ModificationTrackingEntityAccess(mappingContext.createEntityAccess(persistentEntity, entity));
            grailsEvent = new org.grails.datastore.mapping.engine.event.PreInsertEvent(
                    this.datastore, persistentEntity, entityAccess);
        } else {
            grailsEvent = new org.grails.datastore.mapping.engine.event.PreInsertEvent(this.datastore, entity);
        }

        publishEvent(hibernateEvent, grailsEvent);

        boolean cancelled = grailsEvent.isCancelled();
        if (!cancelled && entityAccess != null) {
            synchronizeHibernateState(hibernateEvent, entityAccess);
        }
        return cancelled;
    }

    private void synchronizeHibernateState(
            PreInsertEvent hibernateEvent, ModificationTrackingEntityAccess entityAccess) {
        Map<String, Object> modifiedProperties = entityAccess.getModifiedProperties();
        if (!modifiedProperties.isEmpty()) {
            Object[] state = hibernateEvent.getState();
            EntityPersister persister = hibernateEvent.getPersister();
            synchronizeHibernateState(persister, state, modifiedProperties);
        }
    }

    private void synchronizeHibernateState(
            PreUpdateEvent hibernateEvent, ModificationTrackingEntityAccess entityAccess, boolean autoTimestamp) {
        Map<String, Object> modifiedProperties = entityAccess.getModifiedProperties();

        if (autoTimestamp) {
            updateModifiedPropertiesWithAutoTimestamp(modifiedProperties, hibernateEvent);
        }

        if (!modifiedProperties.isEmpty()) {
            Object[] state = hibernateEvent.getState();
            EntityPersister persister = hibernateEvent.getPersister();
            synchronizeHibernateState(persister, state, modifiedProperties);
        }
    }

    private void updateModifiedPropertiesWithAutoTimestamp(
            Map<String, Object> modifiedProperties, PreUpdateEvent hibernateEvent) {

        EntityPersister persister = hibernateEvent.getPersister();
        EntityMappingType entityMappingType = persister.getEntityMappingType();
        AttributeMapping dateCreatedMapping =
                entityMappingType.findAttributeMapping(AutoTimestampEventListener.DATE_CREATED_PROPERTY);

        Object[] oldState = hibernateEvent.getOldState();
        Object[] state = hibernateEvent.getState();

        // Only for "dateCreated" property, "lastUpdated" is handled correctly
        if (dateCreatedMapping != null) {
            int dateCreatedIdx = dateCreatedMapping.getStateArrayPosition();
            if (oldState != null &&
                    oldState[dateCreatedIdx] != null &&
                    !oldState[dateCreatedIdx].equals(state[dateCreatedIdx])) {
                modifiedProperties.put(AutoTimestampEventListener.DATE_CREATED_PROPERTY, oldState[dateCreatedIdx]);
            }
        }
    }

    protected void synchronizeHibernateState(
            EntityPersister persister, Object[] state, Map<String, Object> modifiedProperties) {
        EntityMappingType entityMappingType = persister.getEntityMappingType();
        for (Map.Entry<String, Object> entry : modifiedProperties.entrySet()) {
            AttributeMapping attributeMapping = entityMappingType.findAttributeMapping(entry.getKey());
            if (attributeMapping != null) {
                state[attributeMapping.getStateArrayPosition()] = entry.getValue();
            }
        }
    }

    @Override
    public void onPostInsert(PostInsertEvent hibernateEvent) {
        Object entity = hibernateEvent.getEntity();
        org.grails.datastore.mapping.engine.event.PostInsertEvent grailsEvent =
                new org.grails.datastore.mapping.engine.event.PostInsertEvent(this.datastore, entity);
        activateDirtyChecking(entity);
        publishEvent(hibernateEvent, grailsEvent);
    }

    @Override
    public boolean onPreUpdate(PreUpdateEvent hibernateEvent) {
        Object entity = hibernateEvent.getEntity();
        Class<?> type = Hibernate.getClass(entity);
        MappingContext mappingContext = datastore.getMappingContext();
        PersistentEntity persistentEntity = resolvePersistentEntity(type);
        AbstractPersistenceEvent grailsEvent;
        ModificationTrackingEntityAccess entityAccess = null;
        if (persistentEntity != null) {
            entityAccess =
                    new ModificationTrackingEntityAccess(mappingContext.createEntityAccess(persistentEntity, entity));
            grailsEvent = new org.grails.datastore.mapping.engine.event.PreUpdateEvent(
                    this.datastore, persistentEntity, entityAccess);
        } else {
            grailsEvent = new org.grails.datastore.mapping.engine.event.PreUpdateEvent(this.datastore, entity);
        }

        publishEvent(hibernateEvent, grailsEvent);
        boolean cancelled = grailsEvent.isCancelled();
        if (!cancelled && entityAccess != null) {
            boolean autoTimestamp =
                    persistentEntity.getMapping().getMappedForm().isAutoTimestamp();
            synchronizeHibernateState(hibernateEvent, entityAccess, autoTimestamp);
        }
        return cancelled;
    }

    @Override
    public void onPostUpdate(PostUpdateEvent hibernateEvent) {
        Object entity = hibernateEvent.getEntity();
        activateDirtyChecking(entity);
        publishEvent(
                hibernateEvent, new org.grails.datastore.mapping.engine.event.PostUpdateEvent(this.datastore, entity));
    }

    @Override
    public boolean onPreDelete(PreDeleteEvent hibernateEvent) {
        AbstractPersistenceEvent event = new org.grails.datastore.mapping.engine.event.PreDeleteEvent(
                this.datastore, hibernateEvent.getEntity());
        publishEvent(hibernateEvent, event);
        return event.isCancelled();
    }

    @Override
    public void onPostDelete(PostDeleteEvent hibernateEvent) {
        org.grails.datastore.mapping.engine.event.PostDeleteEvent grailsEvent =
                new org.grails.datastore.mapping.engine.event.PostDeleteEvent(
                        this.datastore, hibernateEvent.getEntity());
        publishEvent(hibernateEvent, grailsEvent);
    }

    private void publishEvent(Object hibernateEvent, AbstractPersistenceEvent mappingEvent) {
        if (hibernateEvent instanceof Serializable) {
            mappingEvent.setNativeEvent((Serializable) hibernateEvent);
        }
        if (eventPublisher != null) {
            eventPublisher.publishEvent(mappingEvent);
        }
    }

    @Override
    public void setApplicationContext(@Nullable ApplicationContext applicationContext) throws BeansException {
        if (applicationContext instanceof ConfigurableApplicationContext) {

            this.eventPublisher = new ConfigurableApplicationContextEventPublisher(
                    (ConfigurableApplicationContext) applicationContext);
        }
    }

    protected void activateDirtyChecking(Object entity) {
        if (entity instanceof DirtyCheckable && proxyHandler.isInitialized(entity)) {
            PersistentEntity persistentEntity = mappingContext.getPersistentEntity(
                    Hibernate.getClass(entity).getName());
            Object unwrapped = proxyHandler.unwrap(entity);
            DirtyCheckable dirtyCheckable = (DirtyCheckable) unwrapped;
            dirtyCheckable.trackChanges();
            for (Embedded<?> association : persistentEntity.getEmbedded()) {
                if (DirtyCheckable.class.isAssignableFrom(association.getType())) {
                    Object embedded = association.getReader().read(unwrapped);
                    if (embedded != null) {
                        ((DirtyCheckable) embedded).trackChanges();
                    }
                }
            }
        }
    }
}
