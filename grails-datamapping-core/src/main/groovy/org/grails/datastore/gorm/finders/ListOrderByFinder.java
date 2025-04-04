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

import groovy.lang.Closure;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * @author Graeme Rocher
 */
public class ListOrderByFinder extends AbstractFinder {
    private static final Pattern METHOD_PATTERN = Pattern.compile("(listOrderBy)(\\w+)");
    private Pattern pattern = METHOD_PATTERN;

    public ListOrderByFinder(Datastore datastore) {
        super(datastore);
    }

    public void setPattern(String pattern) {
        this.pattern = Pattern.compile(pattern);
    }

    @SuppressWarnings("rawtypes")
    public Object invoke(final Class clazz, final String methodName, final Object[] arguments) {
        return invoke(clazz, methodName, null, arguments);
    }

    @SuppressWarnings("rawtypes")
    public Object invoke(final Class clazz, final String methodName, final Closure additionalCriteria, final Object[] arguments) {

        Matcher match = pattern.matcher(methodName);
        match.find();

        String nameInSignature = match.group(2);
        final String propertyName = NameUtils.decapitalizeFirstChar(nameInSignature);

        return execute(new SessionCallback<Object>() {
            public Object doInSession(final Session session) {
                Query q = session.createQuery(clazz);
                applyAdditionalCriteria(q, additionalCriteria);

                boolean ascending = true;
                if (arguments.length > 0 && (arguments[0] instanceof Map)) {
                    final Map args = new LinkedHashMap( (Map) arguments[0] );
                    final Object order = args.remove(DynamicFinder.ARGUMENT_ORDER);
                    if(order != null && "desc".equalsIgnoreCase(order.toString())) {
                        ascending = false;
                    }
                    DynamicFinder.populateArgumentsForCriteria(clazz, q, args);
                }

                q.order( ascending ? Query.Order.asc(propertyName) : Query.Order.desc(propertyName));
                q.projections().distinct();
                return invokeQuery(q);
            }
        });
    }

    protected Object invokeQuery(Query q) {
        return q.list();
    }

    public boolean isMethodMatch(String methodName) {
        return pattern.matcher(methodName.subSequence(0, methodName.length())).find();
    }

}
