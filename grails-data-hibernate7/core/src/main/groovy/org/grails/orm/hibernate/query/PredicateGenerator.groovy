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
package org.grails.orm.hibernate.query

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

import groovy.transform.CompileStatic
import jakarta.persistence.criteria.AbstractQuery
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.From
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Subquery
import org.hibernate.query.criteria.HibernateCriteriaBuilder
import org.hibernate.query.criteria.JpaSubQuery

import org.springframework.core.convert.ConversionService

import grails.gorm.DetachedCriteria
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.query.Projections
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.api.QueryableCriteria
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty

/**
 * A class that generates predicates for a given list of criteria.
 *
 * @author walterduquedeestrada
 * @author graemerocher
 * @since 7.0.0
 */
@CompileStatic
class PredicateGenerator {

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
    static interface CriterionHandler {

        Predicate handle(
            AbstractQuery<?> criteriaQuery,
            From<?, ?> root,
            HibernateCriteriaBuilder criteriaBuilder,
            Query.Criterion criterion
        )

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
    static interface CriterionHandlerProvider {

        Class<? extends Query.Criterion> criterionType()

        CriterionHandler criterionHandler()

    }

    public static final Map<Class<? extends Query.Criterion>, CriterionHandler> CUSTOM_HANDLERS =
        new ConcurrentHashMap<>()

    private static final AtomicBoolean SERVICE_LOADERS_INITIALIZED = new AtomicBoolean(false)

