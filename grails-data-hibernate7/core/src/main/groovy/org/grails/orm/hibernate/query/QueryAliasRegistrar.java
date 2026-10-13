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

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;

import grails.gorm.DetachedCriteria;
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria;
import org.grails.datastore.mapping.query.Query;

/**
 * Works out which aliases a criteria query declares and registers them, with their join types, in the
 * {@link JpaQueryContext} the query is built against.
 *
 * <p>An alias comes from three places: one created explicitly with {@code createAlias}, an association
 * criterion that names an alias, and a dotted property name whose first segment is an implicit alias. A join
 * type configured on the {@link DetachedCriteria} is applied to the alias of the same path.</p>
 *
 * @since 8.0
 */
final class QueryAliasRegistrar {

    private final DetachedCriteria<?> detachedCriteria;
    private final HibernateQuery hibernateQuery;

    /**
     * @param detachedCriteria the criteria whose aliases and join types are registered
     * @param hibernateQuery the query that holds aliases created through {@code createAlias}, or {@code null}
     */
    QueryAliasRegistrar(DetachedCriteria<?> detachedCriteria, HibernateQuery hibernateQuery) {
        this.detachedCriteria = detachedCriteria;
        this.hibernateQuery = hibernateQuery;
    }

    /**
     * Creates the context a query or subquery is built against and registers every alias the criteria declare.
     *
     * @param parent the context of the enclosing query, or {@code null} for a top level query
     * @param root the root of the query
     * @return the context, with its aliases registered
     */
    JpaQueryContext createContext(JpaQueryContext parent, Root<?> root) {
        JpaQueryContext context = JpaQueryContext.forSubquery(parent, explicitAliases(), root);
        registerDetachedJoins(context);
        discover(detachedCriteria.getCriteria(), context);
        return context;
    }

    /**
     * @return the aliases created explicitly: those held by the query, then those among the criteria
     */
    List<HibernateAlias> explicitAliases() {
        List<HibernateAlias> aliases = new ArrayList<>();
        if (hibernateQuery != null) {
            aliases.addAll(hibernateQuery.getAliases());
        }
        for (Query.Criterion criterion : detachedCriteria.getCriteria()) {
            if (criterion instanceof HibernateAlias alias) {
                aliases.add(alias);
            }
        }
        return aliases;
    }

    /**
     * Registers an alias for each path that has a join type configured on the criteria, keeping that join type.
     *
     * @param context the context to register the aliases in
     */
    void registerDetachedJoins(JpaQueryContext context) {
        detachedCriteria.getJoinTypes().forEach((path, joinType) ->
                context.registerAlias(path, new HibernateAlias(path, path, joinType)));
    }

    /**
     * Registers the aliases declared by the given criteria, descending into associations and junctions.
     * An alias the context already knows is left alone.
     *
     * @param criteria the criteria to inspect, may be {@code null}
     * @param context the context to register the aliases in
     */
    void discover(List<Query.Criterion> criteria, JpaQueryContext context) {
        if (criteria == null) {
            return;
        }
        for (Query.Criterion criterion : criteria) {
            if (criterion instanceof HibernateAlias ha) {
                if (!context.hasAlias(ha.alias())) {
                    context.registerAlias(ha.alias(), ha);
                }
            }
            else if (criterion instanceof DetachedAssociationCriteria<?> dac) {
                if (dac.getAlias() != null) {
                    String associationPath = dac.getAssociationPath();
                    JoinType joinType = detachedCriteria.getJoinTypes().getOrDefault(associationPath, JoinType.INNER);
                    context.registerAlias(dac.getAlias(), new HibernateAlias(associationPath, dac.getAlias(), joinType));
                }
                discover(dac.getCriteria(), context);
            }
            else if (criterion instanceof Query.PropertyNameCriterion pnc) {
                String propertyName = pnc.getProperty();
                if (propertyName.contains(".")) {
                    String alias = propertyName.substring(0, propertyName.indexOf('.'));
                    if (!context.hasAlias(alias)) {
                        context.registerAlias(alias, new HibernateAlias(alias, alias));
                    }
                }
            }
            else if (criterion instanceof Query.Junction junction) {
                discover(junction.getCriteria(), context);
            }
        }
    }
}
