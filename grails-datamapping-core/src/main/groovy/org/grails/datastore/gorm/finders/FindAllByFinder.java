/* Copyright (C) 2010 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.gorm.finders;

import java.util.regex.Pattern;

import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.core.SessionCallback;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.query.Query;

/**
 * Finder used to return multiple results. Eg. Book.findAllBy..(..)
 */
public class FindAllByFinder extends DynamicFinder {

    private static final String OPERATOR_OR = "Or";
    private static final String OPERATOR_AND = "And";
    private static final String METHOD_PATTERN = "(findAllBy)([A-Z]\\w*)";
    private static final String[] OPERATORS = { OPERATOR_AND, OPERATOR_OR };

    public FindAllByFinder(final Datastore datastore) {
        super(Pattern.compile(METHOD_PATTERN), OPERATORS, datastore);
    }

    public FindAllByFinder(final MappingContext mappingContext) {
        super(Pattern.compile(METHOD_PATTERN), OPERATORS, mappingContext);
    }

    @Override
    protected Object doInvokeInternal(final DynamicFinderInvocation invocation) {
        return execute(new SessionCallback<Object>() {
            public Object doInSession(final Session session) {
                Query q = buildQuery(invocation, session);
                return invokeQuery(q);
            }
        });
    }

    protected Object invokeQuery(Query q) {
        return q.list();
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
        }
        else {
            for (MethodExpression expression : invocation.getExpressions()) {
                query.add( expression.createCriterion() );
            }
        }
        query.projections().distinct();
        return query;
    }
}
