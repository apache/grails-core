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
 * An order that criteria declare inside an association block. Only the orders of the root criteria reach the
 * query, so the order becomes an order of the criteria that enclose its block, and its
 * {@link #getProperty() property} is the path from the root of the query.
 * <p>
 * The path does not tell which join the order sorts by. Two blocks of the same association each join what is
 * inside it, a collection can have several aliases in one block, each with a join of its own, and an alias
 * exists only in the block that declares it and in the blocks inside that one. The order therefore keeps its
 * block and the property as the block names it, and is resolved against that block: it sorts by the joins that
 * its own block made. For an order by an alias of the criteria around the block, which only the query can
 * resolve, the path is the property as the block names it, qualified with the association.
 * <p>
 * An order that stayed in its block had no effect. A query that selects distinct values of a property, grouped
 * values or aggregates can only be ordered by what it selects, and most databases reject any other order. In
 * such a query an order of a block is therefore still left out, unless the query selects its property. So is an
 * order of a block that names nothing the query can resolve.
 *
 * @since 8.0.1
 */
public class AssociationOrder extends Query.Order {

    private static final long serialVersionUID = 1L;

    // criteria are not serializable; without its block the order sorts by its path
    private final transient DetachedAssociationCriteria<?> association;
    private final String associationProperty;

    /**
     * @param property the path of the property from the root of the query
     * @param direction the direction of the order
     * @param association the criteria of the association block that declares the order
     * @param associationProperty the property as the block names it
     */
    public AssociationOrder(
            String property, Direction direction, DetachedAssociationCriteria<?> association, String associationProperty) {
        super(property, direction);
        this.association = association;
        this.associationProperty = associationProperty;
    }

    /**
     * @return the criteria of the association block that declares the order
     */
    public DetachedAssociationCriteria<?> getAssociation() {
        return association;
    }

    /**
     * @return the property as the block names it
     */
    public String getAssociationProperty() {
        return associationProperty;
    }

    /**
     * Returns this order as an order of the criteria that enclose the given association, which leaves the block
     * that it is resolved against as it is.
     *
     * @param associationName the name of the association whose block encloses the block of this order
     * @return the order, with its path qualified with the association
     */
    public AssociationOrder qualifiedWith(String associationName) {
        AssociationOrder qualified = new AssociationOrder(
                associationName + '.' + getProperty(), getDirection(), association, associationProperty);
        if (isIgnoreCase()) {
            qualified.ignoreCase();
        }
        return qualified;
    }
}
