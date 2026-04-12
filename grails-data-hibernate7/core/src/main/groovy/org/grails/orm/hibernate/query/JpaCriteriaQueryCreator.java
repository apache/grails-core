/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.orm.hibernate.query;

import grails.gorm.DetachedCriteria;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.query.Query;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaCriteriaQuery;
import org.hibernate.query.criteria.JpaSubQuery;
import org.springframework.core.convert.ConversionService;

import java.util.List;
import java.util.Objects;

/**
 * A class that creates a JPA {@link CriteriaQuery} from a GORM {@link Query} and {@link DetachedCriteria}.
 *
 * @author burt
 * @author graemerocher
 * @since 7.0.0
 */
public class JpaCriteriaQueryCreator<T> {

    private final Query.ProjectionList projections;
    private final HibernateCriteriaBuilder criteriaBuilder;
    private final PersistentEntity entity;
    private final DetachedCriteria<?> detachedCriteria;
    private final ConversionService conversionService;
    private final HibernateQuery hibernateQuery;

    public JpaCriteriaQueryCreator(
            Query.ProjectionList projections,
            CriteriaBuilder criteriaBuilder,
            PersistentEntity entity,
            DetachedCriteria<?> detachedCriteria,
            ConversionService conversionService) {
        this(projections, (HibernateCriteriaBuilder) criteriaBuilder, entity, detachedCriteria, conversionService, null);
    }

    public JpaCriteriaQueryCreator(
            Query.ProjectionList projections,
            HibernateCriteriaBuilder criteriaBuilder,
            PersistentEntity entity,
            DetachedCriteria<?> detachedCriteria,
            ConversionService conversionService,
            HibernateQuery hibernateQuery) {
        this.projections = projections;
        this.criteriaBuilder = criteriaBuilder;
        this.entity = entity;
        this.detachedCriteria = detachedCriteria;
        this.conversionService = conversionService;
        this.hibernateQuery = hibernateQuery;
    }

    public JpaCriteriaQuery<?> createQuery() {
        var projectionList = collectProjections();
        var cq = createCriteriaQuery(projectionList);
        Class<?> javaClass = entity.getJavaClass();
        Root<?> root = cq.from(javaClass);
        var context = new JpaQueryContext(
                detachedCriteria,
                projectionList,
                hibernateQuery != null ? hibernateQuery.getAliases() : List.of(),
                root);
        
        // Pass 1: Discover all projection aliases without full resolution
        for (Query.Projection projection : projectionList) {
            if (projection instanceof Query.PropertyProjection propertyProjection) {
                context.registerAliasFromPath(propertyProjection.getPropertyName());
            } else if (projection instanceof Query.CountDistinctProjection countDistinctProjection) {
                context.registerAliasFromPath(countDistinctProjection.getPropertyName());
            }
        }

        var translator = new JpaProjectionTranslator(criteriaBuilder, context);

        assignProjections(projectionList, cq, translator, context);
        assignGroupBy(cq, context);

        assignOrderBy(cq, context);
        assignCriteria(cq, root, context, entity);
        return cq;
    }

    public <T> void populateSubquery(JpaSubQuery<T> subquery) {
        var projectionList = collectProjections();
        Class<?> javaClass = entity.getJavaClass();
        Root<?> root = subquery.from(javaClass);
        var context = new JpaQueryContext(
                detachedCriteria,
                projectionList,
                hibernateQuery != null ? hibernateQuery.getAliases() : List.of(),
                root);

        // Pass 1: Discover all projection aliases
        for (Query.Projection projection : projectionList) {
            if (projection instanceof Query.PropertyProjection propertyProjection) {
                context.registerAliasFromPath(propertyProjection.getPropertyName());
            } else if (projection instanceof Query.CountDistinctProjection countDistinctProjection) {
                context.registerAliasFromPath(countDistinctProjection.getPropertyName());
            }
        }

        if (projectionList.stream().anyMatch(Query.DistinctProjection.class::isInstance)) {
            subquery.distinct(true);
        }

        var translator = new JpaProjectionTranslator(criteriaBuilder, context);

        var aliasedProjections = new java.util.concurrent.atomic.AtomicInteger(0);
        var projectionExpressions = projectionList.stream()
                .map(translator::translate)
                .filter(Objects::nonNull)
                .map(expr -> expr.alias("col_" + aliasedProjections.getAndIncrement()))
                .toList();
        if (!projectionExpressions.isEmpty()) {
            subquery.multiselect(projectionExpressions.toArray(new Selection<?>[0]));
        }

        Expression<?>[] groupByPaths = collectGroupProjections().stream()
                .map(gp -> context.getFullyQualifiedExpression(gp.getPropertyName()))
                .filter(Objects::nonNull)
                .toArray(Expression[]::new);
        if (groupByPaths.length > 0) {
            subquery.groupBy(groupByPaths);
        }

        assignCriteria(subquery, root, context, entity);
    }

