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

package org.grails.orm.hibernate.query;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.FetchParent;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.hibernate.Criteria;
import org.hibernate.FetchMode;
import org.hibernate.FlushMode;
import org.hibernate.LockMode;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.hibernate.internal.CriteriaImpl;
import org.hibernate.query.Query;

import org.springframework.core.convert.ConversionService;

import org.grails.datastore.gorm.finders.DynamicFinder;
import org.grails.datastore.mapping.config.Property;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.datastore.mapping.model.types.Embedded;
import org.grails.datastore.mapping.model.types.ToMany;
import org.grails.datastore.mapping.reflect.ClassUtils;
import org.grails.orm.hibernate.cfg.AbstractGrailsDomainBinder;
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil;
import org.grails.orm.hibernate.cfg.Mapping;

/**
 * Utility methods for configuring Hibernate queries
 *
 * @author Graeme Rocher
 * @since 4.0
 */
public class GrailsHibernateQueryUtils {

    /**
     * Populates criteria arguments for the given target class and arguments map
     *
     * @param entity The {@link org.grails.datastore.mapping.model.PersistentEntity} instance
     * @param c      The criteria instance
     * @param argMap The arguments map
     */
    @SuppressWarnings("rawtypes")
    @Deprecated
    public static void populateArgumentsForCriteria(PersistentEntity entity, Criteria c, Map argMap, ConversionService conversionService, boolean useDefaultMapping) {
        Integer maxParam = null;
        Integer offsetParam = null;
        if (argMap.containsKey(DynamicFinder.ARGUMENT_MAX)) {
            maxParam = conversionService.convert(argMap.get(DynamicFinder.ARGUMENT_MAX), Integer.class);
        }
        if (argMap.containsKey(DynamicFinder.ARGUMENT_OFFSET)) {
            offsetParam = conversionService.convert(argMap.get(DynamicFinder.ARGUMENT_OFFSET), Integer.class);
        }
        if (argMap.containsKey(DynamicFinder.ARGUMENT_FETCH_SIZE)) {
            c.setFetchSize(conversionService.convert(argMap.get(DynamicFinder.ARGUMENT_FETCH_SIZE), Integer.class));
        }
        if (argMap.containsKey(DynamicFinder.ARGUMENT_TIMEOUT)) {
            c.setTimeout(conversionService.convert(argMap.get(DynamicFinder.ARGUMENT_TIMEOUT), Integer.class));
        }
        if (argMap.containsKey(DynamicFinder.ARGUMENT_FLUSH_MODE)) {
            c.setFlushMode(convertFlushMode(argMap.get(DynamicFinder.ARGUMENT_FLUSH_MODE)));
        }
        if (argMap.containsKey(DynamicFinder.ARGUMENT_READ_ONLY)) {
            c.setReadOnly(ClassUtils.getBooleanFromMap(DynamicFinder.ARGUMENT_READ_ONLY, argMap));
        }
        // Typed collection fetches must see the limits before choosing how to load their elements.
        final int max = maxParam == null ? -1 : maxParam;
        final int offset = offsetParam == null ? -1 : offsetParam;
        if (max > -1) {
            c.setMaxResults(max);
        }
        if (offset > -1) {
            c.setFirstResult(offset);
        }
        String orderParam = (String) argMap.get(DynamicFinder.ARGUMENT_ORDER);
        Object fetchObj = argMap.get(DynamicFinder.ARGUMENT_FETCH);
        Map fetch = fetchObj instanceof Map ? (Map) fetchObj : null;
        if (fetch != null) {
            for (Object o : fetch.keySet()) {
                String associationName = (String) o;
                Object fetchValue = fetch.get(associationName);
                if (fetchValue instanceof JoinType) {
                    // applied before the sort, so that a sort by the association uses the requested join
                    GrailsHibernateUtil.applyJoinType(entity, c, associationName, (JoinType) fetchValue);
                }
                else {
                    c.setFetchMode(associationName, getFetchMode(fetchValue));
                }
            }
        }

        if (ClassUtils.getBooleanFromMap(DynamicFinder.ARGUMENT_LOCK, argMap)) {
            c.setLockMode(LockMode.PESSIMISTIC_WRITE);
            c.setCacheable(false);
        } else {
            if (argMap.containsKey(DynamicFinder.ARGUMENT_CACHE)) {
                c.setCacheable(ClassUtils.getBooleanFromMap(DynamicFinder.ARGUMENT_CACHE, argMap));
            } else {
                cacheCriteriaByMapping(entity.getJavaClass(), c);
            }
        }

        final Object sortObj = argMap.get(DynamicFinder.ARGUMENT_SORT);
        if (sortObj != null) {
            boolean ignoreCase = true;
            Object caseArg = argMap.get(DynamicFinder.ARGUMENT_IGNORE_CASE);
            if (caseArg instanceof Boolean) {
                ignoreCase = (Boolean) caseArg;
            }
            if (sortObj instanceof Map) {
                Map sortMap = (Map) sortObj;
                for (Object sort : sortMap.keySet()) {
                    final String order = DynamicFinder.ORDER_DESC.equalsIgnoreCase((String) sortMap.get(sort)) ? DynamicFinder.ORDER_DESC : DynamicFinder.ORDER_ASC;
                    addOrderPossiblyNested(c, null, entity, (String) sort, order, ignoreCase, fetch);
                }
            } else {
                final String sort = (String) sortObj;
                final String order = DynamicFinder.ORDER_DESC.equalsIgnoreCase(orderParam) ? DynamicFinder.ORDER_DESC : DynamicFinder.ORDER_ASC;
                addOrderPossiblyNested(c, null, entity, sort, order, ignoreCase, fetch);
            }
        } else if (useDefaultMapping) {
            Mapping m = AbstractGrailsDomainBinder.getMapping(entity.getJavaClass());
            if (m != null) {
                Map sortMap = m.getSort().getNamesAndDirections();
                for (Object sort : sortMap.keySet()) {
                    final String order = DynamicFinder.ORDER_DESC.equalsIgnoreCase((String) sortMap.get(sort)) ? DynamicFinder.ORDER_DESC : DynamicFinder.ORDER_ASC;
                    addOrderPossiblyNested(c, null, entity, (String) sort, order, true, fetch);
                }
            }
        }
    }

