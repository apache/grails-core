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

import java.util.Map;

import org.hibernate.Hibernate;
import org.hibernate.HibernateException;
import org.hibernate.event.spi.AbstractEvent;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostLoadEvent;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PreDeleteEvent;
import org.hibernate.event.spi.PreInsertEvent;
import org.hibernate.event.spi.PreLoadEvent;
import org.hibernate.event.spi.PreUpdateEvent;
import org.hibernate.event.spi.SaveOrUpdateEvent;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.tuple.entity.EntityMetamodel;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
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
import org.grails.orm.hibernate.AbstractHibernateDatastore;

/**
 * Listens for Hibernate events and publishes corresponding Datastore events.
 *
 * @author Graeme Rocher
 * @author Lari Hotari
 * @author Burt Beckwith
 * @since 1.0
 */
public class ClosureEventTriggeringInterceptor extends AbstractClosureEventTriggeringInterceptor {

    //    private final Logger log = LoggerFactory.getLogger(getClass());
    private static final long serialVersionUID = 1;

    protected AbstractHibernateDatastore datastore;
    protected ConfigurableApplicationEventPublisher eventPublisher;

    private MappingContext mappingContext;
    private ProxyHandler proxyHandler;

    public void setDatastore(AbstractHibernateDatastore datastore) {
        this.datastore = datastore;
        this.mappingContext = datastore.getMappingContext();
        this.proxyHandler = mappingContext.getProxyHandler();
    }

