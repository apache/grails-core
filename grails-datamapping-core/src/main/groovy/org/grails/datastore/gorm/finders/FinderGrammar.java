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
package org.grails.datastore.gorm.finders;

import groovy.lang.Closure;

import org.grails.datastore.mapping.query.Query;

/**
 * The composition seam finder implementations use to parse a dynamic finder method name into a
 * {@link DynamicFinderInvocation} and build the resulting {@link Query.Junction}, without
 * subclassing {@link DynamicFinder}. Implemented by {@link DynamicFinder} itself; finder
 * implementations (in this package and in {@code grails-datamapping-rx}) hold a {@code DynamicFinder}
 * instance as a composed collaborator rather than extending it.
 */
public interface FinderGrammar {

    /**
     * @param methodName The method name
     * @return True if the given method name matches this grammar's pattern
     */
    boolean isMethodMatch(String methodName);

    /**
     * Parses a dynamic finder method invocation into its constituent {@link MethodExpression}s.
     *
     * @param clazz The persistent class the finder targets
     * @param methodName The full method name
     * @param additionalCriteria An optional additional-criteria closure
     * @param arguments The method call arguments
     * @return The parsed invocation
     */
    DynamicFinderInvocation createFinderInvocation(Class clazz, String methodName, Closure additionalCriteria, Object[] arguments);

    /**
     * Builds the AND/OR junction of criteria for the given invocation.
     *
     * @param invocation The invocation
     * @return The junction
     */
    Query.Junction getJunction(DynamicFinderInvocation invocation);

    /**
     * @return Whether the first parsed expression is a required boolean clause (the
     * "find&lt;booleanProperty&gt;By*"/"findAll&lt;booleanProperty&gt;By*" finder forms)
     */
    boolean firstExpressionIsRequiredBoolean();
}
