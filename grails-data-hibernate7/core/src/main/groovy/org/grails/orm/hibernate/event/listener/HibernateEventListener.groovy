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
package org.grails.orm.hibernate.event.listener

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

import groovy.transform.CompileStatic
import jakarta.annotation.Nonnull
import org.hibernate.Hibernate
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.event.spi.EventSource
import org.hibernate.event.spi.MergeEvent
import org.hibernate.event.spi.PersistEvent
import org.hibernate.event.spi.PostDeleteEvent
import org.hibernate.event.spi.PostInsertEvent
import org.hibernate.event.spi.PostLoadEvent
import org.hibernate.event.spi.PostUpdateEvent
import org.hibernate.event.spi.PreDeleteEvent
import org.hibernate.event.spi.PreInsertEvent
import org.hibernate.event.spi.PreLoadEvent
import org.hibernate.event.spi.PreUpdateEvent
import org.springframework.context.ApplicationEvent

import grails.gorm.MultiTenant
import org.grails.datastore.gorm.timestamp.DefaultTimestampProvider
import org.grails.datastore.gorm.timestamp.TimestampProvider
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEventListener
import org.grails.datastore.mapping.engine.event.EventType
import org.grails.datastore.mapping.engine.event.ValidationEvent
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity
import org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings
import org.grails.orm.hibernate.support.ClosureEventListener
import org.grails.orm.hibernate.support.SoftKey

/**
 * Invokes closure events on domain entities such as beforeInsert, beforeUpdate and beforeDelete.
 *
 * @author Graeme Rocher
 * @author Lari Hotari
 * @author Burt Beckwith
 * @since 2.0
 */
@CompileStatic
class HibernateEventListener extends AbstractPersistenceEventListener {

    /** The cached should trigger. */
    protected final transient ConcurrentMap<SoftKey<Class<?>>, Boolean> cachedShouldTrigger = new ConcurrentHashMap<>()

    /** The fail on error. */
    protected final boolean failOnError

    /** The fail on error packages. */
    protected final List<?> failOnErrorPackages

    protected transient ConcurrentMap<SoftKey<Class<?>>, ClosureEventListener> eventListeners = new ConcurrentHashMap<>()

    HibernateEventListener(HibernateDatastore datastore) {
        super(datastore)
        HibernateConnectionSourceSettings settings = datastore.getConnectionSources().getDefaultConnectionSource().getSettings()
        this.failOnError = settings.isFailOnError()
        this.failOnErrorPackages = settings.getFailOnErrorPackages()
    }

    /**
     * @return The hibernate datastore
     */
    protected HibernateDatastore getDatastore() {
        return (HibernateDatastore) this.@datastore
    }

    @Override
    protected void onPersistenceEvent(final AbstractPersistenceEvent event) {
        switch (event.getEventType()) {
            case EventType.PreInsert:
                if (onPreInsert((PreInsertEvent) event.getNativeEvent())) {
                    event.cancel()
                }
                break
            case EventType.PostInsert:
                onPostInsert((PostInsertEvent) event.getNativeEvent())
                break
            case EventType.PreUpdate:
                if (onPreUpdate((PreUpdateEvent) event.getNativeEvent())) {
                    event.cancel()
                }
                break
            case EventType.PostUpdate:
                onPostUpdate((PostUpdateEvent) event.getNativeEvent())
                break
            case EventType.PreDelete:
                if (onPreDelete((PreDeleteEvent) event.getNativeEvent())) {
                    event.cancel()
                }
                break
            case EventType.PostDelete:
                onPostDelete((PostDeleteEvent) event.getNativeEvent())
                break
            case EventType.PreLoad:
                onPreLoad((PreLoadEvent) event.getNativeEvent())
                break
            case EventType.PostLoad:
                onPostLoad((PostLoadEvent) event.getNativeEvent())
                break
            case EventType.Merge:
                onMergeEvent((MergeEvent) event.getNativeEvent())
                break
            case EventType.Persist:
                onPersistEvent((PersistEvent) event.getNativeEvent())
                break
            case EventType.Validation:
                onValidate((ValidationEvent) event)
                break
            default:
                throw new IllegalStateException('Unexpected EventType: ' + event.getEventType())
        }
    }

    protected void onPersistEvent(PersistEvent event) {
        Object entity = event.getObject()
        if (entity != null) {
            EventSource session = event.getSession()
            ClosureEventListener eventListener = findEventListener(entity, session.getSessionFactory())
            if (eventListener != null) {
                eventListener.onPersist(event)
            }
        }
    }

    protected void onMergeEvent(MergeEvent event) {
        Object entity = event.getOriginal() != null ? event.getOriginal() : event.getEntity()
        if (entity != null) {
            EventSource session = event.getSession()
            ClosureEventListener eventListener = findEventListener(entity, session.getSessionFactory())
            if (eventListener != null) {
                eventListener.onMerge(event)
            }
        }
    }

