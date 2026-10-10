/* Copyright (C) 2010-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License')
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.mapping.query.projections

import groovy.transform.CompileStatic
import org.codehaus.groovy.runtime.DefaultGroovyMethods

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.query.Query.Order
import org.grails.datastore.mapping.query.order.ManualEntityOrdering
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.datastore.mapping.reflect.FieldEntityAccess

/**
 * Implements common projections in-memory given a set of results. Not all
 * NoSQL datastores support projections like SQL min(..), max(..) etc.
 * This class provides support for those that don't.
 */
@SuppressWarnings(['rawtypes', 'unchecked'])
@CompileStatic
class ManualProjections {

    private PersistentEntity entity
    private ManualEntityOrdering order

    ManualProjections(PersistentEntity entity) {
        this.entity = entity
        this.order = new ManualEntityOrdering(entity)
    }

    /**
     * Calculates the minimum value of a property
     *
     * @param results The results
     * @param property The property to calculate
     * @return The minimum value or null if there are no results
     */
    Object min(Collection results, String property) {
        if (results == null || results.isEmpty()) {
            return null
        }

        final List sorted = order.applyOrder(new ArrayList(results), Order.asc(property))
        final Object o = sorted.get(0)
        if (entity.isInstance(o)) {
            return FieldEntityAccess.getOrIntializeReflector(entity).getProperty(o, property)
        }
        return o
    }

    /**
     * Counts the number of distinct values
     *
     * @param results The results
     * @param property The property
     * @return A count of the distinct values
     */
    int countDistinct(Collection results, String property) {
        Collection propertyValues = distinct(results, property)
        return propertyValues.size()
    }

    Collection distinct(Collection results, String property) {
        List propertyValues = this.property(results, property)

        return DefaultGroovyMethods.unique(propertyValues)
    }

    /**
     * Calculates the maximum value of a property
     *
     * @param results The results
     * @param property The property to calculate
     * @return The maximum value or null if there are no results
     */
    Object max(Collection results, String property) {
        if (results == null || results.isEmpty()) {
            return null
        }

        final List sorted = order.applyOrder(new ArrayList(results), Order.asc(property))
        final Object o = sorted.get(results.size() - 1)
        if (entity.isInstance(o)) {
            return FieldEntityAccess.getOrIntializeReflector(entity).getProperty(o, property)
        }
        return o
    }

    /**
     * Obtains a properties value from the results
     *
     * @param results The results
     * @param property The property
     * @return A list of results
     */
    List property(Collection results, String property) {
        List projectedResults = new ArrayList()
        if (results == null || results.isEmpty()) {
            return projectedResults
        }

        for (Object o in results) {

            EntityReflector ea = FieldEntityAccess.getOrIntializeReflector(entity)
            if (entity.isInstance(o)) {
                projectedResults.add(ea.getProperty(o, property))
            }
            else {
                projectedResults.add(null)
            }
        }

        return projectedResults
    }

}
