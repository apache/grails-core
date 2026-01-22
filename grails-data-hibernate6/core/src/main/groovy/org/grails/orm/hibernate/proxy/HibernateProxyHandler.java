/*
 * Copyright 2004-2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.orm.hibernate.proxy;

import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.engine.AssociationQueryExecutor;
import org.grails.datastore.mapping.proxy.EntityProxy;
import org.grails.datastore.mapping.proxy.ProxyFactory;
import org.grails.datastore.mapping.proxy.ProxyHandler;
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher;
import org.hibernate.Hibernate;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.HibernateProxyHelper;

import java.io.Serializable;

import groovy.lang.GroovyObject;
import groovy.lang.MetaClass;
import org.codehaus.groovy.runtime.HandleMetaClass;
import org.grails.datastore.gorm.proxy.ProxyInstanceMetaClass;
import org.grails.orm.hibernate.GrailsHibernateTemplate;

/**
 * Implementation of the ProxyHandler interface for Hibernate using org.hibernate.Hibernate
 * and HibernateProxyHelper where possible.
 *
 * @author Graeme Rocher
 * @since 1.2.2
 */
public class HibernateProxyHandler implements ProxyHandler, ProxyFactory {

    /**
     * Check if the proxy or persistent collection is initialized.
     * {@inheritDoc}
     */
    @Override
    public boolean isInitialized(Object o) {
        if (o instanceof EntityProxy) {
            return ((EntityProxy)o).isInitialized();
        }
        ProxyInstanceMetaClass proxyMc = getProxyInstanceMetaClass(o);
        if (proxyMc != null) {
            return proxyMc.isProxyInitiated();
        }
        return Hibernate.isInitialized(o);
    }

    /**
     * Check if an association proxy or persistent collection is initialized.
     * {@inheritDoc}
     */
    @Override
    public boolean isInitialized(Object obj, String associationName) {
        try {
            Object proxy = ClassPropertyFetcher.getInstancePropertyValue(obj, associationName);
            return isInitialized(proxy);
        }
        catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Unproxies a HibernateProxy. If the proxy is uninitialized, it automatically triggers an initialization.
     * In case the supplied object is null or not a proxy, the object will be returned as-is.
     * {@inheritDoc}
     * @see Hibernate#unproxy
     */
    @Override
    public Object unwrap(Object object) {
        if (object instanceof EntityProxy) {
            return ((EntityProxy)object).getTarget();
        }
        ProxyInstanceMetaClass proxyMc = getProxyInstanceMetaClass(object);
        if (proxyMc != null) {
            return proxyMc.getProxyTarget();
        }
        if (object instanceof PersistentCollection) {
            initialize(object);
            return object;
        }
        return Hibernate.unproxy(object);
    }

    /**
     * {@inheritDoc}
     * @see org.hibernate.proxy.AbstractLazyInitializer#getIdentifier
     */
    @Override
    public Serializable getIdentifier(Object o) {
        if (o instanceof EntityProxy) {
            return ((EntityProxy)o).getProxyKey();
        }
        ProxyInstanceMetaClass proxyMc = getProxyInstanceMetaClass(o);
        if (proxyMc != null) {
            return proxyMc.getKey();
        }
        if (o instanceof HibernateProxy) {
            return (Serializable) ((HibernateProxy)o).getHibernateLazyInitializer().getIdentifier();
        }
        else {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     * @see HibernateProxyHelper#getClassWithoutInitializingProxy
     */
    @Override
    public Class<?> getProxiedClass(Object o) {
        return HibernateProxyHelper.getClassWithoutInitializingProxy(o);
    }

    /**
     * calls unwrap which calls unproxy
     * @see #unwrap(Object)
     * @deprecated use unwrap
     */
    @Deprecated
    public Object unwrapIfProxy(Object instance) {
        return unwrap(instance);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isProxy(Object o) {
        if (getProxyInstanceMetaClass(o) != null) {
            return true;
        }
        return (o instanceof EntityProxy) || (o instanceof HibernateProxy)  || (o instanceof PersistentCollection);
    }

    /**
     * Force initialization of a proxy or persistent collection.
     * {@inheritDoc}
     */
    @Override
    public void initialize(Object o) {
        if (o instanceof EntityProxy) {
            ((EntityProxy)o).initialize();
        }
        else {
            ProxyInstanceMetaClass proxyMc = getProxyInstanceMetaClass(o);
            if (proxyMc != null) {
                proxyMc.getProxyTarget();
            }
            else {
                Hibernate.initialize(o);
            }
        }
    }

    private ProxyInstanceMetaClass getProxyInstanceMetaClass(Object o) {
        if (o instanceof GroovyObject) {
            MetaClass mc = ((GroovyObject) o).getMetaClass();
            if (mc instanceof HandleMetaClass) {
                mc = ((HandleMetaClass) mc).getAdaptee();
            }
            if (mc instanceof ProxyInstanceMetaClass) {
                return (ProxyInstanceMetaClass) mc;
            }
        }
        return null;
    }

    @Override
    public <T> T createProxy(Session session, Class<T> type, Serializable key) {
        org.hibernate.Session hibSession = null;
        if (session.getNativeInterface() instanceof GrailsHibernateTemplate grailsHibernateTemplate) {
            hibSession = grailsHibernateTemplate.getSession();
        }
        if (hibSession == null) {
            throw new IllegalStateException("Could not obtain native Hibernate Session from Session#getNativeInterface()");
        }
        return (T) hibSession.getReference(type, key);
    }

    @Override
    public <T, K extends Serializable> T createProxy(Session session, AssociationQueryExecutor<K, T> executor, K associationKey) {
        throw new UnsupportedOperationException("createProxy not supported in HibernateProxyHandler");
    }

    /**
     * @deprecated use unwrap
     */
    @Deprecated
    public Object unwrapProxy(Object proxy) {
        return unwrap(proxy);
    }

    /**
     * returns the proxy for an association. returns null if its not a proxy.
     * Note: Only used in a test. Deprecate?
     */
    public HibernateProxy getAssociationProxy(Object obj, String associationName) {
        try {
            Object proxy = ClassPropertyFetcher.getInstancePropertyValue(obj, associationName);
            if (proxy instanceof HibernateProxy) {
                return (HibernateProxy) proxy;
            }
            return null;
        }
        catch (RuntimeException e) {
            return null;
        }
    }
}
