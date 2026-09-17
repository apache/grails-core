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
package org.grails.orm.hibernate.proxy

import groovy.transform.CompileStatic
import org.hibernate.Hibernate
import org.hibernate.collection.spi.LazyInitializable
import org.hibernate.collection.spi.PersistentCollection
import org.hibernate.proxy.HibernateProxy
import org.hibernate.proxy.HibernateProxyHelper

import org.grails.datastore.gorm.proxy.ProxyInstanceMetaClass
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.engine.AssociationQueryExecutor
import org.grails.datastore.mapping.proxy.EntityProxy
import org.grails.datastore.mapping.proxy.ProxyFactory
import org.grails.datastore.mapping.proxy.ProxyHandler
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher
import org.grails.orm.hibernate.GrailsHibernateTemplate

/**
 * Implementation of the ProxyHandler interface for Hibernate 7.
 *
 * @author Graeme Rocher
 * @since 7.0
 */
@SuppressWarnings('PMD.CloseResource')
@CompileStatic
class HibernateProxyHandler implements ProxyHandler, ProxyFactory {

    @Override
    boolean isInitialized(Object o) {
        if (o == null) return false

        if (o instanceof HibernateProxy) {
            return !o.getHibernateLazyInitializer().isUninitialized()
        }
        if (o instanceof EntityProxy) {
            return o.isInitialized()
        }
        if (o instanceof LazyInitializable) {
            return o.wasInitialized()
        }

        Boolean groovyProxyInitialized = GroovyProxyInterceptorLogic.isInitialized(o)
        if (groovyProxyInitialized != null) {
            return groovyProxyInitialized
        }

        return Hibernate.isInitialized(o)
    }

    @Override
    boolean isInitialized(Object obj, String associationName) {
        try {
            Object proxy = ClassPropertyFetcher.getInstancePropertyValue(obj, associationName)
            return isInitialized(proxy)
        }
        catch (RuntimeException e) {
            return false
        }
    }

    @Override
    Object unwrap(Object object) {
        if (object instanceof EntityProxy) {
            return object.getTarget()
        }

        Object unwrapped = GroovyProxyInterceptorLogic.unwrap(object)
        if (unwrapped != null) {
            return unwrapped
        }

        if (object instanceof PersistentCollection) {
            initialize(object)
            return object
        }

        return Hibernate.unproxy(object)
    }

    @Override
    Serializable getIdentifier(Object o) {
        if (o instanceof EntityProxy) {
            return o.getProxyKey()
        }

        // check HibernateProxy before the Groovy metaClass probe: probing the metaClass of a
        // proxy whose interceptor is not Groovy-aware would initialize it (or throw
        // LazyInitializationException when detached), while the LazyInitializer holds the
        // identifier without needing a session
        if (o instanceof HibernateProxy) {
            return (Serializable) o.getHibernateLazyInitializer().getIdentifier()
        }

        return GroovyProxyInterceptorLogic.getIdentifier(o)
    }

    @Override
    Class<?> getProxiedClass(Object o) {
        return HibernateProxyHelper.getClassWithoutInitializingProxy(o)
    }

    @Override
    boolean isProxy(Object o) {
        // instanceof checks first: the Groovy metaClass probe initializes a proxy whose
        // interceptor is not Groovy-aware
        return o instanceof EntityProxy ||
                o instanceof HibernateProxy ||
                o instanceof PersistentCollection ||
                GroovyProxyInterceptorLogic.getProxyInstanceMetaClass(o) != null
    }

    @Override
    void initialize(Object o) {
        if (o instanceof EntityProxy) {
            o.initialize()
            return
        }

        ProxyInstanceMetaClass proxyMc = GroovyProxyInterceptorLogic.getProxyInstanceMetaClass(o)
        if (proxyMc != null) {
            proxyMc.getProxyTarget()
        }
        else {
            Hibernate.initialize(o)
        }
    }

    @Override
    def <T> T createProxy(Session session, Class<T> type, Serializable key) {
        if (session.getNativeInterface() instanceof GrailsHibernateTemplate) {
            GrailsHibernateTemplate ght = (GrailsHibernateTemplate) session.getNativeInterface()
            org.hibernate.SessionFactory sessionFactory = ght.getSessionFactory()
            if (sessionFactory != null) {
                return Hibernate.createDetachedProxy(sessionFactory, type, key)
            }
        }
        throw new IllegalStateException(
                'Could not obtain native Hibernate SessionFactory from Session#getNativeInterface()')
    }

    @Override
    def <T, K extends Serializable> T createProxy(
            Session session, AssociationQueryExecutor<K, T> executor, K associationKey) {
        throw new UnsupportedOperationException(
                'createProxy via AssociationQueryExecutor not supported in HibernateProxyHandler')
    }

    HibernateProxy getAssociationProxy(Object obj, String associationName) {
        try {
            Object proxy = ClassPropertyFetcher.getInstancePropertyValue(obj, associationName)
            return (proxy instanceof HibernateProxy) ? (HibernateProxy) proxy : null
        }
        catch (RuntimeException e) {
            return null
        }
    }

    @Deprecated
    Object unwrapIfProxy(Object instance) {
        return unwrap(instance)
    }

    @Deprecated
    Object unwrapProxy(Object proxy) {
        return unwrap(proxy)
    }

}
