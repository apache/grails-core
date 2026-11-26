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

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import groovy.lang.GroovyObject;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;

import org.hibernate.Criteria;
import org.hibernate.FetchMode;
import org.hibernate.FlushMode;
import org.hibernate.Hibernate;
import org.hibernate.LockMode;
import org.hibernate.MappingException;
import org.hibernate.QueryException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Order;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.engine.spi.Status;
import org.hibernate.internal.CriteriaImpl;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.sql.JoinType;
import org.hibernate.type.CollectionType;
import org.hibernate.type.EntityType;
import org.hibernate.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.convert.ConversionService;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Embedded;
import org.grails.datastore.mapping.model.types.ToMany;
import org.grails.datastore.mapping.reflect.ClassUtils;
import org.grails.orm.hibernate.AbstractHibernateDatastore;
import org.grails.orm.hibernate.datasource.MultipleDataSourceSupport;
import org.grails.orm.hibernate.proxy.HibernateProxyHandler;
import org.grails.orm.hibernate.support.HibernateRuntimeUtils;

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

    public static void populateArgumentsForCriteria(AbstractHibernateDatastore datastore, Class<?> targetClass, Criteria c, Map argMap, ConversionService conversionService) {
        populateArgumentsForCriteria(datastore, targetClass, c, argMap, conversionService, true);
    }

    /**
     * Populates criteria arguments for the given target class and arguments map
     *
     * @param datastore the GrailsApplication instance
     * @param targetClass The target class
     * @param c The criteria instance
     * @param argMap The arguments map
     */
    @SuppressWarnings("rawtypes")
    public static void populateArgumentsForCriteria(AbstractHibernateDatastore datastore, Class<?> targetClass, Criteria c, Map argMap, ConversionService conversionService, boolean useDefaultMapping) {
        Integer maxParam = null;
        Integer offsetParam = null;
        if (argMap.containsKey(ARGUMENT_MAX)) {
            maxParam = conversionService.convert(argMap.get(ARGUMENT_MAX), Integer.class);
        }
        if (argMap.containsKey(ARGUMENT_OFFSET)) {
            offsetParam = conversionService.convert(argMap.get(ARGUMENT_OFFSET), Integer.class);
        }
        if (argMap.containsKey(ARGUMENT_FETCH_SIZE)) {
            c.setFetchSize(conversionService.convert(argMap.get(ARGUMENT_FETCH_SIZE), Integer.class));
        }
        if (argMap.containsKey(ARGUMENT_TIMEOUT)) {
            c.setTimeout(conversionService.convert(argMap.get(ARGUMENT_TIMEOUT), Integer.class));
        }
        if (argMap.containsKey(ARGUMENT_FLUSH_MODE)) {
            c.setFlushMode(convertFlushMode(argMap.get(ARGUMENT_FLUSH_MODE)));
        }
        if (argMap.containsKey(ARGUMENT_READ_ONLY)) {
            c.setReadOnly(ClassUtils.getBooleanFromMap(ARGUMENT_READ_ONLY, argMap));
        }
        String orderParam = (String) argMap.get(ARGUMENT_ORDER);
        Object fetchObj = argMap.get(ARGUMENT_FETCH);
        if (fetchObj instanceof Map) {
            Map fetch = (Map) fetchObj;
            for (Object o : fetch.keySet()) {
                String associationName = (String) o;
                c.setFetchMode(associationName, getFetchMode(fetch.get(associationName)));
            }
        }

        final int max = maxParam == null ? -1 : maxParam;
        final int offset = offsetParam == null ? -1 : offsetParam;
        if (max > -1) {
            c.setMaxResults(max);
        }
        if (offset > -1) {
            c.setFirstResult(offset);
        }
        if (ClassUtils.getBooleanFromMap(ARGUMENT_LOCK, argMap)) {
            c.setLockMode(LockMode.PESSIMISTIC_WRITE);
            c.setCacheable(false);
        }
        else {
            if (argMap.containsKey(ARGUMENT_CACHE)) {
                c.setCacheable(ClassUtils.getBooleanFromMap(ARGUMENT_CACHE, argMap));
            } else {
                cacheCriteriaByMapping(targetClass, c);
            }
        }

        final Object sortObj = argMap.get(ARGUMENT_SORT);
        if (sortObj != null) {
            boolean ignoreCase = true;
            Object caseArg = argMap.get(ARGUMENT_IGNORE_CASE);
            if (caseArg instanceof Boolean) {
                ignoreCase = (Boolean) caseArg;
            }
            if (sortObj instanceof Map) {
                Map sortMap = (Map) sortObj;
                for (Object sort : sortMap.keySet()) {
                    final String order = ORDER_DESC.equalsIgnoreCase((String) sortMap.get(sort)) ? ORDER_DESC : ORDER_ASC;
                    addOrderPossiblyNested(datastore, c, targetClass, (String) sort, order, ignoreCase);
                }
            } else {
                final String sort = (String) sortObj;
                final String order = ORDER_DESC.equalsIgnoreCase(orderParam) ? ORDER_DESC : ORDER_ASC;
                addOrderPossiblyNested(datastore, c, targetClass, sort, order, ignoreCase);
            }
        }
        else if (useDefaultMapping) {
            Mapping m = GrailsDomainBinder.getMapping(targetClass);
            if (m != null) {
                Map sortMap = m.getSort().getNamesAndDirections();
                for (Object sort : sortMap.keySet()) {
                    final String order = ORDER_DESC.equalsIgnoreCase((String) sortMap.get(sort)) ? ORDER_DESC : ORDER_ASC;
                    addOrderPossiblyNested(datastore, c, targetClass, (String) sort, order, true);
                }
            }
        }
    }

    /**
     * @deprecated No replacement. Do not use.
     */
    @Deprecated
    public static void setBinder(GrailsDomainBinder binder) {
    }

    /**
     * Populates criteria arguments for the given target class and arguments map
     *
     * @param targetClass The target class
     * @param c The criteria instance
     * @param argMap The arguments map
     *
     */
    @Deprecated
    @SuppressWarnings("rawtypes")
    public static void populateArgumentsForCriteria(Class<?> targetClass, Criteria c, Map argMap, ConversionService conversionService) {
        populateArgumentsForCriteria(null, targetClass, c, argMap, conversionService);
    }

    @SuppressWarnings("rawtypes")
    public static void populateArgumentsForCriteria(Criteria c, Map argMap, ConversionService conversionService) {
        populateArgumentsForCriteria(null, null, c, argMap, conversionService);
    }

    private static FlushMode convertFlushMode(Object object) {
        if (object == null) {
            return null;
        }
        if (object instanceof FlushMode) {
            return (FlushMode) object;
        }
        return FlushMode.valueOf(String.valueOf(object));
    }

    /**
     * Whether a sort by a property of an association joins the association with a left join, so that the
     * sort keeps the instances whose association is null. That is the case when the
     * <code>grails.gorm.leftJoinAssociationSort</code> setting is enabled. The association is otherwise
     * joined with an inner join.
     *
     * @param entity an entity of the datastore that runs the query
     * @return whether a sort joins an association with a left join
     * @since 7.0.18
     */
    public static boolean isLeftJoinAssociationSort(PersistentEntity entity) {
        return entity != null && entity.getMappingContext() instanceof HibernateMappingContext &&
                ((HibernateMappingContext) entity.getMappingContext()).isLeftJoinAssociationSort();
    }

    /**
     * Add order to criteria, creating necessary subCriteria if nested sort property (ie. sort:'nested.property').
     */
    private static void addOrderPossiblyNested(AbstractHibernateDatastore datastore, Criteria c, Class<?> targetClass, String sort, String order, boolean ignoreCase) {
        int firstDotPos = sort.indexOf(".");
        if (firstDotPos == -1) {
            addOrder(c, sort, order, ignoreCase);
        } else { // nested property
            String sortHead = sort.substring(0, firstDotPos);
            String sortTail = sort.substring(firstDotPos + 1);
            if (datastore == null) {
                addOrderPossiblyNestedByHibernateMetadata(c, targetClass, sortHead, sortTail, sort, order, ignoreCase);
                return;
            }
            PersistentProperty property = getGrailsDomainClassProperty(datastore, targetClass, sortHead);
            if (property instanceof Embedded) {
                // embedded objects cannot reference entities (at time of writing), so no more recursion needed
                addOrder(c, sort, order, ignoreCase);
            } else if (property instanceof Association) {
                // with the leftJoinAssociationSort setting, a left join, so that the sort keeps the rows where the
                // association is null; a collection is joined with an inner join, since Hibernate would fetch it
                // through a left join
                Criteria subCriteria = c.createCriteria(sortHead, property instanceof ToMany || !isLeftJoinAssociationSort(property.getOwner()) ?
                        JoinType.INNER_JOIN : JoinType.LEFT_OUTER_JOIN);
                Class<?> propertyTargetClass = ((Association) property).getAssociatedEntity().getJavaClass();
                GrailsHibernateUtil.cacheCriteriaByMapping(datastore, propertyTargetClass, subCriteria);
                addOrderPossiblyNested(datastore, subCriteria, propertyTargetClass, sortTail, order, ignoreCase); // Recurse on nested sort
            }
        }
    }

    /**
     * A nested sort of a criteria that has no datastore, such as one of a criteria builder constructed with a session
     * factory: there is no mapping of the class, so the property is looked up in the Hibernate metadata of the session
     * factory of the criteria. An association is joined, as with a mapping, with an inner join (the
     * <code>grails.gorm.leftJoinAssociationSort</code> setting is part of the mapping), a component is sorted by the
     * path, and any other property is sorted as given, which Hibernate reports when it cannot resolve it.
     */
    private static void addOrderPossiblyNestedByHibernateMetadata(Criteria c, Class<?> targetClass, String sortHead, String sortTail,
            String sort, String order, boolean ignoreCase) {
        SessionFactoryImplementor factory = sessionFactoryOf(c);
        Type type = null;
        if (factory != null && targetClass != null) {
            try {
                type = factory.getMetamodel().entityPersister(targetClass).getPropertyType(sortHead);
            }
            catch (MappingException | QueryException ignored) {
                // not a property that Hibernate knows: sorted as given below, so Hibernate reports it
            }
        }
        Class<?> associatedClass = null;
        if (type instanceof EntityType) {
            associatedClass = type.getReturnedClass();
        }
        else if (type instanceof CollectionType) {
            Type elementType = ((CollectionType) type).getElementType(factory);
            associatedClass = elementType.isEntityType() ? elementType.getReturnedClass() : null;
        }
        if (associatedClass != null) {
            // a join of the association that the criteria already has, made by its block or by an earlier sort, is used
            Criteria subCriteria = existingSubcriteria(c, sortHead);
            if (subCriteria == null) {
                subCriteria = c.createCriteria(sortHead, JoinType.INNER_JOIN);
            }
            addOrderPossiblyNested(null, subCriteria, associatedClass, sortTail, order, ignoreCase);
        }
        else {
            addOrder(c, sort, order, ignoreCase);
        }
    }

    private static Criteria existingSubcriteria(Criteria c, String associationPath) {
        Criteria root = c;
        while (root instanceof CriteriaImpl.Subcriteria) {
            root = ((CriteriaImpl.Subcriteria) root).getParent();
        }
        if (root instanceof CriteriaImpl) {
            for (Iterator<CriteriaImpl.Subcriteria> i = ((CriteriaImpl) root).iterateSubcriteria(); i.hasNext();) {
                CriteriaImpl.Subcriteria subcriteria = i.next();
                if (subcriteria.getParent() == c && associationPath.equals(subcriteria.getPath())) {
                    return subcriteria;
                }
            }
        }
        return null;
    }

    private static SessionFactoryImplementor sessionFactoryOf(Criteria c) {
        Criteria current = c;
        while (current instanceof CriteriaImpl.Subcriteria) {
            current = ((CriteriaImpl.Subcriteria) current).getParent();
        }
        if (current instanceof CriteriaImpl && ((CriteriaImpl) current).getSession() != null) {
            return ((CriteriaImpl) current).getSession().getFactory();
        }
        return null;
    }

    /**
     * Add order directly to criteria.
     */
    private static void addOrder(Criteria c, String sort, String order, boolean ignoreCase) {
        if (ORDER_DESC.equals(order)) {
            c.addOrder(ignoreCase ? Order.desc(sort).ignoreCase() : Order.desc(sort));
        }
        else {
            c.addOrder(ignoreCase ? Order.asc(sort).ignoreCase() : Order.asc(sort));
        }
    }

    /**
     * Get hold of the GrailsDomainClassProperty represented by the targetClass' propertyName,
     * assuming targetClass corresponds to a GrailsDomainClass.
     */
    private static PersistentProperty getGrailsDomainClassProperty(AbstractHibernateDatastore datastore, Class<?> targetClass, String propertyName) {
        PersistentEntity grailsClass = datastore != null ? datastore.getMappingContext().getPersistentEntity(targetClass.getName()) : null;
        if (grailsClass == null) {
            throw new IllegalArgumentException("Unexpected: class is not a domain class:" + targetClass.getName());
        }
        return grailsClass.getPropertyByName(propertyName);
    }

    /**
     * Configures the criteria instance to cache based on the configured mapping.
     *
     * @param targetClass The target class
     * @param criteria The criteria
     */
    public static void cacheCriteriaByMapping(Class<?> targetClass, Criteria criteria) {
        Mapping m = GrailsDomainBinder.getMapping(targetClass);
        if (m != null && m.getCache() != null && m.getCache().getEnabled()) {
            criteria.setCacheable(true);
        }
    }

    public static void cacheCriteriaByMapping(AbstractHibernateDatastore datastore, Class<?> targetClass, Criteria criteria) {
        cacheCriteriaByMapping(targetClass, criteria);
    }

    /**
     * Retrieves the fetch mode for the specified instance; otherwise returns the default FetchMode.
     *
     * @param object The object, converted to a string
     * @return The FetchMode
     */
    public static FetchMode getFetchMode(Object object) {
        String name = object != null ? object.toString() : "default";
        if (name.equalsIgnoreCase(FetchMode.JOIN.toString()) || name.equalsIgnoreCase("eager")) {
            return FetchMode.JOIN;
        }
        if (name.equalsIgnoreCase(FetchMode.SELECT.toString()) || name.equalsIgnoreCase("lazy")) {
            return FetchMode.SELECT;
        }
        return FetchMode.DEFAULT;
    }

    /**
     * Sets the target object to read-only using the given SessionFactory instance. This
     * avoids Hibernate performing any dirty checking on the object
     *
     * @see #setObjectToReadWrite(Object, org.hibernate.SessionFactory)
     *
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
     * Sets the target object to read-write, allowing Hibernate to dirty check it and auto-flush changes.
     *
     * @see #setObjectToReadyOnly(Object, org.hibernate.SessionFactory)
     *
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
     * Unproxies a HibernateProxy. If the proxy is uninitialized, it automatically triggers an initialization.
     * In case the supplied object is null or not a proxy, the object will be returned as-is.
     */
    public static Object unwrapIfProxy(Object instance) {
        return proxyHandler.unwrap(instance);
    }

    /**
     * @deprecated Use {@link  MultipleDataSourceSupport#getDefaultDataSource(PersistentEntity)} instead
     */
    @Deprecated
    public static String getDefaultDataSource(PersistentEntity domainClass) {
        return MultipleDataSourceSupport.getDefaultDataSource(domainClass);
    }

    /**
     * @deprecated Use {@link  MultipleDataSourceSupport#getDatasourceNames(PersistentEntity)} instead
     */
    @Deprecated
    public static List<String> getDatasourceNames(PersistentEntity domainClass) {
        return MultipleDataSourceSupport.getDatasourceNames(domainClass);
    }

    /**
     * @deprecated Use {@link  MultipleDataSourceSupport#getDefaultDataSource(PersistentEntity)} instead
     */
    @Deprecated
    public static boolean usesDatasource(PersistentEntity domainClass, String dataSourceName) {
        return MultipleDataSourceSupport.usesDatasource(domainClass, dataSourceName);
    }

    public static boolean isMappedWithHibernate(PersistentEntity domainClass) {
        return domainClass instanceof HibernatePersistentEntity;
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
}
