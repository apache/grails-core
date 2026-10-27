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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import groovy.lang.Closure;

import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;

import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaSubQuery;

import org.springframework.core.convert.ConversionService;

import grails.gorm.DetachedCriteria;
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria;
import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.query.Projections;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.api.QueryableCriteria;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;

/**
 * A class that generates predicates for a given list of criteria.
 *
 * @since 8.0
 */
public class PredicateGenerator {

    /**
     * Extension point for user-defined criterion handlers. Register a handler for a custom
     * {@link Query.Criterion} subclass to have it converted to a JPA {@link Predicate} during
     * query execution. Registered handlers are checked first, before any built-in criterion
     * handling, so a handler can also override built-in behavior.
     *
     * <p>Example registration (e.g., in {@code BootStrap.groovy}):
     * <pre>
     *     PredicateGenerator.registerCriterionHandler(MyCustomCriterion) { query, root, cb, criterion -&gt;
     *         def c = criterion as MyCustomCriterion
     *         cb.like(cb.cast(root.get(c.property), String), c.value)
     *     }
     * </pre>
     *
     * @param <C> the specific criterion type
     */
    @FunctionalInterface
    public interface CriterionHandler {

        Predicate handle(
            AbstractQuery<?> criteriaQuery,
            From<?, ?> root,
            HibernateCriteriaBuilder criteriaBuilder,
            Query.Criterion criterion
        );

        /**
         * Converts a criterion with the context of the query it belongs to. The context resolves a property path
         * the way the built-in criteria do: {@link QueryPathContext#getFullyQualifiedPath(String)} turns
         * {@code alias.property}, for an alias created with {@code createAlias}, or {@code association.property}
         * into a path on the join GORM created for that association, so a custom criterion and a GORM criterion on
         * the same association restrict the same row instead of each joining the association again. This default
         * ignores the context and calls {@link #handle(AbstractQuery, From, HibernateCriteriaBuilder, Query.Criterion)};
         * a handler that needs the context implements {@link ContextualCriterionHandler}.
         *
         * @param criteriaQuery the query or subquery the predicate is for
         * @param root the root of that query
         * @param criteriaBuilder the criteria builder
         * @param criterion the criterion to convert
         * @param context the context of the query: its root, its joins and its aliases
         * @return the predicate, or {@code null} to add none
         * @since 8.1
         */
        default Predicate handle(
            AbstractQuery<?> criteriaQuery,
            From<?, ?> root,
            HibernateCriteriaBuilder criteriaBuilder,
            Query.Criterion criterion,
            QueryPathContext context
        ) {
            return handle(criteriaQuery, root, criteriaBuilder, criterion);
        }
    }

    /**
     * A {@link CriterionHandler} that is given the context of the query, through which it resolves property paths
     * with {@link QueryPathContext#getFullyQualifiedPath(String)}. A closure with five parameters registered with
     * {@link #registerCriterionHandler(Class, Closure)} becomes one, with no {@code as}:
     * <pre>
     *     PredicateGenerator.registerCriterionHandler(MyCriterion) { query, root, cb, criterion, context -&gt;
     *         def c = criterion as MyCriterion
     *         cb.like(context.getFullyQualifiedPath(c.property) as Expression&lt;String&gt;, c.value as String)
     *     }
     * </pre>
     *
     * @since 8.1
     */
    @FunctionalInterface
    public interface ContextualCriterionHandler extends CriterionHandler {

        @Override
        Predicate handle(
            AbstractQuery<?> criteriaQuery,
            From<?, ?> root,
            HibernateCriteriaBuilder criteriaBuilder,
            Query.Criterion criterion,
            QueryPathContext context
        );

        /**
         * Called by code that has no context to give; the handler then receives {@code null} for it.
         */
        @Override
        default Predicate handle(
            AbstractQuery<?> criteriaQuery,
            From<?, ?> root,
            HibernateCriteriaBuilder criteriaBuilder,
            Query.Criterion criterion
        ) {
            return handle(criteriaQuery, root, criteriaBuilder, criterion, null);
        }
    }

    /**
     * Extension point for ordering a criteria query by something other than a property. Register a handler for a
     * {@link Query.Order} subclass to have it converted to a JPA {@link jakarta.persistence.criteria.Order}, in
     * the place the order has among the query's orders. The order reaches the query through
     * {@code order(Query.Order)} of the criteria builder or of a detached criteria.
     *
     * <pre>
     *     class ByRandom extends Query.Order {
     *         ByRandom() { super('random') }
     *     }
     *     PredicateGenerator.registerOrderHandler(ByRandom) { query, root, cb, order, context -&gt;
     *         cb.asc(cb.function('random', Double))
     *     }
     * </pre>
     *
     * @since 8.1
     */
    @FunctionalInterface
    public interface OrderHandler {

        /**
         * @param criteriaQuery the query, whose result type tells a query for the entity from a projection
         * @param root the root of the query
         * @param criteriaBuilder the criteria builder
         * @param order the order to convert
         * @param context the context of the query: its root, its joins and its aliases
         * @return the JPA order, or {@code null} to leave this order out of the query
         */
        jakarta.persistence.criteria.Order handle(
            CriteriaQuery<?> criteriaQuery,
            From<?, ?> root,
            HibernateCriteriaBuilder criteriaBuilder,
            Query.Order order,
            QueryPathContext context
        );
    }

