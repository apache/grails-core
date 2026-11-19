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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

import org.hibernate.query.criteria.HibernateCriteriaBuilder;

import org.springframework.core.convert.ConversionService;

import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria;
import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.criteria.FunctionCallingCriterion;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;

/**
 * Converts Grails Datastore query criteria into JPA {@link Predicate} instances.
 * Dispatches criteria to dedicated handlers while supporting custom criterion extension via {@link CriterionHandler}.
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
     */
    @FunctionalInterface
    public interface CriterionHandler {

        Predicate handle(
            AbstractQuery<?> criteriaQuery,
            From<?, ?> root,
            HibernateCriteriaBuilder criteriaBuilder,
            Query.Criterion criterion
        );
    }

    /**
     * SPI for contributing a {@link CriterionHandler} via {@link ServiceLoader}.
     *
     * <p>Create an implementation and register it in
     * {@code META-INF/services/org.grails.orm.hibernate.query.PredicateGenerator$CriterionHandlerProvider}
     * so that it is discovered automatically on first query execution, with no BootStrap registration needed.
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

    private static final Map<Class<? extends Query.QueryElement>, InternalCriterionHandler<?>> BUILTIN_HANDLERS =
        new ConcurrentHashMap<>();

    private static final AtomicBoolean SERVICE_LOADERS_INITIALIZED = new AtomicBoolean(false);

    static {
        registerBuiltinHandlers();
    }

    private static void registerBuiltinHandlers() {
        registerHandler(Query.IdEquals.class, (context, idEquals) -> {
            String propertyName = context.getEntity().getIdentity().getName();
            Expression<?> propertyPath = context.resolvePath(propertyName);
            return context.getCriteriaBuilder().equal(propertyPath, context.convertValue(propertyName, idEquals.getValue(), propertyPath));
        });
        registerHandler(SqlRestriction.class, new SqlRestrictionCriterionHandler());
        registerHandler(Query.DistinctProjection.class, (context, criterion) -> context.getCriteriaBuilder().conjunction());
        registerHandler(HibernateAlias.class, (context, criterion) -> null);
        registerHandler(DetachedAssociationCriteria.class, AssociationCriterionHandler::handleDetachedAssociationCriteria);
        registerHandler(HibernateAssociationQuery.class, AssociationCriterionHandler::handleHibernateAssociationQuery);
        registerHandler(FunctionCallingCriterion.class, new FunctionCallingCriterionHandler());
        registerHandler(Query.Exists.class, new ExistsCriterionHandler());
        registerHandler(Query.NotExists.class, new NotExistsCriterionHandler());
        registerHandler(Query.Junction.class, new JunctionCriterionHandler());
        registerHandler(Query.SubqueryCriterion.class, new SubqueryCriterionHandler());
        registerHandler(Query.PropertyNameCriterion.class, new PropertyNameCriterionHandler());
        registerHandler(Query.PropertyComparisonCriterion.class, new PropertyComparisonCriterionHandler());
        registerHandler(Query.PropertyCriterion.class, new PropertyCriterionHandler());
    }

    private static <T extends Query.QueryElement> void registerHandler(Class<T> type, InternalCriterionHandler<? super T> handler) {
        BUILTIN_HANDLERS.put(type, handler);
    }

    @SuppressWarnings("unchecked")
    private static InternalCriterionHandler<Query.QueryElement> findHandler(Class<?> criterionClass) {
        InternalCriterionHandler<?> handler = BUILTIN_HANDLERS.get(criterionClass);
        if (handler != null) {
            return (InternalCriterionHandler<Query.QueryElement>) handler;
        }

        for (Class<?> current = criterionClass.getSuperclass(); current != null && Query.QueryElement.class.isAssignableFrom(current); current = current.getSuperclass()) {
            InternalCriterionHandler<?> matched = BUILTIN_HANDLERS.get(current);
            if (matched != null) {
                return (InternalCriterionHandler<Query.QueryElement>) matched;
            }
        }
        return null;
    }

    private static void loadServiceProviders() {
        if (SERVICE_LOADERS_INITIALIZED.compareAndSet(false, true)) {
            ServiceLoader.load(CriterionHandlerProvider.class,
                    Thread.currentThread().getContextClassLoader())
                .forEach(p -> CUSTOM_HANDLERS.putIfAbsent(p.criterionType(), p.criterionHandler()));
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
     * Removes all registered custom criterion handlers and resets the ServiceLoader flag.
     * Useful for test cleanup.
     */
    public static void clearCustomCriterionHandlers() {
        CUSTOM_HANDLERS.clear();
        SERVICE_LOADERS_INITIALIZED.set(false);
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
                return customHandler.handle(criteriaQuery, root, criteriaBuilder, c);
            }
        }

        InternalCriterionHandler<Query.QueryElement> handler = findHandler(criterion.getClass());
        if (handler != null) {
            CriterionContext context = new CriterionContext(
                    criteriaQuery, root, fromsByProvider, entity, criteriaBuilder, conversionService, this);
            return handler.handle(context, criterion);
        }

        throw new IllegalArgumentException("Unsupported criterion: " + criterion);
    }

    public Predicate generate(
        AbstractQuery<?> cq, From<?, ?> root, List<Query.Criterion> criteriaList, JpaQueryContext tablesByName, GrailsHibernatePersistentEntity entity) {
        Predicate[] predicates = getPredicates(cq, root, criteriaList, tablesByName, entity);
        return criteriaBuilder.and(predicates);
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
}
