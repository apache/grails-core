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
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.datastore.mapping.model.types.Embedded;
import org.grails.datastore.mapping.query.Query;

/**
 * Works out which aliases a criteria query declares and registers them, with their join types, in the
 * {@link JpaQueryContext} the query is built against.
 *
 * <p>An alias comes from three places: one created explicitly with {@code createAlias}, an association
 * criterion that names an alias, and a dotted property name whose first segment is an implicit alias. A join
 * type configured on the {@link DetachedCriteria} is applied to the alias of the same path. An association
 * block without an alias is registered as an implicit alias of its path, so that its join keeps its join type
 * when an alias on a nested path goes through the same association.</p>
 *
 * @since 8.0.1
 */
final class QueryAliasRegistrar {

    private final DetachedCriteria<?> detachedCriteria;
    private final PersistentEntity persistentEntity;

    /**
     * @param detachedCriteria the criteria whose aliases and join types are registered
     */
    QueryAliasRegistrar(DetachedCriteria<?> detachedCriteria) {
        this.detachedCriteria = detachedCriteria;
        this.persistentEntity = detachedCriteria instanceof DetachedAssociationCriteria<?> associationCriteria ?
                associationCriteria.getAssociation().getAssociatedEntity() :
                detachedCriteria.getPersistentEntity();
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
        return createContext(parent, root, null);
    }

    /**
     * Creates the context of an association block, which knows its path in the enclosing query before its aliases
     * are materialized, so that an alias of the block for an association the enclosing query already joins uses
     * that join.
     *
     * @param parent the context of the enclosing query
     * @param root the join of the block's association
     * @param blockPath the association path of the block, relative to the enclosing context, or {@code null}
     * @return the context, with its aliases registered and explicit entity association joins materialized
     */
    JpaQueryContext createContext(JpaQueryContext parent, From<?, ?> root, String blockPath) {
        List<HibernateAlias> aliases = explicitAliases();
        JpaQueryContext context = JpaQueryContext.forSubquery(parent, aliases, root);
        if (blockPath != null) {
            context.setBlockPath(blockPath);
        }
        registerDetachedJoins(context);
        discover(detachedCriteria.getCriteria(), context);
        for (HibernateAlias alias : aliases) {
            if (!(persistentEntity.getPropertyByName(alias.path()) instanceof Basic)) {
                context.getFullyQualifiedExpression(alias.alias());
            }
        }
        return context;
    }

    /**
     * @return the aliases created explicitly, which are among the criteria
     */
    List<HibernateAlias> explicitAliases() {
        List<HibernateAlias> aliases = new ArrayList<>();
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
        detachedCriteria.getJoinTypes().forEach((path, joinType) -> registerImplicit(context, path, joinType));
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
                String associationPath = dac.getAssociationPath();
                if (dac.getAlias() != null) {
                    // An alias the query already declares keeps its own join type: a path has one join type
                    // in the criteria, so a second alias of the same path would otherwise take the last one.
                    if (!context.getAliasRegistry().isOwnDefined(dac.getAlias())) {
                        JoinType joinType = detachedCriteria.getJoinTypes().getOrDefault(associationPath, JoinType.INNER);
                        context.registerAlias(dac.getAlias(), new HibernateAlias(associationPath, dac.getAlias(), joinType));
                    }
                }
                else if (!(dac.getAssociation() instanceof Embedded)) {
                    // The block joins its association with an inner join, whichever part of the query
                    // joins the association first. A configured join type is already registered.
                    registerImplicit(context, associationPath, JoinType.INNER);
                }
                if (!(dac.getAssociation() instanceof Embedded)) {
                    recordNestedBlockJoins(dac, associationPath, context);
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

    /**
     * Registers an implicit alias, named after the path it joins, unless this query declares an alias of that name
     * or an enclosing query has an explicit one, in which case only the join type is kept for the block's association.
     * An implicit alias never hides an explicit alias that is visible
     * from the scope it is registered in: a reference to the name must still reach the alias the user declared.
     *
     * @param context the context to register the alias in
     * @param path the association path the alias joins, which is also its name
     * @param joinType the join type of the alias
     */
    private void registerImplicit(JpaQueryContext context, String path, JoinType joinType) {
        AliasRegistry registry = context.getAliasRegistry();
        HibernateAlias visible = registry.getDefinition(path);
        if (registry.isOwnDefined(path)) {
            if (visible != null && visible.explicit()) {
                // an alias declared here with the name of the block's association is another join
                registry.defineAssociationJoinType(path, joinType);
            }
            return;
        }
        if (visible != null && visible.explicit()) {
            registry.defineAssociationJoinType(path, joinType);
            return;
        }
        context.registerAlias(path, new HibernateAlias(path, path, joinType, false));
    }

    /**
     * Records the join type of each association block nested in a block, under its path from this query's root,
     * so that a nested alias path such as {@code team.likes.tag}, which joins {@code team.likes} before this
     * query reaches the block, gives that join the type the block asks for and not the type of the alias.
     */
    private void recordNestedBlockJoins(DetachedAssociationCriteria<?> block, String prefix, JpaQueryContext context) {
        for (Query.Criterion criterion : block.getCriteria()) {
            if (criterion instanceof DetachedAssociationCriteria<?> nested && !(nested.getAssociation() instanceof Embedded)) {
                String path = prefix + "." + nested.getAssociationPath();
                context.getAliasRegistry().defineAssociationJoinType(path,
                        block.getJoinTypes().getOrDefault(nested.getAssociationPath(), JoinType.INNER));
                recordNestedBlockJoins(nested, path, context);
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
                    (persistentEntity.getPropertyByName(alias) instanceof Association ||
                            context.getFrom(alias) != null)) {
                context.registerAlias(alias, new HibernateAlias(alias, alias, JoinType.INNER, false));
            }
        }
    }
}