    /**
     * SPI for contributing an {@link OrderHandler} via {@link ServiceLoader}. Register an implementation in
     * {@code META-INF/services/org.grails.orm.hibernate.query.PredicateGenerator$OrderHandlerProvider}; it is
     * discovered on first query execution, like a {@link CriterionHandlerProvider}.
     *
     * @since 8.1
     */
    public interface OrderHandlerProvider {

        Class<? extends Query.Order> orderType();

        OrderHandler orderHandler();
    }

    /**
     * SPI for contributing a {@link CriterionHandler} via {@link ServiceLoader}.
     *
     * <p>Create an implementation and register it in
     * {@code META-INF/services/org.grails.orm.hibernate.query.PredicateGenerator$CriterionHandlerProvider}
     * so that it is discovered automatically on first query execution — no BootStrap registration needed.
     *
     * <pre>
     *     // Example implementation (Groovy)
     *     class MyHandlerProvider implements PredicateGenerator.CriterionHandlerProvider {
     *         Class&lt;? extends Query.Criterion&gt; criterionType() { MyCriterion }
     *         PredicateGenerator.CriterionHandler criterionHandler() {
     *             { query, root, cb, criterion -&gt; ... } as PredicateGenerator.CriterionHandler
     *         }
     *     }
     * </pre>
     */
    public interface CriterionHandlerProvider {

        Class<? extends Query.Criterion> criterionType();

        CriterionHandler criterionHandler();
    }

    public static final Map<Class<? extends Query.Criterion>, CriterionHandler> CUSTOM_HANDLERS =
        new ConcurrentHashMap<>();

    private static final Map<Class<? extends Query.Order>, OrderHandler> CUSTOM_ORDER_HANDLERS =
        new ConcurrentHashMap<>();

    private static final Object SERVICE_LOADER_LOCK = new Object();

    private static volatile boolean serviceLoadersInitialized = false;

    /**
     * Loads the handler providers once. A caller that arrives while another thread is loading them waits until the
     * loading is complete, so no query decides on a handler before the discovered ones are registered. Discovery uses
     * {@code putIfAbsent}, so a discovered handler does not replace one that is already registered; registering in
     * code uses {@code put}, so a handler registered in code replaces a discovered one.
     */
    private static void loadServiceProviders() {
        if (serviceLoadersInitialized) {
            return;
        }
        synchronized (SERVICE_LOADER_LOCK) {
            if (serviceLoadersInitialized) {
                return;
            }
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            ServiceLoader.load(CriterionHandlerProvider.class, classLoader)
                .forEach(p -> CUSTOM_HANDLERS.putIfAbsent(p.criterionType(), p.criterionHandler()));
            ServiceLoader.load(OrderHandlerProvider.class, classLoader)
                .forEach(p -> CUSTOM_ORDER_HANDLERS.putIfAbsent(p.orderType(), p.orderHandler()));
            serviceLoadersInitialized = true;
        }
    }

    /**
     * Registers an {@link OrderHandler} for the given order type. The handler converts every order of exactly
     * that type; programmatic registration takes precedence over a {@link ServiceLoader}-discovered handler.
     *
     * @param type the exact order class to handle
     * @param handler the handler that converts the order to a JPA order
     * @since 8.1
     */
    public static void registerOrderHandler(Class<? extends Query.Order> type, OrderHandler handler) {
        CUSTOM_ORDER_HANDLERS.put(type, handler);
    }

    /**
     * Returns the handler registered for an order type, loading the {@link ServiceLoader} providers first.
     *
     * @param type the exact order class
     * @return the handler, or {@code null} when the type is ordered by its property as usual
     * @since 8.1
     */
    public static OrderHandler orderHandler(Class<? extends Query.Order> type) {
        loadServiceProviders();
        return CUSTOM_ORDER_HANDLERS.get(type);
    }

    /**
     * Removes all registered custom order handlers and resets the ServiceLoader flag. Useful for test cleanup.
     *
     * @since 8.1
     */
    public static void clearCustomOrderHandlers() {
        synchronized (SERVICE_LOADER_LOCK) {
            CUSTOM_ORDER_HANDLERS.clear();
            serviceLoadersInitialized = false;
        }
    }

    /**
     * Registers a {@link CriterionHandler} for the given criterion type. The handler is called
     * before any built-in criterion logic when a criterion of exactly that type is encountered.
     * Programmatic registration takes precedence over {@link ServiceLoader}-discovered handlers.
     *
     * @param type    the exact criterion class to handle
     * @param handler the handler that converts the criterion to a JPA {@link Predicate}
     */
    public static void registerCriterionHandler(Class<? extends Query.Criterion> type, CriterionHandler handler) {
        CUSTOM_HANDLERS.put(type, handler);
    }

    /**
     * Registers a closure as the {@link CriterionHandler} for the given criterion type. A closure with five
     * parameters, {@code query, root, criteriaBuilder, criterion, context}, becomes a
     * {@link ContextualCriterionHandler}; any other closure becomes a {@link CriterionHandler} and is called with
     * four arguments. This is what makes the two shapes work without {@code as}, as {@link #registerOrderHandler} does
     * for an order handler.
     *
     * @param type    the exact criterion class to handle
     * @param handler the closure that converts the criterion to a JPA {@link Predicate}
     * @since 8.1
     */
    public static void registerCriterionHandler(Class<? extends Query.Criterion> type, Closure<?> handler) {
        if (handler.getMaximumNumberOfParameters() == 5) {
            registerCriterionHandler(type, (ContextualCriterionHandler) (query, root, criteriaBuilder, criterion, context) ->
                (Predicate) handler.call(query, root, criteriaBuilder, criterion, context));
        } else {
            registerCriterionHandler(type, (CriterionHandler) (query, root, criteriaBuilder, criterion) ->
                (Predicate) handler.call(query, root, criteriaBuilder, criterion));
        }
    }

