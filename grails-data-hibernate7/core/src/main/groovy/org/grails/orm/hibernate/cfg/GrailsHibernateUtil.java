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
package org.grails.orm.hibernate.cfg;

import java.lang.annotation.Annotation;

import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;

import org.grails.datastore.gorm.GormEntity;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.proxy.HibernateProxyHandler;
import org.grails.orm.hibernate.support.HibernateRuntimeUtils;
import org.hibernate.FlushMode;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.engine.spi.Status;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.proxy.HibernateProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import grails.gorm.annotation.Entity;

/**
 * Utility methods for configuring Hibernate inside Grails.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
public class GrailsHibernateUtil extends HibernateRuntimeUtils {
  protected static final Logger LOG = LoggerFactory.getLogger(GrailsHibernateUtil.class);

  public static final String ARGUMENT_FETCH_SIZE = "fetchSize";
  public static final String ARGUMENT_TIMEOUT = "timeout";
  public static final String ARGUMENT_READ_ONLY = "readOnly";
  public static final String ARGUMENT_FLUSH_MODE = "flushMode";
  public static final String ARGUMENT_MAX = "max";
  public static final String ARGUMENT_OFFSET = "offset";
  public static final String ARGUMENT_ORDER = "order";
  public static final String ARGUMENT_SORT = "sort";
  public static final String ORDER_DESC = "desc";
  public static final String ORDER_ASC = "asc";
  public static final String ARGUMENT_FETCH = "fetch";
  public static final String ARGUMENT_IGNORE_CASE = "ignoreCase";
  public static final String ARGUMENT_CACHE = "cache";
  public static final String ARGUMENT_LOCK = "lock";
  public static final Class<?>[] EMPTY_CLASS_ARRAY = {};

  private static HibernateProxyHandler proxyHandler = new HibernateProxyHandler();

  /**
   * Sets the target object to read-only using the given SessionFactory instance. This avoids
   * Hibernate performing any dirty checking on the object
   *
   * @see #setObjectToReadWrite(Object, org.hibernate.SessionFactory)
   * @param target The target object
   * @param sessionFactory The SessionFactory instance
   */
  public static void setObjectToReadyOnly(Object target, SessionFactory sessionFactory) {
    Object resource = TransactionSynchronizationManager.getResource(sessionFactory);
    if (resource != null) {
      Session session = sessionFactory.getCurrentSession();
      if (canModifyReadWriteState(session, target)) {
        if (target instanceof HibernateProxy) {
          target = ((HibernateProxy) target).getHibernateLazyInitializer().getImplementation();
        }
        session.setReadOnly(target, true);
        session.setHibernateFlushMode(FlushMode.MANUAL);
      }
    }
  }

  private static boolean canModifyReadWriteState(Session session, Object target) {
    return session.contains(target) && Hibernate.isInitialized(target);
  }

  /**
   * Sets the target object to read-write, allowing Hibernate to dirty check it and auto-flush
   * changes.
   *
   * @see #setObjectToReadyOnly(Object, org.hibernate.SessionFactory)
   * @param target The target object
   * @param sessionFactory The SessionFactory instance
   */
  public static void setObjectToReadWrite(final Object target, SessionFactory sessionFactory) {
    Session session = sessionFactory.getCurrentSession();
    if (!canModifyReadWriteState(session, target)) {
      return;
    }

    SessionImplementor sessionImpl = (SessionImplementor) session;
    EntityEntry ee = sessionImpl.getPersistenceContext().getEntry(target);

    if (ee == null || ee.getStatus() != Status.READ_ONLY) {
      return;
    }

    Object actualTarget = target;
    if (target instanceof HibernateProxy) {
      actualTarget = ((HibernateProxy) target).getHibernateLazyInitializer().getImplementation();
    }

    session.setReadOnly(actualTarget, false);
    session.setHibernateFlushMode(FlushMode.AUTO);
    incrementVersion(target);
  }

  /**
   * Increments the entities version number in order to force an update
   *
   * @param target The target entity
   */
  public static void incrementVersion(Object target) {
    MetaClass metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(target.getClass());
    if (metaClass.hasProperty(target, GormProperties.VERSION) != null) {
      Object version = metaClass.getProperty(target, GormProperties.VERSION);
      if (version instanceof Long) {
        Long newVersion = (Long) version + 1;
        metaClass.setProperty(target, GormProperties.VERSION, newVersion);
      }
    }
  }

  /**
   * Ensures the meta class is correct for a given class
   *
   * @param target The GroovyObject
   * @param persistentClass The persistent class
   */
  @Deprecated
  public static void ensureCorrectGroovyMetaClass(Object target, Class<?> persistentClass) {
    if (target instanceof GroovyObject) {
      GroovyObject go = ((GroovyObject) target);
      if (!go.getMetaClass().getTheClass().equals(persistentClass)) {
        go.setMetaClass(GroovySystem.getMetaClassRegistry().getMetaClass(persistentClass));
      }
    }
  }

  /**
   * Unwraps and initializes a HibernateProxy.
   *
   * @param proxy The proxy
   * @return the unproxied instance
   */
  public static Object unwrapProxy(HibernateProxy proxy) {
    return proxyHandler.unwrap(proxy);
  }

  /**
   * Returns the proxy for a given association or null if it is not proxied
   *
   * @param obj The object
   * @param associationName The named assoication
   * @return A proxy
   */
  public static HibernateProxy getAssociationProxy(Object obj, String associationName) {
    return proxyHandler.getAssociationProxy(obj, associationName);
  }

  /**
   * Checks whether an associated property is initialized and returns true if it is
   *
   * @param obj The name of the object
   * @param associationName The name of the association
   * @return true if is initialized
   */
  public static boolean isInitialized(Object obj, String associationName) {
    return proxyHandler.isInitialized(obj, associationName);
  }

  /**
   * Unproxies a HibernateProxy. If the proxy is uninitialized, it automatically triggers an
   * initialization. In case the supplied object is null or not a proxy, the object will be returned
   * as-is.
   */
  public static Object unwrapIfProxy(Object instance) {
    return proxyHandler.unwrap(instance);
  }

  public static boolean isMappedWithHibernate(PersistentEntity domainClass) {
    return domainClass instanceof GrailsHibernatePersistentEntity;
  }

  public static String qualify(final String prefix, final String name) {
    return StringHelper.qualify(prefix, name);
  }

  public static boolean isNotEmpty(final String string) {
    return StringHelper.isNotEmpty(string);
  }

  public static String unqualify(final String qualifiedName) {
    return StringHelper.unqualify(qualifiedName);
  }

    public static boolean isDomainClass(Class clazz) {
      if (GormEntity.class.isAssignableFrom(clazz)) {
        return true;
      }

      // it's not a closure
      if (Closure.class.isAssignableFrom(clazz)) {
        return false;
      }

      if (((Class<?>) clazz).isEnum()) return false;

      Annotation[] allAnnotations = ((Class<?>) clazz).getAnnotations();
      for (Annotation annotation : allAnnotations) {
        Class<? extends Annotation> type = annotation.annotationType();
        String annName = type.getName();
        if (annName.equals("grails.persistence.Entity")) {
          return true;
        }
        if (type.equals(Entity.class)) {
          return true;
        }
      }

      Class<?> testClass = (Class<?>) clazz;
      while (testClass != null
          && !testClass.equals(GroovyObject.class)
          && !testClass.equals(Object.class)) {
        try {
          // make sure the identify and version field exist
          testClass.getDeclaredField(GormProperties.IDENTITY);
          testClass.getDeclaredField(GormProperties.VERSION);

          // passes all conditions return true
          return true;
        } catch (SecurityException e) {
          // ignore
        } catch (NoSuchFieldException e) {
          // ignore
        }
        testClass = testClass.getSuperclass();
      }

      return false;
    }
}
