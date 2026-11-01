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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;

import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaSubQuery;

import grails.gorm.DetachedCriteria;
import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.query.Projections;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.api.QueryableCriteria;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;

/**
 * Handles criteria targeting a persistent property, such as equality, relational comparisons,
 * pattern matching, collection size, and IN/BETWEEN checks.
 *
 * @since 8.0.1
 */
public class PropertyCriterionHandler implements InternalCriterionHandler<Query.PropertyCriterion> {

    @Override
    @SuppressWarnings("unchecked")
    public Predicate handle(CriterionContext context, Query.PropertyCriterion pc) {
        String propertyName = pc.getProperty();
        Expression<?> propertyPath = context.resolvePath(propertyName);
        if (propertyPath == null) {
            String className = context.getEntity() != null ? context.getEntity().getJavaClass().getName() : "unknown";
            throw new ConfigurationException("Cannot use comparison criteria on non-existent property [" + propertyName + "] of class [" + className + "]");
        }

        HibernateCriteriaBuilder criteriaBuilder = context.getCriteriaBuilder();

        if (pc instanceof EqualsIgnoreCase) {
            return handleEqualsIgnoreCase(context, pc, propertyPath);
        } else if (pc instanceof Query.Equals) {
            return handleEquals(context, pc, propertyPath);
        } else if (pc instanceof Query.NotEquals) {
            return handleNotEquals(context, pc, propertyPath);
        } else if (pc instanceof Query.ILike) {
            return criteriaBuilder.ilike((Expression<String>) propertyPath, (String) context.convertValue(propertyName, pc.getValue(), propertyPath));
        } else if (pc instanceof Query.RLike rLike) {
            return handleRLike(criteriaBuilder, (Expression<String>) propertyPath, rLike);
        } else if (pc instanceof Query.Like) {
            return criteriaBuilder.like((Expression<String>) propertyPath, (String) context.convertValue(propertyName, pc.getValue(), propertyPath));
        } else if (pc instanceof Query.GreaterThan) {
            if (pc.getValue() instanceof QueryableCriteria qc) {
                Subquery<?> subquery = buildSubquery(context, propertyPath, qc, propertyName);
                return criteriaBuilder.greaterThan((Expression<? extends Comparable>) propertyPath, (Expression) subquery);
            }
            Object value = context.convertComparisonValue(propertyName, pc.getValue(), propertyPath);
            return value instanceof Comparable<?> comparable ?
                criteriaBuilder.greaterThan((Expression<? extends Comparable>) propertyPath, (Comparable) comparable) :
                criteriaBuilder.greaterThan((Expression<? extends Comparable>) propertyPath, (Expression) context.asExpression(value, propertyPath));
        } else if (pc instanceof Query.GreaterThanEquals) {
            if (pc.getValue() instanceof QueryableCriteria qc) {
                Subquery<?> subquery = buildSubquery(context, propertyPath, qc, propertyName);
                return criteriaBuilder.greaterThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression) subquery);
            }
            Object value = context.convertComparisonValue(propertyName, pc.getValue(), propertyPath);
            return value instanceof Comparable<?> comparable ?
                criteriaBuilder.greaterThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Comparable) comparable) :
                criteriaBuilder.greaterThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression) context.asExpression(value, propertyPath));
        } else if (pc instanceof Query.LessThan) {
            if (pc.getValue() instanceof QueryableCriteria qc) {
                Subquery<?> subquery = buildSubquery(context, propertyPath, qc, propertyName);
                return criteriaBuilder.lessThan((Expression<? extends Comparable>) propertyPath, (Expression) subquery);
            }
            Object value = context.convertComparisonValue(propertyName, pc.getValue(), propertyPath);
            return value instanceof Comparable<?> comparable ?
                criteriaBuilder.lessThan((Expression<? extends Comparable>) propertyPath, (Comparable) comparable) :
                criteriaBuilder.lessThan((Expression<? extends Comparable>) propertyPath, (Expression) context.asExpression(value, propertyPath));
        } else if (pc instanceof Query.LessThanEquals) {
            if (pc.getValue() instanceof QueryableCriteria qc) {
                Subquery<?> subquery = buildSubquery(context, propertyPath, qc, propertyName);
                return criteriaBuilder.lessThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression) subquery);
            }
            Object value = context.convertComparisonValue(propertyName, pc.getValue(), propertyPath);
            return value instanceof Comparable<?> comparable ?
                criteriaBuilder.lessThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Comparable) comparable) :
                criteriaBuilder.lessThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression) context.asExpression(value, propertyPath));
        } else if (pc instanceof Query.In) {
            return handleIn(context, pc, propertyPath, propertyName);
        } else if (pc instanceof Query.Between between) {
            return criteriaBuilder.between((Expression<? extends Comparable>) propertyPath,
                (Comparable) context.convertValue(propertyName, between.getFrom(), propertyPath),
                (Comparable) context.convertValue(propertyName, between.getTo(), propertyPath));
        } else if (pc instanceof Query.SizeEquals) {
            return criteriaBuilder.equal(criteriaBuilder.size(context.resolveCollectionPath(propertyPath)), (Integer) pc.getValue());
        } else if (pc instanceof Query.SizeNotEquals) {
            return criteriaBuilder.notEqual(criteriaBuilder.size(context.resolveCollectionPath(propertyPath)), (Integer) pc.getValue());
        } else if (pc instanceof Query.SizeGreaterThan) {
            return criteriaBuilder.greaterThan(criteriaBuilder.size(context.resolveCollectionPath(propertyPath)), (Integer) pc.getValue());
        } else if (pc instanceof Query.SizeGreaterThanEquals) {
            return criteriaBuilder.greaterThanOrEqualTo(criteriaBuilder.size(context.resolveCollectionPath(propertyPath)), (Integer) pc.getValue());
        } else if (pc instanceof Query.SizeLessThan) {
            return criteriaBuilder.lessThan(criteriaBuilder.size(context.resolveCollectionPath(propertyPath)), (Integer) pc.getValue());
        } else if (pc instanceof Query.SizeLessThanEquals) {
            return criteriaBuilder.lessThanOrEqualTo(criteriaBuilder.size(context.resolveCollectionPath(propertyPath)), (Integer) pc.getValue());
        }

        throw new UnsupportedOperationException("Unsupported criterion: " + pc.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    private static Predicate handleEquals(CriterionContext context, Query.PropertyCriterion pc, Expression<?> propertyPath) {
        if (pc.getValue() instanceof QueryableCriteria qc) {
            Subquery<?> subquery = buildSubquery(context, propertyPath, qc, pc.getProperty());
            return context.getCriteriaBuilder().equal(propertyPath, subquery);
        } else {
            return context.getCriteriaBuilder().equal(propertyPath, context.convertComparisonValue(pc.getProperty(), pc.getValue(), propertyPath));
        }
    }

    @SuppressWarnings("unchecked")
    private static Predicate handleEqualsIgnoreCase(CriterionContext context, Query.PropertyCriterion pc, Expression<?> propertyPath) {
        Object value = pc.getValue();
        if (!String.class.equals(propertyPath.getJavaType()) || !(value instanceof CharSequence)) {
            return handleEquals(context, pc, propertyPath);
        }
        return context.getCriteriaBuilder().equal(
                context.getCriteriaBuilder().lower((Expression<String>) propertyPath),
                value.toString().toLowerCase(Locale.ROOT));
    }

    @SuppressWarnings("unchecked")
    private static Predicate handleNotEquals(CriterionContext context, Query.PropertyCriterion pc, Expression<?> propertyPath) {
        Object value = pc.getValue();
        HibernateCriteriaBuilder criteriaBuilder = context.getCriteriaBuilder();
        if (value == null) {
            return criteriaBuilder.isNotNull(propertyPath);
        }
        if (value instanceof QueryableCriteria qc) {
            Subquery<?> subquery = buildSubquery(context, propertyPath, qc, pc.getProperty());
            return criteriaBuilder.or(criteriaBuilder.notEqual(propertyPath, subquery), criteriaBuilder.isNull(propertyPath));
        }
        return criteriaBuilder.or(
            criteriaBuilder.notEqual(propertyPath, context.convertComparisonValue(pc.getProperty(), value, propertyPath)),
            criteriaBuilder.isNull(propertyPath));
    }

    @SuppressWarnings("unchecked")
    private static Predicate handleIn(CriterionContext context, Query.PropertyCriterion pc, Expression<?> propertyPath, String propertyName) {
        HibernateCriteriaBuilder criteriaBuilder = context.getCriteriaBuilder();
        Object value = pc.getValue();
        if (value instanceof QueryableCriteria qc) {
            Subquery<?> subquery = buildSubquery(context, propertyPath, qc, propertyName);
            return propertyPath.in(subquery);
        }

        Collection<?> collection = value instanceof Collection ? (Collection<?>) value : Collections.singletonList(value);
        List<Object> converted = collection.stream()
            .filter(Objects::nonNull)
            .map(v -> context.convertValue(propertyName, v, propertyPath))
            .collect(Collectors.toList());

        if (converted.isEmpty()) {
            return criteriaBuilder.disjunction();
        }

        if (context.isCollectionPath(propertyPath)) {
            Predicate[] memberOfPredicates = converted.stream()
                .map(v -> criteriaBuilder.isMember((Object) v, (Expression) context.resolveCollectionPath(propertyPath)))
                .toArray(Predicate[]::new);
            return criteriaBuilder.or(memberOfPredicates);
        }

        return propertyPath.in(converted);
    }

    @SuppressWarnings("unchecked")
    static Subquery<?> buildSubquery(CriterionContext context, Expression<?> propertyPath, QueryableCriteria<?> qc, String propertyName) {
        QueryableCriteria<?> materialized = qc.materializeLazyCriteria();
        Query.ProjectionList projectionList = new Query.ProjectionList();
        for (Object p : materialized.getProjections()) {
            projectionList.add((Query.Projection) p);
        }

        if (projectionList.isEmpty()) {
            String propName = propertyName;
            if (propName != null && propName.contains(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)) {
                propName = propName.split(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)[1];
            }
            if (propName != null) {
                PersistentProperty prop = materialized.getPersistentEntity().getPropertyByName(propName);
                if (prop != null) {
                    projectionList.add(Projections.property(propName));
                }
            }
        }

        Class<?> expectedType = resolveSubqueryType(propertyPath, materialized, projectionList);
        Subquery<?> subquery = context.getCriteriaQuery().subquery(expectedType);
        var creator = new JpaCriteriaQueryCreator(projectionList, context.getCriteriaBuilder(), (GrailsHibernatePersistentEntity) materialized.getPersistentEntity(), (DetachedCriteria) materialized, context.getConversionService());
        creator.setParentContext(context.getQueryContext());
        creator.populateSubquery((JpaSubQuery) subquery);
        return subquery;
    }

    static Class<?> resolveSubqueryType(Expression<?> propertyPath, QueryableCriteria<?> qc, Query.ProjectionList projectionList) {
        List<Query.Projection> projections = projectionList != null ? projectionList.getProjectionList() : qc.getProjections();
        if (!projections.isEmpty()) {
            Object first = projections.get(0);
            if (first instanceof Query.CountProjection || first instanceof Query.CountDistinctProjection) {
                return Long.class;
            } else if (first instanceof Query.AvgProjection) {
                return Double.class;
            } else if (first instanceof Query.IdProjection) {
                PersistentProperty identity = qc.getPersistentEntity().getIdentity();
                return identity != null ? identity.getType() : Object.class;
            } else if (first instanceof Query.PropertyProjection propertyProjection) {
                PersistentProperty prop = qc.getPersistentEntity().getPropertyByName(propertyProjection.getPropertyName());
                if (prop != null) {
                    return prop.getType();
                }
            }
        }
        return propertyPath != null ? propertyPath.getJavaType() : qc.getPersistentEntity().getJavaClass();
    }

    static Predicate handleRLike(HibernateCriteriaBuilder criteriaBuilder, Expression<String> propertyPath, Query.RLike c) {
        String pattern = c.getValue().toString().replaceAll("^/|/$", "");
        return criteriaBuilder.equal(
            criteriaBuilder.function(
                GrailsRLikeFunctionContributor.RLIKE,
                Boolean.class,
                propertyPath,
                criteriaBuilder.literal(pattern)),
            true);
    }
}
