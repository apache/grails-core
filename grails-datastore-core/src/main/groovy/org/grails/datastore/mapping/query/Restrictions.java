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
package org.grails.datastore.mapping.query;

import java.util.Collection;

import org.grails.datastore.mapping.query.api.QueryableCriteria;

/**
 * Factory for creating {@link org.grails.datastore.mapping.query.Query.Criterion} instances
 */
public class Restrictions {

    /**
     * Restricts the property to be equal to the given value
     * @param property The property
     * @param value The value
     * @return An instance of Query.Equals
     */
    public static Query.Equals eq(String property, Object value) {
        return new Query.Equals(property, value);
    }

    /**
     * Restricts the property to be equal to the given value
     * @param value The value
     * @return An instance of Query.Equals
     */
    public static Query.IdEquals idEq(Object value) {
        return new Query.IdEquals(value);
    }

    /**
     * Restricts the property to be not equal to the given value
     * @param property The property
     * @param value The value
     * @return An instance of Query.Equals
     */

    public static Query.NotEquals ne(String property, Object value) {
        return new Query.NotEquals(property, value);
    }

    /**
     * Restricts the property to be in the list of given values
     * @param property The property
     * @param values The values
     * @return An instance of Query.In
     */
    public static Query.In in(String property, Collection<?> values) {
        return new Query.In(property, values);
    }

    /**
     * Restricts the property to be in the list of given values
     * @param property The property
     * @param subquery The subquery
     * @return An instance of Query.In
     */
    public static Query.In in(String property, QueryableCriteria subquery) {
        return new Query.In(property, subquery);
    }


    /**
     * Restricts the property to be in the list of given values
     * @param property The property
     * @param subquery The subquery
     * @return An instance of Query.In
     */
    public static Query.NotIn notIn(String property, QueryableCriteria subquery) {
        return new Query.NotIn(property, subquery);
    }

    /**
     * Restricts the property match the given String expressions. Expressions use SQL-like % to denote wildcards
     * @param property The property name
     * @param expression The expression
     * @return An instance of Query.Like
     */
    public static Query.Like like(String property, String expression) {
        return new Query.Like(property, expression);
    }

    /**
     * Case insensitive like
     *
     * @param property The property
     * @param expression The expression
     * @return An ILike expression
     */
    public static Query.ILike ilike(String property, String expression) {
        return new Query.ILike(property, expression);
    }

    /**
     * Restricts the property match the given regular expressions.
     *
     * @param property The property name
     * @param expression The expression
     * @return An instance of Query.RLike
     */
    public static Query.RLike rlike(String property, String expression) {
        return new Query.RLike(property, expression);
    }

    public static Query.Criterion and(Query.Criterion a, Query.Criterion b) {
        return new Query.Conjunction().add(a).add(b);
    }

    public static Query.Criterion or(Query.Criterion a, Query.Criterion b) {
        return new Query.Disjunction().add(a).add(b);
    }

    /**
     * Restricts the results by the given property value range
     *
     * @param property The name of the property
     * @param start The start of the range
     * @param end The end of the range
     * @return The Between instance
     */
    public static Query.Between between(String property, Object start, Object end) {
        return new Query.Between(property, start, end);
    }

    /**
     * Used to restrict a value to be greater than the given value
     * @param property The property
     * @param value The value
     * @return The GreaterThan instance
     */
    public static Query.GreaterThan gt(String property, Object value) {
        return new Query.GreaterThan(property, value);
    }

    /**
     * Used to restrict a value to be less than the given value
     * @param property The property
     * @param value The value
     * @return The LessThan instance
     */
    public static Query.LessThan lt(String property, Object value) {
        return new Query.LessThan(property, value);
    }

    /**
     * Used to restrict a value to be greater than or equal to the given value
     * @param property The property
     * @param value The value
     * @return The LessThan instance
     */
    public static Query.GreaterThanEquals gte(String property, Object value) {
        return new Query.GreaterThanEquals(property, value);
    }

    /**
     * Used to restrict a value to be less than or equal to the given value
     * @param property The property
     * @param value The value
     * @return The LessThan instance
     */
    public static Query.LessThanEquals lte(String property, Object value) {
        return new Query.LessThanEquals(property, value);
    }

    /**
     * Used to restrict a value to be null
     *
     * @param property The property name
     * @return The IsNull instance
     */
    public static Query.IsNull isNull(String property) {
        return new Query.IsNull(property);
    }

