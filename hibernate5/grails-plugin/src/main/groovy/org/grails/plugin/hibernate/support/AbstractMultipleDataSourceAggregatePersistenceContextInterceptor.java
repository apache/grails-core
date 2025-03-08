/*
 * Copyright 2013 SpringSource.
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
package org.grails.plugin.hibernate.support;

import grails.persistence.support.PersistenceContextInterceptor;
import org.grails.datastore.mapping.core.connections.ConnectionSource;
import org.grails.datastore.mapping.core.connections.ConnectionSources;
import org.grails.orm.hibernate.AbstractHibernateDatastore;
import org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings;
import org.hibernate.SessionFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract implementation of the {@link grails.persistence.support.PersistenceContextInterceptor} interface that supports multiple data sources
 *
 * @author Graeme Rocher
 * @since 2.0.7
 */
public abstract class AbstractMultipleDataSourceAggregatePersistenceContextInterceptor implements PersistenceContextInterceptor {



    protected final List<PersistenceContextInterceptor> interceptors = new ArrayList<PersistenceContextInterceptor>();
    protected final AbstractHibernateDatastore hibernateDatastore;

    public AbstractMultipleDataSourceAggregatePersistenceContextInterceptor(AbstractHibernateDatastore hibernateDatastore) {
        this.hibernateDatastore = hibernateDatastore;
        ConnectionSources<SessionFactory, HibernateConnectionSourceSettings> connectionSources = hibernateDatastore.getConnectionSources();
        Iterable<ConnectionSource<SessionFactory, HibernateConnectionSourceSettings>> allConnectionSources = connectionSources.getAllConnectionSources();
        for (ConnectionSource<SessionFactory, HibernateConnectionSourceSettings> connectionSource : allConnectionSources) {
            SessionFactoryAwarePersistenceContextInterceptor interceptor = createPersistenceContextInterceptor(connectionSource.getName());
            this.interceptors.add(interceptor);
        }
    }

    public boolean isOpen() {
        for (PersistenceContextInterceptor interceptor : interceptors) {
            if (interceptor.isOpen()) {
                // true at least one is true
                return true;
            }
        }
        return false;
    }

    public void reconnect() {
        for (PersistenceContextInterceptor interceptor : interceptors) {
            interceptor.reconnect();
        }
    }

    public void destroy() {
        for (PersistenceContextInterceptor interceptor : interceptors) {
            try {
                if (interceptor.isOpen()) {
                    interceptor.destroy();
                }
            } catch (Exception e) {
                // ignore exception
            }
        }
    }

    public void clear() {
        for (PersistenceContextInterceptor interceptor : interceptors) {
            interceptor.clear();
        }
    }

    public void disconnect() {
        for (PersistenceContextInterceptor interceptor : interceptors) {
            interceptor.disconnect();
        }
    }

    public void flush() {
        for (PersistenceContextInterceptor interceptor : interceptors) {
            interceptor.flush();
        }
    }

    public void init() {
        for (PersistenceContextInterceptor interceptor : interceptors) {
            interceptor.init();
        }
    }

    public void setReadOnly() {
        for (PersistenceContextInterceptor interceptor : interceptors) {
            interceptor.setReadOnly();
        }
    }

    public void setReadWrite() {
        for (PersistenceContextInterceptor interceptor : interceptors) {
            interceptor.setReadWrite();
        }
    }

    protected abstract  SessionFactoryAwarePersistenceContextInterceptor createPersistenceContextInterceptor(String dataSourceName);

}