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

import groovy.transform.CompileStatic

import org.grails.datastore.gorm.query.criteria.AbstractDetachedCriteria
import org.grails.datastore.mapping.query.api.Criteria

/**
 * Groovy extension methods that add {@code sqlRestriction} to detached criteria, registered in
 * {@code META-INF/services/org.codehaus.groovy.runtime.ExtensionModule}. The {@code and}, {@code or} and {@code not}
 * blocks of a criteria query, and the association blocks inside them, resolve their calls against a detached
 * criteria, so a {@code sqlRestriction} in such a block restricts the criteria it is called in. The same methods
 * restrict a {@link grails.gorm.DetachedCriteria} of a Hibernate entity.
 *
 * @since 8.0.0
 */
@CompileStatic
final class HibernateDetachedCriteriaExtension {

    private HibernateDetachedCriteriaExtension() {
    }

    /**
     * Restricts the results with a native SQL condition. {@code {alias}} in the SQL stands for the table alias of
     * the criteria's entity.
     *
     * @param self the detached criteria
     * @param sql the SQL condition
     * @return the detached criteria
     */
    static Criteria sqlRestriction(AbstractDetachedCriteria<?> self, String sql) {
        return sqlRestriction(self, sql, List.of())
    }

    /**
     * Restricts the results with a native SQL condition whose {@code ?} placeholders are bound to the given values.
     * {@code {alias}} in the SQL stands for the table alias of the criteria's entity.
     *
     * @param self the detached criteria
     * @param sql the SQL condition
     * @param values the values of the {@code ?} placeholders, in order, none of them {@code null}
     * @return the detached criteria
     * @throws IllegalArgumentException if the number of {@code ?} placeholders differs from the number of values,
     *     or a value is {@code null}
     */
    static Criteria sqlRestriction(AbstractDetachedCriteria<?> self, String sql, List<?> values) {
        self.add(new SqlRestriction(sql, values))
        return self
    }

}
