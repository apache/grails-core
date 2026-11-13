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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.criteria.JoinType;

import grails.gorm.DetachedCriteria;
import grails.orm.HibernateCriteriaBuilder;
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria;
import org.grails.datastore.mapping.model.types.Embedded;
import org.grails.datastore.mapping.query.Query;

/**
 * Builds the {@link JoinPlan} of a query: it reads every alias, association block, dotted property and configured
 * join type of the criteria, including those inside association blocks, and names each association from the root
 * of the query.
 *
 * <p>Two passes read the criteria. The first only learns which path each alias names, so that the second can tell
 * an association criterion that carries an alias, which {@code createAlias} adds, from a block the user wrote.</p>
 *
 * @since 8.0.1
 */
final class JoinPlanner {

    private final DetachedCriteria<?> criteria;
    private final HibernateQuery hibernateQuery;
    private final Map<String, String> aliasPaths = new HashMap<>();
    private final Map<String, String> pathOwners = new HashMap<>();
    private final Map<String, JoinType> lastAliasTypes = new HashMap<>();
    private final Set<String> createdAliases = new HashSet<>();
    private JoinPlan target;
    private int disjunctions;

    private JoinPlanner(DetachedCriteria<?> criteria, HibernateQuery hibernateQuery) {
        this.criteria = criteria;
        this.hibernateQuery = hibernateQuery;
    }

    /**
     * @param criteria the criteria of the query or subquery
     * @param hibernateQuery the query that holds aliases created through {@code createAlias}, or {@code null}
     * @return the join type of every association path of the query
     */
    static JoinPlan plan(DetachedCriteria<?> criteria, HibernateQuery hibernateQuery) {
        return new JoinPlanner(criteria, hibernateQuery).plan();
    }

    private JoinPlan plan() {
        JoinPlan plan = new JoinPlan();
        configure(plan, "", criteria);
        for (JoinPlan pass : new JoinPlan[] {new JoinPlan(), plan}) {
            target = pass;
            if (hibernateQuery != null) {
                hibernateQuery.getAliases().forEach(alias -> declareAlias(alias, ""));
            }
            scan(criteria.getCriteria(), "", criteria.getJoinTypes());
        }
        // createAlias configures the join type of its path as well, so a join type that is the type of the last
        // alias of the path is that alias again, and any other one was configured after the aliases
        plan.configuredTypes().forEach((path, joinType) -> {
            if (lastAliasTypes.get(path) != joinType) {
                plan.override(path, joinType);
            }
        });
        return plan;
    }

    private static void configure(JoinPlan plan, String scope, DetachedCriteria<?> owner) {
        owner.getJoinTypes().forEach((path, joinType) -> {
            if (!path.contains(HibernateCriteriaBuilder.ALIAS_SEPARATOR)) {
                plan.configure(join(scope, path), joinType);
            }
        });
    }

    private void scan(List<Query.Criterion> criteria, String scope, Map<String, JoinType> ownerJoinTypes) {
        CriteriaVisitor.walk(criteria, new CriteriaVisitor() {
            @Override
            public void alias(HibernateAlias alias) {
                if (alias.explicit()) {
                    declareAlias(alias, scope);
                }
            }

            @Override
            public void block(DetachedAssociationCriteria<?> block) {
                scanBlock(block, scope, ownerJoinTypes);
            }

            @Override
            public void associationQuery(HibernateAssociationQuery query) {
                String path = join(scope, query.associationPath);
                target.declare(path, joinType(path, query.associationPath, ownerJoinTypes,
                        !query.getAssociationCriteria().isEmpty()));
                scan(query.getAssociationCriteria(), path, Map.of());
            }

            @Override
            public void enter(Query.Junction junction) {
                if (alternatives(junction)) {
                    disjunctions++;
                }
            }

            @Override
            public void leave(Query.Junction junction) {
                if (alternatives(junction)) {
                    disjunctions--;
                }
            }

            @Override
            public void property(String property) {
                mention(property, scope);
            }
        });
    }

    private void declareAlias(HibernateAlias alias, String scope) {
        String path = absolutePath(alias.path(), scope);
        if (pathOwners.computeIfAbsent(path, key -> alias.alias()).equals(alias.alias())) {
            target.declare(path, alias.joinType());
        }
        lastAliasTypes.put(path, alias.joinType());
        createdAliases.add(alias.alias());
        aliasPaths.put(alias.alias(), path);
    }

    private void scanBlock(DetachedAssociationCriteria<?> block, String scope, Map<String, JoinType> ownerJoinTypes) {
        if (block.getAssociation() instanceof Embedded) {
            return;
        }
        String path = join(scope, block.getAssociationPath());
        configure(target, path, block);
        boolean declaredByCreateAlias = block.getAlias() != null && createdAliases.contains(block.getAlias()) &&
                path.equals(aliasPaths.get(block.getAlias()));
        if (!declaredByCreateAlias) {
            target.declare(path, joinType(path, block.getAssociationPath(), ownerJoinTypes, !block.getCriteria().isEmpty()));
        }
        if (block.getAlias() != null) {
            aliasPaths.put(block.getAlias(), path);
        }
        scan(block.getCriteria(), path, block.getJoinTypes());
    }

    /**
     * The join type a block asks for: the one configured for its path, then the one its container configured for
     * it, and otherwise an inner join, or a left join when the block is a branch of a disjunction: the entities
     * without the association are then still found by the other branches. A block without criteria asks only for
     * the join, so it keeps the inner join.
     */
    private JoinType joinType(String path, String associationPath, Map<String, JoinType> ownerJoinTypes,
                              boolean restricts) {
        JoinType configured = target.configured(path);
        if (configured == null) {
            configured = ownerJoinTypes.get(associationPath);
        }
        if (configured != null) {
            return configured;
        }
        if (restricts && disjunctions > 0) {
            target.alternative(path);
            return JoinType.LEFT;
        }
        return JoinType.INNER;
    }

    /**
     * Whether the junction is an {@code or} with more than one branch. A disjunction of one branch is that branch.
     */
    private static boolean alternatives(Query.Junction junction) {
        return junction instanceof Query.Disjunction && junction.getCriteria().size() > 1;
    }

    private void mention(String property, String scope) {
        int dot = property.lastIndexOf('.');
        if (dot < 0) {
            return;
        }
        String holder = property.substring(0, dot);
        if (!aliasPaths.containsKey(holder)) {
            target.mention(absolutePath(holder, scope));
        }
    }

    /**
     * The path from the root of the query of a path that is relative to a scope, or that starts at an alias.
     */
    private String absolutePath(String path, String scope) {
        int separator = path.indexOf(HibernateCriteriaBuilder.ALIAS_SEPARATOR);
        if (separator >= 0) {
            String base = aliasPaths.get(path.substring(0, separator));
            return base != null ?
                    join(base, path.substring(separator + HibernateCriteriaBuilder.ALIAS_SEPARATOR.length())) :
                    join(scope, path);
        }
        int dot = path.indexOf('.');
        String base = aliasPaths.get(dot < 0 ? path : path.substring(0, dot));
        if (base != null) {
            return dot < 0 ? base : join(base, path.substring(dot + 1));
        }
        return join(scope, path);
    }

    private static String join(String scope, String path) {
        return scope.isEmpty() ? path : scope + "." + path;
    }
}