    /**
     * Used to restrict a value to be empty (such as a blank string or an empty collection)
     *
     * @param property The property name
     * @return The IsEmpty instance
     */
    public static Query.IsEmpty isEmpty(String property) {
        return new Query.IsEmpty(property);
    }

    /**
     * Used to restrict a value to be not empty (such as a non-blank string)
     *
     * @param property The property name
     * @return The IsEmpty instance
     */
    public static Query.IsNotEmpty isNotEmpty(String property) {
        return new Query.IsNotEmpty(property);
    }

    /**
     * Used to restrict a value to be null
     *
     * @param property The property name
     * @return The IsNull instance
     */
    public static Query.IsNotNull isNotNull(String property) {
        return new Query.IsNotNull(property);
    }

    /**
     * Used to restrict the size of a collection property
     *
     * @param property The property
     * @param size The size to restrict
     * @return The result
     */
    public static Query.SizeEquals sizeEq(String property, int size) {
        return new Query.SizeEquals(property, size);
    }

    /**
     * Used to restrict the size of a collection property to be greater than the given value
     *
     * @param property The property
     * @param size The size to restrict
     * @return The result
     */
    public static Query.SizeGreaterThan sizeGt(String property, int size) {
        return new Query.SizeGreaterThan(property, size);
    }

    /**
     * Used to restrict the size of a collection property to be greater than or equal to the given value
     *
     * @param property The property
     * @param size The size to restrict
     * @return The result
     */
    public static Query.SizeGreaterThanEquals sizeGe(String property, int size) {
        return new Query.SizeGreaterThanEquals(property, size);
    }

    /**
     * Creates a Criterion that contrains a collection property to be less than or equal to the given size
     *
     * @param property The property name
     * @param size The size to constrain by
     *
     * @return A Criterion instance
     */
    public static Query.SizeLessThanEquals sizeLe(String property, int size) {
        return new Query.SizeLessThanEquals(property, size);
    }

    /**
     * Creates a Criterion that contrains a collection property to be less than to the given size
     *
     * @param property The property name
     * @param size The size to constrain by
     *
     * @return A Criterion instance
     */
    public static Query.SizeLessThan sizeLt(String property, int size) {
        return new Query.SizeLessThan(property, size);
    }

    /**
     * Creates a Criterion that contrains a collection property to be not equal to the given size
     *
     * @param property The property name
     * @param size The size to constrain by
     *
     * @return A Criterion instance
     */
    public static Query.SizeNotEquals sizeNe(String property, int size) {
        return new Query.SizeNotEquals(property, size);
    }

    /**
     * Constraints a property to be equal to a specified other property
     *
     * @param propertyName      The property
     * @param otherPropertyName The other property
     * @return The criterion instance
     */
    public static Query.EqualsProperty eqProperty(String propertyName, String otherPropertyName) {
        return new Query.EqualsProperty(propertyName, otherPropertyName);
    }

    /**
     * Constraints a property to be not equal to a specified other property
     *
     * @param propertyName      The property
     * @param otherPropertyName The other property
     * @return This criterion instance
     */
    public static Query.NotEqualsProperty neProperty(String propertyName, String otherPropertyName) {
        return new Query.NotEqualsProperty(propertyName, otherPropertyName);
    }

    /**
     * Constraints a property to be greater than a specified other property
     *
     * @param propertyName      The property
     * @param otherPropertyName The other property
     * @return The criterion
     */
    public static Query.GreaterThanProperty gtProperty(String propertyName, String otherPropertyName) {
        return new Query.GreaterThanProperty(propertyName, otherPropertyName);
    }

    /**
     * Constraints a property to be greater than or equal to a specified other property
     *
     * @param propertyName      The property
     * @param otherPropertyName The other property
     * @return The criterion
     */
    public static Query.GreaterThanEqualsProperty geProperty(String propertyName, String otherPropertyName) {
        return new Query.GreaterThanEqualsProperty(propertyName, otherPropertyName);
    }

    /**
     * Constraints a property to be less than a specified other property
     *
     * @param propertyName      The property
     * @param otherPropertyName The other property
     * @return The criterion
     */
    public static Query.LessThanProperty ltProperty(String propertyName, String otherPropertyName) {
        return new Query.LessThanProperty(propertyName, otherPropertyName);
    }

    /**
     * Constraints a property to be less than or equal to a specified other property
     *
     * @param propertyName      The property
     * @param otherPropertyName The other property
     * @return The criterion
     */
    public static Query.LessThanEqualsProperty leProperty(String propertyName, String otherPropertyName) {
        return new Query.LessThanEqualsProperty(propertyName, otherPropertyName);
    }
}
