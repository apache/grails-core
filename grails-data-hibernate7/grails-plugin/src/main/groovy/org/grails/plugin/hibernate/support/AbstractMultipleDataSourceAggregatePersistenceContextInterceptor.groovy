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
package org.grails.plugin.hibernate.support

import groovy.transform.CompileStatic
import org.hibernate.SessionFactory

import grails.persistence.support.PersistenceContextInterceptor
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings

/**
 * Abstract implementation of the {@link grails.persistence.support.PersistenceContextInterceptor} interface that supports multiple data sources
 *
 * @author Graeme Rocher
 * @since 2.0.7
 */
@CompileStatic
abstract class AbstractMultipleDataSourceAggregatePersistenceContextInterceptor
        implements PersistenceContextInterceptor {

    protected final List<PersistenceContextInterceptor> interceptors = []
    protected final HibernateDatastore hibernateDatastore

    AbstractMultipleDataSourceAggregatePersistenceContextInterceptor(HibernateDatastore hibernateDatastore) {
        this.hibernateDatastore = hibernateDatastore
        ConnectionSources<SessionFactory, HibernateConnectionSourceSettings> connectionSources =
                hibernateDatastore.connectionSources
        Iterable<ConnectionSource<SessionFactory, HibernateConnectionSourceSettings>> allConnectionSources =
                connectionSources.allConnectionSources
        for (ConnectionSource<SessionFactory, HibernateConnectionSourceSettings> connectionSource :
                allConnectionSources) {
            SessionFactoryAwarePersistenceContextInterceptor interceptor =
                    createPersistenceContextInterceptor(connectionSource.name)
            this.interceptors.add(interceptor)
        }
    }

    boolean isOpen() {
        for (PersistenceContextInterceptor interceptor : interceptors) {
            if (interceptor.isOpen()) {
                // true at least one is true
                return true
            }
        }
        return false
    }

    void reconnect() {
        for (PersistenceContextInterceptor interceptor : interceptors) {
            interceptor.reconnect()
        }
    }

    void destroy() {
        for (PersistenceContextInterceptor interceptor : interceptors) {
            try {
                if (interceptor.isOpen()) {
                    interceptor.destroy()
                }
            } catch (Exception e) {
                // ignore exception
            }
        }
    }

    void clear() {
        for (PersistenceContextInterceptor interceptor : interceptors) {
            interceptor.clear()
        }
    }

    void disconnect() {
        for (PersistenceContextInterceptor interceptor : interceptors) {
            interceptor.disconnect()
        }
    }

    void flush() {
        for (PersistenceContextInterceptor interceptor : interceptors) {
            interceptor.flush()
        }
    }

    void init() {
        for (PersistenceContextInterceptor interceptor : interceptors) {
            interceptor.init()
        }
    }

    void setReadOnly() {
        for (PersistenceContextInterceptor interceptor : interceptors) {
            interceptor.setReadOnly()
        }
    }

    void setReadWrite() {
        for (PersistenceContextInterceptor interceptor : interceptors) {
            interceptor.setReadWrite()
        }
    }

    protected abstract SessionFactoryAwarePersistenceContextInterceptor createPersistenceContextInterceptor(
            String dataSourceName)

}
