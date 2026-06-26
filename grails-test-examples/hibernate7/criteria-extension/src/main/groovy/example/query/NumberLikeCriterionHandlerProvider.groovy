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

import org.grails.datastore.mapping.query.Query
import org.grails.orm.hibernate.query.PredicateGenerator
import org.hibernate.query.criteria.JpaExpression

/**
 * ServiceLoader provider that registers the {@link NumberLikeCriterion} handler with
 * {@link PredicateGenerator} at first query execution — no BootStrap registration needed.
 *
 * <p>Discovered via
 * {@code META-INF/services/org.grails.orm.hibernate.query.PredicateGenerator$CriterionHandlerProvider}.
 */
class NumberLikeCriterionHandlerProvider implements PredicateGenerator.CriterionHandlerProvider {

    @Override
    Class<NumberLikeCriterion> criterionType() {
        NumberLikeCriterion
    }

    @Override
    PredicateGenerator.CriterionHandler criterionHandler() {
        { query, root, cb, criterion ->
            NumberLikeCriterion nlc = criterion as NumberLikeCriterion
            def cast = cb.cast(root.get(nlc.property) as JpaExpression, String)
            cb.like(cast, nlc.value as String)
        } as PredicateGenerator.CriterionHandler
    }
}
