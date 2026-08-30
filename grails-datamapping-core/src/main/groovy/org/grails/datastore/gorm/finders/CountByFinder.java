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

import grails.gorm.DetachedCriteria;
import org.grails.datastore.gorm.DatastoreResolver;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.core.SessionCallback;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.query.Query;

/**
 * Supports countBy* queries.
 *
 * @author Graeme Rocher
 */
public class CountByFinder extends DynamicFinder implements QueryBuildingFinder {

    private static final String METHOD_PATTERN = "(countBy)([A-Z]\\w*)";
    protected static final String[] OPERATORS = { "And", "Or" };

    public CountByFinder(final Datastore datastore) {
        super(Pattern.compile(METHOD_PATTERN), OPERATORS, datastore);
    }

    public CountByFinder(DatastoreResolver datastoreResolver, MappingContext mappingContext) {
        super(Pattern.compile(METHOD_PATTERN), OPERATORS, datastoreResolver, mappingContext);
    }

    public CountByFinder(MappingContext mappingContext) {
        super(Pattern.compile(METHOD_PATTERN), OPERATORS, mappingContext);
    }

    @Override
    protected Object doInvokeInternal(final DynamicFinderInvocation invocation) {
        return execute(new SessionCallback<Object>() {
            public Object doInSession(final Session session) {
                Query q = buildQuery(invocation, session);
                q.projections().count();
                return q.singleResult();
            }
        });
    }

    @Override
    public Query buildQuery(DynamicFinderInvocation invocation, Session session) {
        final Class clazz = invocation.getJavaClass();
        Query q = session.createQuery(clazz);
        applyDetachedCriteria(q, invocation.getDetachedCriteria());

        final String operator = invocation.getOperator();
        if (operator != null && operator.equals("Or")) {
            Query.Junction disjunction = q.disjunction();
            for (MethodExpression expression : invocation.getExpressions()) {
                disjunction.add(expression.createCriterion());
            }
        }
        else {
            for (MethodExpression expression : invocation.getExpressions()) {
                q.add(expression.createCriterion());
            }
        }
        return q;
    }

    protected void applyDetachedCriteria(Query q, DetachedCriteria detachedCriteria) {
        if (detachedCriteria != null) {
            DynamicFinder.applyDetachedCriteria(q, detachedCriteria);
        }
    }

}
