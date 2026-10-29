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
import java.util.List;

import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;

import grails.gorm.DetachedCriteria;
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.datastore.mapping.query.Query;

/**
 * Works out which aliases a criteria query declares and registers them, with their join types, in the
 * {@link JpaQueryContext} the query is built against.
 *
 * <p>An alias comes from three places: one created explicitly with {@code createAlias}, an association
 * criterion that names an alias, and a dotted property name whose first segment is an implicit alias. A join
 * type configured on the {@link DetachedCriteria} is applied to the alias of the same path.</p>
 *
 * @since 8.0.1
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
     * Creates the context a query or subquery is built against, registers every alias the criteria declare,
     * and materializes explicitly requested entity association joins even when no expression references their aliases.
     *
     * @param parent the context of the enclosing query, or {@code null} for a top level query
     * @param root the root of the query
     * @return the context, with its aliases registered and explicit entity association joins materialized
     */
    JpaQueryContext createContext(JpaQueryContext parent, From<?, ?> root) {
        List<HibernateAlias> aliases = explicitAliases();
        JpaQueryContext context = JpaQueryContext.forSubquery(parent, aliases, root);
        registerDetachedJoins(context);
        discover(detachedCriteria.getCriteria(), context);
        for (HibernateAlias alias : aliases) {
            if (!(detachedCriteria.getPersistentEntity().getPropertyByName(alias.path()) instanceof Basic)) {
                context.getFullyQualifiedExpression(alias.alias());
            }
        }
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
        detachedCriteria.getJoinTypes().forEach((path, joinType) -> {
            if (!context.getAliasRegistry().isOwnDefined(path)) {
                context.registerAlias(path, new HibernateAlias(path, path, joinType, false));
            }
        });
    }

    /**
     * Registers the aliases declared by the given criteria, descending into junctions but leaving
     * association-local aliases for the association's own context.
     * Local aliases are left alone. An inherited implicit path is declared against this query's root,
     * while an explicitly named outer alias remains available for correlation.
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
            }
            else if (criterion instanceof Query.PropertyNameCriterion pnc) {
                discoverProperty(pnc.getProperty(), context);
                if (pnc instanceof Query.PropertyComparisonCriterion comparison) {
                    discoverProperty(comparison.getOtherProperty(), context);
                }
            }
            else if (criterion instanceof Query.Junction junction) {
                discover(junction.getCriteria(), context);
            }
        }
    }

    private void discoverProperty(String propertyName, JpaQueryContext context) {
        if (propertyName.contains(".")) {
            String alias = propertyName.substring(0, propertyName.indexOf('.'));
            AliasRegistry registry = context.getAliasRegistry();
            HibernateAlias inherited = registry.getDefinition(alias);
            boolean inheritedPath = !registry.isOwnDefined(alias) && inherited != null && !inherited.explicit();
            if ((!context.hasAlias(alias) || inheritedPath) &&
                    (detachedCriteria.getPersistentEntity().getPropertyByName(alias) instanceof Association ||
                            context.getFrom(alias) != null)) {
                context.registerAlias(alias, new HibernateAlias(alias, alias, JoinType.INNER, false));
            }
        }
    }
}