    public void setEventPublisher(ConfigurableApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void onSaveOrUpdate(SaveOrUpdateEvent hibernateEvent) throws HibernateException {
        Object entity = getEntity(hibernateEvent);
        if (entity != null && proxyHandler.isInitialized(entity)) {
            activateDirtyChecking(entity);
            org.grails.datastore.mapping.engine.event.SaveOrUpdateEvent grailsEvent = new org.grails.datastore.mapping.engine.event.SaveOrUpdateEvent(
                    this.datastore, entity);
            publishEvent(hibernateEvent, grailsEvent);
        }
        super.onSaveOrUpdate(hibernateEvent);
    }

    protected Object getEntity(SaveOrUpdateEvent hibernateEvent) {
        Object object = hibernateEvent.getObject();
        if (object != null) {
            return object;
        }
        else {
            return hibernateEvent.getEntity();
        }
    }

    public void onPreLoad(PreLoadEvent hibernateEvent) {
        org.grails.datastore.mapping.engine.event.PreLoadEvent grailsEvent = new org.grails.datastore.mapping.engine.event.PreLoadEvent(
                this.datastore, hibernateEvent.getEntity());
        publishEvent(hibernateEvent, grailsEvent);
    }

    public void onPostLoad(PostLoadEvent hibernateEvent) {
        Object entity = hibernateEvent.getEntity();
        activateDirtyChecking(entity);
        publishEvent(hibernateEvent, new org.grails.datastore.mapping.engine.event.PostLoadEvent(
                this.datastore, entity));
    }

    public boolean onPreInsert(PreInsertEvent hibernateEvent) {
        Object entity = hibernateEvent.getEntity();
        Class type = Hibernate.getClass(entity);
        PersistentEntity persistentEntity = mappingContext.getPersistentEntity(type.getName());
        AbstractPersistenceEvent grailsEvent;
        ModificationTrackingEntityAccess entityAccess = null;
        if (persistentEntity != null) {
            entityAccess = new ModificationTrackingEntityAccess(mappingContext.createEntityAccess(persistentEntity, entity));
            grailsEvent = new org.grails.datastore.mapping.engine.event.PreInsertEvent(this.datastore, persistentEntity, entityAccess);
        }
        else {
            grailsEvent = new org.grails.datastore.mapping.engine.event.PreInsertEvent(this.datastore, entity);
        }

        publishEvent(hibernateEvent, grailsEvent);

        boolean cancelled = grailsEvent.isCancelled();
        if (!cancelled && entityAccess != null) {
            synchronizeHibernateState(hibernateEvent, entityAccess);
        }
        return cancelled;
    }

    private void synchronizeHibernateState(PreInsertEvent hibernateEvent, ModificationTrackingEntityAccess entityAccess) {
        Object[] state = hibernateEvent.getState();
        EntityPersister persister = hibernateEvent.getPersister();
        Map<String, Object> modifiedProperties = findModifiedProperties(hibernateEvent.getEntity(), persister, state);
        modifiedProperties.putAll(entityAccess.getModifiedProperties());
        
        if (!modifiedProperties.isEmpty()) {
            synchronizeHibernateState(persister, state, modifiedProperties);
        }
    }

    private void synchronizeHibernateState(PreUpdateEvent hibernateEvent, ModificationTrackingEntityAccess entityAccess, boolean autoTimestamp) {
        Object[] state = hibernateEvent.getState();
        EntityPersister persister = hibernateEvent.getPersister();
        Map<String, Object> modifiedProperties = findModifiedProperties(hibernateEvent.getEntity(), persister, state);
        modifiedProperties.putAll(entityAccess.getModifiedProperties());

        if (autoTimestamp) {
            updateModifiedPropertiesWithAutoTimestamp(modifiedProperties, hibernateEvent);
        }

        if (!modifiedProperties.isEmpty()) {
            synchronizeHibernateState(persister, state, modifiedProperties);
            
            // Synchronize with ActionQueue for Hibernate 5 EntityUpdateAction
            try {
                java.lang.reflect.Field actionQueueUpdatesField = org.springframework.util.ReflectionUtils.findField(org.hibernate.engine.spi.ActionQueue.class, "updates");
                if (actionQueueUpdatesField != null) {
                    actionQueueUpdatesField.setAccessible(true);
                    org.hibernate.engine.spi.ExecutableList<org.hibernate.action.internal.EntityUpdateAction> updates = (org.hibernate.engine.spi.ExecutableList<org.hibernate.action.internal.EntityUpdateAction>) actionQueueUpdatesField.get(hibernateEvent.getSession().getActionQueue());
                    if (updates != null) {
                        java.lang.reflect.Field entityUpdateActionStateField = org.springframework.util.ReflectionUtils.findField(org.hibernate.action.internal.EntityUpdateAction.class, "state");
                        if (entityUpdateActionStateField != null) {
                            entityUpdateActionStateField.setAccessible(true);
                            for (org.hibernate.action.internal.EntityUpdateAction updateAction : updates) {
                                if (updateAction.getInstance() == hibernateEvent.getEntity()) {
                                    Object[] updateState = (Object[]) entityUpdateActionStateField.get(updateAction);
                                    if (updateState != null) {
                                        org.hibernate.tuple.entity.EntityMetamodel entityMetamodel = persister.getEntityMetamodel();
                                        for (Map.Entry<String, Object> entry : modifiedProperties.entrySet()) {
                                            Integer index = entityMetamodel.getPropertyIndexOrNull(entry.getKey());
                                            if (index != null) {
                                                updateState[index] = entry.getValue();
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    private Map<String, Object> findModifiedProperties(Object entity, EntityPersister persister, Object[] state) {
        Map<String, Object> modifiedProperties = new java.util.HashMap<>();
        PersistentEntity persistentEntity = mappingContext.getPersistentEntity(Hibernate.getClass(entity).getName());
        if (persistentEntity != null) {
            org.grails.datastore.mapping.reflect.EntityReflector reflector = persistentEntity.getReflector();
            org.hibernate.tuple.entity.EntityMetamodel entityMetamodel = persister.getEntityMetamodel();
            for (String propertyName : persister.getPropertyNames()) {
                if ("version".equals(propertyName)) continue;
                Integer index = entityMetamodel.getPropertyIndexOrNull(propertyName);
                if (index != null) {
                    org.grails.datastore.mapping.model.PersistentProperty property = persistentEntity.getPropertyByName(propertyName);
                    if (property != null) {
                        Object value = reflector.getProperty(entity, propertyName);
                        if (state[index] != value) {
                            modifiedProperties.put(propertyName, value);
                        }
                    }
                }
            }
        }
        return modifiedProperties;
    }

    private void updateModifiedPropertiesWithAutoTimestamp(Map<String, Object> modifiedProperties, PreUpdateEvent hibernateEvent) {

        EntityMetamodel entityMetamodel = hibernateEvent.getPersister().getEntityMetamodel();
        Integer dateCreatedIdx = entityMetamodel.getPropertyIndexOrNull(AutoTimestampEventListener.DATE_CREATED_PROPERTY);

        Object[] oldState = hibernateEvent.getOldState();
        Object[] state = hibernateEvent.getState();

        // Only for "dateCreated" property, "lastUpdated" is handled correctly
        if (dateCreatedIdx != null && oldState != null && oldState[dateCreatedIdx] != null && !oldState[dateCreatedIdx].equals(state[dateCreatedIdx])) {
            modifiedProperties.put(AutoTimestampEventListener.DATE_CREATED_PROPERTY, oldState[dateCreatedIdx]);
        }
    }

    private void synchronizeHibernateState(EntityPersister persister, Object[] state, Map<String, Object> modifiedProperties) {
        EntityMetamodel entityMetamodel = persister.getEntityMetamodel();
        for (Map.Entry<String, Object> entry : modifiedProperties.entrySet()) {
            Integer index = entityMetamodel.getPropertyIndexOrNull(entry.getKey());
            if (index != null) {
                state[index] = entry.getValue();
            }
        }
    }

    public void onPostInsert(PostInsertEvent hibernateEvent) {
        Object entity = hibernateEvent.getEntity();
        org.grails.datastore.mapping.engine.event.PostInsertEvent grailsEvent = new org.grails.datastore.mapping.engine.event.PostInsertEvent(
                this.datastore, entity);
        activateDirtyChecking(entity);
        publishEvent(hibernateEvent, grailsEvent);
    }

    public boolean onPreUpdate(PreUpdateEvent hibernateEvent) {
        Object entity = hibernateEvent.getEntity();
        Class type = Hibernate.getClass(entity);
        MappingContext mappingContext = datastore.getMappingContext();
        PersistentEntity persistentEntity = mappingContext.getPersistentEntity(type.getName());
        AbstractPersistenceEvent grailsEvent;
        ModificationTrackingEntityAccess entityAccess = null;
        if (persistentEntity != null) {
            entityAccess = new ModificationTrackingEntityAccess(mappingContext.createEntityAccess(persistentEntity, entity));
            grailsEvent = new org.grails.datastore.mapping.engine.event.PreUpdateEvent(this.datastore, persistentEntity, entityAccess);
        }
        else {
            grailsEvent = new org.grails.datastore.mapping.engine.event.PreUpdateEvent(this.datastore, entity);
        }

        publishEvent(hibernateEvent, grailsEvent);
        boolean cancelled = grailsEvent.isCancelled();
        if (!cancelled && entityAccess != null) {
            boolean autoTimestamp = persistentEntity.getMapping().getMappedForm().isAutoTimestamp();
            synchronizeHibernateState(hibernateEvent, entityAccess, autoTimestamp);
            
            // Synchronize with ActionQueue for Hibernate 5 EntityUpdateAction
            Map<String, Object> modifiedProperties = entityAccess.getModifiedProperties();
            if (!modifiedProperties.isEmpty()) {
                try {
                    java.lang.reflect.Field actionQueueUpdatesField = org.springframework.util.ReflectionUtils.findField(org.hibernate.engine.spi.ActionQueue.class, "updates");
                    if (actionQueueUpdatesField != null) {
                        actionQueueUpdatesField.setAccessible(true);
                        org.hibernate.engine.spi.ExecutableList<org.hibernate.action.internal.EntityUpdateAction> updates = (org.hibernate.engine.spi.ExecutableList<org.hibernate.action.internal.EntityUpdateAction>) actionQueueUpdatesField.get(hibernateEvent.getSession().getActionQueue());
                        if (updates != null) {
                            java.lang.reflect.Field entityUpdateActionStateField = org.springframework.util.ReflectionUtils.findField(org.hibernate.action.internal.EntityUpdateAction.class, "state");
                            if (entityUpdateActionStateField != null) {
                                entityUpdateActionStateField.setAccessible(true);
                                for (org.hibernate.action.internal.EntityUpdateAction updateAction : updates) {
                                    if (updateAction.getInstance() == entity) {
                                        Object[] updateState = (Object[]) entityUpdateActionStateField.get(updateAction);
                                        if (updateState != null) {
                                            EntityPersister persister = hibernateEvent.getPersister();
                                            org.hibernate.tuple.entity.EntityMetamodel entityMetamodel = persister.getEntityMetamodel();
                                            for (Map.Entry<String, Object> entry : modifiedProperties.entrySet()) {
                                                Integer index = entityMetamodel.getPropertyIndexOrNull(entry.getKey());
                                                if (index != null) {
                                                    updateState[index] = entry.getValue();
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
        return cancelled;

    }

    public void onPostUpdate(PostUpdateEvent hibernateEvent) {
        Object entity = hibernateEvent.getEntity();
        activateDirtyChecking(entity);
        publishEvent(hibernateEvent, new org.grails.datastore.mapping.engine.event.PostUpdateEvent(
                this.datastore, entity));
    }

    public boolean onPreDelete(PreDeleteEvent hibernateEvent) {
        AbstractPersistenceEvent event = new org.grails.datastore.mapping.engine.event.PreDeleteEvent(
                this.datastore, hibernateEvent.getEntity());
        publishEvent(hibernateEvent, event);
        return event.isCancelled();
    }

    public void onPostDelete(PostDeleteEvent hibernateEvent) {
        org.grails.datastore.mapping.engine.event.PostDeleteEvent grailsEvent = new org.grails.datastore.mapping.engine.event.PostDeleteEvent(
                this.datastore, hibernateEvent.getEntity());
        publishEvent(hibernateEvent, grailsEvent);
    }

    private void publishEvent(AbstractEvent hibernateEvent, AbstractPersistenceEvent mappingEvent) {
        mappingEvent.setNativeEvent(hibernateEvent);
        if (eventPublisher != null) {
            eventPublisher.publishEvent(mappingEvent);
        }
    }

    @Override
    public boolean requiresPostCommitHanding(EntityPersister persister) {
        return false;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        if (applicationContext instanceof ConfigurableApplicationContext) {

            this.eventPublisher = new ConfigurableApplicationContextEventPublisher((ConfigurableApplicationContext) applicationContext);
        }
    }

    private void activateDirtyChecking(Object entity) {
        if (entity instanceof DirtyCheckable && proxyHandler.isInitialized(entity)) {
            PersistentEntity persistentEntity = mappingContext.getPersistentEntity(Hibernate.getClass(entity).getName());
            entity = proxyHandler.unwrap(entity);
            DirtyCheckable dirtyCheckable = (DirtyCheckable) entity;
            Map<String, Object> dirtyCheckingState = persistentEntity.getReflector().getDirtyCheckingState(entity);
            if (dirtyCheckingState == null) {
                dirtyCheckable.trackChanges();
                for (Embedded association : persistentEntity.getEmbedded()) {
                    if (DirtyCheckable.class.isAssignableFrom(association.getType())) {
                        Object embedded = association.getReader().read(entity);
                        if (embedded != null) {
                            DirtyCheckable embeddedCheck = (DirtyCheckable) embedded;
                            if (embeddedCheck.listDirtyPropertyNames().isEmpty()) {
                                embeddedCheck.trackChanges();
                            }
                        }
                    }
                }
            }
        }
    }

}
