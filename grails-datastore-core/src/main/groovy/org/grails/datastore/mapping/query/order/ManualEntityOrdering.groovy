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
package org.grails.datastore.mapping.query.order

import java.beans.PropertyDescriptor
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.springframework.beans.BeanWrapper
import org.springframework.beans.PropertyAccessorFactory
import org.springframework.util.ReflectionUtils

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.query.Query.Order

/**
 * Simple support for ordering results in memory. Used by datastores that don't support
 * native ordering
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@SuppressWarnings(['rawtypes', 'unchecked'])
class ManualEntityOrdering {

    private static final Map<String, Method> cachedReadMethods = new ConcurrentHashMap<>()

    private final PersistentEntity entity

    ManualEntityOrdering(PersistentEntity entity) {
        this.entity = entity
    }

    PersistentEntity getEntity() {
        return entity
    }

    List applyOrder(List results, List<Order> orderDefinition) {
        if (results == null) {
            return null
        }
        if (orderDefinition == null) {
            return results
        }
        List ordered = results
        for (Order order in orderDefinition) {
            ordered = applyOrder(ordered, order)
        }
        return ordered
    }

    private static List reverse(List list) {
        int size = list.size()
        List answer = new ArrayList(size)
        ListIterator iter = list.listIterator(size)
        while (iter.hasPrevious()) {
            answer.add(iter.previous())
        }
        return answer
    }

    List applyOrder(List results, Order order) {
        final String name = order.getProperty()
        final PersistentEntity ownerEntity = getEntity()
        PersistentProperty property = ownerEntity.getPropertyByName(name)
        if (property == null) {
            final PersistentProperty identity = ownerEntity.getIdentity()
            if (name == identity.getName()) {
                property = identity
            }
        }
        if (property != null) {
            final PersistentProperty finalProperty = property
            Collections.sort(results, new PropertyComparator(ownerEntity, finalProperty))
        }
        if (order.getDirection() == Order.Direction.DESC) {
            return reverse(results)
        }
        return results
    }

    @PackageScope
    static Method readMethodFor(String propertyName, Object instance) {
        Method readMethod = cachedReadMethods.get(propertyName)
        if (readMethod == null) {
            BeanWrapper b = PropertyAccessorFactory.forBeanPropertyAccess(instance)
            final PropertyDescriptor pd = b.getPropertyDescriptor(propertyName)
            if (pd != null) {
                readMethod = pd.getReadMethod()
                if (readMethod != null) {
                    ReflectionUtils.makeAccessible(readMethod)
                    cachedReadMethods.put(propertyName, readMethod)
                }
            }
        }
        return readMethod
    }

    @PackageScope
    static class PropertyComparator implements Comparator {

        private final PersistentEntity entity
        private final PersistentProperty property

        PropertyComparator(PersistentEntity entity, PersistentProperty property) {
            this.entity = entity
            this.property = property
        }

        @Override
        int compare(Object o1, Object o2) {
            if (entity.isInstance(o1) && entity.isInstance(o2)) {
                final String propertyName = property.getName()
                Method readMethod = readMethodFor(propertyName, o1)
                if (readMethod != null) {
                    final Class<?> declaringClass = readMethod.getDeclaringClass()
                    if (declaringClass.isInstance(o1) && declaringClass.isInstance(o2)) {
                        Object left = ReflectionUtils.invokeMethod(readMethod, o1)
                        Object right = ReflectionUtils.invokeMethod(readMethod, o2)
                        if (left == null && right == null) {
                            return 0
                        }
                        if (left != null && right == null) {
                            return 1
                        }
                        if (left == null) {
                            return -1
                        }
                        if ((left instanceof Comparable) && (right instanceof Comparable)) {
                            return ((Comparable) left).compareTo(right)
                        }
                    }
                }
            }
            return 0
        }

    }

}