    /**
     * Removes all registered custom criterion handlers and resets the ServiceLoader flag.
     * Useful for test cleanup.
     */
    public static void clearCustomCriterionHandlers() {
        synchronized (SERVICE_LOADER_LOCK) {
            CUSTOM_HANDLERS.clear();
            serviceLoadersInitialized = false;
        }
    }

    private final HibernateCriteriaBuilder criteriaBuilder;
    private final ConversionService conversionService;

    public PredicateGenerator(HibernateCriteriaBuilder criteriaBuilder, ConversionService conversionService) {
        this.criteriaBuilder = criteriaBuilder;
        this.conversionService = conversionService;
    }

    public Predicate[] getPredicates(
        AbstractQuery<?> criteriaQuery,
        From<?, ?> root,
        List<? extends Query.QueryElement> criteria,
        JpaQueryContext fromsByProvider,
        GrailsHibernatePersistentEntity entity) {
        return criteria.stream()
            .map(c -> handleCriterion(criteriaQuery, root, fromsByProvider, entity, c))
            .filter(Objects::nonNull)
            .toList()
            .toArray(new Predicate[0]);
    }

    private boolean isCollectionPath(Expression<?> expression) {
        if (expression instanceof jakarta.persistence.criteria.Path<?> path) {
            return Collection.class.isAssignableFrom(path.getJavaType());
        }
        return false;
    }

    public Predicate handleCriterion(
        AbstractQuery<?> criteriaQuery,
        From<?, ?> root,
        JpaQueryContext fromsByProvider,
        GrailsHibernatePersistentEntity entity,
        Query.QueryElement criterion) {

        loadServiceProviders();

        if (criterion instanceof Query.Criterion c) {
            CriterionHandler customHandler = CUSTOM_HANDLERS.get(c.getClass());
            if (customHandler != null) {
                return customHandler.handle(criteriaQuery, root, criteriaBuilder, c, fromsByProvider);
            }
        }

        if (criterion instanceof SqlRestriction sqlRestriction) {
            return handleSqlRestriction(root, fromsByProvider, entity, sqlRestriction);
        } else if (criterion instanceof Query.Junction junction) {
            return handleJunction(criteriaQuery, root, fromsByProvider, entity, junction);
        } else if (criterion instanceof Query.DistinctProjection) {
            return criteriaBuilder.conjunction();
        } else if (criterion instanceof DetachedAssociationCriteria<?> c) {
            return handleAssociationCriteria(criteriaQuery, fromsByProvider, c);
        } else if (criterion instanceof HibernateAssociationQuery haq) {
            return handleHibernateAssociationQuery(criteriaQuery, fromsByProvider, haq);
        } else if (criterion instanceof Query.SubqueryCriterion c) {
            return handleSubqueryCriterion(criteriaQuery, root, fromsByProvider, entity, c);
        } else if (criterion instanceof Query.IdEquals idEquals) {
            String propertyName = entity.getIdentity().getName();
            Expression<?> propertyPath = fromsByProvider.getFullyQualifiedExpression(propertyName);
            return criteriaBuilder.equal(propertyPath, convertValue(entity, propertyName, idEquals.getValue(), propertyPath));
        } else if (criterion instanceof Query.PropertyCriterion pc) {
            return handlePropertyCriterion(criteriaQuery, root, fromsByProvider, entity, pc);
        } else if (criterion instanceof Query.PropertyComparisonCriterion c) {
            return handlePropertyComparisonCriterion(fromsByProvider, c);
        } else if (criterion instanceof Query.PropertyNameCriterion c) {
            return handlePropertyNameCriterion(fromsByProvider, c);
        } else if (criterion instanceof Query.Exists c) {
            return handleExists(
                criteriaQuery, fromsByProvider, c);
        } else if (criterion instanceof Query.NotExists c) {
            return criteriaBuilder.not(handleExists(
                criteriaQuery, fromsByProvider, new Query.Exists(c.getSubquery())));
        } else if (criterion instanceof HibernateAlias) {
            return null; // Metadata only, handled by JpaQueryContext
        }
        throw new IllegalArgumentException("Unsupported criterion: " + criterion);
    }

