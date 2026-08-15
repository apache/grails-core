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

import java.util.regex.Pattern;

import groovy.lang.Closure;

import grails.gorm.DetachedCriteria;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.query.Query;

/**
 * Implements {@code countBy*}. Kept as its own dedicated class rather than a variant of {@link
 * SingleResultFinder}: its {@link #buildQuery} does not reuse {@link DynamicFinder#getJunction} at
 * all - for the {@code Or} operator it extends the query's own top-level junction directly via
 * {@code Query.disjunction()}/{@code Query.add(Junction, Criterion)}, rather than building a
 * standalone {@code Disjunction} added as one nested criterion the way every other finder's
 * shared {@code buildQuery} does. This divergence is preserved exactly as it existed before this
 * class existed, not unified with the shared grammar's junction-building.
 */
public class CountFinder implements FinderMethod, QueryBuildingFinder {

    private static final Pattern METHOD_PATTERN = Pattern.compile("(countBy)(\\w+)");
    private static final String[] OPERATORS = {"And", "Or"};
    private static final String OPERATOR_OR = "Or";

    private final Datastore datastore;
    private final DynamicFinder grammar;

    private CountFinder(Datastore datastore, DynamicFinder grammar) {
        this.datastore = datastore;
        this.grammar = grammar;
    }

    public static CountFinder countBy(Datastore datastore) {
        return new CountFinder(datastore, new DynamicFinder(METHOD_PATTERN, OPERATORS, datastore.getMappingContext(), false));
    }

    public static CountFinder countBy(MappingContext mappingContext) {
        return new CountFinder(null, new DynamicFinder(METHOD_PATTERN, OPERATORS, mappingContext, false));
    }

    @Override
    public void setPattern(String pattern) {
        grammar.setPattern(pattern);
    }

    @Override
    public boolean isMethodMatch(String methodName) {
        return grammar.isMethodMatch(methodName);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Object invoke(Class clazz, String methodName, Object[] arguments) {
        return invoke(clazz, methodName, (Closure) null, arguments);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Object invoke(Class clazz, String methodName, Closure additionalCriteria, Object[] arguments) {
        DynamicFinderInvocation invocation = grammar.createFinderInvocation(clazz, methodName, additionalCriteria, arguments);
        return doInvoke(invocation);
    }

    /**
     * Not part of {@link FinderMethod} - called reflectively (dynamic Groovy dispatch) by {@link
     * org.grails.datastore.gorm.query.criteria.AbstractDetachedCriteria#methodMissing}. See {@link
     * SingleResultFinder#invoke(Class, String, DetachedCriteria, Object[])} for the full rationale.
     *
     * @param clazz The persistent class
     * @param methodName The method name
     * @param detachedCriteria The detached criteria to merge into the built query
     * @param arguments The method call arguments
     * @return The result of the method call
     */
    @SuppressWarnings("rawtypes")
    public Object invoke(Class clazz, String methodName, DetachedCriteria detachedCriteria, Object[] arguments) {
        DynamicFinderInvocation invocation = grammar.createFinderInvocation(clazz, methodName, null, arguments);
        if (detachedCriteria != null) {
            invocation.setDetachedCriteria(detachedCriteria);
        }
        return doInvoke(invocation);
    }

    private Object doInvoke(final DynamicFinderInvocation invocation) {
        return FinderSupport.execute(datastore, session -> {
            Query query = buildQuery(invocation, session);
            return query.singleResult();
        });
    }

    @Override
    public Query buildQuery(DynamicFinderInvocation invocation, Session session) {
        final Class<?> clazz = invocation.getJavaClass();
        Query query = session.createQuery(clazz);
        return applyCriteriaAndCount(invocation, clazz, query);
    }

    /**
     * Applies this finder's independent (non-{@link DynamicFinder#getJunction}) And/Or criteria
     * handling and the {@code count()} projection to an already-created query. Exposed as a public
     * static helper so other query-building strategies (e.g. {@code grails-datamapping-rx}'s
     * count finder, which builds its query outside a {@link Session}) can reuse this logic rather
     * than duplicating it.
     *
     * @param invocation The invocation
     * @param clazz The persistent class
     * @param query The already-created query to apply criteria to
     * @return The same query, for chaining
     */
    public static Query applyCriteriaAndCount(DynamicFinderInvocation invocation, Class<?> clazz, Query query) {
        DynamicFinder.applyAdditionalCriteria(query, invocation.getCriteria());
        DynamicFinder.applyDetachedCriteria(query, invocation.getDetachedCriteria());
        DynamicFinder.configureQueryWithArguments(clazz, query, invocation.getArguments());

        String operatorInUse = invocation.getOperator();
        if (OPERATOR_OR.equals(operatorInUse)) {
            Query.Junction disjunction = query.disjunction();
            for (MethodExpression expression : invocation.getExpressions()) {
                query.add(disjunction, expression.createCriterion());
            }
        }
        else {
            for (MethodExpression expression : invocation.getExpressions()) {
                query.add(expression.createCriterion());
            }
        }

        query.projections().count();
        return query;
    }
}
