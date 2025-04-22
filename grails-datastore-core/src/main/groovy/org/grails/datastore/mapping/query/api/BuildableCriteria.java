/*
 * Copyright 2014 the original author or authors.
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
package org.grails.datastore.mapping.query.api;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

import jakarta.persistence.criteria.JoinType;
import java.util.Map;

/**
 * 
 * @author Jeff Brown
 * @since 3.1.2
 *
 */
public interface BuildableCriteria extends Criteria {


    /**
     * @return The class the criteria applies to
     */
    Class getTargetClass();

    /**
     * Whether to cache the query
     *
     * @param cache True if the query should be cached
     * @return This criteria
     */
    BuildableCriteria cache(boolean cache);

    /**
     * Whether to cache the query should be readOnly
     *
     * @param readOnly True if the results should be read-only
     * @return This criteria
     */
    BuildableCriteria readOnly(boolean readOnly);

    /**
     * Whether to join on an association
     *
     * @param property The property to join on
     * @return This criteria
     */
    BuildableCriteria join(String property);

    /**
     * Whether to join on an association
     *
     * @param property The property to join on
     * @param joinType The type of join
     * @return This criteria
     */
    BuildableCriteria join(String property, JoinType joinType);
    /**
     * Whether to select on an association
     *
     * @param property The property to join on
     * @return This criteria
     */
    BuildableCriteria select(String property);

    /**
     * Defines and executes a list query in a single call. Example: Foo.createCriteria().list { }
     * @param closure The closure to execute
     *
     * @return The result
     */
    Object list(@DelegatesTo(Criteria.class) Closure closure);

    /**
     * Defines and executes a list query in a single call. Example: Foo.createCriteria().list { }
     * 
     * @param params pagination parameters (max, offset, etc...)
     * @param closure The closure to execute
     *
     * @return The result
     */
    Object list(Map params, @DelegatesTo(Criteria.class) Closure closure);
    
    /**
     * Defines and executes a list distinct query in a single call. Example: Foo.createCriteria().listDistinct { }
     * @param closure The closure to execute
     *
     * @return The result 
     */
    Object listDistinct(@DelegatesTo(Criteria.class) Closure closure);
    
    /**
     * Defines and executes a scroll query in a single call. Example: Foo.createCriteria().scroll { }
     * 
     * @param closure The closure to execute
     *
     * @return A scrollable result set
     */
    Object scroll(@DelegatesTo(Criteria.class) Closure closure);
    
    /**
     * Defines and executes a get query ( a single result) in a single call. Example: Foo.createCriteria().get { }
     *
     * @param closure The closure to execute
     *
     * @return A single result
     */
    Object get(@DelegatesTo(Criteria.class) Closure closure);
}
