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

package org.grails.datastore.mapping.core.order

import groovy.transform.CompileStatic

import org.grails.datastore.mapping.core.Ordered

/**
 * Support class for ordering objects that implement {@link org.grails.datastore.mapping.core.Ordered}
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class OrderedComparator<T> implements Comparator<T> {


    /**
     * Useful constant for the highest precedence value.
     * @see java.lang.Integer#MIN_VALUE
     */
    public static final int HIGHEST_PRECEDENCE = Integer.MIN_VALUE

    /**
     * Useful constant for the lowest precedence value.
     * @see java.lang.Integer#MAX_VALUE
     */
    public static final int LOWEST_PRECEDENCE = Integer.MAX_VALUE

    @Override
    int compare(T o1, T o2) {
        int i1 = getOrder(o1)
        int i2 = getOrder(o2)
        return i1 <=> i2
    }

    /**
     * Determine the order value for the given object.
     * @param obj the object to check
     * @return the order value, or {@code Ordered.LOWEST_PRECEDENCE} as fallback
     */
    protected Integer getOrder(Object obj) {
        Integer order = findOrder(obj)
        return (order != null ? order : LOWEST_PRECEDENCE)
    }
    /**
     * Find an order value indicated by the given object.
     * <p>The default implementation checks against the {@link Ordered} trait.
     * Can be overridden in subclasses.
     *
     * @param obj the object to check
     * @return the order value, or {@code null} if none found
     */
    protected Integer findOrder(Object obj) {
        return (obj instanceof Ordered ? ((Ordered) obj).getOrder() : null)
    }
}
