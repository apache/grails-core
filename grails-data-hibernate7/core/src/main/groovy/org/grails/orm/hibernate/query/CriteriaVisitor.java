/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.orm.hibernate.query;

import java.util.List;

import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria;
import org.grails.datastore.mapping.query.Query;

/**
 * Receives what a list of criteria declares or refers to, in the order the criteria were added. {@link #walk}
 * descends into junctions and leaves the criteria inside an association block to whoever visits the block, which
 * knows the path the block starts at. Every pass over a criteria tree goes through here, so that the passes agree
 * on what is in the tree.
 *
 * @since 8.0.1
 */
interface CriteriaVisitor {

    /**
     * An alias created with {@code createAlias}, or the implicit one of a path.
     */
    default void alias(HibernateAlias alias) {
    }

    /**
     * An association block, or an association criterion that carries an alias.
     */
    default void block(DetachedAssociationCriteria<?> block) {
    }

    /**
     * A property name a criterion restricts or compares, which may be a dotted path.
     */
    default void property(String property) {
    }

    /**
     * An association scope that {@link HibernateQuery#createQuery(String)} returned, which holds the criteria added
     * inside it.
     */
    default void associationQuery(HibernateAssociationQuery query) {
    }

    /**
     * A junction is entered, and its criteria are visited next.
     */
    default void enter(Query.Junction junction) {
    }

    /**
     * The criteria of the junction entered last have been visited.
     */
    default void leave(Query.Junction junction) {
    }

    /**
     * Visits the criteria in order. The criteria that are not visited have nothing to say about the association
     * paths of this query: a subquery ({@link Query.Exists}, {@link Query.NotExists}) has paths of its own, which
     * its own query plans, and an {@link SqlRestriction} is SQL text. Any other criterion, such as one an SPI
     * handler translates, is not visited unless it names its property as a {@link Query.PropertyNameCriterion}.
     */
    static void walk(List<Query.Criterion> criteria, CriteriaVisitor visitor) {
        if (criteria == null) {
            return;
        }
        for (Query.Criterion criterion : criteria) {
            if (criterion instanceof HibernateAlias alias) {
                visitor.alias(alias);
            }
            else if (criterion instanceof DetachedAssociationCriteria<?> block) {
                visitor.block(block);
            }
            else if (criterion instanceof HibernateAssociationQuery query) {
                visitor.associationQuery(query);
            }
            else if (criterion instanceof Query.PropertyNameCriterion named) {
                visitor.property(named.getProperty());
                if (named instanceof Query.PropertyComparisonCriterion comparison) {
                    visitor.property(comparison.getOtherProperty());
                }
            }
            else if (criterion instanceof Query.Junction junction) {
                visitor.enter(junction);
                try {
                    walk(junction.getCriteria(), visitor);
                }
                finally {
                    visitor.leave(junction);
                }
            }
        }
    }
}
