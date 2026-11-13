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
package org.grails.datastore.mapping.query.criteria;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.core.convert.ConversionService;
import org.springframework.dao.InvalidDataAccessResourceUsageException;

import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.api.QueryableCriteria;

/**
 * What a {@link FunctionCallingCriterion} asks for, decided once: the kind of comparison, its operator and its
 * operand, already converted to the type the function returns. Every query that runs a function around a property,
 * a read as well as a bulk statement, builds its query from this, so a rule about an operand lives here and not
 * in each of them.
 *
 * @since 8.0.1
 */
public final class FunctionComparison {

    /**
     * The shape of the comparison.
     */
    public enum Kind {
        /** {@code f(p) is null} */
        IS_NULL,
        /** {@code f(p) is not null} */
        IS_NOT_NULL,
        /** {@code f(p) in (subquery)} */
        IN_SUBQUERY,
        /** {@code f(p) in (v1, v2, ...)} */
        IN_VALUES,
        /** {@code f(p) <op> (subquery)} */
        COMPARE_SUBQUERY,
        /** {@code f(p) ilike v} */
        ILIKE,
        /** {@code f(p) rlike v} */
        RLIKE,
        /** {@code f(p) <op> v} */
        COMPARE
    }

    /**
     * The comparison operator of {@link Kind#COMPARE}, {@link Kind#COMPARE_SUBQUERY} and a like comparison.
     */
    public enum Operator {
        EQ(" = "), NE(" != "), GT(" > "), GE(" >= "), LT(" < "), LE(" <= "), LIKE(" LIKE ");

        private final String jpql;

        Operator(String jpql) {
            this.jpql = jpql;
        }

        /**
         * @return the operator as JPQL text, with a space on each side
         */
        public String jpql() {
            return jpql;
        }
    }

    /**
     * Thrown for a function call that no query can run: a function on the value of a comparison, or a criterion
     * that cannot be compared with a function. A query that reports it differently catches this and translates it.
     */
    public static final class UnsupportedComparisonException extends InvalidDataAccessResourceUsageException {
        public UnsupportedComparisonException(String message) {
            super(message);
        }
    }

    private final String functionName;
    private final String property;
    private final Kind kind;
    private final Operator operator;
    private final Object operand;
    private final List<Object> values;
    private final QueryableCriteria<?> subquery;

    private FunctionComparison(String functionName, String property, Kind kind, Operator operator, Object operand,
                               List<Object> values, QueryableCriteria<?> subquery) {
        this.functionName = functionName;
        this.property = property;
        this.kind = kind;
        this.operator = operator;
        this.operand = operand;
        this.values = values;
        this.subquery = subquery;
    }

    /**
     * Decides what a criterion asks for.
     *
     * @param criterion the function criterion
     * @param conversionService converts an operand to the type the function returns, may be {@code null}
     * @return the comparison
     * @throws InvalidDataAccessResourceUsageException when the function is not supported, is called on the value,
     * or wraps a criterion that cannot be compared with a function
     */
    public static FunctionComparison of(FunctionCallingCriterion criterion, ConversionService conversionService) {
        String functionName = criterion.getFunctionName();
        FunctionCallingCriterion.requireSupported(functionName);
        if (criterion.isOnValue()) {
            throw new UnsupportedComparisonException("A function on the value of a comparison is not supported: " + functionName);
        }
        String property = criterion.getProperty();
        Query.PropertyCriterion wrapped = criterion.getPropertyCriterion();
        Object raw = wrapped.getValue();
        if (wrapped instanceof Query.In in) {
            if (in.getSubquery() != null) {
                return new FunctionComparison(functionName, property, Kind.IN_SUBQUERY, null, null, null, in.getSubquery());
            }
            List<Object> converted = new ArrayList<>();
            for (Object value : in.getValues()) {
                converted.add(FunctionCallingCriterion.convertOperand(conversionService, functionName, value));
            }
            return new FunctionComparison(functionName, property, Kind.IN_VALUES, null, null,
                    Collections.unmodifiableList(converted), null);
        }
        if (raw == null && wrapped instanceof Query.Equals) {
            return new FunctionComparison(functionName, property, Kind.IS_NULL, null, null, null, null);
        }
        if (raw == null && wrapped instanceof Query.NotEquals) {
            return new FunctionComparison(functionName, property, Kind.IS_NOT_NULL, null, null, null, null);
        }
        Operator operator = operatorOf(wrapped);
        Kind kind = switch (wrapped) {
            case Query.ILike ignored -> Kind.ILIKE;
            case Query.RLike ignored -> Kind.RLIKE;
            default -> operator == null ? null : Kind.COMPARE;
        };
        if (kind == null) {
            throw new UnsupportedComparisonException("Unsupported criterion in a function call: " + wrapped.getClass().getSimpleName());
        }
        if (kind == Kind.COMPARE && raw instanceof QueryableCriteria<?> subquery) {
            return new FunctionComparison(functionName, property, Kind.COMPARE_SUBQUERY, operator, null, null, subquery);
        }
        return new FunctionComparison(functionName, property, kind, operator,
                FunctionCallingCriterion.convertOperand(conversionService, functionName, raw), null, null);
    }

    private static Operator operatorOf(Query.PropertyCriterion wrapped) {
        if (wrapped instanceof Query.RLike || wrapped instanceof Query.ILike) {
            return null;
        }
        if (wrapped instanceof Query.Equals) {
            return Operator.EQ;
        }
        if (wrapped instanceof Query.NotEquals) {
            return Operator.NE;
        }
        if (wrapped instanceof Query.GreaterThan) {
            return Operator.GT;
        }
        if (wrapped instanceof Query.GreaterThanEquals) {
            return Operator.GE;
        }
        if (wrapped instanceof Query.LessThan) {
            return Operator.LT;
        }
        if (wrapped instanceof Query.LessThanEquals) {
            return Operator.LE;
        }
        if (wrapped instanceof Query.Like) {
            return Operator.LIKE;
        }
        return null;
    }

    public String getFunctionName() {
        return functionName;
    }

    public String getProperty() {
        return property;
    }

    public Kind getKind() {
        return kind;
    }

    /**
     * @return the operator, or {@code null} for a kind that has none
     */
    public Operator getOperator() {
        return operator;
    }

    /**
     * @return the operand of {@link Kind#COMPARE}, {@link Kind#ILIKE} and {@link Kind#RLIKE}, converted
     */
    public Object getOperand() {
        return operand;
    }

    /**
     * @return the converted values of {@link Kind#IN_VALUES}
     */
    public List<Object> getValues() {
        return values;
    }

    /**
     * @return the subquery of {@link Kind#IN_SUBQUERY} and {@link Kind#COMPARE_SUBQUERY}
     */
    public QueryableCriteria<?> getSubquery() {
        return subquery;
    }

    /**
     * Whether an operand cannot be bound as a parameter, because a parameter takes the type of the function, an
     * int, and would change the number. Such an operand is written into the query as a literal of its own type.
     *
     * @param value an operand or a value
     */
    public static boolean needsLiteral(Object value) {
        return FunctionCallingCriterion.isFractional(value);
    }
}
