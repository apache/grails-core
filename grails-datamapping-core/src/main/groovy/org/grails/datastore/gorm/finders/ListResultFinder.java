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
import org.grails.datastore.mapping.core.SessionCallback;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.query.Query;

/**
 * Implements every dynamic finder that returns a list of results: {@code findAllBy*} and the
 * {@code findAll<booleanProperty>By*} boolean-clause form - configured via static factory methods
 * rather than subclassed, composing a {@link DynamicFinder} grammar instead of extending it.
 */
public class ListResultFinder implements FinderMethod, QueryBuildingFinder {

    private static final String FIND_ALL_BY_PATTERN = "(findAllBy)([A-Z]\\w*)";
    private static final String FIND_ALL_BY_BOOLEAN_PATTERN = "(findAll)((\\w+)(By)([A-Z]\\w*)|(\\w+))";
    private static final String[] OPERATORS = {"And", "Or"};

    private final Datastore datastore;
    private final DynamicFinder grammar;

    private ListResultFinder(Datastore datastore, DynamicFinder grammar) {
        this.datastore = datastore;
        this.grammar = grammar;
    }

    public static ListResultFinder findAllBy(Datastore datastore) {
        return new ListResultFinder(datastore, grammar(FIND_ALL_BY_PATTERN, datastore.getMappingContext(), false));
    }

    public static ListResultFinder findAllBy(MappingContext mappingContext) {
        return new ListResultFinder(null, grammar(FIND_ALL_BY_PATTERN, mappingContext, false));
    }

    public static ListResultFinder findAllByBoolean(Datastore datastore) {
        return new ListResultFinder(datastore, grammar(FIND_ALL_BY_BOOLEAN_PATTERN, datastore.getMappingContext(), true));
    }

    public static ListResultFinder findAllByBoolean(MappingContext mappingContext) {
        return new ListResultFinder(null, grammar(FIND_ALL_BY_BOOLEAN_PATTERN, mappingContext, true));
    }

    private static DynamicFinder grammar(String pattern, MappingContext mappingContext, boolean booleanClause) {
        return new DynamicFinder(Pattern.compile(pattern), OPERATORS, mappingContext, booleanClause);
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
        return FinderSupport.execute(datastore, (SessionCallback<Object>) session -> {
            Query query = buildQuery(invocation, session);
            query.projections().distinct();
            return query.list();
        });
    }

    @Override
    public Query buildQuery(DynamicFinderInvocation invocation, Session session) {
        return grammar.buildQuery(invocation, session);
    }
}