    /**
     * Populates criteria arguments for the given target class and arguments map
     *
     * @param entity The {@link org.grails.datastore.mapping.model.PersistentEntity} instance
     * @param query  The criteria instance
     * @param argMap The arguments map
     */
    @SuppressWarnings("rawtypes")
    public static void populateArgumentsForCriteria(
            PersistentEntity entity,
            CriteriaQuery query,
            Root queryRoot,
            CriteriaBuilder criteriaBuilder,
            Map argMap,
            ConversionService conversionService,
            boolean useDefaultMapping) {
        String orderParam = (String) argMap.get(DynamicFinder.ARGUMENT_ORDER);
        Object fetchObj = argMap.get(DynamicFinder.ARGUMENT_FETCH);
        if (fetchObj instanceof Map) {
            Map fetch = (Map) fetchObj;
            for (Object o : fetch.keySet()) {
                String associationName = (String) o;
                Object fetchValue = fetch.get(associationName);
                if (fetchValue instanceof JoinType) {
                    fetchWithJoinType(entity, query, queryRoot, criteriaBuilder, associationName, (JoinType) fetchValue);
                }
                else if (getFetchMode(fetchValue) == FetchMode.JOIN) {
                    // the fetch mode join fetches the association and never leaves out an instance whose association
                    // is null or empty, as it does for a where query and a dynamic finder; only a join type asks for that
                    fetchWithJoinType(entity, query, queryRoot, criteriaBuilder, associationName, JoinType.LEFT);
                }
            }
        }

        final Object sortObj = argMap.get(DynamicFinder.ARGUMENT_SORT);
        if (sortObj != null) {
            boolean ignoreCase = true;
            Object caseArg = argMap.get(DynamicFinder.ARGUMENT_IGNORE_CASE);
            if (caseArg instanceof Boolean) {
                ignoreCase = (Boolean) caseArg;
            }
            if (sortObj instanceof Map) {
                Map sortMap = (Map) sortObj;
                for (Object sort : sortMap.keySet()) {
                    final String order = DynamicFinder.ORDER_DESC.equalsIgnoreCase((String) sortMap.get(sort)) ? DynamicFinder.ORDER_DESC : DynamicFinder.ORDER_ASC;
                    addOrderPossiblyNested(query, queryRoot, criteriaBuilder, entity, (String) sort, order, ignoreCase);
                }
            } else {
                final String sort = (String) sortObj;
                final String order = DynamicFinder.ORDER_DESC.equalsIgnoreCase(orderParam) ? DynamicFinder.ORDER_DESC : DynamicFinder.ORDER_ASC;
                addOrderPossiblyNested(query, queryRoot, criteriaBuilder, entity, sort, order, ignoreCase);
            }
        } else if (useDefaultMapping) {
            Mapping m = AbstractGrailsDomainBinder.getMapping(entity.getJavaClass());
            if (m != null) {
                Map sortMap = m.getSort().getNamesAndDirections();
                for (Object sort : sortMap.keySet()) {
                    final String order = DynamicFinder.ORDER_DESC.equalsIgnoreCase((String) sortMap.get(sort)) ? DynamicFinder.ORDER_DESC : DynamicFinder.ORDER_ASC;
                    addOrderPossiblyNested(query, queryRoot, criteriaBuilder, entity, (String) sort, order, true);
                }
            }
        }
    }

