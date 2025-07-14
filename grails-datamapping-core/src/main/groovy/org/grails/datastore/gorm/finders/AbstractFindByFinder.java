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

import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.core.SessionCallback;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.query.Query;

import java.util.regex.Pattern;

public abstract class AbstractFindByFinder extends DynamicFinder {

    public static final String OPERATOR_OR = "Or";
    public static final String OPERATOR_AND = "And";
    public static final String[] OPERATORS = {OPERATOR_AND, OPERATOR_OR};

    protected AbstractFindByFinder(Pattern pattern, Datastore datastore) {
        super(pattern, OPERATORS, datastore);
    }

    protected AbstractFindByFinder(Pattern pattern, MappingContext mappingContext) {
        super(pattern, OPERATORS, mappingContext);
    }

    @Override
    protected Object doInvokeInternal(final DynamicFinderInvocation invocation) {
        return execute(new SessionCallback<Object>() {
            public Object doInSession(final Session session) {
                return invokeQuery(buildQuery(invocation, session));
            }
        });
    }

    protected Object invokeQuery(Query q) {
        return q.singleResult();
    }

    public boolean firstExpressionIsRequiredBoolean() {
        return false;
    }

    public Query buildQuery(DynamicFinderInvocation invocation, Session session) {
        final Class<?> clazz = invocation.getJavaClass();
        Query q = session.createQuery(clazz);
        return buildQuery(invocation, clazz, q);
    }

    protected Query buildQuery(DynamicFinderInvocation invocation, Class<?> clazz, Query query) {
        applyAdditionalCriteria(query, invocation.getCriteria());
        applyDetachedCriteria(query, invocation.getDetachedCriteria());
        configureQueryWithArguments(clazz, query, invocation.getArguments());

        final String operatorInUse = invocation.getOperator();

        if (operatorInUse != null && operatorInUse.equals(OPERATOR_OR)) {
            if (firstExpressionIsRequiredBoolean()) {
                MethodExpression expression = invocation.getExpressions().remove(0);
                query.add(expression.createCriterion());
            }

            Query.Junction disjunction = query.disjunction();

            for (MethodExpression expression : invocation.getExpressions()) {
                query.add(disjunction, expression.createCriterion());
            }
        } else {
            for (MethodExpression expression : invocation.getExpressions()) {
                query.add(expression.createCriterion());
            }
        }
        return query;
    }

}
