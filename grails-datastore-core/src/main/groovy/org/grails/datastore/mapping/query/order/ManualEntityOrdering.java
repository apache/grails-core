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
package org.grails.datastore.mapping.query.order;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.query.Query;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.util.ReflectionUtils;

/**
 * Manual implementation of query ordering for datastores that don't support native ordering. Not all
 * NoSQL datastores support the SQL equivalent of ORDER BY, hence manual in-memory ordering is the
 * only way to simulate such queries.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class ManualEntityOrdering {

    PersistentEntity entity;
    private static Map<String, Method> cachedReadMethods = new ConcurrentHashMap<String, Method>();

    public ManualEntityOrdering(PersistentEntity entity) {
        this.entity = entity;
    }

    public PersistentEntity getEntity() {
        return entity;
    }

    public List applyOrder(List results, List<Query.Order> orderDefinition) {
        if (results == null) return null;
        if (orderDefinition == null) return results;
        for (Query.Order order : orderDefinition) {
            results = applyOrder(results, order);
        }
        return results;
    }

    /**
     * Reverses the list.  The result is a new List with the identical contents
     * in reverse order.
     *
     * @param list a List
     * @return a reversed List
     */
    private static List reverse(List list) {
        int size = list.size();
        List answer = new ArrayList(size);
        ListIterator iter = list.listIterator(size);
        while (iter.hasPrevious()) {
            answer.add(iter.previous());
        }
        return answer;
    }

    public List applyOrder(List results, Query.Order order) {
       final String name = order.getProperty();

        final PersistentEntity entity = getEntity();
        PersistentProperty property = entity.getPropertyByName(name);
        if (property == null) {
            final PersistentProperty identity = entity.getIdentity();
            if (name.equals(identity.getName())) {
                property = identity;
            }
        }

        if (property != null) {
            final PersistentProperty finalProperty = property;
            Collections.sort(results, new Comparator() {

                public int compare(Object o1, Object o2) {

                    if (entity.isInstance(o1) && entity.isInstance(o2)) {
                        final String propertyName = finalProperty.getName();
                        Method readMethod = cachedReadMethods.get(propertyName);
                        if (readMethod == null) {
                            BeanWrapper b = PropertyAccessorFactory.forBeanPropertyAccess(o1);
                            final PropertyDescriptor pd = b.getPropertyDescriptor(propertyName);
                            if (pd != null) {
                                readMethod = pd.getReadMethod();
                                if (readMethod != null) {
                                    ReflectionUtils.makeAccessible(readMethod);
                                    cachedReadMethods.put(propertyName, readMethod);
                                }
                            }
                        }

                        if (readMethod != null) {
                            final Class<?> declaringClass = readMethod.getDeclaringClass();
                            if (declaringClass.isInstance(o1) && declaringClass.isInstance(o2)) {
                                Object left = ReflectionUtils.invokeMethod(readMethod, o1);
                                Object right = ReflectionUtils.invokeMethod(readMethod, o2);

                                if (left == null && right == null) return 0;
                                if (left != null && right == null) return 1;
                                if (left == null) return -1;
                                if ((left instanceof Comparable) && (right instanceof Comparable)) {
                                    return ((Comparable)left).compareTo(right);
                                }
                            }
                        }
                    }
                    return 0;
                }
            });
        }

        if (order.getDirection() == Query.Order.Direction.DESC) {
            results = reverse(results);
        }

        return results;
    }
}
