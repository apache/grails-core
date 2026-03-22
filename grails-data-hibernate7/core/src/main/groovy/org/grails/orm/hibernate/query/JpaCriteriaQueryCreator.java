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

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;

import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaCriteriaQuery;
import org.hibernate.query.criteria.JpaExpression;
import org.hibernate.query.criteria.JpaSubQuery;

import org.springframework.core.convert.ConversionService;

import grails.gorm.DetachedCriteria;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.query.Query;

@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class JpaCriteriaQueryCreator {

    private final Query.ProjectionList projections;
    private final HibernateCriteriaBuilder criteriaBuilder;
    private final PersistentEntity entity;
    private final DetachedCriteria<?> detachedCriteria;
    private final ConversionService conversionService;
    private final HibernateQuery hibernateQuery;

    public JpaCriteriaQueryCreator(
            Query.ProjectionList projections,
            HibernateCriteriaBuilder criteriaBuilder,
            PersistentEntity entity,
            DetachedCriteria<?> detachedCriteria,
            ConversionService conversionService) {
        this(projections, criteriaBuilder, entity, detachedCriteria, conversionService, null);
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
        var tablesByName = new JpaFromProvider(
                detachedCriteria,
                projectionList,
                hibernateQuery != null ? hibernateQuery.getAliases() : List.of(),
                root);
        assignProjections(projectionList, cq, tablesByName);
        assignGroupBy(cq, tablesByName);

        assignOrderBy(cq, tablesByName);
        assignCriteria(cq, root, tablesByName, entity);
        return cq;
    }

    public <T> void populateSubquery(JpaSubQuery<T> subquery) {
        var projectionList = collectProjections();
        Class<?> javaClass = entity.getJavaClass();
        Root<?> root = subquery.from(javaClass);
        var tablesByName = new JpaFromProvider(
                detachedCriteria,
                projectionList,
                hibernateQuery != null ? hibernateQuery.getAliases() : List.of(),
                root);

        var aliasedProjections = new java.util.concurrent.atomic.AtomicInteger(0);
        var projectionExpressions = projectionList.stream()
                .map(projectionToJpaExpression(tablesByName))
                .filter(Objects::nonNull)
                .map(expr -> expr.alias("col_" + aliasedProjections.getAndIncrement()))
                .toList();
        if (!projectionExpressions.isEmpty()) {
            subquery.multiselect(projectionExpressions.toArray(new Selection<?>[0]));
        }

        Expression<?>[] groupByPaths = collectGroupProjections().stream()
                .map(gp -> (Expression<?>) tablesByName.getFullyQualifiedPath(gp.getPropertyName()))
                .filter(Objects::nonNull)
                .toArray(Expression<?>[]::new);
        if (groupByPaths.length > 0) {
            subquery.groupBy(groupByPaths);
        }

        List<Query.Criterion> criteriaList = detachedCriteria.getCriteria();
        if (!criteriaList.isEmpty()) {
            // Build predicates using a temporary CriteriaQuery since PredicateGenerator
            // requires CriteriaQuery<?> for subquery creation in nested exists/in clauses.
            // The predicates themselves are independent of the query type.
            var tempCq = criteriaBuilder.createTupleQuery();
            tempCq.from(javaClass);
            Predicate[] predicates = new PredicateGenerator(conversionService)
                    .getPredicates(criteriaBuilder, tempCq, root, criteriaList, tablesByName, entity);
            subquery.where(criteriaBuilder.and(predicates));
        }
    }

    private List<Query.Projection> collectProjections() {
        return projections.getProjectionList().stream()
                .filter(new ProjectionPredicate())
                .toList();
    }

    private JpaCriteriaQuery<?> createCriteriaQuery(List<Query.Projection> projections) {
        var cq = projections.stream()
                                .filter(it -> !(it instanceof Query.DistinctProjection
                                        || it instanceof Query.DistinctPropertyProjection))
                                .toList()
                                .size()
                        > 1
                ? criteriaBuilder.createTupleQuery()
                : criteriaBuilder.createQuery(Object.class);
        projections.stream()
                .filter(it -> it instanceof Query.DistinctProjection || it instanceof Query.DistinctPropertyProjection)
                .findFirst()
                .ifPresent(projection -> cq.distinct(true));
        return cq;
    }

    @SuppressWarnings("unchecked")
    private <T> void assignProjections(
            List<Query.Projection> projections, CriteriaQuery<T> cq, JpaFromProvider tablesByName) {
        var projectionExpressions = projections.stream()
                .map(projectionToJpaExpression(tablesByName))
                .filter(Objects::nonNull)
                .toList();
        if (!projectionExpressions.isEmpty()) {
            var tupleCriteriaQuery = (CriteriaQuery<Tuple>) cq;
            tupleCriteriaQuery.select(criteriaBuilder.tuple(projectionExpressions.toArray(new Selection<?>[0])));
        } else {
            cq.select((Selection<? extends T>) tablesByName.getFullyQualifiedPath("root"));
        }
    }

    private void assignGroupBy(CriteriaQuery<?> cq, JpaFromProvider tablesByName) {
        var groupByPaths = collectGroupProjections().stream()
                .map(groupPropertyProjection ->
                        tablesByName.getFullyQualifiedPath(groupPropertyProjection.getPropertyName()))
                .filter(Objects::nonNull)
                .toArray(Path<?>[]::new);
        cq.groupBy(groupByPaths);
    }

    @SuppressWarnings("unchecked")
    private void assignOrderBy(CriteriaQuery<?> cq, JpaFromProvider tablesByName) {
        List<Query.Order> orders = detachedCriteria.getOrders();
        if (!orders.isEmpty()) {
            var jpaOrders = orders.stream()
                    .map(order -> {
                        Path<?> expression = tablesByName.getFullyQualifiedPath(order.getProperty());
                        if (order.isIgnoreCase() && expression.getJavaType().equals(String.class)) {
                            return order.getDirection().equals(Query.Order.Direction.ASC)
                                    ? criteriaBuilder.asc(criteriaBuilder.lower((Expression<String>) expression))
                                    : criteriaBuilder.desc(criteriaBuilder.lower((Expression<String>) expression));
                        } else {
                            return order.getDirection().equals(Query.Order.Direction.ASC)
                                    ? criteriaBuilder.asc(expression)
                                    : criteriaBuilder.desc(expression);
                        }
                    })
                    .toArray(Order[]::new);
            cq.orderBy(jpaOrders);
        }
    }

    private void assignCriteria(
            CriteriaQuery<?> cq, From<?, ?> root, JpaFromProvider tablesByName, PersistentEntity entity) {
        List<Query.Criterion> criteriaList = detachedCriteria.getCriteria();
        if (!criteriaList.isEmpty()) {
            Predicate[] predicates = new PredicateGenerator(conversionService)
                    .getPredicates(criteriaBuilder, cq, root, criteriaList, tablesByName, entity);
            cq.where(criteriaBuilder.and(predicates));
        }
    }

    @SuppressWarnings("unchecked")
    private Function<Query.Projection, JpaExpression<?>> projectionToJpaExpression(JpaFromProvider tablesByName) {
        return projection -> {
            if (projection instanceof Query.CountProjection) {
                return criteriaBuilder.count(tablesByName.getFullyQualifiedPath("root"));
            } else if (projection instanceof Query.CountDistinctProjection countDistinctProjection) {
                var propertyName = countDistinctProjection.getPropertyName();
                return criteriaBuilder.countDistinct(tablesByName.getFullyQualifiedPath("root." + propertyName));
            } else if (projection instanceof Query.IdProjection) {
                return (JpaExpression<?>) tablesByName.getFullyQualifiedPath("root.id");
            } else if (projection instanceof Query.DistinctProjection) {
                return null;
            } else {
                var propertyName = ((Query.PropertyProjection) projection).getPropertyName();
                Path<?> path = tablesByName.getFullyQualifiedPath(propertyName);
                if (projection instanceof Query.MaxProjection) {
                    return criteriaBuilder.max((Expression<? extends Number>) path);
                } else if (projection instanceof Query.MinProjection) {
                    return criteriaBuilder.min((Expression<? extends Number>) path);
                } else if (projection instanceof Query.AvgProjection) {
                    return criteriaBuilder.avg((Expression<? extends Number>) path);
                } else if (projection instanceof Query.SumProjection) {
                    return criteriaBuilder.sum((Expression<? extends Number>) path);
                } else { // keep this last!!!
                    return (JpaExpression<?>) path;
                }
            }
        };
    }

    private List<Query.GroupPropertyProjection> collectGroupProjections() {
        return projections.getProjectionList().stream()
                .filter(Query.GroupPropertyProjection.class::isInstance)
                .map(Query.GroupPropertyProjection.class::cast)
                .toList();
    }
}
