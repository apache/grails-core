/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.orm.hibernate

import groovy.transform.CompileStatic
import org.hibernate.Criteria
import org.hibernate.LockMode
import org.hibernate.SessionFactory
import org.hibernate.query.Query
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import grails.gorm.multitenancy.Tenants

/**
 * A {@link IHibernateTemplate} implementation that binds a tenant id for the duration of the execution
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class TenantBoundHibernateTemplate implements IHibernateTemplate {

    private final IHibernateTemplate delegate
    private final Serializable tenantId
    private final MultiTenantCapableDatastore datastore

    TenantBoundHibernateTemplate(IHibernateTemplate delegate, Serializable tenantId, MultiTenantCapableDatastore datastore) {
        this.delegate = delegate
        this.tenantId = tenantId
        this.datastore = datastore
    }

    @Override
    Serializable save(Object o) {
        return (Serializable) Tenants.withId(datastore, tenantId) {
            delegate.save(o)
        }
    }

    @Override
    void refresh(Object o) {
        Tenants.withId(datastore, tenantId) {
            delegate.refresh(o)
        }
    }

    @Override
    void lock(Object o, LockMode lockMode) {
        Tenants.withId(datastore, tenantId) {
            delegate.lock(o, lockMode)
        }
    }

    @Override
    void flush() {
        delegate.flush()
    }

    @Override
    void clear() {
        delegate.clear()
    }

    @Override
    void evict(Object o) {
        delegate.evict(o)
    }

    @Override
    boolean contains(Object o) {
        delegate.contains(o)
    }

    @Override
    void setFlushMode(int mode) {
        delegate.setFlushMode(mode)
    }

    @Override
    int getFlushMode() {
        delegate.getFlushMode()
    }

    @Override
    void deleteAll(Collection<?> list) {
        Tenants.withId(datastore, tenantId) {
            delegate.deleteAll(list)
        }
    }

    @Override
    void applySettings(Query query) {
        delegate.applySettings(query)
    }

    @Override
    void applySettings(Criteria criteria) {
        delegate.applySettings(criteria)
    }

    @Override
    <T> T get(Class<T> type, Serializable key) {
        return (T) Tenants.withId(datastore, tenantId) {
            delegate.get(type, key)
        }
    }

    @Override
    <T> T get(Class<T> type, Serializable key, LockMode mode) {
        return (T) Tenants.withId(datastore, tenantId) {
            delegate.get(type, key, mode)
        }
    }

    @Override
    <T> T load(Class<T> type, Serializable key) {
        return (T) Tenants.withId(datastore, tenantId) {
            delegate.load(type, key)
        }
    }

    @Override
    <T> T lock(Class<T> type, Serializable key, LockMode mode) {
        return (T) Tenants.withId(datastore, tenantId) {
            delegate.lock(type, key, mode)
        }
    }

    @Override
    void delete(Object o) {
        Tenants.withId(datastore, tenantId) {
            delegate.delete(o)
        }
    }

    @Override
    SessionFactory getSessionFactory() {
        delegate.getSessionFactory()
    }

    @Override
    <T> T execute(GrailsHibernateTemplate.HibernateCallback<T> action) {
        return (T) Tenants.withId(datastore, tenantId) {
            delegate.execute(action)
        }
    }

    @Override
    <T> T execute(Closure<T> callable) {
        return (T) Tenants.withId(datastore, tenantId) {
            delegate.execute(callable)
        }
    }

    @Override
    <T> T executeWithNewSession(Closure<T> callable) {
        return (T) Tenants.withId(datastore, tenantId) {
            delegate.executeWithNewSession(callable)
        }
    }

    @Override
    <T1> T1 executeWithExistingOrCreateNewSession(SessionFactory sessionFactory, Closure<T1> callable) {
        return (T1) Tenants.withId(datastore, tenantId) {
            delegate.executeWithExistingOrCreateNewSession(sessionFactory, callable)
        }
    }
}
