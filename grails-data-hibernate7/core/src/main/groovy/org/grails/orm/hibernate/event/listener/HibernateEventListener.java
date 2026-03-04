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
package org.grails.orm.hibernate.event.listener;

import grails.gorm.MultiTenant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.grails.datastore.gorm.timestamp.DefaultTimestampProvider;
import org.grails.datastore.gorm.timestamp.TimestampProvider;
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent;
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEventListener;
import org.grails.datastore.mapping.engine.event.ValidationEvent;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.orm.hibernate.HibernateDatastore;
import org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings;
import org.grails.orm.hibernate.support.ClosureEventListener;
import org.grails.orm.hibernate.support.SoftKey;
import org.hibernate.Hibernate;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.spi.EventSource;
import org.hibernate.event.spi.MergeEvent;
import org.hibernate.event.spi.PersistEvent;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostLoadEvent;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PreDeleteEvent;
import org.hibernate.event.spi.PreInsertEvent;
import org.hibernate.event.spi.PreLoadEvent;
import org.hibernate.event.spi.PreUpdateEvent;

import org.springframework.context.ApplicationEvent;

import grails.gorm.MultiTenant;
import org.grails.datastore.gorm.timestamp.DefaultTimestampProvider;
import org.grails.datastore.gorm.timestamp.TimestampProvider;
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent;
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEventListener;
import org.grails.datastore.mapping.engine.event.ValidationEvent;
import org.grails.orm.hibernate.HibernateDatastore;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity;
import org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings;
import org.grails.orm.hibernate.support.ClosureEventListener;
import org.grails.orm.hibernate.support.SoftKey;

/**
 * Invokes closure events on domain entities such as beforeInsert, beforeUpdate and beforeDelete.
 *
 * @author Graeme Rocher
 * @author Lari Hotari
 * @author Burt Beckwith
 * @since 2.0
 */
@SuppressWarnings({"PMD.CloseResource", "PMD.DataflowAnomalyAnalysis"})
public class HibernateEventListener extends AbstractPersistenceEventListener {

  /** The cached should trigger. */
  protected final transient ConcurrentMap<SoftKey<Class<?>>, Boolean> cachedShouldTrigger =
      new ConcurrentHashMap<SoftKey<Class<?>>, Boolean>();

  /** The fail on error. */
  protected final boolean failOnError;

  /** The fail on error packages. */
  protected final List<?> failOnErrorPackages;

    /** The cached should trigger. */
    protected final transient ConcurrentMap<SoftKey<Class<?>>, Boolean> cachedShouldTrigger =
            new ConcurrentHashMap<SoftKey<Class<?>>, Boolean>();

  public HibernateEventListener(HibernateDatastore datastore) {
    super(datastore);
    HibernateConnectionSourceSettings settings =
        datastore.getConnectionSources().getDefaultConnectionSource().getSettings();
    this.failOnError = settings.isFailOnError();
    this.failOnErrorPackages = settings.getFailOnErrorPackages();
  }

  /**
   * @return The hibernate datastore
   */
  protected HibernateDatastore getDatastore() {
    return (HibernateDatastore) this.datastore;
  }

    /** The fail on error packages. */
    protected final List<?> failOnErrorPackages;

    protected transient ConcurrentMap<SoftKey<Class<?>>, ClosureEventListener> eventListeners =
            new ConcurrentHashMap<>();

    public HibernateEventListener(HibernateDatastore datastore) {
        super(datastore);
        HibernateConnectionSourceSettings settings =
                datastore.getConnectionSources().getDefaultConnectionSource().getSettings();
        this.failOnError = settings.isFailOnError();
        this.failOnErrorPackages = settings.getFailOnErrorPackages();
    }

    /**
     * @return The hibernate datastore
     */
    protected HibernateDatastore getDatastore() {
        return (HibernateDatastore) this.datastore;
    }

    @Override
    protected void onPersistenceEvent(final AbstractPersistenceEvent event) {
        switch (event.getEventType()) {
            case PreInsert:
                if (onPreInsert((PreInsertEvent) event.getNativeEvent())) {
                    event.cancel();
                }
                break;
            case PostInsert:
                onPostInsert((PostInsertEvent) event.getNativeEvent());
                break;
            case PreUpdate:
                if (onPreUpdate((PreUpdateEvent) event.getNativeEvent())) {
                    event.cancel();
                }
                break;
            case PostUpdate:
                onPostUpdate((PostUpdateEvent) event.getNativeEvent());
                break;
            case PreDelete:
                if (onPreDelete((PreDeleteEvent) event.getNativeEvent())) {
                    event.cancel();
                }
                break;
            case PostDelete:
                onPostDelete((PostDeleteEvent) event.getNativeEvent());
                break;
            case PreLoad:
                onPreLoad((PreLoadEvent) event.getNativeEvent());
                break;
            case PostLoad:
                onPostLoad((PostLoadEvent) event.getNativeEvent());
                break;
            case Merge:
                onMergeEvent((MergeEvent) event.getNativeEvent());
                break;
            case Persist:
                onPersistEvent((PersistEvent) event.getNativeEvent());
                break;
            case Validation:
                onValidate((ValidationEvent) event);
                break;
            default:
                throw new IllegalStateException("Unexpected EventType: " + event.getEventType());
        }
    }

