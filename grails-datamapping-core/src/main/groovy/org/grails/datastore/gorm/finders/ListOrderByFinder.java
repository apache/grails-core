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

import org.grails.datastore.gorm.DatastoreResolver;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.core.SessionCallback;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.reflect.NameUtils;

/**
 * The "listOrderBy*" static persistent method. This method allows queries on the properties of the class of the form
 * listOrderBy[Property]([Map] args)
 *
 * eg.
 * Book.listOrderByTitle(max:10)
 * Book.listOrderByTitleAndAuthor(max:10)
 *
 * @author Graeme Rocher
 */
public class ListOrderByFinder extends AbstractFinder {
    private static final Pattern METHOD_PATTERN = Pattern.compile("(listOrderBy)(\\w+)");
    private Pattern pattern = METHOD_PATTERN;

    public ListOrderByFinder(final Datastore datastore) {
        super(datastore);
    }

    public ListOrderByFinder(DatastoreResolver datastoreResolver, MappingContext mappingContext) {
        super(datastoreResolver);
    }

    public void setPattern(String pattern) {
        this.pattern = Pattern.compile(pattern);
    }

    public boolean isMethodMatch(String methodName) {
        return pattern.matcher(methodName).find();
    }

    @Override
    public Object invoke(final Class clazz, final String methodName, final Object[] arguments) {
        return invoke(clazz, methodName, null, arguments);
    }

    @Override
    public Object invoke(final Class clazz, final String methodName, final Closure additionalCriteria, final Object[] arguments) {
        return execute(new SessionCallback<Object>() {
            @Override
            public Object doInSession(final Session session) {
                final Matcher matcher = pattern.matcher(methodName);
                matcher.find();
                String parts = matcher.group(2);

                final Query q = session.createQuery(clazz);
                String[] propertyNames = parts.split("And");

                // Resolve the sort direction BEFORE applying any order. Applying asc first and then
                // trying to clear/replace it leaves the eagerly-applied asc order in the underlying
                // criteria, so an explicit order:'desc' argument was silently ignored.
                boolean ascending = true;
                if (arguments.length > 0 && (arguments[0] instanceof Map)) {
                    Map args = new LinkedHashMap((Map) arguments[0]);
                    final Object order = args.remove("order");
                    if (order != null && order.toString().equalsIgnoreCase("desc")) {
                        ascending = false;
                    }
                    DynamicFinder.populateArgumentsForCriteria(clazz, q, args);
                }

                for (String propertyName : propertyNames) {
                    // Lower-case only the first character: a GORM property's name is the method-name
                    // segment with its first letter de-capitalised. JavaBeans-style decapitalize()
                    // leaves a name whose first two letters are upper-case unchanged (e.g. "ISize"),
                    // which would not match a Hungarian-notation property such as "iSize".
                    String property = NameUtils.decapitalizeFirstChar(propertyName);
                    q.order(ascending ? Query.Order.asc(property) : Query.Order.desc(property));
                }

                if (additionalCriteria != null) {
                    applyAdditionalCriteria(q, additionalCriteria);
                }

                return q.list();
            }
        });
    }
}
