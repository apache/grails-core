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

import java.util.Collection;
import java.util.List;

import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

import org.hibernate.query.criteria.HibernateCriteriaBuilder;

import org.springframework.core.convert.ConversionService;

import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.grails.datastore.mapping.query.Query;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;

/**
 * Encapsulates the execution context for converting a {@link Query.QueryElement} into a JPA {@link Predicate}.
 * Holds the query, root, query context, persistent entity, criteria builder, conversion service,
 * and predicate generator reference, providing helper methods for property resolution and value conversion.
 *
 * @since 8.0.1
 */
public class CriterionContext {

    private final AbstractQuery<?> criteriaQuery;
    private final From<?, ?> root;
    private final JpaQueryContext queryContext;
    private final GrailsHibernatePersistentEntity entity;
    private final HibernateCriteriaBuilder criteriaBuilder;
    private final ConversionService conversionService;
    private final PredicateGenerator generator;

    public CriterionContext(
            AbstractQuery<?> criteriaQuery,
            From<?, ?> root,
            JpaQueryContext queryContext,
            GrailsHibernatePersistentEntity entity,
            HibernateCriteriaBuilder criteriaBuilder,
            ConversionService conversionService,
            PredicateGenerator generator) {
        this.criteriaQuery = criteriaQuery;
        this.root = root;
        this.queryContext = queryContext;
        this.entity = entity;
        this.criteriaBuilder = criteriaBuilder;
        this.conversionService = conversionService;
        this.generator = generator;
    }

    public AbstractQuery<?> getCriteriaQuery() {
        return criteriaQuery;
    }

    public From<?, ?> getRoot() {
        return root;
    }

    public JpaQueryContext getQueryContext() {
        return queryContext;
    }

    public GrailsHibernatePersistentEntity getEntity() {
        return entity;
    }

    public HibernateCriteriaBuilder getCriteriaBuilder() {
        return criteriaBuilder;
    }

    public ConversionService getConversionService() {
        return conversionService;
    }

    public PredicateGenerator getGenerator() {
        return generator;
    }

    public CriterionContext withRoot(From<?, ?> newRoot, JpaQueryContext newQueryContext, GrailsHibernatePersistentEntity newEntity) {
        return new CriterionContext(criteriaQuery, newRoot, newQueryContext, newEntity, criteriaBuilder, conversionService, generator);
    }

    public Expression<?> resolvePath(String propertyName) {
        return queryContext != null ? queryContext.getFullyQualifiedExpression(propertyName) : null;
    }

    public boolean isCollectionPath(Expression<?> expression) {
        if (expression instanceof Path<?> path) {
            return Collection.class.isAssignableFrom(path.getJavaType());
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public Expression<Collection<?>> resolveCollectionPath(Expression<?> expression) {
        if (expression instanceof Join<?, ?> join && join.getParent() != null && join.getAttribute() != null) {
            return (Expression<Collection<?>>) (Expression<?>) join.getParent().get(join.getAttribute().getName());
        }
        return (Expression<Collection<?>>) expression;
    }

    public Object convertValue(String propertyName, Object value, Expression<?> propertyPath) {
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

        if (targetType != null && conversionService != null && conversionService.canConvert(value.getClass(), targetType)) {
            if (!Collection.class.isAssignableFrom(targetType) || Collection.class.isAssignableFrom(value.getClass())) {
                return conversionService.convert(value, targetType);
            }
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public Object convertComparisonValue(String propertyName, Object value, Expression<?> propertyPath) {
        if (value instanceof PropertyArithmetic pa) {
            Expression<Number> left = (Expression<Number>) queryContext.getFullyQualifiedExpression(pa.propertyName());
            Expression<Number> right = (Expression<Number>) criteriaBuilder.literal(pa.operand());

            return switch (pa.operator()) {
                case MULTIPLY -> criteriaBuilder.prod(left, right);
                case DIVIDE -> criteriaBuilder.quot(left, right);
                case ADD -> criteriaBuilder.sum(left, right);
                case SUBTRACT -> criteriaBuilder.diff(left, right);
            };
        }
        return convertValue(propertyName, value, propertyPath);
    }

    public Expression<?> asExpression(Object value, Expression<?> propertyPath) {
        if (value instanceof Expression<?> expression) {
            return expression;
        }
        ParameterExpression<?> parameter = criteriaBuilder.parameter(propertyPath.getJavaType());
        queryContext.bindParameter(parameter, value);
        return parameter;
    }

    public Predicate handle(Query.QueryElement criterion) {
        return generator.handleCriterion(criteriaQuery, root, queryContext, entity, criterion);
    }

    public Predicate[] getPredicates(List<? extends Query.QueryElement> criteria) {
        return generator.getPredicates(criteriaQuery, root, criteria, queryContext, entity);
    }
}
