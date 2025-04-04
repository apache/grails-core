/*
 * Copyright 2011 SpringSource.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.orm.hibernate.event.listener;

import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent;
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEventListener;
import org.grails.orm.hibernate.AbstractHibernateDatastore;
import org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings;
import org.grails.orm.hibernate.support.SoftKey;
import org.springframework.context.ApplicationEvent;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * <p>Invokes closure events on domain entities such as beforeInsert, beforeUpdate and beforeDelete.
 *
 * @author Graeme Rocher
 * @author Lari Hotari
 * @author Burt Beckwith
 * @since 2.0
 */
public abstract class AbstractHibernateEventListener extends AbstractPersistenceEventListener {

    protected final transient ConcurrentMap<SoftKey<Class<?>>, Boolean> cachedShouldTrigger =
            new ConcurrentHashMap<SoftKey<Class<?>>, Boolean>();
    protected final boolean failOnError;
    protected final List<?> failOnErrorPackages;

    protected AbstractHibernateEventListener(AbstractHibernateDatastore datastore) {
        super(datastore);
        HibernateConnectionSourceSettings settings = datastore.getConnectionSources().getDefaultConnectionSource().getSettings();
        this.failOnError = settings.isFailOnError();
        this.failOnErrorPackages = settings.getFailOnErrorPackages();
    }

    /**
     * {@inheritDoc}
     * @see org.springframework.context.event.SmartApplicationListener#supportsEventType(
     *     java.lang.Class)
     */
    public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        return AbstractPersistenceEvent.class.isAssignableFrom(eventType);
    }

    /**
     * @return The hibernate datastore
     */
    protected AbstractHibernateDatastore getDatastore() {
        return (AbstractHibernateDatastore) this.datastore;
    }
}
