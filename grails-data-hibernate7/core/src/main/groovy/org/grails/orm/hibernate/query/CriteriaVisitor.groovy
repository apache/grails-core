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
package org.grails.orm.hibernate.query

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria
import org.grails.datastore.mapping.query.Query

/**
 * Receives what a list of criteria declares or refers to, in the order the criteria were added. {@link #walk}
 * descends into junctions and leaves the criteria inside an association block to whoever visits the block, which
 * knows the path the block starts at. Every pass over a criteria tree goes through here, so that the passes agree
 * on what is in the tree.
 *
 * @since 8.0.1
 */
@CompileStatic
@PackageScope
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
     * Visits the criteria in order. The criteria that are not visited have nothing to say about the association
     * paths of this query: a subquery ({@link Query.Exists}, {@link Query.NotExists}) has paths of its own, which
     * its own query plans, and an {@link SqlRestriction} is SQL text. Any other criterion, such as one an SPI
     * handler translates, is not visited unless it names its property as a {@link Query.PropertyNameCriterion}.
     */
    static void walk(List<Query.Criterion> criteria, CriteriaVisitor visitor) {
        if (criteria == null) {
            return
        }
        for (Query.Criterion criterion : criteria) {
            if (criterion instanceof HibernateAlias) {
                visitor.alias((HibernateAlias) criterion)
            }
            else if (criterion instanceof DetachedAssociationCriteria) {
                visitor.block((DetachedAssociationCriteria<?>) criterion)
            }
            else if (criterion instanceof HibernateAssociationQuery) {
                visitor.associationQuery((HibernateAssociationQuery) criterion)
            }
            else if (criterion instanceof Query.PropertyNameCriterion) {
                Query.PropertyNameCriterion named = (Query.PropertyNameCriterion) criterion
                visitor.property(named.getProperty())
                if (named instanceof Query.PropertyComparisonCriterion) {
                    visitor.property(((Query.PropertyComparisonCriterion) named).getOtherProperty())
                }
            }
            else if (criterion instanceof Query.Junction) {
                walk(((Query.Junction) criterion).getCriteria(), visitor)
            }
        }
    }
}
