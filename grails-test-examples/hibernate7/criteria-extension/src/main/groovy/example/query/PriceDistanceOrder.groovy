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
 * An order by how far a product's price is from a target price, nearest first. It is a
 * {@link Query.Order} subclass, so it is given to a criteria query with {@code order(...)}, and the
 * {@link org.grails.orm.hibernate.query.PredicateGenerator.OrderHandler} that
 * {@link PriceDistanceOrderHandlerProvider} registers turns it into a JPA order by an expression,
 * which an order by a property name cannot express.
 */
@CompileStatic
class PriceDistanceOrder extends Query.Order {

    final BigDecimal target

    PriceDistanceOrder(BigDecimal target) {
        super('price')
        this.target = target
    }
}
