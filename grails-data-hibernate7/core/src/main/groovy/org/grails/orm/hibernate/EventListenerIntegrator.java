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
package org.grails.orm.hibernate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.internal.DefaultPersistOnFlushEventListener;
import org.hibernate.event.service.spi.EventListenerGroup;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;

import org.grails.orm.hibernate.support.ClosureEventTriggeringInterceptor;

public class EventListenerIntegrator implements Integrator {

    protected static final List<EventType<?>> TYPES = Arrays.asList(
            EventType.AUTO_FLUSH,
            EventType.MERGE,
            EventType.PERSIST,
            EventType.PERSIST_ONFLUSH,
            EventType.DELETE,
            EventType.DIRTY_CHECK,
            EventType.EVICT,
            EventType.FLUSH,
            EventType.FLUSH_ENTITY,
            EventType.LOAD,
            EventType.INIT_COLLECTION,
            EventType.LOCK,
            EventType.REFRESH,
            EventType.REPLICATE,
            EventType.PRE_LOAD,
            EventType.PRE_UPDATE,
            EventType.PRE_DELETE,
            EventType.PRE_INSERT,
            EventType.PRE_COLLECTION_RECREATE,
            EventType.PRE_COLLECTION_REMOVE,
            EventType.PRE_COLLECTION_UPDATE,
            EventType.POST_LOAD,
            EventType.POST_UPDATE,
            EventType.POST_DELETE,
            EventType.POST_INSERT,
            EventType.POST_COMMIT_UPDATE,
            EventType.POST_COMMIT_DELETE,
            EventType.POST_COMMIT_INSERT,
            EventType.POST_COLLECTION_RECREATE,
            EventType.POST_COLLECTION_REMOVE,
            EventType.POST_COLLECTION_UPDATE);
    protected HibernateEventListeners hibernateEventListeners;
    protected Map<String, Object> eventListeners;

    public EventListenerIntegrator(
            HibernateEventListeners hibernateEventListeners, Map<String, Object> eventListeners) {
        this.hibernateEventListeners = hibernateEventListeners;
        this.eventListeners = eventListeners;
    }

    @SuppressWarnings({"unchecked", "rawtypes", "PMD.DataflowAnomalyAnalysis"})
    @Override
    public void integrate(
            Metadata metadata,
            BootstrapContext bootstrapContext,
            SessionFactoryImplementor sfi) {

        EventListenerRegistry listenerRegistry = sfi.getServiceRegistry().getService(EventListenerRegistry.class);
        if (listenerRegistry == null) {
            throw new IllegalStateException("EventListenerRegistry not available from ServiceRegistry");
        }

        if (eventListeners != null) {
            for (Map.Entry<String, Object> entry : eventListeners.entrySet()) {
                EventType type = EventType.resolveEventTypeByName(entry.getKey());
                Object listenerObject = entry.getValue();
                if (listenerObject instanceof Collection) {
                    appendListeners(listenerRegistry, type, (Collection) listenerObject);
                } else if (listenerObject != null) {
                    appendListeners(listenerRegistry, type, Collections.singleton(listenerObject));
                }
            }
        }

        if (hibernateEventListeners != null && hibernateEventListeners.getListenerMap() != null) {
            Map<String, Object> listenerMap = hibernateEventListeners.getListenerMap();
            for (EventType<?> type : TYPES) {
                appendListeners(listenerRegistry, type, listenerMap);
            }
        }
    }

    @SuppressWarnings("PMD.DataflowAnomalyAnalysis")
    protected <T> void appendListeners(
            EventListenerRegistry listenerRegistry, EventType<T> eventType, Collection<T> listeners) {

        EventListenerGroup<T> group = listenerRegistry.getEventListenerGroup(eventType);
        for (T listener : listeners) {
            if (listener != null) {
                if (shouldOverrideListeners(eventType, listener)) {
                    // since ClosureEventTriggeringInterceptor extends DefaultSaveOrUpdateEventListener we
                    // want to override instead of append the listener here
                    // to avoid there being 2 implementations which would impact performance too
                    List<T> retained = grailsOwnedListeners(group, listener);
                    group.clearListeners();
                    group.appendListener(listener);
                    for (T grailsListener : retained) {
                        group.appendListener(grailsListener);
                    }
                } else {
                    group.appendListener(listener);
                }
            }
        }
    }

