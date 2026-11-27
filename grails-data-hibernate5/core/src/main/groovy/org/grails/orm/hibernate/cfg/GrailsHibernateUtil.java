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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import groovy.lang.GroovyObject;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import groovy.transform.Internal;

import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.FetchParent;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.PluralAttribute;

import org.hibernate.Criteria;
import org.hibernate.FetchMode;
import org.hibernate.FlushMode;
import org.hibernate.Hibernate;
import org.hibernate.LockMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.Subqueries;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.engine.spi.Status;
import org.hibernate.internal.CriteriaImpl;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.persister.entity.OuterJoinLoadable;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.sql.JoinType;
import org.hibernate.type.BagType;
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
import org.grails.datastore.mapping.model.types.Basic;
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
        final int max = maxParam == null ? -1 : maxParam;
        final int offset = offsetParam == null ? -1 : offsetParam;
        if (max > -1) {
            c.setMaxResults(max);
        }
        if (offset > -1) {
            c.setFirstResult(offset);
        }
        String orderParam = (String) argMap.get(ARGUMENT_ORDER);
        Object fetchObj = argMap.get(ARGUMENT_FETCH);
        if (fetchObj instanceof Map) {
            Map fetch = (Map) fetchObj;
            for (Object o : fetch.keySet()) {
                String associationName = (String) o;
                Object fetchValue = fetch.get(associationName);
                if (fetchValue instanceof jakarta.persistence.criteria.JoinType) {
                    applyJoinType(datastore, targetClass, c, associationName, (jakarta.persistence.criteria.JoinType) fetchValue);
                }
                else {
                    c.setFetchMode(associationName, getFetchMode(fetchValue));
                }
            }
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
     * Add order to criteria, creating necessary subCriteria if nested sort property (ie. sort:'nested.property').
     */
    private static void addOrderPossiblyNested(AbstractHibernateDatastore datastore, Criteria c, Class<?> targetClass, String sort, String order, boolean ignoreCase) {
        Criteria criteria = c;
        Class<?> currentClass = targetClass;
        String associationPath = null;
        String property = sort;
        int firstDotPos;
        while ((firstDotPos = property.indexOf('.')) != -1) {
            String sortHead = property.substring(0, firstDotPos);
            PersistentProperty persistentProperty = getGrailsDomainClassProperty(datastore, currentClass, sortHead);
            if (persistentProperty == null && associationPath == null) {
                // an alias that the criteria give an association: the rest of the path starts from that association
                CriteriaImpl root = getRootCriteria(c);
                Criteria aliased = root != null ? findCriteriaByAlias(root, sortHead) : null;
                String aliasedPath = aliased instanceof CriteriaImpl.Subcriteria ? getWholePath(root, (CriteriaImpl.Subcriteria) aliased) : null;
                Class<?> aliasedClass = aliasedPath != null ? getAssociatedClass(datastore, targetClass, aliasedPath) : null;
                if (aliasedClass != null) {
                    criteria = aliased;
                    associationPath = aliasedPath;
                    currentClass = aliasedClass;
                    property = property.substring(firstDotPos + 1);
                    continue;
                }
            }
            if (persistentProperty instanceof Embedded) {
                // embedded objects cannot reference entities (at time of writing), so the rest is a component path
                break;
            }
            if (!(persistentProperty instanceof Association)) {
                return;
            }
            associationPath = associationPath == null ? sortHead : associationPath + '.' + sortHead;
            criteria = getOrCreateSortCriteria(c, criteria, associationPath, sortHead, persistentProperty instanceof ToMany,
                    isLeftJoinAssociationSort(persistentProperty.getOwner()));
            currentClass = ((Association) persistentProperty).getAssociatedEntity().getJavaClass();
            GrailsHibernateUtil.cacheCriteriaByMapping(datastore, currentClass, criteria);
            property = property.substring(firstDotPos + 1);
        }
        addOrder(criteria, property, order, ignoreCase);
    }

    /**
     * Returns the class at the end of an association path from the given class, or null when the path
     * does not consist of associations only.
     */
    private static Class<?> getAssociatedClass(AbstractHibernateDatastore datastore, Class<?> targetClass, String associationPath) {
        Class<?> currentClass = targetClass;
        for (String token : associationPath.split("\\.")) {
            PersistentProperty persistentProperty = getGrailsDomainClassProperty(datastore, currentClass, token);
            if (!(persistentProperty instanceof Association) || persistentProperty instanceof Embedded) {
                return null;
            }
            currentClass = ((Association) persistentProperty).getAssociatedEntity().getJavaClass();
        }
        return currentClass;
    }

    /**
     * Returns the criteria that joins an association for a sort: the one the query already has for the
     * association path or, failing that, a new one. A new one is created from the root criteria,
     * because the count of a paged list recreates each join from the root criteria by its path, and
     * an association after the first one on the path is joined through the alias of the join before
     * it, so that Hibernate does not read the first association of the path as an alias of the same
     * name. Without the <code>grails.gorm.leftJoinAssociationSort</code> setting it is an inner join.
     * With the setting it is a left join, so that the sort keeps the rows where the association is
     * null, and a collection is joined without being fetched, unless the query fetches it: see
     * {@link #keepsSortedCollectionUnfetched(Criteria, String)} and
     * {@link #deferSortedCollectionFetches(Criteria)}.
     */
    private static Criteria getOrCreateSortCriteria(Criteria root, Criteria parent, String associationPath, String associationName, boolean collection,
                                                    boolean leftJoin) {
        Criteria existing = findCriteria(root, associationPath);
        if (existing != null) {
            return existing;
        }
        if (!leftJoin) {
            return root.createCriteria(getJoinPath(parent, associationPath, associationName), newJoinAlias(root, associationPath, "_sort"), JoinType.INNER_JOIN);
        }
        Criteria join = root.createCriteria(getJoinPath(parent, associationPath, associationName), newJoinAlias(root, associationPath, "_sort"), JoinType.LEFT_OUTER_JOIN);
        if (collection && keepsSortedCollectionUnfetched(root, associationPath)) {
            keepCollectionUnfetched(join);
        }
        return join;
    }

    /**
     * Returns the path to create the join of an association with from the root criteria: the alias of
     * the join of the association before it, followed by its name, or the whole path when the
     * association follows the root, or a join without an alias.
     */
    private static String getJoinPath(Criteria parent, String associationPath, String associationName) {
        String parentAlias = parent instanceof CriteriaImpl.Subcriteria ? ((CriteriaImpl.Subcriteria) parent).getAlias() : null;
        return parentAlias != null ? parentAlias + '.' + associationName : associationPath;
    }

    /**
     * Returns an alias for a join that the criteria do not use yet.
     */
    private static String newJoinAlias(Criteria root, String associationPath, String suffix) {
        CriteriaImpl criteria = getRootCriteria(root);
        String base = associationPath.replace('.', '_') + suffix;
        String alias = base;
        int count = 1;
        while (criteria != null && findCriteriaByAlias(criteria, alias) != null) {
            alias = base + count++;
        }
        return alias;
    }

    private static CriteriaImpl getRootCriteria(Criteria criteria) {
        Criteria root = criteria;
        while (root instanceof CriteriaImpl.Subcriteria) {
            root = ((CriteriaImpl.Subcriteria) root).getParent();
        }
        return root instanceof CriteriaImpl ? (CriteriaImpl) root : null;
    }

    /**
     * Whether the criteria ask for the association at the given path to be fetched with a join. A
     * join that is made for a sort must not keep a collection from being fetched then: see
     * {@link #keepsSortedCollectionUnfetched(Criteria, String)}.
     *
     * @param root The root criteria
     * @param associationPath The path of the association from the root entity
     * @return Whether a join fetch is requested for the association
     */
    private static boolean isFetchRequested(Criteria root, String associationPath) {
        return getRequestedFetchMode(root, associationPath) == FetchMode.JOIN;
    }

    /**
     * Defers collection fetches that a collection sort would multiply into a bag. Call after the
     * orders and joins have been built, and initialize the returned paths after executing the query.
     * Projections load no collections. The sort joins and their join types are kept.
     *
     * @param criteria The completed criteria
     * @return Collection paths to initialize after the query
     */
    public static List<String> deferSortedCollectionFetches(Criteria criteria) {
        CriteriaImpl root = getRootCriteria(criteria);
        if (root == null || root.getProjection() != null || root.getSession() == null) {
            return Collections.emptyList();
        }
        List<String> deferred = new ArrayList<>();
        List<String> fetchedPaths = findFetchedCollections(root);
        Set<String> sortedPaths = new HashSet<>();
        Iterator<CriteriaImpl.OrderEntry> orders = root.iterateOrderings();
        while (orders.hasNext()) {
            CriteriaImpl.OrderEntry entry = orders.next();
            String property = entry.getOrder().getPropertyName();
            Criteria owner = entry.getCriteria();
            int dot = property.indexOf('.');
            if (dot > 0) {
                Criteria aliased = findCriteriaByAlias(root, property.substring(0, dot));
                if (aliased != null) {
                    owner = aliased;
                    property = property.substring(dot + 1);
                }
            }
            String path = owner instanceof CriteriaImpl.Subcriteria ? getWholePath(root, (CriteriaImpl.Subcriteria) owner) + '.' + property : property;
            dot = path.lastIndexOf('.');
            while (dot > 0) {
                sortedPaths.add(path.substring(0, dot));
                dot = path.lastIndexOf('.', dot - 1);
            }
        }
        SessionFactoryImplementor factory = root.getSession().getFactory();
        EntityPersister persister = factory.getMetamodel().entityPersister(root.getEntityOrClassName());
        for (String sorted : sortedPaths) {
            if (getCollectionRole(factory, persister, sorted) == null) {
                continue;
            }
            String fetched;
            while ((fetched = findOtherFetchedCollection(root, sorted, true)) != null) {
                for (String path : fetchedPaths) {
                    if ((path.equals(fetched) || path.startsWith(fetched + '.')) && !deferred.contains(path)) {
                        deferred.add(path);
                    }
                }
                root.setFetchMode(fetched, FetchMode.SELECT);
                Criteria joined = findCriteria(root, fetched);
                if (joined != null) {
                    keepCollectionUnfetched(joined);
                }
            }
            CriteriaImpl.Subcriteria join = findCriteria(root, sorted);
            if (join != null && join.getJoinType() == JoinType.LEFT_OUTER_JOIN && keepsSortedCollectionUnfetched(root, sorted)) {
                keepCollectionUnfetched(join);
            }
        }
        return deferred;
    }

    /**
     * Defers bag fetches multiplied by the collection joins of a static {@code list} query. Its
     * distinct result and any restrictions requested by a join type remain in the query. Unlike
     * legacy criteria, a JPA sort join does not implicitly fetch a collection of the same element type.
     *
     * @param query The query
     * @return Paths to initialize after the query, including fetches below a deferred bag
     */
    public static List<String> deferSortedCollectionFetches(CriteriaQuery<?> query) {
        List<String> deferred = new ArrayList<>();
        for (Root<?> root : query.getRoots()) {
            if (hasCollectionJoin(root)) {
                deferBagFetches(root, "", deferred);
            }
        }
        return deferred;
    }

    private static boolean hasCollectionJoin(From<?, ?> parent) {
        for (Join<?, ?> join : parent.getJoins()) {
            if (join.getAttribute() instanceof PluralAttribute || hasCollectionJoin(join)) {
                return true;
            }
        }
        for (Fetch<?, ?> fetch : parent.getFetches()) {
            if (fetch instanceof From && hasCollectionJoin((From<?, ?>) fetch)) {
                return true;
            }
        }
        return false;
    }

    private static void deferBagFetches(FetchParent<?, ?> parent, String prefix, List<String> deferred) {
        Iterator<? extends Fetch<?, ?>> fetches = parent.getFetches().iterator();
        while (fetches.hasNext()) {
            Fetch<?, ?> fetch = fetches.next();
            String path = prefix + fetch.getAttribute().getName();
            if (fetch.getAttribute() instanceof PluralAttribute && isBag(fetch.getAttribute())) {
                deferred.add(path);
                collectFetchPaths(fetch, path + '.', deferred);
                fetches.remove();
            }
            else {
                deferBagFetches(fetch, path + '.', deferred);
            }
        }
    }

    private static void collectFetchPaths(FetchParent<?, ?> parent, String prefix, List<String> paths) {
        for (Fetch<?, ?> fetch : parent.getFetches()) {
            String path = prefix + fetch.getAttribute().getName();
            paths.add(path);
            collectFetchPaths(fetch, path + '.', paths);
        }
    }

    private static boolean isBag(Attribute<?, ?> attribute) {
        return ((PluralAttribute<?, ?, ?>) attribute).getCollectionType() == PluralAttribute.CollectionType.COLLECTION;
    }

    /**
     * Whether a left join of a collection that a query makes for a sort, and not for criteria on the
     * collection, must keep the collection from being fetched.
     * <p>
     * Hibernate initializes a collection from the rows of a left join that it is not kept from
     * fetching. It does so also when a row limit leaves elements out of those rows, and the collection
     * then lacks those elements. A sort must not change what a query fetches either. Such a join is
     * therefore kept from fetching the collection, unless the query asks to fetch it.
     * <p>
     * It is fetched as well when the query fetches another collection of the same element type, because Hibernate then reads
     * the elements of the collection that it fetches from the columns of the join that it does not
     * fetch, when both collections hold elements of the same table. That is not possible when either
     * collection is a bag: see {@link #deferSortedCollectionFetches(Criteria)}.
     *
     * @param criteria The criteria of the query
     * @param associationPath The path of the collection from the root entity
     * @return Whether the collection is kept from being fetched
     */
    public static boolean keepsSortedCollectionUnfetched(Criteria criteria, String associationPath) {
        return !isFetchRequested(criteria, associationPath) && !fetchesAnotherCollection(criteria, associationPath);
    }

    /**
     * Whether the query fetches a collection of the root entity other than the one at the given path.
     * Hibernate then reads the elements of the collection that it fetches from the columns of a left
     * join of a collection that it does not fetch, when both collections hold elements of the same table.
     *
     * @param criteria The criteria of the query
     * @param associationPath The path of the collection from the root entity
     * @return Whether another collection is fetched
     */
    private static boolean fetchesAnotherCollection(Criteria criteria, String associationPath) {
        return findOtherFetchedCollection(criteria, associationPath, false) != null;
    }

    /**
     * Returns the paths, from the root entity, of the collections that the query fetches, at any depth.
     *
     * @param criteria The criteria of the query
     * @return The paths of the collections
     */
    public static List<String> findFetchedCollections(Criteria criteria) {
        CriteriaImpl root = getRootCriteria(criteria);
        if (root == null || root.getSession() == null) {
            return Collections.emptyList();
        }
        SessionFactoryImplementor factory = root.getSession().getFactory();
        Map<String, String> fetched = new LinkedHashMap<>();
        collectFetchedCollections(root, factory, factory.getMetamodel().entityPersister(root.getEntityOrClassName()), "", 0, new HashSet<>(), fetched);
        return new ArrayList<>(fetched.keySet());
    }

    /**
     * Initializes the collections at the given paths of the instances that a query returned, also of
     * the elements of a collection that a path passes through.
     *
     * @param entity The entity of the instances
     * @param instances The instances
     * @param associationPaths The paths of the collections from the entity
     */
    public static void initializeCollections(PersistentEntity entity, Collection<?> instances, Collection<String> associationPaths) {
        for (String associationPath : associationPaths) {
            String[] tokens = associationPath.split("\\.");
            for (Object instance : instances) {
                initializeAlongPath(entity, instance, tokens, 0);
            }
        }
    }

    private static void initializeAlongPath(PersistentEntity entity, Object owner, String[] tokens, int index) {
        if (owner == null || index == tokens.length) {
            return;
        }
        Object value = entity.getReflector().getProperty(Hibernate.unproxy(owner), tokens[index]);
        Hibernate.initialize(value);
        PersistentEntity associated = ((Association<?>) entity.getPropertyByName(tokens[index])).getAssociatedEntity();
        Collection<?> owners = value instanceof Map ? ((Map<?, ?>) value).values() : value instanceof Collection ? (Collection<?>) value : Collections.singletonList(value);
        for (Object next : owners) {
            initializeAlongPath(associated, next, tokens, index + 1);
        }
    }

    /**
     * Returns the path of a collection, other than the one at the given path, that the query fetches at
     * any depth, or null. When a bag must be involved, a fetched bag cannot share the multiplying
     * rows, and a sorted bag cannot be implicitly fetched alongside a collection of the same type.
     */
    private static String findOtherFetchedCollection(Criteria criteria, String associationPath, boolean bagInvolved) {
        CriteriaImpl root = getRootCriteria(criteria);
        if (root == null || root.getSession() == null) {
            return null;
        }
        SessionFactoryImplementor factory = root.getSession().getFactory();
        EntityPersister persister = factory.getMetamodel().entityPersister(root.getEntityOrClassName());
        String sortedRole = getCollectionRole(factory, persister, associationPath);
        if (sortedRole == null) {
            return null;
        }
        boolean sortedBag = false;
        if (bagInvolved) {
            sortedBag = isBag(factory, sortedRole);
        }
        Map<String, String> fetched = new LinkedHashMap<>();
        collectFetchedCollections(root, factory, persister, "", 0, new HashSet<>(), fetched);
        boolean sortedFetched = isFetchRequested(root, associationPath) || fetched.containsKey(associationPath);
        for (Map.Entry<String, String> collection : fetched.entrySet()) {
            if (collection.getKey().equals(associationPath)) {
                continue;
            }
            boolean otherBag = bagInvolved && isBag(factory, collection.getValue());
            Type sortedElement = factory.getMetamodel().collectionPersister(sortedRole).getElementType();
            Type fetchedElement = factory.getMetamodel().collectionPersister(collection.getValue()).getElementType();
            boolean sameElements = sortedElement instanceof EntityType && fetchedElement instanceof EntityType &&
                    ((EntityType) sortedElement).getAssociatedEntityName(factory).equals(((EntityType) fetchedElement).getAssociatedEntityName(factory));
            if (bagInvolved ? otherBag || sortedBag && (sortedFetched || sameElements) : sameElements) {
                return collection.getKey();
            }
        }
        return null;
    }

    /**
     * Collects the role of each collection that the query fetches, by its path. It follows the
     * associations that the query or the mapping fetches, as Hibernate does. A fetch from the mapping is
     * not followed when the same association is already on the path, so that a class that fetches itself
     * comes to an end, as it does in Hibernate, or when the association is as many joins away from the
     * queried class as {@code hibernate.max_fetch_depth} allows: Hibernate does not join it then, and
     * loads it with a query of its own. A fetch that the query requests is joined at any depth.
     */
    private static void collectFetchedCollections(CriteriaImpl root, SessionFactoryImplementor factory, EntityPersister persister, String prefix, int depth, Set<String> mappedFetches, Map<String, String> fetched) {
        String[] propertyNames = persister.getPropertyNames();
        Type[] propertyTypes = persister.getPropertyTypes();
        for (int i = 0; i < propertyNames.length; i++) {
            if (!propertyTypes[i].isAssociationType()) {
                continue;
            }
            String path = prefix + propertyNames[i];
            FetchMode requested = getRequestedFetchMode(root, path);
            Integer maximumFetchDepth = factory.getSessionFactoryOptions().getMaximumFetchDepth();
            boolean joined = requested != null ? requested == FetchMode.JOIN :
                    persister instanceof OuterJoinLoadable && ((OuterJoinLoadable) persister).getFetchMode(i) == FetchMode.JOIN &&
                            (maximumFetchDepth == null || depth < maximumFetchDepth);
            String mappedFetch = requested == null ? persister.getEntityName() + '.' + propertyNames[i] : null;
            if (!joined || (mappedFetch != null && !mappedFetches.add(mappedFetch))) {
                continue;
            }
            if (propertyTypes[i].isCollectionType()) {
                fetched.put(path, ((CollectionType) propertyTypes[i]).getRole());
            }
            EntityPersister associated = getAssociatedPersister(factory, propertyTypes[i]);
            if (associated != null) {
                collectFetchedCollections(root, factory, associated, path + '.', depth + 1, mappedFetches, fetched);
            }
            if (mappedFetch != null) {
                mappedFetches.remove(mappedFetch);
            }
        }
    }

    private static EntityPersister getAssociatedPersister(SessionFactoryImplementor factory, Type type) {
        Type target = type instanceof CollectionType ? factory.getMetamodel().collectionPersister(((CollectionType) type).getRole()).getElementType() : type;
        return target instanceof EntityType ? factory.getMetamodel().entityPersister(((EntityType) target).getAssociatedEntityName(factory)) : null;
    }

    private static String getCollectionRole(SessionFactoryImplementor factory, EntityPersister persister, String associationPath) {
        EntityPersister current = persister;
        Type type = null;
        for (String token : associationPath.split("\\.")) {
            if (current == null) {
                return null;
            }
            try {
                type = current.getPropertyType(token);
            }
            catch (RuntimeException e) {
                return null;
            }
            current = getAssociatedPersister(factory, type);
        }
        return type instanceof CollectionType ? ((CollectionType) type).getRole() : null;
    }

    private static boolean isBag(SessionFactoryImplementor factory, String role) {
        return factory.getMetamodel().collectionPersister(role).getCollectionType().getClass() == BagType.class;
    }

    /**
     * Keeps Hibernate from fetching the collection that a left join of the criteria joins.
     * <p>
     * Hibernate does not fetch a collection through a join that has a restriction of its own. The
     * restriction is always true, so the join returns the same rows, and it is added through the
     * criteria of the join, so it is part of the where clause and not of the join condition, which
     * Hibernate combines with the filters of a collection that has a join table.
     *
     * @param joinCriteria The criteria of the join
     */
    public static void keepCollectionUnfetched(Criteria joinCriteria) {
        joinCriteria.add(Restrictions.sqlRestriction("1=1"));
    }

    /**
     * Keeps Hibernate from fetching the collection that the join with the given alias joins: see
     * {@link #keepCollectionUnfetched(Criteria)}.
     *
     * @param criteria The criteria that the join was made on
     * @param alias The alias of the join
     */
    public static void keepCollectionUnfetched(Criteria criteria, String alias) {
        CriteriaImpl root = getRootCriteria(criteria);
        Criteria join = root != null ? findCriteriaByAlias(root, alias) : null;
        if (join instanceof CriteriaImpl.Subcriteria) {
            keepCollectionUnfetched(join);
        }
    }

    /**
     * Returns the alias of the join that the criteria have for an association path, whichever part
     * of the query made the join.
     *
     * @param root The root criteria
     * @param associationPath The path of the association from the root entity
     * @return The alias, or null when the criteria do not join the association or join it without an alias
     */
    public static String getJoinAlias(Criteria root, String associationPath) {
        CriteriaImpl.Subcriteria existing = findCriteria(root, associationPath);
        return existing != null ? existing.getAlias() : null;
    }

    /**
     * Returns the criteria that the root criteria have for an association path, or null.
     */
    private static CriteriaImpl.Subcriteria findCriteria(Criteria root, String associationPath) {
        CriteriaImpl criteria = getRootCriteria(root);
        if (criteria != null) {
            Iterator<CriteriaImpl.Subcriteria> subcriteria = criteria.iterateSubcriteria();
            while (subcriteria.hasNext()) {
                CriteriaImpl.Subcriteria existing = subcriteria.next();
                if (associationPath.equals(getWholePath(criteria, existing))) {
                    return existing;
                }
            }
        }
        return null;
    }

    private static String getWholePath(CriteriaImpl root, CriteriaImpl.Subcriteria subcriteria) {
        String path = subcriteria.getPath();
        Criteria parent = subcriteria.getParent();
        int firstDotPos = path.indexOf('.');
        if (firstDotPos > 0) {
            String qualifier = path.substring(0, firstDotPos);
            Criteria qualified = qualifier.equals(subcriteria.getAlias()) ? null : findCriteriaByAlias(root, qualifier);
            if (qualified != null) {
                parent = qualified;
                path = path.substring(firstDotPos + 1);
            }
        }
        return parent instanceof CriteriaImpl.Subcriteria ? getWholePath(root, (CriteriaImpl.Subcriteria) parent) + '.' + path : path;
    }

    private static Criteria findCriteriaByAlias(CriteriaImpl root, String alias) {
        if (alias.equals(root.getAlias())) {
            return root;
        }
        Iterator<CriteriaImpl.Subcriteria> subcriteria = root.iterateSubcriteria();
        while (subcriteria.hasNext()) {
            CriteriaImpl.Subcriteria existing = subcriteria.next();
            if (alias.equals(existing.getAlias())) {
                return existing;
            }
        }
        return null;
    }

    private static FetchMode getRequestedFetchMode(Criteria root, String associationPath) {
        CriteriaImpl criteria = getRootCriteria(root);
        if (criteria == null) {
            return null;
        }
        // Hibernate records a fetch mode under the path from the alias of the root criteria
        String rootAlias = criteria.getAlias();
        String path = rootAlias != null && !associationPath.startsWith(rootAlias + '.') ? rootAlias + '.' + associationPath : associationPath;
        return criteria.getFetchMode(path);
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
     * Applies the join type that a criteria query requests for an association, with the {@code fetch}
     * argument or with {@code join(String, JoinType)}. The association is fetched, as it is for the
     * {@code 'join'} fetch mode. A fetch mode alone always joins with an outer join, so a join type
     * is applied to the existing join of the association as well as to a new join.
     * <p>
     * Hibernate fetches a collection only through a left join, and a join through a collection
     * returns an instance once for each element of the collection. A path that ends in a collection,
     * or passes through one, that the criteria do not join is therefore joined in a subquery, see
     * {@link #joinedInSubquery(String, String, String)}. A collection at the end of the path keeps the
     * left join of the fetch mode, and the associations before it are joined, so that the collection
     * is fetched.
     *
     * @param datastore The datastore
     * @param targetClass The class that the criteria query
     * @param root The root criteria
     * @param associationPath The path of the association from the target class
     * @param joinType The requested join type
     */
    @Internal
    public static void applyJoinType(AbstractHibernateDatastore datastore, Class<?> targetClass, Criteria root, String associationPath, jakarta.persistence.criteria.JoinType joinType) {
        PersistentEntity entity = datastore != null && targetClass != null ? datastore.getMappingContext().getPersistentEntity(targetClass.getName()) : null;
        applyJoinType(entity, root, associationPath, joinType);
    }

    /**
     * Applies the join type that a query requests for an association: see
     * {@link #applyJoinType(AbstractHibernateDatastore, Class, Criteria, String, jakarta.persistence.criteria.JoinType)}.
     *
     * @param entity The entity that the criteria query
     * @param root The root criteria
     * @param associationPath The path of the association from the entity
     * @param joinType The requested join type
     */
    @Internal
    public static void applyJoinType(PersistentEntity entity, Criteria root, String associationPath, jakarta.persistence.criteria.JoinType joinType) {
        root.setFetchMode(associationPath, FetchMode.JOIN);
        if (entity == null) {
            return;
        }

        String[] tokens = associationPath.split("\\.");
        int last = tokens.length - 1;
        Association[] associations = new Association[tokens.length];
        PersistentEntity currentEntity = entity;
        for (int i = 0; i < tokens.length; i++) {
            PersistentProperty persistentProperty = currentEntity != null ? currentEntity.getPropertyByName(tokens[i]) : null;
            if (!(persistentProperty instanceof Association) || persistentProperty instanceof Embedded ||
                    (persistentProperty instanceof Basic && i != last)) {
                return;
            }
            associations[i] = (Association) persistentProperty;
            currentEntity = associations[i].getAssociatedEntity();
        }
        fetchAssociationsBefore(root, associationPath);
        CriteriaImpl rootCriteria = getRootCriteria(root);
        if (rootCriteria != null && (rootCriteria.getMaxResults() != null ||
                rootCriteria.getFirstResult() != null && rootCriteria.getFirstResult() > 0)) {
            boolean collection = false;
            String path = "";
            for (int i = 0; i < tokens.length; i++) {
                path = i == 0 ? tokens[i] : path + '.' + tokens[i];
                collection |= associations[i] instanceof ToMany;
                if (collection) {
                    root.setFetchMode(path, FetchMode.SELECT);
                }
            }
        }
        if (joinType == null) {
            return;
        }
        String[] paths = new String[tokens.length];
        CriteriaImpl.Subcriteria[] joined = new CriteriaImpl.Subcriteria[tokens.length];
        int unjoinedCollection = -1;
        for (int i = 0; i < tokens.length; i++) {
            paths[i] = i == 0 ? tokens[i] : paths[i - 1] + '.' + tokens[i];
            joined[i] = findCriteria(root, paths[i]);
            if (unjoinedCollection < 0 && joined[i] == null && associations[i] instanceof ToMany) {
                unjoinedCollection = i;
            }
        }
        if (unjoinedCollection >= 0 && joinType != jakarta.persistence.criteria.JoinType.LEFT) {
            root.add(joinedInSubquery(entity.getName(), root.getAlias(), associationPath));
        }

        // the associations before a collection that the subquery joins are joined and fetched
        JoinType lastJoinType = toHibernateJoinType(joinType);
        int joinedCount = unjoinedCollection < 0 ? tokens.length : unjoinedCollection;
        String parentAlias = null;
        for (int i = 0; i < joinedCount; i++) {
            if (joined[i] != null) {
                if (i == last) {
                    rejoin(joined[i], lastJoinType);
                }
                parentAlias = joined[i].getAlias();
                continue;
            }
            // joined from the root criteria, as the count of a paged list recreates it, through the
            // alias of the join before it
            String alias = newJoinAlias(root, paths[i], "_join");
            root.createAlias(parentAlias != null ? parentAlias + '.' + tokens[i] : paths[i], alias, i == last ? lastJoinType : JoinType.LEFT_OUTER_JOIN);
            parentAlias = alias;
        }
    }

    /**
     * Applies a join type to the join that the criteria already make for an association, with an
     * association block or an alias. The join is made again with the join type, under the same alias,
     * so that the criteria that use the alias use the join type. Nothing is joined when the criteria
     * make no join for the association.
     *
     * @param root The root criteria
     * @param associationPath The path of the association from the root entity
     * @param joinType The join type
     */
    @Internal
    public static void applyJoinTypeToExistingJoin(Criteria root, String associationPath, jakarta.persistence.criteria.JoinType joinType) {
        CriteriaImpl.Subcriteria existing = findCriteria(root, associationPath);
        if (existing != null) {
            rejoin(existing, toHibernateJoinType(joinType));
        }
    }

    private static JoinType toHibernateJoinType(jakarta.persistence.criteria.JoinType joinType) {
        return joinType == jakarta.persistence.criteria.JoinType.INNER ? JoinType.INNER_JOIN :
                joinType == jakarta.persistence.criteria.JoinType.LEFT ? JoinType.LEFT_OUTER_JOIN : JoinType.RIGHT_OUTER_JOIN;
    }

    /**
     * Makes a join of the criteria again with another join type, under the same alias. Hibernate
     * does not change the join type of a join that the criteria have made.
     */
    private static void rejoin(CriteriaImpl.Subcriteria existing, JoinType joinType) {
        if (existing.getJoinType() == joinType) {
            return;
        }
        Iterator<CriteriaImpl.Subcriteria> subcriteria = getRootCriteria(existing).iterateSubcriteria();
        while (subcriteria.hasNext()) {
            if (subcriteria.next() == existing) {
                subcriteria.remove();
                break;
            }
        }
        existing.getParent().createAlias(existing.getPath(), existing.getAlias(), joinType, existing.getWithClause());
    }

    /**
     * Asks the criteria to fetch, with a join, the associations before the last one on a path that a
     * join type is requested for, unless they request another fetch mode for them. Hibernate fetches
     * an association by its fetch mode only when it fetches the associations before it, and a
     * collection on the path is fetched with all of its elements, since nothing restricts them: the
     * requested join type only decides which instances the query returns.
     *
     * @param criteria The criteria of the query
     * @param associationPath The path from the root entity
     */
    public static void fetchAssociationsBefore(Criteria criteria, String associationPath) {
        int dot = associationPath.indexOf('.');
        while (dot > 0) {
            String path = associationPath.substring(0, dot);
            if (getRequestedFetchMode(criteria, path) == null) {
                criteria.setFetchMode(path, FetchMode.JOIN);
            }
            dot = associationPath.indexOf('.', dot + 1);
        }
    }

    /**
     * Returns a restriction to the instances of an entity for which an inner join of an association
     * path returns a row. The path is joined in a subquery, which is correlated with the query by the
     * identifier of the entity.
     * <p>
     * A join through a collection returns an instance once for each element of the collection. That
     * multiplies the rows that a row limit and a count work on, and a collection that is fetched from
     * those rows lacks the elements that the rest of the path leaves out. A subquery restricts the
     * query to the same instances without either effect. Unlike a check that a collection is not
     * empty, its joins apply the filters that are enabled for the session, such as the one of
     * discriminator based multi-tenancy.
     *
     * @param entityName The name of the entity that the query returns
     * @param rootAlias The alias of the root of the query
     * @param associationPath The path of the association from the entity
     * @return The restriction
     */
    public static Criterion joinedInSubquery(String entityName, String rootAlias, String associationPath) {
        // Hibernate uses this alias as it is in the SQL of the subquery, so it is not made from the
        // association path, which can be longer than a database allows an identifier to be. It is made
        // from the alias of the root, so that an alias in the subquery cannot hide the root that it refers to
        String subqueryAlias = rootAlias + "_sub";
        DetachedCriteria subquery = DetachedCriteria.forEntityName(entityName, subqueryAlias);
        String parentAlias = subqueryAlias;
        int level = 0;
        for (String association : associationPath.split("\\.")) {
            String alias = subqueryAlias + level++;
            subquery.createAlias(parentAlias + '.' + association, alias, JoinType.INNER_JOIN);
            parentAlias = alias;
        }
        subquery.add(Restrictions.eqProperty(subqueryAlias + ".id", rootAlias + ".id"));
        subquery.setProjection(Projections.id());
        return Subqueries.exists(subquery);
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
