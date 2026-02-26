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
package org.grails.orm.hibernate.proxy;

import groovy.lang.GroovyObject;
import groovy.lang.MetaClass;
import java.io.Serializable;
import org.codehaus.groovy.runtime.HandleMetaClass;
import org.grails.datastore.gorm.proxy.ProxyInstanceMetaClass;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.engine.AssociationQueryExecutor;
import org.grails.datastore.mapping.proxy.EntityProxy;
import org.grails.datastore.mapping.proxy.ProxyFactory;
import org.grails.datastore.mapping.proxy.ProxyHandler;
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher;
import org.grails.orm.hibernate.GrailsHibernateTemplate;
import org.hibernate.Hibernate;
import org.hibernate.collection.spi.LazyInitializable;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.HibernateProxyHelper;

/**
 * Implementation of the ProxyHandler interface for Hibernate 6 using Java 17 features.
 *
 * @author Graeme Rocher
 * @since 1.2.2
 */
@SuppressWarnings("PMD.CloseResource")
public class HibernateProxyHandler implements ProxyHandler, ProxyFactory {

  @Override
  public boolean isInitialized(Object o) {
    if (o == null) return false;

    if (o instanceof HibernateProxy hp) {
      return !hp.getHibernateLazyInitializer().isUninitialized();
    }
    if (o instanceof EntityProxy ep) {
      return ep.isInitialized();
    }
    if (o instanceof LazyInitializable li) {
      return li.wasInitialized();
    }

    return Hibernate.isInitialized(o);
  }

  @Override
  public boolean isInitialized(Object obj, String associationName) {
    try {
      Object proxy = ClassPropertyFetcher.getInstancePropertyValue(obj, associationName);
      return isInitialized(proxy);
    } catch (RuntimeException e) {
      return false;
    }
  }

  @Override
  public Object unwrap(Object object) {
    if (object instanceof EntityProxy ep) {
      return ep.getTarget();
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

  @Override
  public Serializable getIdentifier(Object o) {
    if (o instanceof EntityProxy ep) {
      return ep.getProxyKey();
    }

    ProxyInstanceMetaClass proxyMc = getProxyInstanceMetaClass(o);
    if (proxyMc != null) {
      return proxyMc.getKey();
    }

    if (o instanceof HibernateProxy hp) {
      return (Serializable) hp.getHibernateLazyInitializer().getIdentifier();
    }

    return null;
  }

  @Override
  public Class<?> getProxiedClass(Object o) {
    return HibernateProxyHelper.getClassWithoutInitializingProxy(o);
  }

  @Override
  public boolean isProxy(Object o) {
    return getProxyInstanceMetaClass(o) != null
        || o instanceof EntityProxy
        || o instanceof HibernateProxy
        || o instanceof PersistentCollection;
  }

  @Override
  public void initialize(Object o) {
    if (o instanceof EntityProxy ep) {
      ep.initialize();
      return;
    }

    ProxyInstanceMetaClass proxyMc = getProxyInstanceMetaClass(o);
    if (proxyMc != null) {
      proxyMc.getProxyTarget();
    } else {
      Hibernate.initialize(o);
    }
  }

  private ProxyInstanceMetaClass getProxyInstanceMetaClass(Object o) {
    if (o instanceof GroovyObject go) {
      MetaClass mc = go.getMetaClass();
      if (mc instanceof HandleMetaClass hmc) {
        mc = hmc.getAdaptee();
      }
      if (mc instanceof ProxyInstanceMetaClass pmc) {
        return pmc;
      }
    }
    return null;
  }

  @Override
  public <T> T createProxy(Session session, Class<T> type, Serializable key) {
    if (session.getNativeInterface() instanceof GrailsHibernateTemplate ght) {
      org.hibernate.Session hibSession = ght.getSession();
      if (hibSession != null) {
        return hibSession.getReference(type, key);
      }
    }
    throw new IllegalStateException(
        "Could not obtain native Hibernate Session from Session#getNativeInterface()");
  }

  @Override
  public <T, K extends Serializable> T createProxy(
      Session session, AssociationQueryExecutor<K, T> executor, K associationKey) {
    throw new UnsupportedOperationException(
        "createProxy via AssociationQueryExecutor not supported in HibernateProxyHandler");
  }

  public HibernateProxy getAssociationProxy(Object obj, String associationName) {
    try {
      Object proxy = ClassPropertyFetcher.getInstancePropertyValue(obj, associationName);
      return (proxy instanceof HibernateProxy hp) ? hp : null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  @Deprecated
  public Object unwrapIfProxy(Object instance) {
    return unwrap(instance);
  }

  @Deprecated
  public Object unwrapProxy(Object proxy) {
    return unwrap(proxy);
  }
}