    @SuppressWarnings("unchecked")
    private Predicate handleSubqueryCriterion(AbstractQuery<?> criteriaQuery, From<?, ?> root, JpaQueryContext fromsByProvider, GrailsHibernatePersistentEntity entity, Query.SubqueryCriterion c) {
        Expression<?> propertyPath = fromsByProvider.getFullyQualifiedExpression(c.getProperty());
        QueryableCriteria<?> qc = c.getValue();

        // If it's a comparison criterion, we expect the subquery to return the same type as the property
        Class<?> expectedType = propertyPath != null ? propertyPath.getJavaType() : qc.getPersistentEntity().getJavaClass();

        // If the subquery has no projections, we default to projecting the SAME property name if available on the subquery entity
        if (qc.getProjections().isEmpty()) {
            PersistentProperty prop = qc.getPersistentEntity().getPropertyByName(c.getProperty());
            if (prop != null) {
                ((QueryableCriteria) qc).getProjections().add(Projections.property(c.getProperty()));
            }
        }

        Query.ProjectionList projectionList = new Query.ProjectionList();
        for (Query.Projection p : qc.getProjections()) {
            projectionList.add(p);
        }

        Subquery<?> subquery = criteriaQuery.subquery(expectedType);
        var creator = new JpaCriteriaQueryCreator(projectionList, criteriaBuilder, (GrailsHibernatePersistentEntity) qc.getPersistentEntity(), (DetachedCriteria) qc, conversionService);
        creator.setParentContext(fromsByProvider);

        creator.populateSubquery((JpaSubQuery) subquery);

        if (c instanceof Query.EqualsAll) {
            return criteriaBuilder.equal(propertyPath, (Expression) criteriaBuilder.all(subquery));
        } else if (c instanceof Query.NotEqualsAll) {
            return criteriaBuilder.notEqual(propertyPath, (Expression) criteriaBuilder.all(subquery));
        } else if (c instanceof Query.GreaterThanAll) {
            return criteriaBuilder.greaterThan((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.all(subquery));
        } else if (c instanceof Query.GreaterThanSome) {
            return criteriaBuilder.greaterThan((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.some(subquery));
        } else if (c instanceof Query.GreaterThanEqualsAll) {
            return criteriaBuilder.greaterThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.all(subquery));
        } else if (c instanceof Query.GreaterThanEqualsSome) {
            return criteriaBuilder.greaterThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.some(subquery));
        } else if (c instanceof Query.LessThanAll) {
            return criteriaBuilder.lessThan((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.all(subquery));
        } else if (c instanceof Query.LessThanSome) {
            return criteriaBuilder.lessThan((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.some(subquery));
        } else if (c instanceof Query.LessThanEqualsAll) {
            return criteriaBuilder.lessThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.all(subquery));
        } else if (c instanceof Query.LessThanEqualsSome) {
            return criteriaBuilder.lessThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.some(subquery));
        } else if (c instanceof Query.NotIn) {
            return criteriaBuilder.not(propertyPath.in(subquery));
        }

        throw new UnsupportedOperationException("Unsupported subquery criterion: " + c.getClass().getName());
    }

    private Predicate handleJunction(
        AbstractQuery<?> criteriaQuery,
        From<?, ?> root_,
        JpaQueryContext fromsByProvider,
        GrailsHibernatePersistentEntity entity,
        Query.Junction junction) {
        List<Query.Criterion> criteriaList = junction.getCriteria();
        Predicate[] predicates = getPredicates(criteriaQuery, root_, criteriaList, fromsByProvider, entity);
        if (junction instanceof Query.Conjunction) {
            return criteriaBuilder.and(predicates);
        } else if (junction instanceof Query.Disjunction) {
            return criteriaBuilder.or(predicates);
        } else if (junction instanceof Query.Negation) {
            return criteriaBuilder.not(criteriaBuilder.or(predicates));
        }
        throw new IllegalArgumentException("Unsupported junction: " + junction);
    }

    private Predicate handleAssociationCriteria(
        AbstractQuery<?> criteriaQuery,
        JpaQueryContext fromsByProvider,
        DetachedAssociationCriteria<?> associationCriteria) {
        String associationName = associationCriteria.getAssociationPath();
        From<?, ?> associationRoot = fromsByProvider.getFrom(associationName);
        if (associationRoot == null) {
            // Check if we already have it in our parent or alias map
            Expression<?> expr = fromsByProvider.getFullyQualifiedExpression(associationName);
            if (expr instanceof From<?, ?> from) {
                associationRoot = from;
            } else {
                associationRoot = fromsByProvider.getRoot().join(associationName);
                fromsByProvider.addFrom(associationName, associationRoot);
            }
        }

        // Create a nested context for this association
        JpaQueryContext nestedContext = new JpaQueryContext(fromsByProvider, null, associationRoot);

        GrailsHibernatePersistentEntity associatedEntity = (GrailsHibernatePersistentEntity) associationCriteria.getAssociation().getAssociatedEntity();
        List<Query.Criterion> criteriaList = associationCriteria.getCriteria();
        return criteriaBuilder.and(getPredicates(criteriaQuery, associationRoot, criteriaList, nestedContext, associatedEntity));
    }

    private Predicate handleHibernateAssociationQuery(
        AbstractQuery<?> criteriaQuery,
        JpaQueryContext fromsByProvider,
        HibernateAssociationQuery associationQuery) {
        String associationName = associationQuery.associationPath;
        From<?, ?> associationRoot = fromsByProvider.getFrom(associationName);
        if (associationRoot == null) {
            Expression<?> expr = fromsByProvider.getFullyQualifiedExpression(associationName);
            if (expr instanceof From<?, ?> from) {
                associationRoot = from;
            } else {
                associationRoot = fromsByProvider.getRoot().join(associationName, JoinType.INNER);
                fromsByProvider.addFrom(associationName, associationRoot);
            }
        }

        // Create a nested context for this association
        JpaQueryContext nestedContext = new JpaQueryContext(fromsByProvider, null, associationRoot);

        GrailsHibernatePersistentEntity associatedEntity = associationQuery.getEntity();
        List<Query.Criterion> criteriaList = associationQuery.getAssociationCriteria();
        return criteriaBuilder.and(getPredicates(criteriaQuery, associationRoot, criteriaList, nestedContext, associatedEntity));
    }

