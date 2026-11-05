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

import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria;
import org.grails.datastore.mapping.query.Query;

/**
 * An order by an alias that an association block of a criteria query declares.
 * <p>
 * Only the orders of the root criteria reach the query, so the order is listed with them, but an alias exists
 * only in the block that declares it. The order therefore keeps its block and the property as the block names
 * it, and is resolved against that block, which gives the join of the alias that the order names. A collection
 * can have several aliases in one block, each with a join of its own, so the path of the alias does not tell
 * them apart.
 * <p>
 * The {@link #getProperty() property} of the order is the path that the alias stands for, from the root of the
 * query.
 *
 * @since 8.0.1
 */
public class AssociationAliasOrder extends Query.Order {

    private static final long serialVersionUID = 1L;

    // criteria are not serializable; without its block the order sorts by its path
    private final transient DetachedAssociationCriteria<?> association;
    private final String aliasedProperty;

    /**
     * @param property the path that the alias stands for, from the root of the query
     * @param direction the direction of the order
     * @param association the criteria of the association block that declares the alias
     * @param aliasedProperty the property as the block names it, which starts with the alias
     */
    public AssociationAliasOrder(
            String property, Direction direction, DetachedAssociationCriteria<?> association, String aliasedProperty) {
        super(property, direction);
        this.association = association;
        this.aliasedProperty = aliasedProperty;
    }

    /**
     * @return the criteria of the association block that declares the alias
     */
    public DetachedAssociationCriteria<?> getAssociation() {
        return association;
    }

    /**
     * @return the property as the block names it, which starts with the alias
     */
    public String getAliasedProperty() {
        return aliasedProperty;
    }

    /**
     * Returns this order as an order of the criteria that enclose the given association, which leaves the block
     * that it is resolved against as it is.
     *
     * @param associationName the name of the association whose block encloses the block of this order
     * @return the order, with its path qualified with the association
     */
    public AssociationAliasOrder qualifiedWith(String associationName) {
        AssociationAliasOrder qualified = new AssociationAliasOrder(
                associationName + '.' + getProperty(), getDirection(), association, aliasedProperty);
        if (isIgnoreCase()) {
            qualified.ignoreCase();
        }
        return qualified;
    }
}