    void onPreLoad(PreLoadEvent event) {
        Object entity = event.getEntity()
        ClosureEventListener eventListener = findEventListener(entity, event.getPersister().getFactory())
        if (eventListener != null) {
            eventListener.onPreLoad(event)
        }
    }

    void onPostLoad(PostLoadEvent event) {
        ClosureEventListener eventListener = findEventListener(event.getEntity(), event.getPersister().getFactory())
        if (eventListener != null) {
            eventListener.onPostLoad(event)
        }
    }

    void onPostInsert(PostInsertEvent event) {
        ClosureEventListener eventListener = findEventListener(event.getEntity(), event.getPersister().getFactory())
        if (eventListener != null) {
            eventListener.onPostInsert(event)
        }
    }

    boolean onPreInsert(PreInsertEvent event) {
        ClosureEventListener eventListener = findEventListener(event.getEntity(), event.getPersister().getFactory())
        return eventListener != null && eventListener.onPreInsert(event)
    }

    boolean onPreUpdate(PreUpdateEvent event) {
        ClosureEventListener eventListener = findEventListener(event.getEntity(), event.getPersister().getFactory())
        return eventListener != null && eventListener.onPreUpdate(event)
    }

    void onPostUpdate(PostUpdateEvent event) {
        ClosureEventListener eventListener = findEventListener(event.getEntity(), event.getPersister().getFactory())
        if (eventListener != null) {
            eventListener.onPostUpdate(event)
        }
    }

    boolean onPreDelete(PreDeleteEvent event) {
        ClosureEventListener eventListener = findEventListener(event.getEntity(), event.getPersister().getFactory())
        return eventListener != null && eventListener.onPreDelete(event)
    }

    void onPostDelete(PostDeleteEvent event) {
        ClosureEventListener eventListener = findEventListener(event.getEntity(), event.getPersister().getFactory())
        if (eventListener != null) {
            eventListener.onPostDelete(event)
        }
    }

    void onValidate(ValidationEvent event) {
        ClosureEventListener eventListener = findEventListener(event.getEntityObject(), null)
        if (eventListener != null) {
            eventListener.onValidate(event)
        }
    }

    protected ClosureEventListener findEventListener(Object entity, SessionFactoryImplementor factory) {
        if (entity == null) {
            return null
        }
        Class<?> clazz = Hibernate.getClass(entity)

        SoftKey<Class<?>> key = new SoftKey<Class<?>>(clazz)
        ClosureEventListener eventListener = eventListeners.get(key)
        if (eventListener != null) {
            return eventListener
        }

        Boolean shouldTrigger = cachedShouldTrigger.get(key)
        if (shouldTrigger == null || shouldTrigger) {
            synchronized (cachedShouldTrigger) {
                eventListener = eventListeners.get(key)
                if (eventListener == null) {
                    HibernateDatastore hibernateDatastore = getDatastore()
                    boolean isValidSessionFactory = MultiTenant.isAssignableFrom(clazz) ||
                            factory == null ||
                            hibernateDatastore.getSessionFactory().equals(factory)
                    HibernatePersistentEntity persistentEntity = (HibernatePersistentEntity) hibernateDatastore.getMappingContext().getPersistentEntity(clazz.getName())
                    shouldTrigger = (persistentEntity != null && isValidSessionFactory)
                    if (shouldTrigger) {
                        eventListener = new ClosureEventListener(persistentEntity, failOnError, failOnErrorPackages)
                        ClosureEventListener previous = eventListeners.putIfAbsent(key, eventListener)
                        if (previous != null) {
                            eventListener = previous
                        }
                    }
                    cachedShouldTrigger.put(key, shouldTrigger)
                }
            }
        }
        return eventListener
    }

    /**
     * {@inheritDoc}
     *
     * @see
     *     org.springframework.context.event.SmartApplicationListener#supportsEventType(java.lang.Class)
     */
    @Override
    boolean supportsEventType(@Nonnull Class<? extends ApplicationEvent> eventType) {
        return AbstractPersistenceEvent.isAssignableFrom(eventType)
    }

    /**
     * @deprecated Replaced by {@link org.grails.datastore.gorm.events.AutoTimestampEventListener}
     */
    @Deprecated
    TimestampProvider getTimestampProvider() {
        return new DefaultTimestampProvider()
    }

    /**
     * @deprecated Replaced by {@link org.grails.datastore.gorm.events.AutoTimestampEventListener}
     */
    @Deprecated
    void setTimestampProvider(TimestampProvider timestampProvider) {
        // no-op
    }

}