    /**
     * Populates criteria arguments for the given target class and arguments map
     *
     * @param entity The {@link org.grails.datastore.mapping.model.PersistentEntity} instance
     * @param query  The criteria instance
     * @param argMap The arguments map
     */
    @SuppressWarnings("rawtypes")
    public static void populateArgumentsForCriteria(
            PersistentEntity entity,
            Query query,
            Map argMap,
            ConversionService conversionService,
            boolean useDefaultMapping) {
        Integer maxParam = null;
        Integer offsetParam = null;
        if (argMap.containsKey(DynamicFinder.ARGUMENT_MAX)) {
            maxParam = conversionService.convert(argMap.get(DynamicFinder.ARGUMENT_MAX), Integer.class);
        }
        if (argMap.containsKey(DynamicFinder.ARGUMENT_OFFSET)) {
            offsetParam = conversionService.convert(argMap.get(DynamicFinder.ARGUMENT_OFFSET), Integer.class);
        }
        if (argMap.containsKey(DynamicFinder.ARGUMENT_FETCH_SIZE)) {
            query.setFetchSize(conversionService.convert(argMap.get(DynamicFinder.ARGUMENT_FETCH_SIZE), Integer.class));
        }
        if (argMap.containsKey(DynamicFinder.ARGUMENT_TIMEOUT)) {
            query.setTimeout(conversionService.convert(argMap.get(DynamicFinder.ARGUMENT_TIMEOUT), Integer.class));
        }
        if (argMap.containsKey(DynamicFinder.ARGUMENT_FLUSH_MODE)) {
            query.setHibernateFlushMode(convertFlushMode(argMap.get(DynamicFinder.ARGUMENT_FLUSH_MODE)));
        }
        if (argMap.containsKey(DynamicFinder.ARGUMENT_READ_ONLY)) {
            query.setReadOnly(ClassUtils.getBooleanFromMap(DynamicFinder.ARGUMENT_READ_ONLY, argMap));
        }

        final int max = maxParam == null ? -1 : maxParam;
        final int offset = offsetParam == null ? -1 : offsetParam;
        if (max > -1) {
            query.setMaxResults(max);
        }
        if (offset > -1) {
            query.setFirstResult(offset);
        }
        if (ClassUtils.getBooleanFromMap(DynamicFinder.ARGUMENT_LOCK, argMap)) {
            query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
            query.setCacheable(false);
        } else {
            if (argMap.containsKey(DynamicFinder.ARGUMENT_CACHE)) {
                query.setCacheable(ClassUtils.getBooleanFromMap(DynamicFinder.ARGUMENT_CACHE, argMap));
            } else {
                cacheCriteriaByMapping(entity.getJavaClass(), query);
            }
        }

    }