    private List<Query.Projection> collectProjections() {
        return projections.getProjectionList();
    }

    private JpaCriteriaQuery<?> createCriteriaQuery(List<Query.Projection> projections) {
        if (projections.size() > 1) {
            return (JpaCriteriaQuery<?>) criteriaBuilder.createTupleQuery();
        } else if (projections.isEmpty()) {
            return (JpaCriteriaQuery<?>) criteriaBuilder.createQuery(entity.getJavaClass());
        } else {
            var first = projections.get(0);
            if (first instanceof Query.CountProjection || first instanceof Query.CountDistinctProjection) {
                return (JpaCriteriaQuery<?>) criteriaBuilder.createQuery(Long.class);
            } else if (first instanceof Query.PropertyProjection propertyProjection) {
                PersistentEntity persistentEntity = entity.getMappingContext().getPersistentEntity(entity.getJavaClass().getName());
                String propertyName = propertyProjection.getPropertyName();
                if (propertyName.contains(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)) {
                    propertyName = propertyName.split(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)[1];
                }
                var property = persistentEntity.getPropertyByName(propertyName);
                if (property == null) {
                    return (JpaCriteriaQuery<?>) criteriaBuilder.createQuery(Object.class);
                }
                return (JpaCriteriaQuery<?>) criteriaBuilder.createQuery(property.getType());
            } else if (first instanceof Query.IdProjection) {
                return (JpaCriteriaQuery<?>) criteriaBuilder.createQuery(entity.getIdentity().getType());
            }
            return (JpaCriteriaQuery<?>) criteriaBuilder.createQuery(entity.getJavaClass());
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void assignProjections(
            List<Query.Projection> projections, CriteriaQuery<T> cq, JpaProjectionTranslator translator, JpaQueryContext context) {
        if (projections.stream().anyMatch(Query.DistinctProjection.class::isInstance)) {
            cq.distinct(true);
        }
        var projectionExpressions = projections.stream()
                .map(translator::translate)
                .filter(Objects::nonNull)
                .toList();
        if (!projectionExpressions.isEmpty()) {
            if (cq.getResultType().equals(Tuple.class)) {
                cq.select((Selection<? extends T>) criteriaBuilder.tuple(projectionExpressions.toArray(new Selection<?>[0])));
            } else {
                cq.select((Selection<? extends T>) projectionExpressions.get(0));
            }
        } else {
            // Default select root
            cq.select((Selection<? extends T>) context.getRoot());
        }
    }

    private void assignGroupBy(CriteriaQuery<?> cq, JpaQueryContext context) {
        var groupByExpressions = collectGroupProjections().stream()
                .map(groupPropertyProjection -> context.getFullyQualifiedExpression(groupPropertyProjection.getPropertyName()))
                .filter(Objects::nonNull)
                .toArray(Expression[]::new);
        if (groupByExpressions.length > 0) {
            cq.groupBy(groupByExpressions);
        }
    }

    @SuppressWarnings("unchecked")
    private void assignOrderBy(CriteriaQuery<?> cq, JpaQueryContext context) {
        List<Query.Order> orders = detachedCriteria.getOrders();
        if (!orders.isEmpty()) {
            var jpaOrders = orders.stream()
                    .map(order -> {
                        var propertyName = order.getProperty();
                        Expression<?> expression = context.getFullyQualifiedExpression(propertyName);
                        if (order.isIgnoreCase() && expression.getJavaType().equals(String.class)) {
                            return order.getDirection().equals(Query.Order.Direction.ASC) ?
                                    criteriaBuilder.asc(criteriaBuilder.lower((Expression<String>) expression)) :
                                    criteriaBuilder.desc(criteriaBuilder.lower((Expression<String>) expression));
                        } else {
                            return order.getDirection().equals(Query.Order.Direction.ASC) ?
                                    criteriaBuilder.asc(expression) :
                                    criteriaBuilder.desc(expression);
                        }
                    })
                    .toArray(jakarta.persistence.criteria.Order[]::new);
            cq.orderBy(jpaOrders);
        }
    }

    private void assignCriteria(
            AbstractQuery<?> cq, Root<?> root, JpaQueryContext context, PersistentEntity entity) {
        List<Query.Criterion> criteriaList = detachedCriteria.getCriteria();
        if (!criteriaList.isEmpty()) {
            var predicateGenerator = new PredicateGenerator(criteriaBuilder, conversionService);
            var predicate = predicateGenerator.generate(criteriaList, cq, root, context, entity);
            if (predicate != null) {
                cq.where(predicate);
            }
        }
    }

    private List<Query.GroupPropertyProjection> collectGroupProjections() {
        return projections.getProjectionList().stream()
                .filter(Query.GroupPropertyProjection.class::isInstance)
                .map(Query.GroupPropertyProjection.class::cast)
                .toList();
    }
}
