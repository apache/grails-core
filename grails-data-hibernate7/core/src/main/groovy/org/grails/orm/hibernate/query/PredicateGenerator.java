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
 * Dispatches criteria to dedicated {@link InternalCriterionHandler} implementations
 * while supporting custom criterion extension via {@link CriterionHandler}.
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public class PredicateGenerator {

    /**
     * Handler SPI for converting a custom {@link Query.Criterion} to a JPA {@link Predicate}.
     *
     * <pre>
     *     PredicateGenerator.registerCriterionHandler(MyCustomCriterion) { query, root, cb, criterion -&gt;
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
        registerHandler(Query.Conjunction.class, new JunctionCriterionHandler());
        registerHandler(Query.Disjunction.class, new JunctionCriterionHandler());
        registerHandler(Query.Negation.class, new JunctionCriterionHandler());
        registerHandler(Query.SubqueryCriterion.class, new SubqueryCriterionHandler());
        registerHandler(Query.EqualsAll.class, new SubqueryCriterionHandler());
        registerHandler(Query.NotEqualsAll.class, new SubqueryCriterionHandler());
        registerHandler(Query.GreaterThanAll.class, new SubqueryCriterionHandler());
        registerHandler(Query.GreaterThanSome.class, new SubqueryCriterionHandler());
        registerHandler(Query.GreaterThanEqualsAll.class, new SubqueryCriterionHandler());
        registerHandler(Query.GreaterThanEqualsSome.class, new SubqueryCriterionHandler());
        registerHandler(Query.LessThanAll.class, new SubqueryCriterionHandler());
        registerHandler(Query.LessThanSome.class, new SubqueryCriterionHandler());
        registerHandler(Query.LessThanEqualsAll.class, new SubqueryCriterionHandler());
        registerHandler(Query.LessThanEqualsSome.class, new SubqueryCriterionHandler());
        registerHandler(Query.NotIn.class, new SubqueryCriterionHandler());
        registerHandler(Query.PropertyNameCriterion.class, new PropertyNameCriterionHandler());
        registerHandler(Query.IsNull.class, new PropertyNameCriterionHandler());
        registerHandler(Query.IsNotNull.class, new PropertyNameCriterionHandler());
        registerHandler(Query.IsEmpty.class, new PropertyNameCriterionHandler());
        registerHandler(Query.IsNotEmpty.class, new PropertyNameCriterionHandler());
        registerHandler(Query.PropertyComparisonCriterion.class, new PropertyComparisonCriterionHandler());
        registerHandler(Query.EqualsProperty.class, new PropertyComparisonCriterionHandler());
        registerHandler(Query.NotEqualsProperty.class, new PropertyComparisonCriterionHandler());
        registerHandler(Query.GreaterThanProperty.class, new PropertyComparisonCriterionHandler());
        registerHandler(Query.GreaterThanEqualsProperty.class, new PropertyComparisonCriterionHandler());
        registerHandler(Query.LessThanProperty.class, new PropertyComparisonCriterionHandler());
        registerHandler(Query.LessThanEqualsProperty.class, new PropertyComparisonCriterionHandler());
        registerHandler(Query.PropertyCriterion.class, new PropertyCriterionHandler());
        registerHandler(Query.Equals.class, new PropertyCriterionHandler());
        registerHandler(Query.NotEquals.class, new PropertyCriterionHandler());
        registerHandler(Query.Like.class, new PropertyCriterionHandler());
        registerHandler(Query.ILike.class, new PropertyCriterionHandler());
        registerHandler(Query.RLike.class, new PropertyCriterionHandler());
        registerHandler(Query.GreaterThan.class, new PropertyCriterionHandler());
        registerHandler(Query.GreaterThanEquals.class, new PropertyCriterionHandler());
        registerHandler(Query.LessThan.class, new PropertyCriterionHandler());
        registerHandler(Query.LessThanEquals.class, new PropertyCriterionHandler());
        registerHandler(Query.Between.class, new PropertyCriterionHandler());
        registerHandler(Query.In.class, new PropertyCriterionHandler());
        registerHandler(Query.SizeEquals.class, new PropertyCriterionHandler());
        registerHandler(Query.SizeNotEquals.class, new PropertyCriterionHandler());
        registerHandler(Query.SizeGreaterThan.class, new PropertyCriterionHandler());
        registerHandler(Query.SizeGreaterThanEquals.class, new PropertyCriterionHandler());
        registerHandler(Query.SizeLessThan.class, new PropertyCriterionHandler());
        registerHandler(Query.SizeLessThanEquals.class, new PropertyCriterionHandler());
        registerHandler(EqualsIgnoreCase.class, new PropertyCriterionHandler());
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

    public static void registerCriterionHandler(Class<? extends Query.Criterion> type, CriterionHandler handler) {
        CUSTOM_HANDLERS.put(type, handler);
    }

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

    static List<Expression<?>> nativeSqlArguments(
            CriteriaBuilder criteriaBuilder, String sql, From<?, ?> root, GrailsHibernatePersistentEntity entity) {
        List<Expression<?>> arguments = new ArrayList<>();
        arguments.add(criteriaBuilder.literal(sql));
        if (sql.contains(GrailsSqlRestrictionFunction.ALIAS_PLACEHOLDER)) {
            arguments.add(aliasColumn(root, entity));
        }
        return arguments;
    }

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