    private static void loadServiceProviders() {
        if (SERVICE_LOADERS_INITIALIZED.compareAndSet(false, true)) {
            for (CriterionHandlerProvider p : ServiceLoader.load(CriterionHandlerProvider,
                    Thread.currentThread().contextClassLoader)) {
                CUSTOM_HANDLERS.putIfAbsent(p.criterionType(), p.criterionHandler())
            }
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
    static void registerCriterionHandler(Class<? extends Query.Criterion> type, CriterionHandler handler) {
        CUSTOM_HANDLERS.put(type, handler)
    }

    /**
     * Removes all registered custom criterion handlers and resets the ServiceLoader flag.
     * Useful for test cleanup.
     */
    static void clearCustomCriterionHandlers() {
        CUSTOM_HANDLERS.clear()
        SERVICE_LOADERS_INITIALIZED.set(false)
    }

    private final HibernateCriteriaBuilder criteriaBuilder
    private final ConversionService conversionService

    PredicateGenerator(HibernateCriteriaBuilder criteriaBuilder, ConversionService conversionService) {
        this.criteriaBuilder = criteriaBuilder
        this.conversionService = conversionService
    }

    Predicate[] getPredicates(
            AbstractQuery<?> criteriaQuery,
            From<?, ?> root,
            List<? extends Query.QueryElement> criteria,
            JpaQueryContext fromsByProvider,
            GrailsHibernatePersistentEntity entity) {
        List<Predicate> result = []
        for (Query.QueryElement c : criteria) {
            Predicate p = handleCriterion(criteriaQuery, root, fromsByProvider, entity, c)
            if (p != null) {
                result.add(p)
            }
        }
        return result.toArray(new Predicate[0])
    }

    private boolean isCollectionPath(Expression<?> expression) {
        if (expression instanceof jakarta.persistence.criteria.Path) {
            jakarta.persistence.criteria.Path path = (jakarta.persistence.criteria.Path) expression
            return Collection.isAssignableFrom(path.javaType)
        }
        return false
    }

    Predicate handleCriterion(
            AbstractQuery<?> criteriaQuery,
            From<?, ?> root,
            JpaQueryContext fromsByProvider,
            GrailsHibernatePersistentEntity entity,
            Query.QueryElement criterion) {

        loadServiceProviders()

        if (criterion instanceof Query.Criterion) {
            Query.Criterion c = (Query.Criterion) criterion
            CriterionHandler customHandler = CUSTOM_HANDLERS.get(c.class)
            if (customHandler != null) {
                return customHandler.handle(criteriaQuery, root, criteriaBuilder, c)
            }
        }

        if (criterion instanceof Query.Junction) {
            return handleJunction(criteriaQuery, root, fromsByProvider, entity, (Query.Junction) criterion)
        } else if (criterion instanceof Query.DistinctProjection) {
            return criteriaBuilder.conjunction()
        } else if (criterion instanceof DetachedAssociationCriteria) {
            return handleAssociationCriteria(criteriaQuery, fromsByProvider, (DetachedAssociationCriteria) criterion)
        } else if (criterion instanceof HibernateAssociationQuery) {
            return handleHibernateAssociationQuery(criteriaQuery, fromsByProvider, (HibernateAssociationQuery) criterion)
        } else if (criterion instanceof Query.SubqueryCriterion) {
            return handleSubqueryCriterion(criteriaQuery, root, fromsByProvider, entity, (Query.SubqueryCriterion) criterion)
        } else if (criterion instanceof Query.IdEquals) {
            Query.IdEquals idEquals = (Query.IdEquals) criterion
            String propertyName = entity.identity.name
            Expression<?> propertyPath = fromsByProvider.getFullyQualifiedExpression(propertyName)
            return criteriaBuilder.equal(propertyPath, convertValue(entity, propertyName, idEquals.value, propertyPath))
        } else if (criterion instanceof Query.PropertyCriterion) {
            return handlePropertyCriterion(criteriaQuery, root, fromsByProvider, entity, (Query.PropertyCriterion) criterion)
        } else if (criterion instanceof Query.PropertyComparisonCriterion) {
            return handlePropertyComparisonCriterion(fromsByProvider, (Query.PropertyComparisonCriterion) criterion)
        } else if (criterion instanceof Query.PropertyNameCriterion) {
            return handlePropertyNameCriterion(fromsByProvider, (Query.PropertyNameCriterion) criterion)
        } else if (criterion instanceof Query.Exists) {
            return handleExists(criteriaQuery, fromsByProvider, (Query.Exists) criterion)
        } else if (criterion instanceof Query.NotExists) {
            Query.NotExists c = (Query.NotExists) criterion
            return criteriaBuilder.not(handleExists(criteriaQuery, fromsByProvider, new Query.Exists(c.subquery)))
        } else if (criterion instanceof HibernateAlias) {
            return null // Metadata only, handled by JpaQueryContext
        }
        throw new IllegalArgumentException("Unsupported criterion: ${criterion}".toString())
    }

    @SuppressWarnings(['unchecked', 'rawtypes'])
    private Predicate handleSubqueryCriterion(AbstractQuery<?> criteriaQuery, From<?, ?> root, JpaQueryContext fromsByProvider, GrailsHibernatePersistentEntity entity, Query.SubqueryCriterion c) {
        Expression<?> propertyPath = fromsByProvider.getFullyQualifiedExpression(c.property)
        QueryableCriteria<?> qc = c.value

        // If it's a comparison criterion, we expect the subquery to return the same type as the property
        Class<?> expectedType = propertyPath != null ? propertyPath.javaType : qc.persistentEntity.javaClass

        // If the subquery has no projections, we default to projecting the SAME property name if available on the subquery entity
        if (qc.projections.isEmpty()) {
            PersistentProperty prop = qc.persistentEntity.getPropertyByName(c.property)
            if (prop != null) {
                ((QueryableCriteria) qc).projections.add(Projections.property(c.property))
            }
        }

        Query.ProjectionList projectionList = new Query.ProjectionList()
        for (Query.Projection p : qc.projections) {
            projectionList.add(p)
        }

        Subquery<?> subquery = criteriaQuery.subquery(expectedType)
        JpaCriteriaQueryCreator creator = new JpaCriteriaQueryCreator(projectionList, criteriaBuilder, (GrailsHibernatePersistentEntity) qc.persistentEntity, (DetachedCriteria) qc, conversionService)
        creator.setParentContext(fromsByProvider)

        creator.populateSubquery((JpaSubQuery) subquery)

        if (c instanceof Query.EqualsAll) {
            return criteriaBuilder.equal(propertyPath, (Expression) criteriaBuilder.all(subquery))
        } else if (c instanceof Query.NotEqualsAll) {
            return criteriaBuilder.notEqual(propertyPath, (Expression) criteriaBuilder.all(subquery))
        } else if (c instanceof Query.GreaterThanAll) {
            return criteriaBuilder.greaterThan((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.all(subquery))
        } else if (c instanceof Query.GreaterThanSome) {
            return criteriaBuilder.greaterThan((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.some(subquery))
        } else if (c instanceof Query.GreaterThanEqualsAll) {
            return criteriaBuilder.greaterThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.all(subquery))
        } else if (c instanceof Query.GreaterThanEqualsSome) {
            return criteriaBuilder.greaterThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.some(subquery))
        } else if (c instanceof Query.LessThanAll) {
            return criteriaBuilder.lessThan((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.all(subquery))
        } else if (c instanceof Query.LessThanSome) {
            return criteriaBuilder.lessThan((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.some(subquery))
        } else if (c instanceof Query.LessThanEqualsAll) {
            return criteriaBuilder.lessThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.all(subquery))
        } else if (c instanceof Query.LessThanEqualsSome) {
            return criteriaBuilder.lessThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.some(subquery))
        } else if (c instanceof Query.NotIn) {
            return criteriaBuilder.not(propertyPath.in((Expression) subquery))
        }

        throw new UnsupportedOperationException("Unsupported subquery criterion: ${c.class.name}".toString())
    }

    private Predicate handleJunction(
            AbstractQuery<?> criteriaQuery,
            From<?, ?> rootFrom,
            JpaQueryContext fromsByProvider,
            GrailsHibernatePersistentEntity entity,
            Query.Junction junction) {
        List<Query.Criterion> criteriaList = junction.criteria
        Predicate[] predicates = getPredicates(criteriaQuery, rootFrom, criteriaList, fromsByProvider, entity)
        if (junction instanceof Query.Conjunction) {
            return criteriaBuilder.and(predicates)
        } else if (junction instanceof Query.Disjunction) {
            return criteriaBuilder.or(predicates)
        } else if (junction instanceof Query.Negation) {
            return criteriaBuilder.not(criteriaBuilder.or(predicates))
        }
        throw new IllegalArgumentException("Unsupported junction: ${junction}".toString())
    }

    private Predicate handleAssociationCriteria(
            AbstractQuery<?> criteriaQuery,
            JpaQueryContext fromsByProvider,
            DetachedAssociationCriteria<?> associationCriteria) {
        String associationName = associationCriteria.associationPath
        From<?, ?> associationRoot = fromsByProvider.getFrom(associationName)
        if (associationRoot == null) {
            // Check if we already have it in our parent or alias map
            Expression<?> expr = fromsByProvider.getFullyQualifiedExpression(associationName)
            if (expr instanceof From) {
                associationRoot = (From<?, ?>) expr
            } else {
                associationRoot = fromsByProvider.root.join(associationName)
                fromsByProvider.addFrom(associationName, associationRoot)
            }
        }

        // Create a nested context for this association
        JpaQueryContext nestedContext = new JpaQueryContext(fromsByProvider, null, associationRoot)

        GrailsHibernatePersistentEntity associatedEntity = (GrailsHibernatePersistentEntity) associationCriteria.association.associatedEntity
        List<Query.Criterion> criteriaList = associationCriteria.criteria
        return criteriaBuilder.and(getPredicates(criteriaQuery, associationRoot, criteriaList, nestedContext, associatedEntity))
    }

    private Predicate handleHibernateAssociationQuery(
            AbstractQuery<?> criteriaQuery,
            JpaQueryContext fromsByProvider,
            HibernateAssociationQuery associationQuery) {
        String associationName = associationQuery.associationPath
        From<?, ?> associationRoot = fromsByProvider.getFrom(associationName)
        if (associationRoot == null) {
            Expression<?> expr = fromsByProvider.getFullyQualifiedExpression(associationName)
            if (expr instanceof From) {
                associationRoot = (From<?, ?>) expr
            } else {
                associationRoot = fromsByProvider.root.join(associationName, JoinType.INNER)
                fromsByProvider.addFrom(associationName, associationRoot)
            }
        }

        // Create a nested context for this association
        JpaQueryContext nestedContext = new JpaQueryContext(fromsByProvider, null, associationRoot)

        GrailsHibernatePersistentEntity associatedEntity = associationQuery.entity
        List<Query.Criterion> criteriaList = associationQuery.associationCriteria
        return criteriaBuilder.and(getPredicates(criteriaQuery, associationRoot, criteriaList, nestedContext, associatedEntity))
    }

    @SuppressWarnings(['unchecked', 'rawtypes'])
    private Predicate handlePropertyCriterion(
            AbstractQuery<?> criteriaQuery,
            From<?, ?> root,
            JpaQueryContext fromsByProvider,
            GrailsHibernatePersistentEntity entity,
            Query.PropertyCriterion pc) {
        String propertyName = pc.property
        Expression<?> propertyPath = fromsByProvider.getFullyQualifiedExpression(propertyName)
        if (propertyPath == null) {
            throw new ConfigurationException("Cannot use comparison criteria on non-existent property [${propertyName}] of class [${entity.javaClass.name}]".toString())
        }

        if (pc instanceof Query.Equals) {
            return handleEquals(criteriaQuery, pc, propertyPath, fromsByProvider, entity)
        } else if (pc instanceof Query.NotEquals) {
            return handleNotEquals(criteriaQuery, pc, propertyPath, fromsByProvider, entity)
        } else if (pc instanceof Query.ILike) {
            return criteriaBuilder.ilike((Expression<String>) propertyPath, (String) convertValue(entity, propertyName, pc.value, propertyPath))
        } else if (pc instanceof Query.RLike) {
            return handleRLike((Expression<String>) propertyPath, (Query.RLike) pc)
        } else if (pc instanceof Query.Like) {
            return criteriaBuilder.like((Expression<String>) propertyPath, (String) convertValue(entity, propertyName, pc.value, propertyPath))
        } else if (pc instanceof Query.GreaterThan) {
            return criteriaBuilder.greaterThan((Expression<? extends Comparable>) propertyPath, (Expression) convertComparisonValue(entity, propertyName, pc.value, fromsByProvider, propertyPath))
        } else if (pc instanceof Query.GreaterThanEquals) {
            return criteriaBuilder.greaterThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression) convertComparisonValue(entity, propertyName, pc.value, fromsByProvider, propertyPath))
        } else if (pc instanceof Query.LessThan) {
            return criteriaBuilder.lessThan((Expression<? extends Comparable>) propertyPath, (Expression) convertComparisonValue(entity, propertyName, pc.value, fromsByProvider, propertyPath))
        } else if (pc instanceof Query.LessThanEquals) {
            return criteriaBuilder.lessThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression) convertComparisonValue(entity, propertyName, pc.value, fromsByProvider, propertyPath))
        } else if (pc instanceof Query.In) {
            Object value = pc.value
            if (value instanceof QueryableCriteria) {
                QueryableCriteria qc = (QueryableCriteria) value
                Class<?> expectedType = propertyPath != null ? propertyPath.javaType : qc.persistentEntity.javaClass

                // If the subquery has no projections, we default to projecting the SAME property name if available on the subquery entity
                if (qc.projections.isEmpty() && propertyPath != null) {
                    PersistentProperty prop = qc.persistentEntity.getPropertyByName(propertyName)
                    if (prop != null) {
                        ((QueryableCriteria) qc).projections.add(Projections.property(propertyName))
                    }
                }

                Query.ProjectionList projectionList = new Query.ProjectionList()
                for (Object p : qc.projections) {
                    projectionList.add((Query.Projection) p)
                }

                Subquery<?> subquery = criteriaQuery.subquery(expectedType)
                JpaCriteriaQueryCreator creator = new JpaCriteriaQueryCreator(projectionList, criteriaBuilder, (GrailsHibernatePersistentEntity) qc.persistentEntity, (DetachedCriteria) qc, conversionService)
                creator.setParentContext(fromsByProvider)

                creator.populateSubquery((JpaSubQuery) subquery)
                return propertyPath.in((Expression) subquery)
            }

            Collection<?> collection = value instanceof Collection ? (Collection<?>) value : Collections.singletonList(value)
            List<Object> converted = []
            for (Object v : collection) {
                converted.add(convertValue(entity, propertyName, v, propertyPath))
            }

            if (isCollectionPath(propertyPath)) {
                // For collection properties, we use "member of" for each value joined with OR
                if (converted.isEmpty()) {
                    return criteriaBuilder.disjunction() // Always false for empty IN on collection
                }
                List<Predicate> memberOfPredicates = []
                for (Object v : converted) {
                    memberOfPredicates.add(criteriaBuilder.isMember((Object) v, (Expression) propertyPath))
                }
                return criteriaBuilder.or(memberOfPredicates.toArray(new Predicate[0]))
            }

            return propertyPath.in(converted)
        } else if (pc instanceof Query.Between) {
            Query.Between between = (Query.Between) pc
            return criteriaBuilder.between((Expression<? extends Comparable>) propertyPath, (Comparable) convertValue(entity, propertyName, between.from, propertyPath), (Comparable) convertValue(entity, propertyName, between.to, propertyPath))
        } else if (pc instanceof Query.SizeEquals) {
            return criteriaBuilder.equal(criteriaBuilder.size((Expression<Collection<?>>) propertyPath), (Integer) pc.value)
        } else if (pc instanceof Query.SizeNotEquals) {
            return criteriaBuilder.notEqual(criteriaBuilder.size((Expression<Collection<?>>) propertyPath), (Integer) pc.value)
        } else if (pc instanceof Query.SizeGreaterThan) {
            Expression sizeExpr = criteriaBuilder.size((Expression<Collection<?>>) propertyPath)
            return criteriaBuilder.greaterThan(sizeExpr, (Comparable) pc.value)
        } else if (pc instanceof Query.SizeGreaterThanEquals) {
            Expression sizeExpr = criteriaBuilder.size((Expression<Collection<?>>) propertyPath)
            return criteriaBuilder.greaterThanOrEqualTo(sizeExpr, (Comparable) pc.value)
        } else if (pc instanceof Query.SizeLessThan) {
            Expression sizeExpr = criteriaBuilder.size((Expression<Collection<?>>) propertyPath)
            return criteriaBuilder.lessThan(sizeExpr, (Comparable) pc.value)
        } else if (pc instanceof Query.SizeLessThanEquals) {
            Expression sizeExpr = criteriaBuilder.size((Expression<Collection<?>>) propertyPath)
            return criteriaBuilder.lessThanOrEqualTo(sizeExpr, (Comparable) pc.value)
        }

