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
import jakarta.persistence.criteria.Predicate;

import org.hibernate.query.criteria.HibernateCriteriaBuilder;

import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.criteria.FunctionCallingCriterion;
import org.grails.datastore.mapping.query.criteria.FunctionComparison;

/**
 * Handles criteria that apply a SQL/HQL function to a property before comparison,
 * such as {@code upper(name) == 'A'} or {@code year(birthDate) > 2000}.
 *
 * @since 8.0.1
 */
class FunctionCallingCriterionHandler implements InternalCriterionHandler<FunctionCallingCriterion> {

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Predicate handle(CriterionContext context, FunctionCallingCriterion criterion) {
        FunctionComparison comparison;
        try {
            comparison = FunctionComparison.of(criterion, context.getConversionService());
        }
        catch (FunctionComparison.UnsupportedComparisonException e) {
            throw new UnsupportedOperationException(e.getMessage(), e);
        }
        String functionName = comparison.getFunctionName();
        Expression<?> propertyPath = context.resolvePath(comparison.getProperty());
        if (propertyPath == null) {
            throw new ConfigurationException("Cannot resolve property [" + comparison.getProperty() + "] in a " + functionName + " function call");
        }
        HibernateCriteriaBuilder cb = context.getCriteriaBuilder();
        Expression<?> left = applyFunction(cb, functionName, propertyPath);
        switch (comparison.getKind()) {
            case IS_NULL:
                return cb.isNull(left);
            case IS_NOT_NULL:
                return cb.isNotNull(left);
            case IN_SUBQUERY:
                return left.in(PropertyCriterionHandler.buildSubquery(context, left, comparison.getSubquery(), comparison.getProperty()));
            case IN_VALUES:
                // the parameters of a list take the type of the function, an int, which would not hold a fraction;
                // a literal keeps its own type
                if (comparison.getValues().stream().anyMatch(FunctionComparison::needsLiteral)) {
                    return left.in(comparison.getValues().stream()
                            .map(value -> value instanceof Number number ? cb.literal(number) : cb.literal(value))
                            .toArray(Expression[]::new));
                }
                return left.in(comparison.getValues());
            case ILIKE:
                return cb.ilike((Expression<String>) left, (String) comparison.getOperand());
            case RLIKE:
                return PropertyCriterionHandler.handleRLike(cb, (Expression<String>) left,
                        (Query.RLike) criterion.getPropertyCriterion());
            case COMPARE_SUBQUERY:
                return compare(cb, comparison.getOperator(), left, (Expression<? extends Comparable>)
                        PropertyCriterionHandler.buildSubquery(context, left, comparison.getSubquery(), comparison.getProperty()));
            default:
                Object value = comparison.getOperand();
                if (FunctionComparison.needsLiteral(value)) {
                    // the parameter of a comparison takes the type of the function, an int, which would cut the
                    // fraction off; a literal keeps its own type
                    return compare(cb, comparison.getOperator(), left, (Expression<? extends Comparable>) (Expression<?>) cb.literal((Number) value));
                }
                if (comparison.getOperator() == FunctionComparison.Operator.LIKE) {
                    return cb.like((Expression<String>) left, (String) value);
                }
                return compareValue(cb, comparison.getOperator(), left, (Comparable) value);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate compareValue(HibernateCriteriaBuilder cb, FunctionComparison.Operator operator, Expression<?> left,
                                          Comparable value) {
        Expression<? extends Comparable> l = (Expression<? extends Comparable>) left;
        return switch (operator) {
            case EQ -> cb.equal(left, value);
            case NE -> cb.notEqual(left, value);
            case GT -> cb.greaterThan(l, value);
            case GE -> cb.greaterThanOrEqualTo(l, value);
            case LT -> cb.lessThan(l, value);
            case LE -> cb.lessThanOrEqualTo(l, value);
            case LIKE -> throw new UnsupportedOperationException("like takes a text operand");
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate compare(HibernateCriteriaBuilder cb, FunctionComparison.Operator operator, Expression<?> left,
                                     Expression<? extends Comparable> right) {
        Expression<? extends Comparable> l = (Expression<? extends Comparable>) left;
        return switch (operator) {
            case EQ -> cb.equal(left, right);
            case NE -> cb.notEqual(left, right);
            case GT -> cb.greaterThan(l, right);
            case GE -> cb.greaterThanOrEqualTo(l, right);
            case LT -> cb.lessThan(l, right);
            case LE -> cb.lessThanOrEqualTo(l, right);
            case LIKE -> throw new UnsupportedOperationException("like takes a text operand");
        };
    }

    @SuppressWarnings("unchecked")
    static Expression<?> applyFunction(HibernateCriteriaBuilder criteriaBuilder, String functionName, Expression<?> expression) {
        return switch (functionName) {
            case "lower" -> criteriaBuilder.lower((Expression<String>) expression);
            case "upper" -> criteriaBuilder.upper((Expression<String>) expression);
            case "trim" -> criteriaBuilder.trim((Expression<String>) expression);
            case "length" -> criteriaBuilder.length((Expression<String>) expression);
            case "year" -> criteriaBuilder.year((Expression<? extends java.time.temporal.TemporalAccessor>) expression);
            case "month" -> criteriaBuilder.month((Expression<? extends java.time.temporal.TemporalAccessor>) expression);
            case "day" -> criteriaBuilder.day((Expression<? extends java.time.temporal.TemporalAccessor>) expression);
            case "hour" -> criteriaBuilder.hour((Expression<? extends java.time.temporal.TemporalAccessor>) expression);
            case "minute" -> criteriaBuilder.minute((Expression<? extends java.time.temporal.TemporalAccessor>) expression);
            case "second" -> criteriaBuilder.second((Expression<? extends java.time.temporal.TemporalAccessor>) expression);
            default -> criteriaBuilder.function(functionName, Integer.class, expression);
        };
    }
}
