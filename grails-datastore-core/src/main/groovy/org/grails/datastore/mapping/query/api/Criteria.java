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
package org.grails.datastore.mapping.query.api;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.grails.datastore.mapping.query.Query;

import java.util.Collection;
import java.util.Map;

/**
 * Interface for the implementations that construct of criteria queries.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public interface Criteria  {

    /**
     * Executes an exists subquery
     *
     * @param subquery The subquery
     * @return this criteria
     */
    Criteria exists(QueryableCriteria<?> subquery);

    /**
     * Executes a not exists subquery
     *
     * @param subquery The subquery
     * @return this criteria
     */
    Criteria notExists(QueryableCriteria<?> subquery);

    /**
     * Creates a criterion that restricts the id to the given value
     * @param value The value
     * @return The criteria
     */
    Criteria idEquals(Object value);

    /**
     * Creates a criterion that asserts the given property is empty (such as a blank string)
     *
     * @param propertyName The property name
     * @return The criteria
     */
    Criteria isEmpty(String propertyName);

    /**
     * Creates a criterion that asserts the given property is not empty
     *
     * @param propertyName The property name
     * @return The criteria
     */
    Criteria isNotEmpty(String propertyName);

    /**
     * Creates a criterion that asserts the given property is null
     *
     * @param propertyName The property name
     * @return The criteria
     */
    Criteria isNull(String propertyName);

    /**
     * Creates a criterion that asserts the given property is not null
     *
     * @param propertyName The property name
     * @return The criteria
     */
    Criteria isNotNull(String propertyName);

    /**
     * Creates an "equals" Criterion based on the specified property name and value
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return The criteria
     */
    Criteria eq(String propertyName, Object propertyValue);

    /**
     * Creates an "equals" Criterion based on the specified property name and value
     *
     * @param propertyValue The property value
     *
     * @return The criteria
     */
    Criteria idEq(Object propertyValue);

    /**
     * Creates a "not equals" Criterion based on the specified property name and value
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return The criteria
     */
    Criteria ne(String propertyName, Object propertyValue);

    /**
     * Restricts the results by the given property value range (inclusive)
     *
     * @param propertyName The property name
     *
     * @param start The start of the range
     * @param finish The end of the range
     * @return The criteria
     */
    Criteria between(String propertyName, Object start, Object finish);

    /**
     * Used to restrict a value to be greater than or equal to the given value
     * @param property The property
     * @param value The value
     * @return The Criterion instance
     */
    Criteria gte(String property, Object value);

    /**
     * Used to restrict a value to be greater than or equal to the given value
     * @param property The property
     * @param value The value
     * @return The Criterion instance
     */
    Criteria ge(String property, Object value);

    /**
     * Used to restrict a value to be greater than or equal to the given value
     * @param property The property
     * @param value The value
     * @return The Criterion instance
     */
    Criteria gt(String property, Object value);

    /**
     * Used to restrict a value to be less than or equal to the given value
     * @param property The property
     * @param value The value
     * @return The Criterion instance
     */
    Criteria lte(String property, Object value);

    /**
     * Used to restrict a value to be less than or equal to the given value
     * @param property The property
     * @param value The value
     * @return The Criterion instance
     */
    Criteria le(String property, Object value);

    /**
     * Used to restrict a value to be less than or equal to the given value
     * @param property The property
     * @param value The value
     * @return The Criterion instance
     */
    Criteria lt(String property, Object value);

    /**
     * Creates a like Criterion based on the specified property name and value
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return The criteria
     */
    Criteria like(String propertyName, Object propertyValue);

    /**
     * Creates an ilike Criterion based on the specified property name and value. Unlike a like condition, ilike is case insensitive
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return The criteria
     */
    Criteria ilike(String propertyName, Object propertyValue);

    /**
     * Creates an rlike Criterion based on the specified property name and value
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return The criteria
     */
    Criteria rlike(String propertyName, Object propertyValue);

    /**
     * Creates a logical conjunction
     * @param callable The closure
     *
     * @return This criteria
     */
    Criteria and(@DelegatesTo(Criteria.class) Closure callable);

    /**
     * Creates a logical disjunction
     * @param callable The closure
     * @return This criteria
     */
    Criteria or(@DelegatesTo(Criteria.class) Closure callable);

    /**
     * Creates a logical negation
     * @param callable The closure
     * @return This criteria
     */
    Criteria not(@DelegatesTo(Criteria.class) Closure callable);

    /**
     * Creates an "in" Criterion based on the specified property name and list of values
     *
     * @param propertyName The property name
     * @param values The values
     *
     * @return The criteria
     */
    Criteria in(String propertyName, Collection values);

    /**
     * Creates an "in" Criterion using a subquery
     *
     * @param propertyName The property name
     * @param subquery The subquery
     *
     * @return The criteria
     */
    Criteria in(String propertyName, QueryableCriteria<?> subquery);


    /**
     * Creates an "in" Criterion using a subquery
     *
     * @param propertyName The property name
     * @param subquery The subquery
     *
     * @return The criteria
     */
    Criteria inList(String propertyName, QueryableCriteria<?> subquery);

    /**
     * Creates an "in" Criterion using a subquery
     *
     * @param propertyName The property name
     * @param subquery The subquery
     *
     * @return The criteria
     */
    Criteria in(String propertyName, Closure<?> subquery);

    /**
     * Creates an "in" Criterion using a subquery
     *
     * @param propertyName The property name
     * @param subquery The subquery
     *
     * @return The criteria
     */
    Criteria inList(String propertyName, Closure<?> subquery);

    /**
     * Creates an "in" Criterion based on the specified property name and list of values
     *
     * @param propertyName The property name
     * @param values The values
     *
     * @return The criteria
     */
    Criteria inList(String propertyName, Collection values);

    /**
     * Creates an "in" Criterion based on the specified property name and list of values
     *
     * @param propertyName The property name
     * @param values The values
     *
     * @return The criteria
     */
    Criteria inList(String propertyName, Object[] values);

    /**
      * Creates an "in" Criterion based on the specified property name and list of values
      *
      * @param propertyName The property name
      * @param values The values
      *
      * @return The criteria
      */
    Criteria in(String propertyName, Object[] values);

    /**
     * Creates a negated "in" Criterion using a subquery
     *
     * @param propertyName The property name
     * @param subquery The subquery
     *
     * @return The criteria
     */
    Criteria notIn(String propertyName, QueryableCriteria<?> subquery);

    /**
     * Creates a negated "in" Criterion using a subquery
     *
     * @param propertyName The property name
     * @param subquery The subquery
     *
     * @return The criteria
     */
    Criteria notIn(String propertyName, Closure<?> subquery);

    /**
     * Orders by the specified property name (defaults to ascending)
     *
     * @param propertyName The property name to order by
     * @return This criteria
     */
    Criteria order(String propertyName);

    /**
     * Adds an order object
     *
     * @param o The order object
     * @return The order object
     */
    Criteria order(Query.Order o);

    /**
     * Orders by the specified property name and direction
     *
     * @param propertyName The property name to order by
     * @param direction Either "asc" for ascending or "desc" for descending
     *
     * @return This criteria
     */
    Criteria order(String propertyName, String direction);

    /**
     * Creates a Criterion that constrains a collection property by size
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return This criteria
     */
    Criteria sizeEq(String propertyName, int size) ;

    /**
     * Creates a Criterion that constrains a collection property to be greater than the given size
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return This criteria
     */
    Criteria sizeGt(String propertyName, int size);

    /**
     * Creates a Criterion that constrains a collection property to be greater than or equal to the given size
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return This criteria
     */
    Criteria sizeGe(String propertyName, int size);

    /**
     * Creates a Criterion that constrains a collection property to be less than or equal to the given size
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return This criteria
     */
    Criteria sizeLe(String propertyName, int size);

    /**
     * Creates a Criterion that constrains a collection property to be less than to the given size
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return This criteria
     */
    Criteria sizeLt(String propertyName, int size);

    /**
     * Creates a Criterion that constrains a collection property to be not equal to the given size
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return This criteria
     */
    Criteria sizeNe(String propertyName, int size);

    /**
     * Constrains a property to be equal to a specified other property
     *
     * @param propertyName The property
     * @param otherPropertyName The other property
     * @return This criteria
     */
    Criteria eqProperty(java.lang.String propertyName, java.lang.String otherPropertyName);

    /**
     * Constrains a property to be not equal to a specified other property
     *
     * @param propertyName The property
     * @param otherPropertyName The other property
     * @return This criteria
     */
    Criteria neProperty(java.lang.String propertyName, java.lang.String otherPropertyName);

    /**
     * Constrains a property to be greater than a specified other property
     *
     * @param propertyName The property
     * @param otherPropertyName The other property
     * @return This criteria
     */
    Criteria gtProperty(java.lang.String propertyName, java.lang.String otherPropertyName);

    /**
     * Constrains a property to be greater than or equal to a specified other property
     *
     * @param propertyName The property
     * @param otherPropertyName The other property
     * @return This criteria
     */
    Criteria geProperty(java.lang.String propertyName, java.lang.String otherPropertyName);

    /**
     * Constrains a property to be less than a specified other property
     *
     * @param propertyName The property
     * @param otherPropertyName The other property
     * @return This criteria
     */
    Criteria ltProperty(java.lang.String propertyName, java.lang.String otherPropertyName);

    /**
     * Constrains a property to be less than or equal to a specified other property
     *
     * @param propertyName The property
     * @param otherPropertyName The other property
     * @return This criteria
     */
    Criteria leProperty(java.lang.String propertyName, java.lang.String otherPropertyName);

    /**
     * Apply an "equals" constraint to each property in the key set of a <code>Map</code>
     *
     * @param propertyValues a map from property names to values
     *
     * @return Criterion
     *
     * @see org.grails.datastore.mapping.query.Query.Conjunction
     */
    Criteria allEq(Map<String, Object> propertyValues);


    //===== Subquery methods

    /**
     * Creates a subquery criterion that ensures the given property is equals to all the given returned values
     *
     * @param propertyName The property name
     * @param propertyValue A closure that is converted to a {@link org.grails.datastore.mapping.query.api.QueryableCriteria}
     * @return This criterion instance
     */
    Criteria eqAll(String propertyName, Closure<?> propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is greater than all the given returned values
     *
     * @param propertyName The property name
     * @param propertyValue A closure that is converted to a {@link org.grails.datastore.mapping.query.api.QueryableCriteria}
     * @return This criterion instance
     */
    Criteria gtAll(String propertyName, Closure<?> propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is less than all the given returned values
     *
     * @param propertyName The property name
     * @param propertyValue A closure that is converted to a {@link org.grails.datastore.mapping.query.api.QueryableCriteria}
     * @return This criterion instance
     */
    Criteria ltAll(String propertyName, Closure<?> propertyValue);
    /**
     * Creates a subquery criterion that ensures the given property is greater than or equals to all the given returned values
     *
     * @param propertyName The property name
     * @param propertyValue A closure that is converted to a {@link org.grails.datastore.mapping.query.api.QueryableCriteria}
     * @return This criterion instance
     */
    Criteria geAll(String propertyName, Closure<?> propertyValue);
    /**
     * Creates a subquery criterion that ensures the given property is less than or equal to all the given returned values
     *
     * @param propertyName The property name
     * @param propertyValue A closure that is converted to a {@link org.grails.datastore.mapping.query.api.QueryableCriteria}
     * @return This criterion instance
     */
    Criteria leAll(String propertyName, Closure<?> propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is equal to all the given returned values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return This Criteria instance
     */
    Criteria eqAll(String propertyName, QueryableCriteria propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is greater than all the given returned values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return This Criteria instance
     */
    Criteria gtAll(String propertyName, QueryableCriteria propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is less than all the given returned values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return This Criteria instance
     */
    Criteria ltAll(String propertyName, QueryableCriteria propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is greater than all the given returned values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return This Criteria instance
     */
    Criteria geAll(String propertyName, QueryableCriteria propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is less than all the given returned values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return This Criteria instance
     */
    Criteria leAll(String propertyName, QueryableCriteria propertyValue);


    /**
     * Creates a subquery criterion that ensures the given property is greater than some of the given values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return This Criteria instance
     */
    Criteria gtSome(String propertyName, QueryableCriteria propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is greater than some of the given values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return This Criteria instance
     */
    Criteria gtSome(String propertyName, Closure<?> propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is greater than or equal to some of the given values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return This Criteria instance
     */
    Criteria geSome(String propertyName, QueryableCriteria propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is greater than or equal to some of the given values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return This Criteria instance
     */
    Criteria geSome(String propertyName, Closure<?> propertyValue);


    /**
     * Creates a subquery criterion that ensures the given property is less than some of the given values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return This Criteria instance
     */
    Criteria ltSome(String propertyName, QueryableCriteria propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is less than some of the given values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return This Criteria instance
     */
    Criteria ltSome(String propertyName, Closure<?> propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is less than or equal to some of the given values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return This Criteria instance
     */
    Criteria leSome(String propertyName, QueryableCriteria propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is less than or equal to some of the given values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return This Criteria instance
     */
    Criteria leSome(String propertyName, Closure<?> propertyValue);

    /**
     * <p>Configures the second-level cache with the default usage of 'read-write' and the default include of 'all' if
     *  the passed argument is true
     *
     * <code> { cache true } </code>
     *
     * @param shouldCache True if the default cache configuration should be applied
     *
     * @return This Criteria instance
     */
    Criteria cache(boolean shouldCache);

    /**
     * <p>Configures the hibernate readOnly property to avoid checking for changes on the objects if the passed argument is true
     *
     * <code> { readOnly true } </code>
     *
     * @param readOnly True to disable dirty checking
     *
     * @return This Criteria instance          
     */
    Criteria readOnly(boolean readOnly);
}
