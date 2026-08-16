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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import groovy.lang.Closure;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import groovy.lang.MissingMethodException;

import org.springframework.core.convert.ConversionException;

import grails.gorm.DetachedCriteria;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.query.Query;

/**
 * Implements every dynamic finder that returns a single result: {@code findBy*}, the
 * {@code find<booleanProperty>By*} boolean-clause form, {@code findOrCreateBy*} and
 * {@code findOrSaveBy*} - configured via static factory methods rather than subclassed, composing
 * a {@link DynamicFinder} grammar instead of extending it.
 */
public class SingleResultFinder implements FinderMethod, QueryBuildingFinder {

    private static final String FIND_BY_PATTERN = "(findBy)([A-Z]\\w*)";
    private static final String FIND_BY_BOOLEAN_PATTERN = "(find)((\\w+)(By)([A-Z]\\w*)|(\\w++))";
    private static final String FIND_OR_CREATE_BY_PATTERN = "(findOrCreateBy)([A-Z]\\w*)";
    private static final String FIND_OR_SAVE_BY_PATTERN = "(findOrSaveBy)([A-Z]\\w*)";
    private static final String[] OPERATORS = {"And", "Or"};
    private static final String OPERATOR_OR = "Or";

    private final Datastore datastore;
    private final DynamicFinder grammar;
    private final Consumer<DynamicFinderInvocation> validate;
    private final Function<DynamicFinderInvocation, Object> onNullResult;

    private SingleResultFinder(Datastore datastore, DynamicFinder grammar,
            Consumer<DynamicFinderInvocation> validate, Function<DynamicFinderInvocation, Object> onNullResult) {
        this.datastore = datastore;
        this.grammar = grammar;
        this.validate = validate;
        this.onNullResult = onNullResult;
    }

    public static SingleResultFinder findBy(Datastore datastore) {
        return new SingleResultFinder(datastore, grammar(FIND_BY_PATTERN, datastore.getMappingContext(), false), null, null);
    }

    public static SingleResultFinder findBy(MappingContext mappingContext) {
        return new SingleResultFinder(null, grammar(FIND_BY_PATTERN, mappingContext, false), null, null);
    }

    public static SingleResultFinder findByBoolean(Datastore datastore) {
        return new SingleResultFinder(datastore, grammar(FIND_BY_BOOLEAN_PATTERN, datastore.getMappingContext(), true), null, null);
    }

    public static SingleResultFinder findByBoolean(MappingContext mappingContext) {
        return new SingleResultFinder(null, grammar(FIND_BY_BOOLEAN_PATTERN, mappingContext, true), null, null);
    }

    public static SingleResultFinder findOrCreateBy(Datastore datastore) {
        return findOrCreateOrSave(datastore, datastore.getMappingContext(), false);
    }

    public static SingleResultFinder findOrCreateBy(MappingContext mappingContext) {
        return findOrCreateOrSave(null, mappingContext, false);
    }

    public static SingleResultFinder findOrSaveBy(Datastore datastore) {
        return findOrCreateOrSave(datastore, datastore.getMappingContext(), true);
    }

    public static SingleResultFinder findOrSaveBy(MappingContext mappingContext) {
        return findOrCreateOrSave(null, mappingContext, true);
    }

    private static SingleResultFinder findOrCreateOrSave(Datastore datastore, MappingContext mappingContext, boolean save) {
        String pattern = save ? FIND_OR_SAVE_BY_PATTERN : FIND_OR_CREATE_BY_PATTERN;
        return new SingleResultFinder(datastore, grammar(pattern, mappingContext, false),
                SingleResultFinder::rejectOrAndComparisonOperators,
                invocation -> constructFromEqualExpressions(invocation, save));
    }

    private static DynamicFinder grammar(String pattern, MappingContext mappingContext, boolean booleanClause) {
        return new DynamicFinder(Pattern.compile(pattern), OPERATORS, mappingContext, booleanClause);
    }

    /**
     * Shared by {@code findOrCreateBy}/{@code findOrSaveBy}: rejects the {@code Or} operator and
     * any comparison-based expression (only equality-based finds can be created/saved).
     */
    private static void rejectOrAndComparisonOperators(DynamicFinderInvocation invocation) {
        if (OPERATOR_OR.equals(invocation.getOperator())) {
            throw new MissingMethodException(invocation.getMethodName(), invocation.getJavaClass(), invocation.getArguments());
        }
        for (MethodExpression methodExpression : invocation.getExpressions()) {
            if (methodExpression instanceof MethodExpression.GreaterThan ||
                    methodExpression instanceof MethodExpression.LessThan ||
                    methodExpression instanceof MethodExpression.GreaterThanEquals ||
                    methodExpression instanceof MethodExpression.LessThanEquals) {
                throw new ConfigurationException("Only equality-based expressions are supported for " + invocation.getMethodName());
            }
        }
    }

    /**
     * Shared by {@code findOrCreateBy}/{@code findOrSaveBy}: constructs a new instance from the
     * invocation's {@code Equal}-typed expressions when the underlying query returns null, saving
     * it too when {@code save} is true. The single implementation this helper provides is what
     * prevents the two finder kinds from ever diverging into duplicate, driftable copies.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object constructFromEqualExpressions(DynamicFinderInvocation invocation, boolean save) {
        Map m = new HashMap();
        List<MethodExpression> expressions = invocation.getExpressions();
        for (MethodExpression me : expressions) {
            if (!(me instanceof MethodExpression.Equal)) {
                throw new MissingMethodException(invocation.getMethodName(), invocation.getJavaClass(), invocation.getArguments());
            }
            String propertyName = me.propertyName;
            Object[] arguments = me.getArguments();
            m.put(propertyName, arguments[0]);
        }
        MetaClass metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(invocation.getJavaClass());
        Object result = metaClass.invokeConstructor(new Object[]{m});
        if (save) {
            metaClass.invokeMethod(result, "save", null);
        }
        return result;
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
     * org.grails.datastore.gorm.query.criteria.AbstractDetachedCriteria#methodMissing}, which
     * invokes a matched dynamic finder with the detached criteria instance itself as the "extra"
     * argument so its accumulated criteria/projections/orders get merged into the built query.
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
        if (validate != null) {
            validate.accept(invocation);
        }
        return FinderSupport.execute(datastore, session -> {
            Object result;
            if (onNullResult != null) {
                try {
                    result = buildQuery(invocation, session).singleResult();
                } catch (ConversionException e) {
                    throw new MissingMethodException(invocation.getMethodName(), invocation.getJavaClass(), invocation.getArguments());
                }
                if (result == null) {
                    result = onNullResult.apply(invocation);
                }
            }
            else {
                result = buildQuery(invocation, session).singleResult();
            }
            return result;
        });
    }

    @Override
    public Query buildQuery(DynamicFinderInvocation invocation, Session session) {
        return grammar.buildQuery(invocation, session);
    }
}
