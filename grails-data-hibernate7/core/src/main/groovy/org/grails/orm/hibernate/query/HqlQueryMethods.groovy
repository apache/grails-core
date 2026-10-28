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
package org.grails.orm.hibernate.query

import java.lang.reflect.Array

import groovy.transform.CompileStatic
import jakarta.persistence.LockModeType

@CompileStatic
interface HqlQueryMethods {

    Set<String> INTERNAL_SETTINGS = Set.of(
        HibernateQueryArgument.FLUSH_MODE.value(),
        HibernateQueryArgument.CACHE.value(),
        HibernateQueryArgument.TIMEOUT.value(),
        HibernateQueryArgument.READ_ONLY.value(),
        HibernateQueryArgument.FETCH_SIZE.value(),
        HibernateQueryArgument.MAX.value(),
        HibernateQueryArgument.OFFSET.value(),
        HibernateQueryArgument.LOCK.value()
    )

    default void populateQuerySettings(HqlQueryDelegate d, Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return
        }
        if (args.containsKey(HibernateQueryArgument.FLUSH_MODE.value())) {
            d.setQueryFlushMode(GrailsQueryFlushMode.mapToHibernateQueryFlushMode(args.get(HibernateQueryArgument.FLUSH_MODE.value())))
        }
        if (args.containsKey(HibernateQueryArgument.MAX.value())) {
            d.setMaxResults(toInteger(args.get(HibernateQueryArgument.MAX.value())))
        }
        if (args.containsKey(HibernateQueryArgument.OFFSET.value())) {
            d.setFirstResult(toInteger(args.get(HibernateQueryArgument.OFFSET.value())))
        }
        if (args.containsKey(HibernateQueryArgument.FETCH_SIZE.value())) {
            d.setFetchSize(toInteger(args.get(HibernateQueryArgument.FETCH_SIZE.value())))
        }
        if (args.containsKey(HibernateQueryArgument.TIMEOUT.value())) {
            d.setTimeout(toInteger(args.get(HibernateQueryArgument.TIMEOUT.value())))
        }
        if (args.containsKey(HibernateQueryArgument.READ_ONLY.value())) {
            d.setReadOnly(toBoolean(args.get(HibernateQueryArgument.READ_ONLY.value())))
        }
        if (toBoolean(args.get(HibernateQueryArgument.LOCK.value()))) {
            d.setLockMode(LockModeType.PESSIMISTIC_WRITE)
            d.setCacheable(false)
        } else if (args.containsKey(HibernateQueryArgument.CACHE.value())) {
            d.setCacheable(toBoolean(args.get(HibernateQueryArgument.CACHE.value())))
        }
    }

    static int toInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue()
        }
        return Integer.parseInt(value.toString())
    }

    static boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value
        }
        return value != null && Boolean.parseBoolean(value.toString())
    }

    default void populateHints(HqlQueryDelegate d, Map<String, Object> hints) {
        if (hints == null || hints.isEmpty()) {
            return
        }
        for (Map.Entry<String, Object> entry : hints.entrySet()) {
            d.setHint(entry.key, entry.value)
        }
    }

    static void populateParameters(HqlQueryDelegate d, HqlQueryContext queryContext) {
        if (queryContext.namedParams() != null && !queryContext.namedParams().isEmpty()) {
            for (Map.Entry<String, Object> entry : queryContext.namedParams().entrySet()) {
                if (INTERNAL_SETTINGS.contains(entry.key)) {
                    continue
                }
                Object val = convertValue(entry.value)
                if (val instanceof Collection) {
                    d.setParameterList(entry.key, (Collection<?>) val)
                } else if (val != null && val.class.isArray()) {
                    d.setParameterList(entry.key, (Object[]) val)
                } else {
                    d.setParameter(entry.key, val)
                }
            }
        } else if (queryContext.positionalParams() != null && !queryContext.positionalParams().isEmpty()) {
            for (int i = 0; i < queryContext.positionalParams().size(); i++) {
                Object val = convertValue(queryContext.positionalParams().get(i))
                d.setParameter(i + 1, val)
            }
        }
    }

    static Object convertValue(Object value) {
        if (value instanceof CharSequence) {
            return value.toString()
        }
        if (value instanceof Collection<?>) {
            Collection<?> coll = (Collection<?>) value
            List<Object> newList = new ArrayList<>(coll.size())
            for (Object o : coll) {
                newList.add(convertValue(o))
            }
            return newList
        }
        if (value != null && value.class.isArray()) {
            int length = Array.getLength(value)
            Class<?> componentType = value.class.componentType
            Class<?> targetType = (componentType == CharSequence || CharSequence.isAssignableFrom(componentType)) ? String : componentType
            Object newArray = Array.newInstance(targetType, length)
            for (int i = 0; i < length; i++) {
                Array.set(newArray, i, convertValue(Array.get(value, i)))
            }
            return newArray
        }
        return value
    }

}