    private <T> boolean shouldOverrideListeners(EventType<T> eventType, Object listener) {
        var isMergeListener = listener instanceof org.hibernate.event.internal.DefaultMergeEventListener;
        var isMergeEvent = eventType.equals(EventType.MERGE);
        var isPersistEventListener = listener instanceof org.hibernate.event.internal.DefaultPersistEventListener;
        var isPersistEvent = eventType.equals(EventType.PERSIST);
        var isPersistOnFlushListener = listener instanceof DefaultPersistOnFlushEventListener;
        var isPersistOnFlushEvent = eventType.equals(EventType.PERSIST_ONFLUSH);
        // ClosureEventTriggeringInterceptor is registered for merge and persist too, but it composes (rather
        // than extends) DefaultMergeEventListener/DefaultPersistEventListener, delegating every call to its own
        // internal instance of each. Appending it to Hibernate's own default listener group would run that
        // delegated persist/merge logic a second time for every entity - replace the default listener instead,
        // the same way we do for the Default*EventListener subclasses above. The persist-on-flush listener it
        // supplies extends DefaultPersistOnFlushEventListener and is covered by that clause.
        var isClosureEventTriggeringInterceptor = listener instanceof ClosureEventTriggeringInterceptor;
        return isMergeListener && isMergeEvent ||
                isPersistEventListener && isPersistEvent ||
                isPersistOnFlushListener && isPersistOnFlushEvent ||
                isClosureEventTriggeringInterceptor && (isMergeEvent || isPersistEvent);
    }

    @SuppressWarnings("unchecked")
    protected <T> void appendListeners(
            final EventListenerRegistry listenerRegistry,
            final EventType<T> eventType,
            final Map<String, Object> listeners) {

        Object listener = listeners.get(eventType.eventName());
        if (listener != null) {
            if (shouldOverrideListeners(eventType, listener)) {
                // since ClosureEventTriggeringInterceptor extends DefaultSaveOrUpdateEventListener we want
                // to override instead of append the listener here
                // to avoid there being 2 implementations which would impact performance too
                EventListenerGroup<T> group = listenerRegistry.getEventListenerGroup(eventType);
                List<T> retained = grailsOwnedListeners(group, (T) listener);
                listenerRegistry.setListeners(eventType, (T) listener);
                for (T grailsListener : retained) {
                    group.appendListener(grailsListener);
                }
            } else {
                listenerRegistry.appendListeners(eventType, (T) listener);
            }
        }
    }

    /**
     * What GORM contributed to the group that a replacement must not discard, in the form it can keep.
     * <p>
     * Replacing a group is meant to displace Hibernate's own default listener, but an application can register
     * a listener of its own for the same event. Without this, such a listener would silently take GORM's
     * persistence events and its dirty-check activation with it. The replacement performs the persist or merge
     * itself, so what is kept is the interceptor's observing listener rather than the interceptor, which would
     * otherwise perform that same operation a second time for every entity.
     */
    @SuppressWarnings("unchecked")
    private <T> List<T> grailsOwnedListeners(EventListenerGroup<T> group, T replacement) {
        List<T> retained = new ArrayList<>();
        if (group == null || group.listeners() == null) {
            return retained;
        }
        for (T existing : group.listeners()) {
            if (existing == replacement) {
                continue;
            }
            Object kept = retainedForm(existing);
            if (kept != null) {
                retained.add((T) kept);
            }
        }
        return retained;
    }

    private Object retainedForm(Object listener) {
        if (listener instanceof ClosureEventTriggeringInterceptor) {
            return ((ClosureEventTriggeringInterceptor) listener).getObservingEventListener();
        }
        if (listener instanceof ClosureEventTriggeringInterceptor.PersistOnFlushEventListener) {
            return ((ClosureEventTriggeringInterceptor.PersistOnFlushEventListener) listener)
                    .getObservingEventListener();
        }
        if (listener instanceof ClosureEventTriggeringInterceptor.ObservingEventListener) {
            return listener;
        }
        return null;
    }

    @Override
    public void disintegrate(SessionFactoryImplementor sessionFactory, SessionFactoryServiceRegistry serviceRegistry) {
        // nothing to do
    }
}
