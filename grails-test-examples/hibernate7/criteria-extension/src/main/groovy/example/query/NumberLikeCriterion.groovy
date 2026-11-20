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

package example.query

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.query.Query

/**
 * GORM-level criterion for a number-to-string LIKE restriction.
 *
 * <p>Adding this to a {@link grails.gorm.DetachedCriteria} via
 * {@code AbstractDetachedCriteria.add()} causes the query executor to call the
 * registered {@link org.grails.orm.hibernate.query.PredicateGenerator.CriterionHandler}
 * (see {@code BootStrap.groovy}), which converts this criterion to a JPA
 * {@code Predicate} that casts a numeric column to a string and applies a LIKE pattern.
 *
 * <p>Commas are stripped from the pattern so formatted numbers such as {@code "1,000"}
 * work correctly.
 */
@CompileStatic
class NumberLikeCriterion extends Query.PropertyCriterion {

    NumberLikeCriterion(String propertyName, String value) {
        super(propertyName, value.replaceAll(',', ''))
    }

    @Override
    String toString() {
        "${property} like ${value}"
    }
}
