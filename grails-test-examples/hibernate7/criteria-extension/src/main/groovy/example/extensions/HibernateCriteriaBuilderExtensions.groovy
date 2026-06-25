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

package example.extensions

import example.query.NumberLikeCriterion
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.query.criteria.AbstractDetachedCriteria

/**
 * Groovy extension module methods added to {@link AbstractDetachedCriteria}.
 *
 * <p>These methods become available inside {@code DetachedCriteria.build{}} closures
 * whose delegate is an {@code AbstractDetachedCriteria} instance.
 *
 * <p>The extension adds a {@link NumberLikeCriterion} to the detached criteria list.
 * At query execution time the registered
 * {@link org.grails.orm.hibernate.query.PredicateGenerator.CriterionHandler}
 * (see {@code BootStrap.groovy}) converts the criterion to a JPA {@code Predicate}
 * that casts the numeric column to a string and applies a LIKE pattern.
 *
 * <p>Registered via {@code META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule}.
 */
@CompileStatic
class HibernateCriteriaBuilderExtensions {

    /**
     * Adds a {@link NumberLikeCriterion} to the detached criteria.
     *
     * <p>At query execution time the criterion is converted to
     * {@code cast(col as varchar) like ?} via the registered {@code CriterionHandler}.
     *
     * <p>The {@code propertyValue} may contain SQL {@code %} wildcards. Commas are stripped
     * so formatted numbers (e.g. {@code "1,000"}) work correctly.
     *
     * @param self          the detached criteria
     * @param propertyName  the domain property to match against
     * @param propertyValue the String pattern, with optional {@code %} wildcards
     * @return the criteria for chaining
     */
    static AbstractDetachedCriteria numberLike(
            AbstractDetachedCriteria self, String propertyName, Object propertyValue) {
        self.add(new NumberLikeCriterion(propertyName, propertyValue as String))
        self
    }
}