    /**
     * Fetches the association that the {@code fetch} argument requests a join type for, with the
     * associations before it on the path.
     * <p>
     * A path that ends in the association, or in a collection, is fetched with the requested join type
     * for its last association and left joins for the ones before it. An inner join leaves out the
     * instances that it returns no row for, and a fetched collection holds all of its elements, since
     * nothing restricts them. Hibernate does not support right joins in criteria queries, so a right
     * join is made as an inner join.
     * <p>
     * A path that passes through a collection is fetched with left joins, so that the collection holds
     * all of its elements, also those that an inner join of the rest of the path would leave out.
     * <p>
     * An inner or a right join restricts the query to the instances for which the join returns a row
     * in a subquery as well, see {@link #restrictToJoinedInstances}: for a path through a collection,
     * since its fetch is a left join, and for any other path, so that the count of a paged list, which
     * cannot keep the fetches, leaves out the same instances.
     * <p>
     * A query that fetches a collection is distinct, so that it returns each instance once and not
     * once for each element of the collection.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void fetchWithJoinType(PersistentEntity entity, CriteriaQuery query, Root queryRoot, CriteriaBuilder criteriaBuilder, String associationPath, JoinType joinType) {
        String[] tokens = associationPath.split("\\.");
        int last = tokens.length - 1;
        Association[] associations = new Association[tokens.length];
        PersistentEntity currentEntity = entity;
        int throughCollection = -1;
        for (int i = 0; i < tokens.length; i++) {
            PersistentProperty property = currentEntity != null ? currentEntity.getPropertyByName(tokens[i]) : null;
            if (!(property instanceof Association) || property instanceof Embedded || (property instanceof Basic && i != last)) {
                return;
            }
            associations[i] = (Association) property;
            if (throughCollection < 0 && i != last && property instanceof ToMany) {
                throughCollection = i;
            }
            currentEntity = associations[i].getAssociatedEntity();
        }
        boolean inner = joinType != JoinType.LEFT;
        if (inner) {
            restrictToJoinedInstances(entity, query, queryRoot, criteriaBuilder, tokens);
        }
        FetchParent parent = queryRoot;
        boolean collection = false;
        for (int i = 0; i < tokens.length; i++) {
            parent = getOrCreateFetch(parent, tokens[i], inner && i == last && throughCollection < 0 ? JoinType.INNER : JoinType.LEFT);
            collection |= associations[i] instanceof ToMany;
        }
        if (collection) {
            query.distinct(true);
        }
    }

    /**
     * Returns the fetch of an association that the query already has, so that another path that
     * starts with it uses it, or a new one with the given join type.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static FetchParent getOrCreateFetch(FetchParent parent, String associationName, JoinType joinType) {
        for (Object existing : parent.getFetches()) {
            Fetch fetch = (Fetch) existing;
            if (associationName.equals(fetch.getAttribute().getName())) {
                return (FetchParent) fetch;
            }
        }
        return (FetchParent) parent.fetch(associationName, joinType);
    }

    /**
     * Restricts a query to the instances for which an inner join of an association path returns a
     * row. The path is joined in a subquery, which is correlated with the query by the instance, so
     * that a path through a collection does not multiply the rows.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void restrictToJoinedInstances(PersistentEntity entity, CriteriaQuery query, Root queryRoot, CriteriaBuilder criteriaBuilder, String[] tokens) {
        Subquery subquery = query.subquery(entity.getJavaClass());
        Root subqueryRoot = subquery.from(entity.getJavaClass());
        From from = subqueryRoot;
        for (String token : tokens) {
            from = from.join(token, JoinType.INNER);
        }
        subquery.select(subqueryRoot).where(criteriaBuilder.equal(subqueryRoot, queryRoot));
        Predicate exists = criteriaBuilder.exists(subquery);
        Predicate restriction = query.getRestriction();
        query.where(restriction != null ? criteriaBuilder.and(restriction, exists) : exists);
    }

    /**
     * Add order to criteria, creating necessary subCriteria if nested sort property (ie. sort:'nested.property').
     * The association path is the path of the given criteria from the root criteria, or null for the root.
     */
    private static void addOrderPossiblyNested(Criteria c, String associationPath, PersistentEntity entity, String sort, String order, boolean ignoreCase, Map<?, ?> fetch) {
        int firstDotPos = sort.indexOf(".");
        if (firstDotPos == -1) {
            addOrder(c, sort, order, ignoreCase);
        } else { // nested property
            String sortHead = sort.substring(0, firstDotPos);
            String sortTail = sort.substring(firstDotPos + 1);
            PersistentProperty property = entity.getPropertyByName(sortHead);
            if (property instanceof Embedded) {
                // embedded objects cannot reference entities (at time of writing), so no more recursion needed
                addOrder(c, sort, order, ignoreCase);
            } else if (property instanceof Association) {
                Association a = (Association) property;
                PersistentEntity associatedEntity = a.getAssociatedEntity();
                String sortPath = associationPath == null ? sortHead : associationPath + '.' + sortHead;
                // a join that the query already has for the association, such as the one of a requested join type
                Criteria subCriteria = findJoinCriteria(c, sortPath);
                if (subCriteria == null) {
                    boolean requestedLeftJoin = isLeftJoinRequested(fetch, sortPath, a);
                    boolean leftJoin = requestedLeftJoin || !(a instanceof ToMany) && GrailsHibernateUtil.isLeftJoinAssociationSort(entity);
                    CriteriaImpl root = getRootCriteria(c);
                    boolean pagedCollection = requestedLeftJoin && a instanceof ToMany && root != null &&
                            (root.getMaxResults() != null || root.getFirstResult() != null && root.getFirstResult() > 0);
                    // A restricted left join is not a collection fetch in Hibernate. Keep the sort's
                    // rows while loading the complete collection separately when the query is paged.
                    subCriteria = c.createCriteria(sortHead, null,
                            leftJoin ? org.hibernate.sql.JoinType.LEFT_OUTER_JOIN : org.hibernate.sql.JoinType.INNER_JOIN,
                            pagedCollection ? Restrictions.sqlRestriction("1=1") : null);
                    cacheCriteriaByMapping(associatedEntity.getJavaClass(), subCriteria);
                }
                addOrderPossiblyNested(subCriteria, sortPath, associatedEntity, sortTail, order, ignoreCase, fetch); // Recurse on nested sort
            }
        }
    }

