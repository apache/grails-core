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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import groovy.lang.Closure;

import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.core.SessionCallback;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.reflect.NameUtils;

/**
 * The "listOrderBy*" static persistent method. Allows ordered listing of instances based on their properties.
 *
 * eg.
 * Account.listOrderByHolder();
 * Account.listOrderByHolder(max); // max results
 *
 * <p>Never shared {@link DynamicFinder}'s grammar (no And/Or/operator-suffix parsing - just a
 * single trailing property name), so it stays its own standalone implementation, composing
 * nothing beyond {@link FinderSupport} for session execution.
 *
 * @author Graeme Rocher
 */
public class ListOrderByFinder implements FinderMethod {

    private static final Pattern METHOD_PATTERN = Pattern.compile("(listOrderBy)(\\w+)");
    private final Datastore datastore;
    private Pattern pattern = METHOD_PATTERN;

    public ListOrderByFinder(Datastore datastore) {
        this.datastore = datastore;
    }

    @Override
    public void setPattern(String pattern) {
        this.pattern = Pattern.compile(pattern);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Object invoke(final Class clazz, final String methodName, final Object[] arguments) {
        return invoke(clazz, methodName, null, arguments);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Object invoke(final Class clazz, final String methodName, final Closure additionalCriteria, final Object[] arguments) {

        Matcher match = pattern.matcher(methodName);
        match.find();

        String nameInSignature = match.group(2);
        final String propertyName = NameUtils.decapitalizeFirstChar(nameInSignature);

        return FinderSupport.execute(datastore, new SessionCallback<>() {
            public Object doInSession(final Session session) {
                Query q = session.createQuery(clazz);
                DynamicFinder.applyAdditionalCriteria(q, additionalCriteria);

                boolean ascending = true;
                if (arguments.length > 0 && (arguments[0] instanceof Map)) {
                    final Map args = new LinkedHashMap((Map) arguments[0]);
                    final Object order = args.remove(DynamicFinder.ARGUMENT_ORDER);
                    if (order != null && "desc".equalsIgnoreCase(order.toString())) {
                        ascending = false;
                    }
                    DynamicFinder.populateArgumentsForCriteria(clazz, q, args);
                }

                q.order(ascending ? Query.Order.asc(propertyName) : Query.Order.desc(propertyName));
                q.projections().distinct();
                return q.list();
            }
        });
    }

    @Override
    public boolean isMethodMatch(String methodName) {
        return pattern.matcher(methodName.subSequence(0, methodName.length())).find();
    }
}
