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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;

import org.hibernate.FlushMode;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.event.spi.AbstractEvent;
import org.hibernate.event.spi.AbstractPreDatabaseOperationEvent;
import org.hibernate.event.spi.EventSource;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.grails.datastore.gorm.GormValidateable;
import org.grails.datastore.gorm.support.BeforeValidateHelper.BeforeValidateEventTriggerCaller;
import org.grails.datastore.gorm.support.EventTriggerCaller;
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent;
import org.grails.datastore.mapping.engine.event.ValidationEvent;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.reflect.ClassUtils;
import org.grails.datastore.mapping.reflect.EntityReflector;
import org.grails.datastore.mapping.validation.ValidationException;
import org.grails.orm.hibernate.HibernateGormValidationApi;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;

@SuppressWarnings({"rawtypes", "unchecked", "PMD.CloseResource"})
public class ClosureEventListener
        implements PreLoadEventListener,
                PostLoadEventListener,
                PreInsertEventListener, // Added to fix "does not exist in superclass" error
                PostInsertEventListener,
                PostUpdateEventListener,
                PostDeleteEventListener,
                PreDeleteEventListener,
                PreUpdateEventListener,
                MergeEventListener,
                PersistEventListener,
                CallbackRegistryConsumer,
                Serializable {

    protected static final Logger LOG = LoggerFactory.getLogger(ClosureEventListener.class);

    @Serial
    private static final long serialVersionUID = 1;

    private final transient EventTriggerCaller beforeInsertCaller;
    private final transient EventTriggerCaller preLoadEventCaller;
    private final transient EventTriggerCaller postLoadEventListener;
    private final transient EventTriggerCaller postInsertEventListener;
    private final transient EventTriggerCaller postUpdateEventListener;
    private final transient EventTriggerCaller postDeleteEventListener;
    private final transient EventTriggerCaller preDeleteEventListener;
    private final transient EventTriggerCaller preUpdateEventListener;
    private final transient BeforeValidateEventTriggerCaller beforeValidateEventListener;
    private final transient GrailsHibernatePersistentEntity persistentEntity;
    private final transient MetaClass domainMetaClass;
    private final boolean failOnErrorEnabled;
    private final Map validateParams;

    private final transient org.grails.orm.hibernate.HibernateDatastore hibernateDatastore;

    public ClosureEventListener(
            org.grails.orm.hibernate.HibernateDatastore hibernateDatastore,
            GrailsHibernatePersistentEntity persistentEntity, boolean failOnError, List failOnErrorPackages) {
        this.hibernateDatastore = hibernateDatastore;
        this.persistentEntity = persistentEntity;
        Class domainClazz = persistentEntity.getJavaClass();
        this.domainMetaClass = GroovySystem.getMetaClassRegistry().getMetaClass(domainClazz);

        beforeInsertCaller = buildCaller(AbstractPersistenceEvent.BEFORE_INSERT_EVENT, domainClazz);
        EventTriggerCaller preLoadCaller = buildCaller(AbstractPersistenceEvent.ONLOAD_EVENT, domainClazz);
        this.preLoadEventCaller = (preLoadCaller != null) ?
                preLoadCaller :
                buildCaller(AbstractPersistenceEvent.BEFORE_LOAD_EVENT, domainClazz);

        postLoadEventListener = buildCaller(AbstractPersistenceEvent.AFTER_LOAD_EVENT, domainClazz);
        postInsertEventListener = buildCaller(AbstractPersistenceEvent.AFTER_INSERT_EVENT, domainClazz);
        postUpdateEventListener = buildCaller(AbstractPersistenceEvent.AFTER_UPDATE_EVENT, domainClazz);
        postDeleteEventListener = buildCaller(AbstractPersistenceEvent.AFTER_DELETE_EVENT, domainClazz);
        preDeleteEventListener = buildCaller(AbstractPersistenceEvent.BEFORE_DELETE_EVENT, domainClazz);
        preUpdateEventListener = buildCaller(AbstractPersistenceEvent.BEFORE_UPDATE_EVENT, domainClazz);

        beforeValidateEventListener = new BeforeValidateEventTriggerCaller(domainClazz, domainMetaClass);
        failOnErrorEnabled = !failOnErrorPackages.isEmpty() ?
                ClassUtils.isClassBelowPackage(domainClazz, failOnErrorPackages) :
                failOnError;

        validateParams = new HashMap();
        validateParams.put(HibernateGormValidationApi.ARGUMENT_DEEP_VALIDATE, Boolean.FALSE);
    }

    @Override
    public void onPreLoad(PreLoadEvent event) {
        if (preLoadEventCaller != null) {
            doPreLoadWithManualSession(event, () -> preLoadEventCaller.call(event.getEntity()));
        }
    }

    @Override
    public void onPostLoad(PostLoadEvent event) {
        if (postLoadEventListener != null) {
            doPostLoadWithManualSession(event, () -> postLoadEventListener.call(event.getEntity()));
        }
    }

    @Override
    public boolean onPreInsert(PreInsertEvent event) {
        return doBooleanWithManualSession(event, () -> {
            Object entity = event.getEntity();
            if (beforeInsertCaller != null) {
                if (beforeInsertCaller.call(entity)) return true;
                synchronizePersisterState(event, event.getState());
            }
            return doValidate(entity);
        });
    }

    // --- Specific manual session versions for PreLoad and PostLoad ---

    private void doPreLoadWithManualSession(PreLoadEvent event, Runnable action) {
        flushOrRun(event.getSession(), action);
    }

    private void flushOrRun(EventSource event, Runnable action) {
        if ((SharedSessionContractImplementor) event instanceof Session session) {
            FlushMode current = session.getHibernateFlushMode();
            try {
                session.setHibernateFlushMode(FlushMode.MANUAL);
                action.run();
            } finally {
                session.setHibernateFlushMode(current);
            }
        } else {
            action.run();
        }
    }

    private void doPostLoadWithManualSession(PostLoadEvent event, Runnable action) {
        flushOrRun(event.getSession(), action);
    }

    // --- Standard Overrides ---

    @Override
    public void onPostInsert(PostInsertEvent event) {
        if (postInsertEventListener != null) {
            doVoidWithManualSession(event, () -> postInsertEventListener.call(event.getEntity()));
        }
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        if (postUpdateEventListener != null) {
            doVoidWithManualSession(event, () -> postUpdateEventListener.call(event.getEntity()));
        }
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        if (postDeleteEventListener != null) {
            doVoidWithManualSession(event, () -> postDeleteEventListener.call(event.getEntity()));
        }
    }

    @Override
    public boolean onPreDelete(PreDeleteEvent event) {
        if (preDeleteEventListener == null) return false;
        return doBooleanWithManualSession(event, () -> preDeleteEventListener.call(event.getEntity()));
    }

    @Override
    public boolean onPreUpdate(PreUpdateEvent event) {
        return doBooleanWithManualSession(event, () -> {
            Object entity = event.getEntity();
            boolean evict = false;
            if (preUpdateEventListener != null) {
                evict = preUpdateEventListener.call(entity);
                if (!evict) {
                    synchronizePersisterState(event, event.getState());
                }
            }
            return evict || doValidate(entity);
        });
    }

    public void onValidate(ValidationEvent event) {
        beforeValidateEventListener.call(event.getEntityObject(), event.getValidatedFields());
    }

    protected boolean doValidate(Object entity) {
        GormValidateable validateable = (GormValidateable) entity;
        if (!validateable.shouldSkipValidation()) {
            String qualifier = org.grails.datastore.mapping.core.connections.ConnectionSource.DEFAULT;
            if (hibernateDatastore != null) {
                qualifier = hibernateDatastore.getConnectionSources().getDefaultConnectionSource().getName();
            }
            org.grails.datastore.gorm.GormValidationApi validationApi = org.grails.datastore.gorm.GormEnhancer.findValidationApi(entity.getClass(), qualifier);
            
            if (validationApi != null && !validationApi.validate(entity, validateParams)) {
                if (failOnErrorEnabled) {
                    throw org.grails.datastore.mapping.validation.ValidationException.newInstance(
                            "Validation error whilst flushing entity [" +
                                    entity.getClass().getName() + "]",
                            validateable.getErrors());
                }
                return true;
            }
        }
        return false;
    }

    private EventTriggerCaller buildCaller(String eventName, Class<?> domainClazz) {
        return EventTriggerCaller.buildCaller(eventName, domainClazz, domainMetaClass, null);
    }

    private void synchronizePersisterState(AbstractPreDatabaseOperationEvent event, Object... state) {
        EntityPersister persister = event.getPersister();
        Object entity = event.getEntity();
        EntityReflector reflector = persistentEntity.getReflector();
        EntityMappingType entityMappingType = persister.getEntityMappingType();
        String[] propertyNames = persister.getPropertyNames();

        for (String p : propertyNames) {
            AttributeMapping attributeMapping = entityMappingType.findAttributeMapping(p);
            if (attributeMapping == null) continue;

            int index = attributeMapping.getStateArrayPosition();
            PersistentProperty property = persistentEntity.getHibernatePropertyByName(p);

            if (property != null && !GormProperties.VERSION.equals(property.getName())) {
                state[index] = reflector.getProperty(entity, property.getName());
            }
        }
    }

    private void doVoidWithManualSession(AbstractEvent event, Runnable action) {
        SharedSessionContractImplementor sessionImpl = event.getSession();
        if (sessionImpl instanceof Session session) {
            FlushMode current = session.getHibernateFlushMode();
            try {
                session.setHibernateFlushMode(FlushMode.MANUAL);
                action.run();
            } finally {
                session.setHibernateFlushMode(current);
            }
        } else {
            action.run();
        }
    }

    private boolean doBooleanWithManualSession(AbstractEvent event, Callable<Boolean> callable) {
        SharedSessionContractImplementor sessionImpl = event.getSession();
        if (sessionImpl instanceof Session session) {
            FlushMode current = session.getHibernateFlushMode();
            try {
                session.setHibernateFlushMode(FlushMode.MANUAL);
                return callable.call();
            } catch (Exception e) {
                throw new HibernateException(e);
            } finally {
                session.setHibernateFlushMode(current);
            }
        }
        try {
            return callable.call();
        } catch (Exception e) {
            throw new HibernateException(e);
        }
    }

    @Override
    public void onMerge(MergeEvent event) {}

    @Override
    public void onMerge(MergeEvent event, MergeContext copiedAlready) {}

    @Override
    public void onPersist(PersistEvent event) {}

    @Override
    public void onPersist(PersistEvent event, PersistContext createdAlready) {}

    @Override
    public void injectCallbackRegistry(CallbackRegistry callbackRegistry) {}
}
