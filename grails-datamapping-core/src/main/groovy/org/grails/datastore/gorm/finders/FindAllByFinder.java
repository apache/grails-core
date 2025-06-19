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

import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.core.SessionCallback;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.query.Query;

/**
 * Finder used to return multiple results. Eg. Book.findAllBy..(..)
 */
public class FindAllByFinder extends DynamicFinder {

    protected static final String OPERATOR_OR = "Or";
    protected static final String OPERATOR_AND = "And";
    private static final String METHOD_PATTERN = "(findAllBy)([A-Z]\\w*)";
    protected static final String[] OPERATORS = { OPERATOR_AND, OPERATOR_OR };

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
                Query query = buildQuery(invocation, session);
                adjustQuery(query);
                return invokeQuery(query);
            }
        });
    }

    protected Object invokeQuery(Query q) {
        return q.list();
    }

    protected void adjustQuery( Query query) {
        query.projections().distinct();
    }

}