    /**
     * A requested left join of a nested path also needs left joins for its intermediate associations.
     * An explicit type on an intermediate association wins. Only resolved association paths imply a
     * join type; an unknown fetch path must not change which instances the sort returns.
     */
    private static boolean isLeftJoinRequested(Map<?, ?> fetch, String associationPath, Association association) {
        if (fetch == null) {
            return false;
        }
        Object requested = fetch.get(associationPath);
        if (requested instanceof JoinType) {
            return requested == JoinType.LEFT;
        }
        String prefix = associationPath + '.';
        for (Map.Entry<?, ?> entry : fetch.entrySet()) {
            if (entry.getValue() != JoinType.LEFT || !(entry.getKey() instanceof String) || !((String) entry.getKey()).startsWith(prefix)) {
                continue;
            }
            PersistentEntity current = association.getAssociatedEntity();
            boolean resolved = true;
            for (String token : ((String) entry.getKey()).substring(prefix.length()).split("\\.")) {
                PersistentProperty property = current != null ? current.getPropertyByName(token) : null;
                if (!(property instanceof Association) || property instanceof Embedded) {
                    resolved = false;
                    break;
                }
                current = ((Association) property).getAssociatedEntity();
            }
            if (resolved) {
                return true;
            }
        }
        return false;
    }