    Boolean shouldTrigger = cachedShouldTrigger.get(key);
    if (shouldTrigger == null || shouldTrigger) {
      synchronized (cachedShouldTrigger) {
        eventListener = eventListeners.get(key);
        if (eventListener == null) {
          HibernateDatastore datastore = getDatastore();
          boolean isValidSessionFactory =
              MultiTenant.class.isAssignableFrom(clazz)
                  || factory == null
                  || datastore.getSessionFactory().equals(factory);
          PersistentEntity persistentEntity =
              datastore.getMappingContext().getPersistentEntity(clazz.getName());
          shouldTrigger = (persistentEntity != null && isValidSessionFactory);
          if (shouldTrigger) {
            eventListener =
                new ClosureEventListener(persistentEntity, failOnError, failOnErrorPackages);
            ClosureEventListener previous = eventListeners.putIfAbsent(key, eventListener);
            if (previous != null) {
              eventListener = previous;
            }
        }
    }

    protected void onMergeEvent(MergeEvent event) {
        Object entity = Optional.ofNullable(event.getOriginal()).orElse(event.getEntity());
        if (entity != null) {
            ClosureEventListener eventListener;
            EventSource session = event.getSession();
            eventListener = findEventListener(entity, (SessionFactoryImplementor) session.getSessionFactory());
            if (eventListener != null) {
                eventListener.onMerge(event);
            }
        }
    }

    public void onPreLoad(PreLoadEvent event) {
        Object entity = event.getEntity();
        ClosureEventListener eventListener =
                findEventListener(entity, event.getPersister().getFactory());
        if (eventListener != null) {
            eventListener.onPreLoad(event);
        }
    }

    public void onPostLoad(PostLoadEvent event) {
        ClosureEventListener eventListener =
                findEventListener(event.getEntity(), event.getPersister().getFactory());
        if (eventListener != null) {
            eventListener.onPostLoad(event);
        }
    }

    public void onPostInsert(PostInsertEvent event) {
        ClosureEventListener eventListener =
                findEventListener(event.getEntity(), event.getPersister().getFactory());
        if (eventListener != null) {
            eventListener.onPostInsert(event);
        }
    }

    public boolean onPreInsert(PreInsertEvent event) {
        ClosureEventListener eventListener =
                findEventListener(event.getEntity(), event.getPersister().getFactory());
        return eventListener != null && eventListener.onPreInsert(event);
    }

    public boolean onPreUpdate(PreUpdateEvent event) {
        ClosureEventListener eventListener =
                findEventListener(event.getEntity(), event.getPersister().getFactory());
        return eventListener != null && eventListener.onPreUpdate(event);
    }

    public void onPostUpdate(PostUpdateEvent event) {
        ClosureEventListener eventListener =
                findEventListener(event.getEntity(), event.getPersister().getFactory());
        if (eventListener != null) {
            eventListener.onPostUpdate(event);
        }
    }

    public boolean onPreDelete(PreDeleteEvent event) {
        ClosureEventListener eventListener =
                findEventListener(event.getEntity(), event.getPersister().getFactory());
        return eventListener != null && eventListener.onPreDelete(event);
    }

    public void onPostDelete(PostDeleteEvent event) {
        ClosureEventListener eventListener =
                findEventListener(event.getEntity(), event.getPersister().getFactory());
        if (eventListener != null) {
            eventListener.onPostDelete(event);
        }
    }

    public void onValidate(ValidationEvent event) {
        ClosureEventListener eventListener = findEventListener(event.getEntityObject(), null);
        if (eventListener != null) {
            eventListener.onValidate(event);
        }
    }

    protected ClosureEventListener findEventListener(Object entity, SessionFactoryImplementor factory) {
        if (entity == null) return null;
        Class<?> clazz = Hibernate.getClass(entity);

        SoftKey<Class<?>> key = new SoftKey<Class<?>>(clazz);
        ClosureEventListener eventListener = eventListeners.get(key);
        if (eventListener != null) {
            return eventListener;
        }

        Boolean shouldTrigger = cachedShouldTrigger.get(key);
        if (shouldTrigger == null || shouldTrigger) {
            synchronized (cachedShouldTrigger) {
                eventListener = eventListeners.get(key);
                if (eventListener == null) {
                    HibernateDatastore datastore = getDatastore();
                    boolean isValidSessionFactory = MultiTenant.class.isAssignableFrom(clazz) ||
                            factory == null ||
                            datastore.getSessionFactory().equals(factory);
                    HibernatePersistentEntity persistentEntity = (HibernatePersistentEntity)
                            datastore.getMappingContext().getPersistentEntity(clazz.getName());
                    shouldTrigger = (persistentEntity != null && isValidSessionFactory);
                    if (shouldTrigger) {
                        eventListener = new ClosureEventListener(persistentEntity, failOnError, failOnErrorPackages);
                        ClosureEventListener previous = eventListeners.putIfAbsent(key, eventListener);
                        if (previous != null) {
                            eventListener = previous;
                        }
                    }
                    cachedShouldTrigger.put(key, shouldTrigger);
                }
            }
        }
        return eventListener;
    }

    /**
     * {@inheritDoc}
     *
     * @see
     *     org.springframework.context.event.SmartApplicationListener#supportsEventType(java.lang.Class)
     */
    public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        return AbstractPersistenceEvent.class.isAssignableFrom(eventType);
    }

    /**
     * @deprecated Replaced by {@link org.grails.datastore.gorm.events.AutoTimestampEventListener}
     */
    @Deprecated
    public TimestampProvider getTimestampProvider() {
        return new DefaultTimestampProvider();
    }

    /**
     * @deprecated Replaced by {@link org.grails.datastore.gorm.events.AutoTimestampEventListener}
     */
    @Deprecated
    public void setTimestampProvider(TimestampProvider timestampProvider) {
        // no-op
    }
}