    @SuppressWarnings("unchecked")
    private Predicate handlePropertyCriterion(
        AbstractQuery<?> criteriaQuery,
        From<?, ?> root,
        JpaQueryContext fromsByProvider,
        GrailsHibernatePersistentEntity entity,
        Query.PropertyCriterion pc) {
        String propertyName = pc.getProperty();
        Expression<?> propertyPath = fromsByProvider.getFullyQualifiedExpression(propertyName);
        if (propertyPath == null) {
            throw new ConfigurationException("Cannot use comparison criteria on non-existent property [" + propertyName + "] of class [" + entity.getJavaClass().getName() + "]");
        }

        if (pc instanceof EqualsIgnoreCase) {
            return handleEqualsIgnoreCase(criteriaQuery, pc, propertyPath, fromsByProvider, entity);
        } else if (pc instanceof Query.Equals) {
            return handleEquals(criteriaQuery, pc, propertyPath, fromsByProvider, entity);
        } else if (pc instanceof Query.NotEquals) {
            return handleNotEquals(criteriaQuery, pc, propertyPath, fromsByProvider, entity);
        } else if (pc instanceof Query.ILike) {
            return criteriaBuilder.ilike((Expression<String>) propertyPath, (String) convertValue(entity, propertyName, pc.getValue(), propertyPath));
        } else if (pc instanceof Query.RLike rLike) {
            return handleRLike((Expression<String>) propertyPath, rLike);
        } else if (pc instanceof Query.Like) {
            return criteriaBuilder.like((Expression<String>) propertyPath, (String) convertValue(entity, propertyName, pc.getValue(), propertyPath));
        } else if (pc instanceof Query.GreaterThan) {
            Object value = convertComparisonValue(entity, propertyName, pc.getValue(), fromsByProvider, propertyPath);
            return value instanceof Comparable<?> comparable ?
                criteriaBuilder.greaterThan((Expression<? extends Comparable>) propertyPath, (Comparable) comparable) :
                criteriaBuilder.greaterThan((Expression<? extends Comparable>) propertyPath, (Expression) asExpression(value, propertyPath, fromsByProvider));
        } else if (pc instanceof Query.GreaterThanEquals) {
            Object value = convertComparisonValue(entity, propertyName, pc.getValue(), fromsByProvider, propertyPath);
            return value instanceof Comparable<?> comparable ?
                criteriaBuilder.greaterThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Comparable) comparable) :
                criteriaBuilder.greaterThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression) asExpression(value, propertyPath, fromsByProvider));
        } else if (pc instanceof Query.LessThan) {
            Object value = convertComparisonValue(entity, propertyName, pc.getValue(), fromsByProvider, propertyPath);
            return value instanceof Comparable<?> comparable ?
                criteriaBuilder.lessThan((Expression<? extends Comparable>) propertyPath, (Comparable) comparable) :
                criteriaBuilder.lessThan((Expression<? extends Comparable>) propertyPath, (Expression) asExpression(value, propertyPath, fromsByProvider));
        } else if (pc instanceof Query.LessThanEquals) {
            Object value = convertComparisonValue(entity, propertyName, pc.getValue(), fromsByProvider, propertyPath);
            return value instanceof Comparable<?> comparable ?
                criteriaBuilder.lessThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Comparable) comparable) :
                criteriaBuilder.lessThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression) asExpression(value, propertyPath, fromsByProvider));
        } else if (pc instanceof Query.In) {
            Object value = pc.getValue();
            if (value instanceof QueryableCriteria qc) {
                Class<?> expectedType = propertyPath != null ? propertyPath.getJavaType() : qc.getPersistentEntity().getJavaClass();

                // If the subquery has no projections, we default to projecting the SAME property name if available on the subquery entity
                if (qc.getProjections().isEmpty() && propertyPath != null) {
                    PersistentProperty prop = qc.getPersistentEntity().getPropertyByName(propertyName);
                    if (prop != null) {
                        ((QueryableCriteria) qc).getProjections().add(Projections.property(propertyName));
                    }
                }

                Query.ProjectionList projectionList = new Query.ProjectionList();
                for (Object p : qc.getProjections()) {
                    projectionList.add((Query.Projection) p);
                }

                Subquery<?> subquery = criteriaQuery.subquery(expectedType);
                var creator = new JpaCriteriaQueryCreator(projectionList, criteriaBuilder, (GrailsHibernatePersistentEntity) qc.getPersistentEntity(), (DetachedCriteria) qc, conversionService);
                creator.setParentContext(fromsByProvider);

                creator.populateSubquery((JpaSubQuery) subquery);
                return propertyPath.in(subquery);
            }

            Collection<?> collection = value instanceof Collection ? (Collection<?>) value : Collections.singletonList(value);
            List<Object> converted = collection.stream()
                .map(v -> convertValue(entity, propertyName, v, propertyPath))
                .collect(Collectors.toList());

            if (isCollectionPath(propertyPath)) {
                // For collection properties, we use "member of" for each value joined with OR
                if (converted.isEmpty()) {
                    return criteriaBuilder.disjunction(); // Always false for empty IN on collection
                }
                Predicate[] memberOfPredicates = converted.stream()
                    .map(v -> criteriaBuilder.isMember((Object) v, (Expression) propertyPath))
                    .toArray(Predicate[]::new);
                return criteriaBuilder.or(memberOfPredicates);
            }

            return propertyPath.in(converted);
        } else if (pc instanceof Query.Between between) {
            return criteriaBuilder.between((Expression<? extends Comparable>) propertyPath, (Comparable) convertValue(entity, propertyName, between.getFrom(), propertyPath), (Comparable) convertValue(entity, propertyName, between.getTo(), propertyPath));
        } else if (pc instanceof Query.SizeEquals) {
            return criteriaBuilder.equal(criteriaBuilder.size((Expression<java.util.Collection<?>>) propertyPath), (Integer) pc.getValue());
        } else if (pc instanceof Query.SizeNotEquals) {
            return criteriaBuilder.notEqual(criteriaBuilder.size((Expression<java.util.Collection<?>>) propertyPath), (Integer) pc.getValue());
        } else if (pc instanceof Query.SizeGreaterThan) {
            return criteriaBuilder.greaterThan(criteriaBuilder.size((Expression<java.util.Collection<?>>) propertyPath), (Integer) pc.getValue());
        } else if (pc instanceof Query.SizeGreaterThanEquals) {
            return criteriaBuilder.greaterThanOrEqualTo(criteriaBuilder.size((Expression<java.util.Collection<?>>) propertyPath), (Integer) pc.getValue());
        } else if (pc instanceof Query.SizeLessThan) {
            return criteriaBuilder.lessThan(criteriaBuilder.size((Expression<java.util.Collection<?>>) propertyPath), (Integer) pc.getValue());
        } else if (pc instanceof Query.SizeLessThanEquals) {
            return criteriaBuilder.lessThanOrEqualTo(criteriaBuilder.size((Expression<java.util.Collection<?>>) propertyPath), (Integer) pc.getValue());
        }

        throw new UnsupportedOperationException("Unsupported criterion: " + pc.getClass().getName());
    }

    /**
     * Returns a column of the entity's own table, whose table alias replaces {@code {alias}} in native SQL:
     * its identifier column, or the first one of a composite identifier. An identifier that is an association
     * stands for its foreign key columns, which are resolved the same way from the associated entity.
     */
    private static Expression<?> aliasColumn(From<?, ?> root, GrailsHibernatePersistentEntity entity) {
        Path<?> path = root;
        PersistentEntity current = entity;
        while (true) {
            PersistentProperty<?> identity = firstIdentifierProperty(current);
            if (identity == null) {
                throw new ConfigurationException("Cannot use {alias} in native SQL on class [" + entity.getJavaClass().getName() + "] without an identifier");
            }
            path = path.get(identity.getName());
            if (!(identity instanceof Association<?> association) || association.getAssociatedEntity() == null) {
                return path;
            }
            current = association.getAssociatedEntity();
        }
    }

    private static PersistentProperty<?> firstIdentifierProperty(PersistentEntity entity) {
        if (entity.getIdentity() != null) {
            return entity.getIdentity();
        }
        PersistentProperty<?>[] compositeIdentity = entity.getCompositeIdentity();
        return compositeIdentity == null || compositeIdentity.length == 0 ? null : compositeIdentity[0];
    }

    /**
     * Returns the first arguments of a function that renders native SQL: the SQL as a literal and, if it contains
     * {@code {alias}}, a column of the entity's own table whose table alias replaces it.
     */
    static List<Expression<?>> nativeSqlArguments(
            CriteriaBuilder criteriaBuilder, String sql, From<?, ?> root, GrailsHibernatePersistentEntity entity) {
        List<Expression<?>> arguments = new ArrayList<>();
        arguments.add(criteriaBuilder.literal(sql));
        if (sql.contains(GrailsSqlRestrictionFunction.ALIAS_PLACEHOLDER)) {
            arguments.add(aliasColumn(root, entity));
        }
        return arguments;
    }

    private Predicate handleSqlRestriction(From<?, ?> root, JpaQueryContext context, GrailsHibernatePersistentEntity entity, SqlRestriction restriction) {
        List<Expression<?>> arguments = nativeSqlArguments(criteriaBuilder, restriction.sql(), root, entity);
        for (Object value : restriction.values()) {
            ParameterExpression<?> parameter = criteriaBuilder.parameter(value.getClass());
            context.bindParameter(parameter, value);
            arguments.add(parameter);
        }
        return criteriaBuilder.isTrue(criteriaBuilder.function(
                GrailsSqlRestrictionFunction.NAME, Boolean.class, arguments.toArray(new Expression<?>[0])));
    }

    private Predicate handleRLike(Expression<String> propertyPath, Query.RLike c) {
        String pattern = c.getValue().toString().replaceAll("^/|/$", "");
        return criteriaBuilder.equal(
            criteriaBuilder.function(
                GrailsRLikeFunctionContributor.RLIKE,
                Boolean.class,
                propertyPath,
                criteriaBuilder.literal(pattern)),
            true);
    }

    @SuppressWarnings("unchecked")
    private Predicate handlePropertyComparisonCriterion(JpaQueryContext fromsByProvider, Query.PropertyComparisonCriterion c) {
        Expression<?> propertyPath = fromsByProvider.getFullyQualifiedExpression(c.getProperty());
        Expression<?> otherPropertyPath = fromsByProvider.getFullyQualifiedExpression(c.getOtherProperty());

        if (propertyPath == null || otherPropertyPath == null) {
            return null;
        }

        if (c instanceof Query.EqualsProperty) {
            return criteriaBuilder.equal(propertyPath, otherPropertyPath);
        } else if (c instanceof Query.NotEqualsProperty) {
            return criteriaBuilder.notEqual(propertyPath, otherPropertyPath);
        } else if (c instanceof Query.GreaterThanProperty) {
            return criteriaBuilder.greaterThan((Expression<? extends Comparable>) propertyPath, (Expression<? extends Comparable>) otherPropertyPath);
        } else if (c instanceof Query.GreaterThanEqualsProperty) {
            return criteriaBuilder.greaterThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression<? extends Comparable>) otherPropertyPath);
        } else if (c instanceof Query.LessThanProperty) {
            return criteriaBuilder.lessThan((Expression<? extends Comparable>) propertyPath, (Expression<? extends Comparable>) otherPropertyPath);
        } else if (c instanceof Query.LessThanEqualsProperty) {
            return criteriaBuilder.lessThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression<? extends Comparable>) otherPropertyPath);
        }

        throw new UnsupportedOperationException("Unsupported property comparison criterion: " + c.getClass().getName());
    }

    private Predicate handlePropertyNameCriterion(JpaQueryContext fromsByProvider, Query.PropertyNameCriterion c) {
        Expression<?> propertyPath = fromsByProvider.getFullyQualifiedExpression(c.getProperty());
        if (((Object) c) instanceof Query.IsNull) {
            return criteriaBuilder.isNull(propertyPath);
        } else if (((Object) c) instanceof Query.IsNotNull) {
            return criteriaBuilder.isNotNull(propertyPath);
        } else if (((Object) c) instanceof Query.IsEmpty) {
            return criteriaBuilder.isEmpty((Expression<java.util.Collection<?>>) propertyPath);
        } else if (((Object) c) instanceof Query.IsNotEmpty) {
            return criteriaBuilder.isNotEmpty((Expression<java.util.Collection<?>>) propertyPath);
        }
        throw new UnsupportedOperationException("Unsupported property name criterion: " + c.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    private Predicate handleEquals(AbstractQuery<?> criteriaQuery, Query.PropertyCriterion pc, Expression<?> propertyPath, JpaQueryContext fromsByProvider, GrailsHibernatePersistentEntity entity) {
        if (pc.getValue() instanceof QueryableCriteria qc) {
            Class<?> expectedType = propertyPath != null ? propertyPath.getJavaType() : qc.getPersistentEntity().getJavaClass();
            Subquery<?> subquery = criteriaQuery.subquery(expectedType);
            var creator = new JpaCriteriaQueryCreator(new Query.ProjectionList(), criteriaBuilder, (GrailsHibernatePersistentEntity) qc.getPersistentEntity(), (DetachedCriteria) qc, conversionService);
            creator.setParentContext(fromsByProvider);

            if (qc.getProjections().isEmpty() && propertyPath != null) {
                String propertyName = pc.getProperty();
                if (propertyName.contains(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)) {
                    propertyName = propertyName.split(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)[1];
                }
                PersistentProperty prop = qc.getPersistentEntity().getPropertyByName(propertyName);
                if (prop != null) {
                    qc.getProjections().add(Projections.property(propertyName));
                }
            }

            creator.populateSubquery((JpaSubQuery) subquery);
            return criteriaBuilder.equal(propertyPath, subquery);
        } else {
            return criteriaBuilder.equal(propertyPath, convertComparisonValue(entity, pc.getProperty(), pc.getValue(), fromsByProvider, propertyPath));
        }
    }

    /**
     * Compares a {@link String} property with the value in lower case, as Hibernate 5's
     * {@code Restrictions.eq(...).ignoreCase()} did. Any other property is compared with plain equality.
     */
    @SuppressWarnings("unchecked")
    private Predicate handleEqualsIgnoreCase(AbstractQuery<?> criteriaQuery, Query.PropertyCriterion pc, Expression<?> propertyPath, JpaQueryContext fromsByProvider, GrailsHibernatePersistentEntity entity) {
        Object value = pc.getValue();
        if (!String.class.equals(propertyPath.getJavaType()) || !(value instanceof CharSequence)) {
            return handleEquals(criteriaQuery, pc, propertyPath, fromsByProvider, entity);
        }
        return criteriaBuilder.equal(
                criteriaBuilder.lower((Expression<String>) propertyPath),
                value.toString().toLowerCase(Locale.ROOT));
    }

    @SuppressWarnings("unchecked")
    private Predicate handleNotEquals(
        AbstractQuery<?> criteriaQuery,
        Query.PropertyCriterion pc,
        Expression<?> propertyPath,
        JpaQueryContext fromsByProvider,
        GrailsHibernatePersistentEntity entity) {
        Object value = pc.getValue();
        if (value == null) {
            return criteriaBuilder.isNotNull(propertyPath);
        }
        if (value instanceof QueryableCriteria qc) {
            Class<?> expectedType = propertyPath != null ? propertyPath.getJavaType() : qc.getPersistentEntity().getJavaClass();
            Subquery<?> subquery = criteriaQuery.subquery(expectedType);
            var creator = new JpaCriteriaQueryCreator(new Query.ProjectionList(), criteriaBuilder, (GrailsHibernatePersistentEntity) qc.getPersistentEntity(), (DetachedCriteria) qc, conversionService);
            creator.setParentContext(fromsByProvider);

            if (qc.getProjections().isEmpty() && propertyPath != null) {
                String propertyName = pc.getProperty();
                if (propertyName.contains(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)) {
                    propertyName = propertyName.split(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)[1];
                }
                PersistentProperty prop = qc.getPersistentEntity().getPropertyByName(propertyName);
                if (prop != null) {
                    qc.getProjections().add(Projections.property(propertyName));
                }
            }

            creator.populateSubquery((JpaSubQuery) subquery);
            return criteriaBuilder.or(criteriaBuilder.notEqual(propertyPath, subquery), criteriaBuilder.isNull(propertyPath));
        }
        return criteriaBuilder.or(
            criteriaBuilder.notEqual(propertyPath, convertComparisonValue(entity, pc.getProperty(), value, fromsByProvider, propertyPath)),
            criteriaBuilder.isNull(propertyPath));
    }

    @SuppressWarnings("unchecked")
    private Predicate handleExists(
        AbstractQuery<?> criteriaQuery,
        JpaQueryContext fromsByProvider,
        Query.Exists exists) {
        QueryableCriteria subqueryCriteria = exists.getSubquery();
        GrailsHibernatePersistentEntity subqueryEntity = (GrailsHibernatePersistentEntity) subqueryCriteria.getPersistentEntity();
        Subquery<?> subquery = criteriaQuery.subquery(subqueryEntity.getJavaClass());
        var creator = new JpaCriteriaQueryCreator(new Query.ProjectionList(), criteriaBuilder, subqueryEntity, (DetachedCriteria) subqueryCriteria, conversionService);
        creator.setParentContext(fromsByProvider);
        creator.populateSubquery((JpaSubQuery) subquery);
        return criteriaBuilder.exists(subquery);
    }

    private Object convertValue(GrailsHibernatePersistentEntity entity, String propertyName, Object value, Expression<?> propertyPath) {
        if (value == null) {
            throw new ConfigurationException("Null value for property [" + propertyName + "] is not allowed in comparison criteria.");
        }

        Class<?> targetType = propertyPath != null ? propertyPath.getJavaType() : null;

        if (targetType == null && entity != null) {
            HibernatePersistentProperty prop = (HibernatePersistentProperty) entity.getPropertyByName(propertyName);
            if (prop != null) {
                targetType = prop.getType();
            }
        }

        if (targetType != null && Collection.class.isAssignableFrom(targetType) && entity != null) {
            HibernatePersistentProperty prop = (HibernatePersistentProperty) entity.getPropertyByName(propertyName);
            if (prop instanceof HibernateToManyProperty toMany) {
                targetType = toMany.getComponentType();
            }
        }

        if (targetType != null && conversionService.canConvert(value.getClass(), targetType)) {
            if (!Collection.class.isAssignableFrom(targetType) || Collection.class.isAssignableFrom(value.getClass())) {
                return conversionService.convert(value, targetType);
            }
        }
        return value;
    }

    /**
     * Returns an arithmetic expression for a {@link PropertyArithmetic}, otherwise the converted value itself.
     * A plain value is not wrapped in a literal, so the comparison overload that takes it infers its type from
     * the property path, as HQL does for a parameter. A literal is typed from the value's class instead, which a
     * property mapped with a custom {@code UserType} cannot be compared with when that class is
     * {@link java.io.Serializable}.
     */
    @SuppressWarnings("unchecked")
    private Object convertComparisonValue(GrailsHibernatePersistentEntity entity, String propertyName, Object value, JpaQueryContext context, Expression<?> propertyPath) {
        if (value instanceof PropertyArithmetic pa) {
            Expression<Number> left = (Expression<Number>) context.getFullyQualifiedExpression(pa.propertyName());
            Expression<Number> right = (Expression<Number>) criteriaBuilder.literal(pa.operand());

            return switch (pa.operator()) {
                case MULTIPLY -> criteriaBuilder.prod(left, right);
                case DIVIDE -> criteriaBuilder.quot(left, right);
                case ADD -> criteriaBuilder.sum(left, right);
                case SUBTRACT -> criteriaBuilder.diff(left, right);
            };
        }
        return convertValue(entity, propertyName, value, propertyPath);
    }

    /**
     * Returns the value as an expression for a comparison overload that takes an expression, which the JPA ordering
     * overloads need for a value that is not {@link Comparable}. The value becomes a criteria parameter of the
     * property's Java type, bound once the query is created, so Hibernate types it from the property it is compared
     * with, as it does for an HQL parameter.
     */
    private Expression<?> asExpression(Object value, Expression<?> propertyPath, JpaQueryContext context) {
        if (value instanceof Expression<?> expression) {
            return expression;
        }
        ParameterExpression<?> parameter = criteriaBuilder.parameter(propertyPath.getJavaType());
        context.bindParameter(parameter, value);
        return parameter;
    }

    public Predicate generate(
        AbstractQuery<?> cq, From<?, ?> root, List<Query.Criterion> criteriaList, JpaQueryContext tablesByName, GrailsHibernatePersistentEntity entity) {
        Predicate[] predicates = getPredicates(cq, root, criteriaList, tablesByName, entity);
        return criteriaBuilder.and(predicates);
    }
}
