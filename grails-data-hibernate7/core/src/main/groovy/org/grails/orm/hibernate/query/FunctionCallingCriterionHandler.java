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

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;

import org.hibernate.query.criteria.HibernateCriteriaBuilder;

import org.springframework.core.convert.ConversionService;

import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.api.QueryableCriteria;
import org.grails.datastore.mapping.query.criteria.FunctionCallingCriterion;

/**
 * Handles criteria that apply a SQL/HQL function to a property before comparison,
 * such as {@code upper(name) == 'A'} or {@code year(birthDate) > 2000}.
 *
 * @since 8.0.1
 */
public class FunctionCallingCriterionHandler implements InternalCriterionHandler<FunctionCallingCriterion> {

    @Override
    @SuppressWarnings("unchecked")
    public Predicate handle(CriterionContext context, FunctionCallingCriterion criterion) {
        String functionName = criterion.getFunctionName();
        if (criterion.isOnValue()) {
            throw new UnsupportedOperationException("A function on the value of a comparison is not supported: " + functionName);
        }
        Expression<?> propertyPath = context.resolvePath(criterion.getProperty());
        if (propertyPath == null) {
            throw new ConfigurationException("Cannot resolve property [" + criterion.getProperty() + "] in a " + functionName + " function call");
        }
        HibernateCriteriaBuilder criteriaBuilder = context.getCriteriaBuilder();
        Expression<?> left = applyFunction(criteriaBuilder, functionName, propertyPath);
        Query.PropertyCriterion wrapped = criterion.getPropertyCriterion();
        if (wrapped instanceof Query.In in) {
            if (in.getSubquery() != null) {
                Subquery<?> subquery = PropertyCriterionHandler.buildSubquery(context, left, in.getSubquery(), criterion.getProperty());
                return left.in(subquery);
            }
            Collection<Object> values = new ArrayList<>();
            for (Object v : in.getValues()) {
                if (v != null) {
                    values.add(convertFunctionValue(context.getConversionService(), functionName, v));
                }
            }
            if (values.isEmpty()) {
                return criteriaBuilder.disjunction();
            }
            return left.in(values);
        }

        Object rawValue = wrapped.getValue();
        if (wrapped instanceof Query.Equals) {
            if (rawValue == null) {
                return criteriaBuilder.isNull(left);
            }
            if (rawValue instanceof QueryableCriteria<?> qc) {
                Subquery<?> subquery = PropertyCriterionHandler.buildSubquery(context, left, qc, criterion.getProperty());
                return criteriaBuilder.equal(left, subquery);
            }
            Object value = convertFunctionValue(context.getConversionService(), functionName, rawValue);
            return criteriaBuilder.equal(left, value);
        } else if (wrapped instanceof Query.NotEquals) {
            if (rawValue == null) {
                return criteriaBuilder.isNotNull(left);
            }
            if (rawValue instanceof QueryableCriteria<?> qc) {
                Subquery<?> subquery = PropertyCriterionHandler.buildSubquery(context, left, qc, criterion.getProperty());
                return criteriaBuilder.notEqual(left, subquery);
            }
            Object value = convertFunctionValue(context.getConversionService(), functionName, rawValue);
            return criteriaBuilder.notEqual(left, value);
        }

        Object value = convertFunctionValue(context.getConversionService(), functionName, rawValue);
        if (wrapped instanceof Query.ILike) {
            return criteriaBuilder.ilike((Expression<String>) left, (String) value);
        } else if (wrapped instanceof Query.RLike rLike) {
            return PropertyCriterionHandler.handleRLike(criteriaBuilder, (Expression<String>) left, rLike);
        } else if (wrapped instanceof Query.Like) {
            return criteriaBuilder.like((Expression<String>) left, (String) value);
        } else if (wrapped instanceof Query.GreaterThan) {
            return criteriaBuilder.greaterThan((Expression<? extends Comparable>) left, (Comparable) value);
        } else if (wrapped instanceof Query.GreaterThanEquals) {
            return criteriaBuilder.greaterThanOrEqualTo((Expression<? extends Comparable>) left, (Comparable) value);
        } else if (wrapped instanceof Query.LessThan) {
            return criteriaBuilder.lessThan((Expression<? extends Comparable>) left, (Comparable) value);
        } else if (wrapped instanceof Query.LessThanEquals) {
            return criteriaBuilder.lessThanOrEqualTo((Expression<? extends Comparable>) left, (Comparable) value);
        }
        throw new UnsupportedOperationException("Unsupported criterion in a function call: " + wrapped.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    static Expression<?> applyFunction(HibernateCriteriaBuilder criteriaBuilder, String functionName, Expression<?> expression) {
        return switch (functionName) {
            case "lower" -> criteriaBuilder.lower((Expression<String>) expression);
            case "upper" -> criteriaBuilder.upper((Expression<String>) expression);
            case "trim" -> criteriaBuilder.trim((Expression<String>) expression);
            case "length" -> criteriaBuilder.length((Expression<String>) expression);
            default -> criteriaBuilder.function(functionName, Integer.class, expression);
        };
    }

    static Object convertFunctionValue(ConversionService conversionService, String functionName, Object value) {
        if (value == null) {
            return null;
        }
        Class<?> targetType = switch (functionName) {
            case "lower", "upper", "trim" -> String.class;
            default -> Integer.class;
        };
        return conversionService != null && conversionService.canConvert(value.getClass(), targetType) ?
                conversionService.convert(value, targetType) :
                value;
    }
}