    private static Criteria findJoinCriteria(Criteria criteria, String associationPath) {
        CriteriaImpl rootCriteria = getRootCriteria(criteria);
        if (rootCriteria != null) {
            Iterator<CriteriaImpl.Subcriteria> subcriteria = rootCriteria.iterateSubcriteria();
            while (subcriteria.hasNext()) {
                CriteriaImpl.Subcriteria existing = subcriteria.next();
                if (associationPath.equals(getWholePath(rootCriteria, existing))) {
                    return existing;
                }
            }
        }
        return null;
    }

    private static CriteriaImpl getRootCriteria(Criteria criteria) {
        Criteria root = criteria;
        while (root instanceof CriteriaImpl.Subcriteria) {
            root = ((CriteriaImpl.Subcriteria) root).getParent();
        }
        return root instanceof CriteriaImpl ? (CriteriaImpl) root : null;
    }

    /**
     * The path is relative to the parent criteria, or starts with the alias of another criteria.
     */
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

    /**
     * Add order to criteria, creating necessary subCriteria if nested sort property (ie. sort:'nested.property').
     */
    private static void addOrderPossiblyNested(CriteriaQuery query,
                                               From queryRoot,
                                               CriteriaBuilder criteriaBuilder,
                                               PersistentEntity entity,
                                               String sort,
                                               String order,
                                               boolean ignoreCase) {
        int firstDotPos = sort.indexOf(".");
        if (firstDotPos == -1) {
            final PersistentProperty<? extends Property> property = entity.getPropertyByName(sort);
            ignoreCase = isIgnoreCaseProperty(ignoreCase, property);
            addOrder(entity, query, queryRoot, criteriaBuilder, sort, order, ignoreCase);
        } else { // nested property
            String sortHead = sort.substring(0, firstDotPos);
            String sortTail = sort.substring(firstDotPos + 1);
            final PersistentProperty<? extends Property> property = entity.getPropertyByName(sortHead);
            if (property instanceof Embedded) {
                // embedded objects cannot reference entities (at time of writing), so no more recursion needed
                final PersistentProperty<? extends Property> associatedProperty = ((Embedded<?>) property).getAssociatedEntity().getPropertyByName(sortTail);
                ignoreCase = isIgnoreCaseProperty(ignoreCase, associatedProperty);
                addOrder(entity, query, queryRoot, criteriaBuilder, sort, order, ignoreCase);
            } else if (property instanceof Association) {
                final Association<? extends Property> a = (Association<? extends Property>) property;
                final Join join = getOrCreateSortJoin(queryRoot, sortHead, !(a instanceof ToMany) && GrailsHibernateUtil.isLeftJoinAssociationSort(entity));
                PersistentEntity associatedEntity = a.getAssociatedEntity();
                addOrderPossiblyNested(query, join, criteriaBuilder, associatedEntity, sortTail, order, ignoreCase); // Recurse on nested sort
            }
        }
    }

