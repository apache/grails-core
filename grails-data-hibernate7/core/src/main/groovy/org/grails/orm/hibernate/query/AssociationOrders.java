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
 * the blocks, since only the orders of the root criteria reach the query. Each becomes an
 * {@link AssociationOrder}, which keeps its block and is resolved against it.
 * <ul>
 * <li>An order by a property of the association has the path of the property through the association.</li>
 * <li>An order by an alias that the block declares, {@code alias.property}, has the path that the alias stands
 * for.</li>
 * <li>An order by any other dotted name may be an order by an alias of the criteria around the block, which the
 * block sees as well. Its path is the name, qualified with the association.</li>
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
    private static AssociationOrder ofEnclosingCriteria(Query.Order order, DetachedAssociationCriteria<?> block) {
        String associationName = block.getAssociationPath();
        if (order instanceof AssociationOrder inner) {
            // an order of a block inside this one, which is still resolved against that block
            return inner.qualifiedWith(associationName);
        }
        String property = order.getProperty();
        int dot = property.indexOf('.');
        String head = dot < 0 ? property : property.substring(0, dot);
        String aliasPath = dot > 0 ? pathOfAlias(block, head) : null;
        String path;
        if (aliasPath != null) {
            path = aliasPath + property.substring(dot);
        } else if (dot > 0 || block.getAssociation().getAssociatedEntity().getPropertyByName(head) != null) {
            // the property is looked up on the entity of the association, which is also there for an embedded
            // component: the class of a component is not a domain class, so the criteria of its block have no
            // entity. A dotted name that starts with no property can be an alias of the criteria around the
            // block; the block resolves it, and an alias that nothing declares leaves the order without an effect
            path = property;
        } else {
            return null;
        }
        AssociationOrder enclosing =
                new AssociationOrder(associationName + '.' + path, order.getDirection(), block, property);
        if (order.isIgnoreCase()) {
            enclosing.ignoreCase();
        }
        return enclosing;
    }

    /**
     * Returns the path of the association that a block declares the alias for, or {@code null}. The criteria
     * builder declares an alias with a {@link HibernateAlias}. Criteria that are built in any other way declare
     * it with association criteria that have the alias.
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
