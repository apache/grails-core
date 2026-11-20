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

import example.query.NumberLikeExpression
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.query.api.BuildableCriteria

/**
 * Groovy extension module methods added to {@link AbstractHibernateCriteriaBuilder}.
 *
 * Placed in the same package as {@code AbstractHibernateCriteriaBuilder} so that
 * {@code @CompileStatic} can access its {@code protected} helper methods.
 *
 * These methods are available inside GORM criteria closures, e.g.:
 * <pre>
 *     Product.withCriteria {
 *         numberLike 'price', '100%'
 *     }
 * </pre>
 *
 * Registered via META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule.
 */
@CompileStatic
class HibernateCriteriaBuilderExtensions {

    /**
     * Adds a {@link NumberLikeExpression} criterion that casts a numeric column to a
     * string using {@code to_char/trunc} and performs a LIKE comparison.
     *
     * The {@code propertyValue} must be a String and may use SQL {@code %} wildcards.
     * Commas are stripped from the value before comparison so formatted numbers
     * (e.g. {@code "1,000"}) work correctly.
     *
     * On H2 falls back to {@code cast(col as varchar) like ?}.
     *
     * @param self          the criteria builder
     * @param propertyName  the domain property to match
     * @param propertyValue a String value (with optional {@code %} wildcards)
     * @return the criteria builder for chaining
     */
    static BuildableCriteria numberLike(AbstractHibernateCriteriaBuilder self, String propertyName, Object propertyValue) {
        if (!self.validateSimpleExpression()) {
            self.throwRuntimeException(new IllegalArgumentException(
                "Call to [numberLike] with propertyName [${propertyName}] and value [${propertyValue}] not allowed here."
            ))
        }
        propertyName = self.calculatePropertyName(propertyName)
        propertyValue = self.calculatePropertyValue(propertyValue)

        assert propertyValue instanceof String, "numberLike value for [${propertyName}] must be a String"
        self.addToCriteria(new NumberLikeExpression(propertyName, propertyValue as String))
        self
    }
}
