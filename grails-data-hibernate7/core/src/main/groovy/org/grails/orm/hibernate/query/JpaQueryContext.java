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

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Path;

/**
 * Orchestrator for JPA query translation state (Aliases, Joins, Expressions).
 *
 * {@link ExpressionResolver} receives the shared {@link AliasRegistry} and {@link JoinTracker}
 * so aliases and joins remain scoped to the current root or subquery context.
 *
 * @since 8.0
 */
public class JpaQueryContext implements Cloneable {

    private final AliasRegistry aliasRegistry;
    private final JoinTracker joinTracker;
    private final ExpressionResolver resolver;
    private final Map<ParameterExpression<?>, Object> parameterValues = new LinkedHashMap<>();
    private final Map<String, Expression<?>> selectionAliases = new HashMap<>();
    private final boolean isSubquery;
    private JpaQueryContext parent;

    public JpaQueryContext() {
        this(null, null, null);
    }

    public JpaQueryContext(From<?, ?> root) {
        this(null, null, root);
    }

    public JpaQueryContext(List<HibernateAlias> aliases, From<?, ?> root) {
        this(null, aliases, root);
    }

    public JpaQueryContext(JpaQueryContext parent, From<?, ?> root) {
        this(parent, null, root);
    }

    public static JpaQueryContext forRoot(From<?, ?> root) {
        return new JpaQueryContext(null, null, root);
    }

    public static JpaQueryContext forRoot(List<HibernateAlias> aliases, From<?, ?> root) {
        return new JpaQueryContext(null, aliases, root);
    }

    public static JpaQueryContext forSubquery(JpaQueryContext parent, From<?, ?> root) {
        return forSubquery(parent, null, root);
    }

    public static JpaQueryContext forSubquery(JpaQueryContext parent, List<HibernateAlias> aliases, From<?, ?> root) {
        return new JpaQueryContext(parent, aliases, root, true);
    }

    /**
     * Internal constructor for subqueries and base initialization.
     */
    public JpaQueryContext(JpaQueryContext parent, List<HibernateAlias> aliases, From<?, ?> root) {
        this(parent, aliases, root, false);
    }

    public JpaQueryContext(JpaQueryContext parent, List<HibernateAlias> aliases, From<?, ?> root, boolean isSubquery) {
        this.parent = parent;
        this.isSubquery = isSubquery;
        this.joinTracker = isSubquery ? new JoinTracker(root) : new JoinTracker(parent != null ? parent.getJoinTracker() : null, root);
        this.aliasRegistry = new AliasRegistry(parent != null ? parent.getAliasRegistry() : null, isSubquery);
        this.resolver = new ExpressionResolver(aliasRegistry, joinTracker, this);
        if (aliases != null) {
            for (HibernateAlias alias : aliases) {
                aliasRegistry.define(alias.alias(), alias);
            }
        }
        if (root != null) {
            this.joinTracker.addJoin("root", root);
        }
    }

    public boolean isSubquery() {
        return isSubquery;
    }

    protected JoinTracker getJoinTracker() {
        return joinTracker;
    }

    protected AliasRegistry getAliasRegistry() {
        return aliasRegistry;
    }

    public void setRoot(From<?, ?> root) {
        this.joinTracker.setRoot(root);
        this.joinTracker.addJoin("root", root);
    }

    public JpaQueryContext getParent() {
        return parent;
    }

    public void setParent(JpaQueryContext parent) {
        this.parent = parent;
        if (this.aliasRegistry != null && parent != null) {
            this.aliasRegistry.setParent(parent.getAliasRegistry());
        }
    }

    public From<?, ?> getRoot() {
        return joinTracker.getRoot();
    }

    public void addFrom(String path, From<?, ?> from) {
        joinTracker.addJoin(path, from);
    }

    public From<?, ?> getFrom(String path) {
        return joinTracker.getJoin(path);
    }

    public void registerAlias(String alias, Expression<?> expression) {
        if (!aliasRegistry.hasLocalDefinition(alias)) {
            aliasRegistry.define(alias, new HibernateAlias(alias, alias, JoinType.INNER, true));
        }
        aliasRegistry.realize(alias, expression);
        if (!isSubquery && parent != null && aliasRegistry.isExplicit(alias)) {
            parent.registerAlias(alias, expression);
        }
    }

    public void registerAlias(String alias, HibernateAlias definition) {
        aliasRegistry.define(alias, definition);
    }

    /**
     * Records a selection of the query under its alias, by which an order of the query refers to it. Unlike
     * {@link #registerAlias(String, Expression)}, the alias does not resolve a property path, in this context or a
     * nested one, so a restriction on a property with the same name still restricts the property.
     *
     * @param alias the alias of the selection
     * @param selection the selection
     */
    public void registerSelectionAlias(String alias, Expression<?> selection) {
        selectionAliases.put(alias, selection);
    }

    /**
     * Returns the selection recorded under an alias by {@link #registerSelectionAlias(String, Expression)}.
     *
     * @param alias the alias of the selection
     * @return the selection, or {@code null} if no selection of this query has the alias
     */
    public Expression<?> getSelectionAlias(String alias) {
        return selectionAliases.get(alias);
    }

    public void registerAliasFromPath(String path) {
        if (path != null && path.contains(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)) {
            String alias = path.split(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)[0];
            if (!aliasRegistry.isDefined(alias) && !aliasRegistry.hasRealized(alias)) {
                aliasRegistry.define(alias, null); // Mark as known
            }
        }
    }

    public boolean hasAlias(String alias) {
        return aliasRegistry.isDefined(alias) || aliasRegistry.hasRealized(alias);
    }

    public Expression<?> getAliasedExpression(String alias) {
        return aliasRegistry.getRealized(alias);
    }

    public Expression<?> getFullyQualifiedExpression(String path) {
        if (parent != null) {
            if ("{alias}".equals(path)) {
                return parent.getRoot();
            }
            if (path != null && path.startsWith("{alias}.")) {
                return parent.getFullyQualifiedExpression("root." + path.substring(8));
            }
        }
        return resolver.resolve(path);
    }

    public Path<?> getFullyQualifiedPath(String path) {
        if (parent != null) {
            if ("{alias}".equals(path)) {
                return parent.getRoot();
            }
            if (path != null && path.startsWith("{alias}.")) {
                return parent.getFullyQualifiedPath("root." + path.substring(8));
            }
        }
        Expression<?> resolved = resolver.resolve(path);
        return resolved instanceof Path<?> path1 ? path1 : null;
    }

    /**
     * Records the value of a criteria parameter, to be bound once the query is created. A subquery context
     * records it in the context of the query that contains the subquery.
     *
     * @param parameter the parameter used in the criteria query
     * @param value the value to bind to the parameter
     */
    public void bindParameter(ParameterExpression<?> parameter, Object value) {
        if (parent != null) {
            parent.bindParameter(parameter, value);
        } else {
            parameterValues.put(parameter, value);
        }
    }

    /**
     * Returns the values of the criteria parameters recorded for the query, in the order they were recorded.
     *
     * @return the value of each parameter
     */
    public Map<ParameterExpression<?>, Object> getParameterValues() {
        return parent != null ? parent.getParameterValues() : Collections.unmodifiableMap(parameterValues);
    }

    @Override
    public JpaQueryContext clone() {
        try {
            return (JpaQueryContext) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
