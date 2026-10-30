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

import jakarta.persistence.criteria.Expression
import org.grails.datastore.mapping.query.Query
import org.grails.orm.hibernate.query.PredicateGenerator

/**
 * ServiceLoader provider that registers the handler of {@link PriceDistanceOrder} with
 * {@link PredicateGenerator} at first query execution. The handler orders by the absolute difference
 * between the price and the target, resolving the {@code price} path through the query context the
 * way the built-in orders do.
 *
 * <p>Discovered via
 * {@code META-INF/services/org.grails.orm.hibernate.query.PredicateGenerator$OrderHandlerProvider}.
 */
class PriceDistanceOrderHandlerProvider implements PredicateGenerator.OrderHandlerProvider {

    @Override
    Class<? extends Query.Order> orderType() {
        PriceDistanceOrder
    }

    @Override
    PredicateGenerator.OrderHandler orderHandler() {
        { query, root, cb, order, context ->
            PriceDistanceOrder byDistance = order as PriceDistanceOrder
            Expression<BigDecimal> price = context.getFullyQualifiedPath(byDistance.property) as Expression<BigDecimal>
            cb.asc(cb.abs(cb.diff(price, byDistance.target)))
        } as PredicateGenerator.OrderHandler
    }
}
