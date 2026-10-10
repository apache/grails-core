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
package org.grails.orm.hibernate.cfg

import groovy.transform.CompileStatic
import org.hibernate.FlushMode
import org.hibernate.Hibernate
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.engine.spi.EntityEntry
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.engine.spi.Status
import org.hibernate.internal.util.StringHelper
import org.hibernate.proxy.HibernateProxy
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.springframework.transaction.support.TransactionSynchronizationManager

import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.proxy.HibernateProxyHandler
import org.grails.orm.hibernate.query.HibernateQueryArgument
import org.grails.orm.hibernate.support.HibernateRuntimeUtils

import java.lang.annotation.Annotation

/**
 * Utility methods for configuring Hibernate inside Grails.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
@CompileStatic
class GrailsHibernateUtil extends HibernateRuntimeUtils {

    /** @deprecated Use {@link org.grails.orm.hibernate.query.HibernateQueryArgument#FETCH_SIZE} */
    @Deprecated(since = '8.0', forRemoval = true)
    static final String ARGUMENT_FETCH_SIZE = HibernateQueryArgument.FETCH_SIZE.value()
    /** @deprecated Use {@link org.grails.orm.hibernate.query.HibernateQueryArgument#TIMEOUT} */
    @Deprecated(since = '8.0', forRemoval = true)
    static final String ARGUMENT_TIMEOUT = HibernateQueryArgument.TIMEOUT.value()
    /** @deprecated Use {@link org.grails.orm.hibernate.query.HibernateQueryArgument#READ_ONLY} */
    @Deprecated(since = '8.0', forRemoval = true)
    static final String ARGUMENT_READ_ONLY = HibernateQueryArgument.READ_ONLY.value()
    /** @deprecated Use {@link org.grails.orm.hibernate.query.HibernateQueryArgument#FLUSH_MODE} */
    @Deprecated(since = '8.0', forRemoval = true)
    static final String ARGUMENT_FLUSH_MODE = HibernateQueryArgument.FLUSH_MODE.value()
    /** @deprecated Use {@link org.grails.orm.hibernate.query.HibernateQueryArgument#MAX} */
    @Deprecated(since = '8.0', forRemoval = true)
    static final String ARGUMENT_MAX = HibernateQueryArgument.MAX.value()
    /** @deprecated Use {@link org.grails.orm.hibernate.query.HibernateQueryArgument#OFFSET} */
    @Deprecated(since = '8.0', forRemoval = true)
    static final String ARGUMENT_OFFSET = HibernateQueryArgument.OFFSET.value()
    /** @deprecated Use {@link org.grails.orm.hibernate.query.HibernateQueryArgument#ORDER} */
    @Deprecated(since = '8.0', forRemoval = true)
    static final String ARGUMENT_ORDER = HibernateQueryArgument.ORDER.value()
    /** @deprecated Use {@link org.grails.orm.hibernate.query.HibernateQueryArgument#SORT} */
    @Deprecated(since = '8.0', forRemoval = true)
    static final String ARGUMENT_SORT = HibernateQueryArgument.SORT.value()
    /** @deprecated Use {@link org.grails.orm.hibernate.query.HibernateQueryArgument#ORDER_DESC} */
    @Deprecated(since = '8.0', forRemoval = true)
    static final String ORDER_DESC = HibernateQueryArgument.ORDER_DESC.value()
    /** @deprecated Use {@link org.grails.orm.hibernate.query.HibernateQueryArgument#ORDER_ASC} */
    @Deprecated(since = '8.0', forRemoval = true)
    static final String ORDER_ASC = HibernateQueryArgument.ORDER_ASC.value()
    /** @deprecated Use {@link org.grails.orm.hibernate.query.HibernateQueryArgument#FETCH} */
    @Deprecated(since = '8.0', forRemoval = true)
    static final String ARGUMENT_FETCH = HibernateQueryArgument.FETCH.value()
    /** @deprecated Use {@link org.grails.orm.hibernate.query.HibernateQueryArgument#IGNORE_CASE} */
    @Deprecated(since = '8.0', forRemoval = true)
    static final String ARGUMENT_IGNORE_CASE = HibernateQueryArgument.IGNORE_CASE.value()
    /** @deprecated Use {@link org.grails.orm.hibernate.query.HibernateQueryArgument#CACHE} */
    @Deprecated(since = '8.0', forRemoval = true)
    static final String ARGUMENT_CACHE = HibernateQueryArgument.CACHE.value()
    /** @deprecated Use {@link org.grails.orm.hibernate.query.HibernateQueryArgument#LOCK} */
    @Deprecated(since = '8.0', forRemoval = true)
    static final String ARGUMENT_LOCK = HibernateQueryArgument.LOCK.value()

    protected static final Logger LOG = LoggerFactory.getLogger(GrailsHibernateUtil)

    private static final HibernateProxyHandler DEFAULT_PROXY_HANDLER = new HibernateProxyHandler()

    /**
     * Sets the target object to read-only using the given SessionFactory instance. This avoids
     * Hibernate performing any dirty checking on the object
     *
     * @see #setObjectToReadWrite(Object, org.hibernate.SessionFactory)
     * @param target The target object
     * @param sessionFactory The SessionFactory instance
     */
    @SuppressWarnings('PMD.CloseResource')
    static void setObjectToReadyOnly(Object target, SessionFactory sessionFactory) {
        Object resource = TransactionSynchronizationManager.getResource(sessionFactory)
        if (resource != null) {
            Session session = sessionFactory.currentSession
            if (canModifyReadWriteState(session, target)) {
                Object targetToUse = target
                if (targetToUse instanceof HibernateProxy) {
                    targetToUse = targetToUse.getHibernateLazyInitializer().getImplementation()
                }
                session.setReadOnly(targetToUse, true)
                session.setHibernateFlushMode(FlushMode.MANUAL)
            }
        }
    }

    private static boolean canModifyReadWriteState(Session session, Object target) {
        return session.contains(target) && Hibernate.isInitialized(target)
    }

    /**
     * Sets the target object to read-write, allowing Hibernate to dirty check it and auto-flush
     * changes.
     *
     * @see #setObjectToReadyOnly(Object, org.hibernate.SessionFactory)
     * @param target The target object
     * @param sessionFactory The SessionFactory instance
     */
    @SuppressWarnings(['PMD.CloseResource', 'PMD.DataflowAnomalyAnalysis'])
    static void setObjectToReadWrite(final Object target, SessionFactory sessionFactory) {
        Session session = sessionFactory.currentSession
        if (!canModifyReadWriteState(session, target)) {
            return
        }

        SessionImplementor sessionImpl = (SessionImplementor) session
        EntityEntry ee = sessionImpl.persistenceContext.getEntry(target)

        if (ee == null || ee.status != Status.READ_ONLY) {
            return
        }

        Object actualTarget = target
        if (target instanceof HibernateProxy) {
            actualTarget = target.getHibernateLazyInitializer().getImplementation()
        }

        session.setReadOnly(actualTarget, false)
        session.setHibernateFlushMode(FlushMode.AUTO)
        incrementVersion(target)
    }

    /**
     * Increments the entities version number in order to force an update
     *
     * @param target The target entity
     */
    static void incrementVersion(Object target) {
        MetaClass metaClass = GroovySystem.metaClassRegistry.getMetaClass(target.class)
        if (metaClass.hasProperty(target, GormProperties.VERSION) != null) {
            Object version = metaClass.getProperty(target, GormProperties.VERSION)
            if (version instanceof Long) {
                Long newVersion = version + 1
                metaClass.setProperty(target, GormProperties.VERSION, newVersion)
            }
        }
    }

    /**
     * Ensures the meta class is correct for a given class
     *
     * @param target The GroovyObject
     * @param persistentClass The persistent class
     */
    static void ensureCorrectGroovyMetaClass(Object target, Class<?> persistentClass) {
        if (target instanceof GroovyObject) {
            if (!target.metaClass.theClass.equals(persistentClass)) {
                target.metaClass = GroovySystem.metaClassRegistry.getMetaClass(persistentClass)
            }
        }
    }

    /**
     * Unwraps and initializes a HibernateProxy.
     *
     * @param proxy The proxy
     * @return the unproxied instance
     */
    static Object unwrapProxy(HibernateProxy proxy) {
        return unwrapProxy(proxy, DEFAULT_PROXY_HANDLER)
    }

    static Object unwrapProxy(HibernateProxy proxy, HibernateProxyHandler handler) {
        return handler.unwrap(proxy)
    }

    /**
     * Returns the proxy for a given association or null if it is not proxied
     *
     * @param obj The object
     * @param associationName The named assoication
     * @return A proxy
     */
    static HibernateProxy getAssociationProxy(Object obj, String associationName) {
        return getAssociationProxy(obj, associationName, DEFAULT_PROXY_HANDLER)
    }

    static HibernateProxy getAssociationProxy(Object obj, String associationName, HibernateProxyHandler handler) {
        return handler.getAssociationProxy(obj, associationName)
    }

    /**
     * Checks whether an associated property is initialized and returns true if it is
     *
     * @param obj The name of the object
     * @param associationName The name of the association
     * @return true if is initialized
     */
    static boolean isInitialized(Object obj, String associationName) {
        return isInitialized(obj, associationName, DEFAULT_PROXY_HANDLER)
    }

    static boolean isInitialized(Object obj, String associationName, HibernateProxyHandler handler) {
        return handler.isInitialized(obj, associationName)
    }

    /**
     * Unproxies a HibernateProxy. If the proxy is uninitialized, it automatically triggers an
     * initialization. In case the supplied object is null or not a proxy, the object will be returned
     * as-is.
     */
    static Object unwrapIfProxy(Object instance) {
        return unwrapIfProxy(instance, DEFAULT_PROXY_HANDLER)
    }

    static Object unwrapIfProxy(Object instance, HibernateProxyHandler handler) {
        return handler.unwrap(instance)
    }

    static boolean isMappedWithHibernate(PersistentEntity domainClass) {
        return domainClass instanceof GrailsHibernatePersistentEntity
    }

    static String qualify(final String prefix, final String name) {
        return StringHelper.qualify(prefix, name)
    }

    static boolean isNotEmpty(final String string) {
        return StringHelper.isNotEmpty(string)
    }

    static String unqualify(final String qualifiedName) {
        return StringHelper.unqualify(qualifiedName)
    }

    static boolean isDomainClass(Class<?> clazz) {
        if (GormEntity.isAssignableFrom(clazz)) {
            return true
        }

        // it's not a closure
        if (Closure.isAssignableFrom(clazz)) {
            return false
        }

        if (clazz.isEnum()) return false

        Annotation[] allAnnotations = clazz.annotations
        for (Annotation annotation : allAnnotations) {
            Class<? extends Annotation> type = annotation.annotationType()
            String annName = type.name
            if ('grails.persistence.Entity' == annName) {
                return true
            }
            if (Entity == type) {
                return true
            }
        }

        Class<?> testClass = clazz
        while (testClass != null && GroovyObject != testClass && Object != testClass) {
            try {
                // make sure the identify and version field exist
                testClass.getDeclaredField(GormProperties.IDENTITY)
                testClass.getDeclaredField(GormProperties.VERSION)

                // passes all conditions return true
                return true
            }
            catch (SecurityException e) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace('Security exception checking for GORM fields: {}', e.message)
                }
            }
            catch (NoSuchFieldException e) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace('Field not found checking for GORM fields: {}', e.message)
                }
            }
            testClass = testClass.superclass
        }

        return false
    }

}
