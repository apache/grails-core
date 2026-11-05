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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria;
import org.grails.datastore.mapping.query.Query;

/**
 * Turns the orders that criteria declare inside association blocks into orders of the criteria that enclose
 * the blocks, since only the orders of the root criteria reach the query.
 * <ul>
 * <li>An order by a property of the association becomes an {@link AssociationOrder} by the path of the property
 * through the association.</li>
 * <li>An order by an alias, {@code alias.property}, becomes an {@link AssociationAliasOrder}, which keeps its
 * block and is resolved against it. The alias is one that the block declares or one of the criteria around the
 * block, which the block sees as well.</li>
 * <li>An order by a single name that is no property of the association is not an order of the enclosing
 * criteria. It stays in its block and has no effect.</li>
 * </ul>
 * The criteria builder moves the orders out of a block when the block ends, so that they keep their place among
 * the orders of the query. Criteria that are built in any other way keep the orders in their blocks, and the
 * query reads them from there, after the orders of the criteria themselves.
 *
 * @since 8.0.1
 */
public final class AssociationOrders {

    private AssociationOrders() {
    }

    /**
     * Moves the orders of an association block to the criteria that enclose it.
     *
     * @param block the criteria of the association block
     * @param enclosingCriteria takes each order of the enclosing criteria
     */
    public static void moveOutOf(DetachedAssociationCriteria<?> block, Consumer<Query.Order> enclosingCriteria) {
        for (Iterator<Query.Order> orders = block.getOrders().iterator(); orders.hasNext();) {
            Query.Order order = ofEnclosingCriteria(orders.next(), block);
            if (order != null) {
                enclosingCriteria.accept(order);
                orders.remove();
            }
        }
    }

    /**
     * Moves the orders of the association blocks among the criteria, at any depth and inside any junction, to
     * the criteria that enclose them.
     *
     * @param criteria the criteria that may hold association blocks
     * @param enclosingCriteria takes each order of the enclosing criteria
     */
    public static void moveOutOfBlocks(List<Query.Criterion> criteria, Consumer<Query.Order> enclosingCriteria) {
        for (Query.Criterion criterion : criteria) {
            if (criterion instanceof Query.Junction junction) {
                moveOutOfBlocks(junction.getCriteria(), enclosingCriteria);
            } else if (criterion instanceof DetachedAssociationCriteria<?> block) {
                // the blocks inside this one first, whose orders become orders of this block
                moveOutOfBlocks(block.getCriteria(), order -> block.order(order));
                moveOutOf(block, enclosingCriteria);
            }
        }
    }

    /**
     * Returns the orders that are in the association blocks among the criteria, at any depth and inside any
     * junction, as orders of the criteria. The blocks keep their orders, so the criteria give the same orders
     * each time a query is built from them.
     *
     * @param criteria the criteria that may hold association blocks
     * @return the orders of the blocks, as orders of the criteria
     */
    public static List<Query.Order> inBlocks(List<Query.Criterion> criteria) {
        List<Query.Order> orders = new ArrayList<>();
        for (Query.Criterion criterion : criteria) {
            if (criterion instanceof Query.Junction junction) {
                orders.addAll(inBlocks(junction.getCriteria()));
            } else if (criterion instanceof DetachedAssociationCriteria<?> block) {
                List<Query.Order> ordersOfBlock = new ArrayList<>(block.getOrders());
                ordersOfBlock.addAll(inBlocks(block.getCriteria()));
                for (Query.Order order : ordersOfBlock) {
                    Query.Order enclosing = ofEnclosingCriteria(order, block);
                    if (enclosing != null) {
                        orders.add(enclosing);
                    }
                }
            }
        }
        return orders;
    }

    /**
     * Returns an order of an association block as an order of the criteria that enclose the block, or
     * {@code null} if it is a single name that is no property of the association.
     */
    private static Query.Order ofEnclosingCriteria(Query.Order order, DetachedAssociationCriteria<?> block) {
        String associationName = block.getAssociationPath();
        if (order instanceof AssociationAliasOrder aliasOrder) {
            // an order by an alias of a block inside this one, which is still resolved against that block
            return aliasOrder.qualifiedWith(associationName);
        }
        String property = order.getProperty();
        int dot = property.indexOf('.');
        String head = dot < 0 ? property : property.substring(0, dot);
        String aliasPath = dot > 0 ? pathOfAlias(block, head) : null;
        Query.Order enclosing;
        if (aliasPath != null) {
            enclosing = new AssociationAliasOrder(
                    associationName + '.' + aliasPath + property.substring(dot), order.getDirection(), block, property);
        } else if (block.getAssociation().getAssociatedEntity().getPropertyByName(head) != null) {
            // the entity of the association, which is also there for an embedded component: the class of a
            // component is not a domain class, so the criteria of its block have no entity to look the property up on
            enclosing = new AssociationOrder(associationName + '.' + property, order.getDirection());
        } else if (dot > 0) {
            // an alias that the block does not declare can be one of the criteria around it; the block
            // resolves it, and an alias that nothing declares leaves the order without an effect
            enclosing = new AssociationAliasOrder(associationName + '.' + property, order.getDirection(), block, property);
        } else {
            return null;
        }
        return order.isIgnoreCase() ? enclosing.ignoreCase() : enclosing;
    }

    /**
     * Returns the path of the association that a block declares the alias for, or {@code null}. The criteria
     * builder declares an alias with a {@link HibernateAlias}. Criteria that are built in any other way, such as
     * the closure of a junction, declare it with association criteria that have the alias.
     */
    private static String pathOfAlias(DetachedAssociationCriteria<?> block, String alias) {
        for (Query.Criterion criterion : block.getCriteria()) {
            if (criterion instanceof HibernateAlias declared) {
                if (alias.equals(declared.alias())) {
                    return declared.path();
                }
            } else if (criterion instanceof DetachedAssociationCriteria<?> aliased && alias.equals(aliased.getAlias())) {
                return aliased.getAssociationPath();
            }
        }
        return null;
    }
}