        throw new UnsupportedOperationException("Unsupported criterion: ${pc.class.name}".toString())
    }

    private Predicate handleRLike(Expression<String> propertyPath, Query.RLike c) {
        String pattern = c.value.toString().replaceAll('^/|/$', '')
        return criteriaBuilder.equal(
            criteriaBuilder.function(
                GrailsRLikeFunctionContributor.RLIKE,
                Boolean,
                propertyPath,
                criteriaBuilder.literal(pattern)),
            true)
    }

    @SuppressWarnings('unchecked')
    private Predicate handlePropertyComparisonCriterion(JpaQueryContext fromsByProvider, Query.PropertyComparisonCriterion c) {
        Expression<?> propertyPath = fromsByProvider.getFullyQualifiedExpression(c.property)
        Expression<?> otherPropertyPath = fromsByProvider.getFullyQualifiedExpression(c.otherProperty)

        if (propertyPath == null || otherPropertyPath == null) {
            return null
        }

        if (c instanceof Query.EqualsProperty) {
            return criteriaBuilder.equal(propertyPath, otherPropertyPath)
        } else if (c instanceof Query.NotEqualsProperty) {
            return criteriaBuilder.notEqual(propertyPath, otherPropertyPath)
        } else if (c instanceof Query.GreaterThanProperty) {
            return criteriaBuilder.greaterThan((Expression<? extends Comparable>) propertyPath, (Expression<? extends Comparable>) otherPropertyPath)
        } else if (c instanceof Query.GreaterThanEqualsProperty) {
            return criteriaBuilder.greaterThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression<? extends Comparable>) otherPropertyPath)
        } else if (c instanceof Query.LessThanProperty) {
            return criteriaBuilder.lessThan((Expression<? extends Comparable>) propertyPath, (Expression<? extends Comparable>) otherPropertyPath)
        } else if (c instanceof Query.LessThanEqualsProperty) {
            return criteriaBuilder.lessThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression<? extends Comparable>) otherPropertyPath)
        }

        throw new UnsupportedOperationException("Unsupported property comparison criterion: ${c.class.name}".toString())
    }

    private Predicate handlePropertyNameCriterion(JpaQueryContext fromsByProvider, Query.PropertyNameCriterion c) {
        Expression<?> propertyPath = fromsByProvider.getFullyQualifiedExpression(c.property)
        if (c instanceof Query.IsNull) {
            return criteriaBuilder.isNull(propertyPath)
        } else if (c instanceof Query.IsNotNull) {
            return criteriaBuilder.isNotNull(propertyPath)
        } else if (c instanceof Query.IsEmpty) {
            return criteriaBuilder.isEmpty((Expression<Collection<?>>) propertyPath)
        } else if (c instanceof Query.IsNotEmpty) {
            return criteriaBuilder.isNotEmpty((Expression<Collection<?>>) propertyPath)
        }
        throw new UnsupportedOperationException("Unsupported property name criterion: ${c.class.name}".toString())
    }

    @SuppressWarnings(['unchecked', 'rawtypes'])
    private Predicate handleEquals(AbstractQuery<?> criteriaQuery, Query.PropertyCriterion pc, Expression<?> propertyPath, JpaQueryContext fromsByProvider, GrailsHibernatePersistentEntity entity) {
        if (pc.value instanceof QueryableCriteria) {
            QueryableCriteria qc = (QueryableCriteria) pc.value
            Class<?> expectedType = propertyPath != null ? propertyPath.javaType : qc.persistentEntity.javaClass
            Subquery<?> subquery = criteriaQuery.subquery(expectedType)
            JpaCriteriaQueryCreator creator = new JpaCriteriaQueryCreator(new Query.ProjectionList(), criteriaBuilder, (GrailsHibernatePersistentEntity) qc.persistentEntity, (DetachedCriteria) qc, conversionService)
            creator.setParentContext(fromsByProvider)

            if (qc.projections.isEmpty() && propertyPath != null) {
                String propertyName = pc.property
                if (propertyName.contains(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)) {
                    propertyName = propertyName.split(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)[1]
                }
                PersistentProperty prop = qc.persistentEntity.getPropertyByName(propertyName)
                if (prop != null) {
                    qc.projections.add(Projections.property(propertyName))
                }
            }

            creator.populateSubquery((JpaSubQuery) subquery)
            return criteriaBuilder.equal(propertyPath, subquery)
        } else {
            return criteriaBuilder.equal(propertyPath, convertComparisonValue(entity, pc.property, pc.value, fromsByProvider, propertyPath))
        }
    }

    @SuppressWarnings(['unchecked', 'rawtypes'])
    private Predicate handleNotEquals(
            AbstractQuery<?> criteriaQuery,
            Query.PropertyCriterion pc,
            Expression<?> propertyPath,
            JpaQueryContext fromsByProvider,
            GrailsHibernatePersistentEntity entity) {
        Object value = pc.value
        if (value == null) {
            return criteriaBuilder.isNotNull(propertyPath)
        }
        if (value instanceof QueryableCriteria) {
            QueryableCriteria qc = (QueryableCriteria) value
            Class<?> expectedType = propertyPath != null ? propertyPath.javaType : qc.persistentEntity.javaClass
            Subquery<?> subquery = criteriaQuery.subquery(expectedType)
            JpaCriteriaQueryCreator creator = new JpaCriteriaQueryCreator(new Query.ProjectionList(), criteriaBuilder, (GrailsHibernatePersistentEntity) qc.persistentEntity, (DetachedCriteria) qc, conversionService)
            creator.setParentContext(fromsByProvider)

            if (qc.projections.isEmpty() && propertyPath != null) {
                String propertyName = pc.property
                if (propertyName.contains(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)) {
                    propertyName = propertyName.split(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)[1]
                }
                PersistentProperty prop = qc.persistentEntity.getPropertyByName(propertyName)
                if (prop != null) {
                    qc.projections.add(Projections.property(propertyName))
                }
            }

            creator.populateSubquery((JpaSubQuery) subquery)
            return criteriaBuilder.or(criteriaBuilder.notEqual(propertyPath, subquery), criteriaBuilder.isNull(propertyPath))
        }
        return criteriaBuilder.or(
            criteriaBuilder.notEqual(propertyPath, convertComparisonValue(entity, pc.property, value, fromsByProvider, propertyPath)),
            criteriaBuilder.isNull(propertyPath))
    }

    @SuppressWarnings(['unchecked', 'rawtypes'])
    private Predicate handleExists(
            AbstractQuery<?> criteriaQuery,
            JpaQueryContext fromsByProvider,
            Query.Exists exists) {
        QueryableCriteria subqueryCriteria = exists.subquery
        GrailsHibernatePersistentEntity subqueryEntity = (GrailsHibernatePersistentEntity) subqueryCriteria.persistentEntity
        Subquery<?> subquery = criteriaQuery.subquery(subqueryEntity.javaClass)
        JpaCriteriaQueryCreator creator = new JpaCriteriaQueryCreator(new Query.ProjectionList(), criteriaBuilder, subqueryEntity, (DetachedCriteria) subqueryCriteria, conversionService)
        creator.setParentContext(fromsByProvider)
        creator.populateSubquery((JpaSubQuery) subquery)
        return criteriaBuilder.exists(subquery)
    }

    private Object convertValue(GrailsHibernatePersistentEntity entity, String propertyName, Object value, Expression<?> propertyPath) {
        if (value == null) {
            throw new ConfigurationException("Null value for property [${propertyName}] is not allowed in comparison criteria.".toString())
        }

        Class<?> targetType = propertyPath != null ? propertyPath.javaType : null

        if (targetType == null && entity != null) {
            HibernatePersistentProperty prop = (HibernatePersistentProperty) entity.getPropertyByName(propertyName)
            if (prop != null) {
                targetType = prop.type
            }
        }

        if (targetType != null && Collection.isAssignableFrom(targetType) && entity != null) {
            HibernatePersistentProperty prop = (HibernatePersistentProperty) entity.getPropertyByName(propertyName)
            if (prop instanceof HibernateToManyProperty) {
                targetType = ((HibernateToManyProperty) prop).componentType
            }
        }

        if (targetType != null && conversionService.canConvert(value.class, targetType)) {
            if (!Collection.isAssignableFrom(targetType) || Collection.isAssignableFrom(value.class)) {
                return conversionService.convert(value, targetType)
            }
        }
        return value
    }

    @SuppressWarnings('unchecked')
    private Object convertComparisonValue(GrailsHibernatePersistentEntity entity, String propertyName, Object value, JpaQueryContext context, Expression<?> propertyPath) {
        if (value instanceof PropertyArithmetic) {
            PropertyArithmetic pa = (PropertyArithmetic) value
            Expression<Number> left = (Expression<Number>) context.getFullyQualifiedExpression(pa.propertyName())
            Expression<Number> right = (Expression<Number>) criteriaBuilder.literal(pa.operand())

            switch (pa.operator()) {
                case PropertyArithmetic.Operator.MULTIPLY:
                    return criteriaBuilder.prod(left, right)
                case PropertyArithmetic.Operator.DIVIDE:
                    return criteriaBuilder.quot(left, right)
                case PropertyArithmetic.Operator.ADD:
                    return criteriaBuilder.sum(left, right)
                case PropertyArithmetic.Operator.SUBTRACT:
                    return criteriaBuilder.diff(left, right)
                default:
                    throw new IllegalStateException("Unexpected operator: ${pa.operator()}".toString())
            }
        }
        Object converted = convertValue(entity, propertyName, value, propertyPath)
        if (!(converted instanceof Expression)) {
            return criteriaBuilder.literal(converted)
        }
        return converted
    }

    Predicate generate(
            AbstractQuery<?> cq, From<?, ?> root, List<Query.Criterion> criteriaList, JpaQueryContext tablesByName, GrailsHibernatePersistentEntity entity) {
        Predicate[] predicates = getPredicates(cq, root, criteriaList, tablesByName, entity)
        return criteriaBuilder.and(predicates)
    }

}
