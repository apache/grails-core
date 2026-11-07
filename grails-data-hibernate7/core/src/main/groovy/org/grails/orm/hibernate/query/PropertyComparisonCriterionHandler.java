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

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;

import org.hibernate.query.criteria.HibernateCriteriaBuilder;

import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.grails.datastore.mapping.query.Query;

/**
 * Handles criteria comparing two properties against each other.
 *
 * @since 8.0.1
 */
class PropertyComparisonCriterionHandler implements InternalCriterionHandler<Query.PropertyComparisonCriterion> {

    @Override
    @SuppressWarnings("unchecked")
    public Predicate handle(CriterionContext context, Query.PropertyComparisonCriterion c) {
        Expression<?> propertyPath = context.resolvePath(c.getProperty());
        if (propertyPath == null) {
            String className = context.getEntity() != null ? context.getEntity().getJavaClass().getName() : "unknown";
            throw new ConfigurationException("Cannot use property comparison criteria on non-existent property [" + c.getProperty() + "] of class [" + className + "]");
        }
        Expression<?> otherPropertyPath = context.resolvePath(c.getOtherProperty());
        if (otherPropertyPath == null) {
            String className = context.getEntity() != null ? context.getEntity().getJavaClass().getName() : "unknown";
            throw new ConfigurationException("Cannot use property comparison criteria on non-existent property [" + c.getOtherProperty() + "] of class [" + className + "]");
        }

        Expression<?> left = identifierIfCompared(propertyPath, otherPropertyPath);
        Expression<?> right = identifierIfCompared(otherPropertyPath, propertyPath);
        propertyPath = left;
        otherPropertyPath = right;

        HibernateCriteriaBuilder criteriaBuilder = context.getCriteriaBuilder();

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

    /**
     * An association compared with a value that is not an instance of it, such as {@code id == team.club},
     * is compared by its identifier, as Hibernate 5 does.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Expression<?> identifierIfCompared(Expression<?> expression, Expression<?> other) {
        if (expression instanceof Path<?> path
                && path.getModel() instanceof SingularAttribute<?, ?> attribute
                && attribute.getType() instanceof EntityType<?> entityType
                && !path.getJavaType().isAssignableFrom(other.getJavaType())
                && !other.getJavaType().isAssignableFrom(path.getJavaType())) {
            Class idType = entityType.getIdType().getJavaType();
            return path.get(entityType.getId(idType).getName());
        }
        return expression;
    }
}
