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
package org.grails.orm.hibernate.query;

import org.grails.datastore.mapping.query.Query;

/**
 * An order that a criteria query declares inside an association block. Only the orders of the root criteria
 * reach the query, so the order is moved to the criteria that enclose its block, and its
 * {@link #getProperty() property} is the path from the root of the query.
 * <p>
 * An order that stayed in its block had no effect. A query that selects distinct or grouped values, or
 * aggregates, can only be ordered by what it selects, and most databases reject any other order. In such a
 * query an order of a block is therefore still left out, unless the query selects its property.
 *
 * @since 8.0.1
 */
public class AssociationOrder extends Query.Order {

    private static final long serialVersionUID = 1L;

    /**
     * @param property the path of the property from the root of the query
     * @param direction the direction of the order
     */
    public AssociationOrder(String property, Direction direction) {
        super(property, direction);
    }
}