    /**
     * Returns the join of an association for a sort: the join or the fetch that the query already has
     * for it, made by the {@code fetch} argument or by an earlier sort property, or, failing that, a
     * new one, so that the association is joined once. With the {@code leftJoinAssociationSort} setting,
     * a new one is a left join, so that the sort keeps the rows where the association is null, except
     * for a collection, which is joined with an inner join.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Join getOrCreateSortJoin(From from, String associationName, boolean leftJoin) {
        for (Object existing : from.getJoins()) {
            Join join = (Join) existing;
            if (associationName.equals(join.getAttribute().getName())) {
                return join;
            }
        }
        for (Object existing : from.getFetches()) {
            if (existing instanceof Join && associationName.equals(((Join) existing).getAttribute().getName())) {
                return (Join) existing;
            }
        }
        return from.join(associationName, leftJoin ? JoinType.LEFT : JoinType.INNER);
    }

    private static boolean isIgnoreCaseProperty(boolean ignoreCase, PersistentProperty<? extends Property> persistentProperty) {
        if (ignoreCase && persistentProperty != null && persistentProperty.getType() != String.class) {
            ignoreCase = false;
        }
        return ignoreCase;
    }

    /**
     * Add order directly to criteria.
     */
    private static void addOrder(PersistentEntity entity,
                                 CriteriaQuery query,
                                 From queryRoot,
                                 CriteriaBuilder criteriaBuilder,
                                 String sort, String order, boolean ignoreCase) {
        if (sort.equalsIgnoreCase(entity.getIdentity().getName())) {
            Expression path = queryRoot;

            if (ignoreCase) {
                path = criteriaBuilder.upper(path);
            }
            if (DynamicFinder.ORDER_DESC.equals(order)) {
                addOrder(query, criteriaBuilder.desc(path));
            } else {
                addOrder(query, criteriaBuilder.asc(path));
            }
        } else {
            Expression path = queryRoot.get(sort);

            if (ignoreCase) {
                path = criteriaBuilder.upper(path);
            }
            if (DynamicFinder.ORDER_DESC.equals(order)) {
                addOrder(query, criteriaBuilder.desc(path));
            } else {
                addOrder(query, criteriaBuilder.asc(path));
            }
        }
    }

    /**
     * Adds an order to the orders the query already has: <code>orderBy</code> replaces them, so a sort
     * by more than one property would otherwise sort by the last one only.
     */
    @SuppressWarnings("unchecked")
    private static void addOrder(CriteriaQuery query, jakarta.persistence.criteria.Order order) {
        List<jakarta.persistence.criteria.Order> orders = new ArrayList<>(query.getOrderList());
        orders.add(order);
        query.orderBy(orders);
    }

    /**
     * Configures the criteria instance to cache based on the configured mapping.
     *
     * @param targetClass The target class
     * @param criteria    The criteria
     */
    private static void cacheCriteriaByMapping(Class<?> targetClass, Criteria criteria) {
        Mapping m = AbstractGrailsDomainBinder.getMapping(targetClass);
        if (m != null && m.getCache() != null && m.getCache().getEnabled()) {
            criteria.setCacheable(true);
        }
    }

    /**
     * Configures the criteria instance to cache based on the configured mapping.
     *
     * @param targetClass The target class
     * @param criteria    The criteria
     */
    private static void cacheCriteriaByMapping(Class<?> targetClass, Query criteria) {
        Mapping m = AbstractGrailsDomainBinder.getMapping(targetClass);
        if (m != null && m.getCache() != null && m.getCache().getEnabled()) {
            criteria.setCacheable(true);
        }
    }

    private static FlushMode convertFlushMode(Object object) {
        if (object == null) {
            return null;
        }
        if (object instanceof FlushMode) {
            return (FlushMode) object;
        }
        try {
            return FlushMode.valueOf(object.toString());
        } catch (IllegalArgumentException e) {
            return FlushMode.COMMIT;
        }
    }

    /**
     * Add order directly to criteria.
     */
    private static void addOrder(Criteria c, String sort, String order, boolean ignoreCase) {
        if (DynamicFinder.ORDER_DESC.equals(order)) {
            c.addOrder(ignoreCase ? Order.desc(sort).ignoreCase() : Order.desc(sort));
        } else {
            c.addOrder(ignoreCase ? Order.asc(sort).ignoreCase() : Order.asc(sort));
        }
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

}
