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

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Path;
import java.util.List;

/**
 * Orchestrator for JPA query translation state (Aliases, Joins, Expressions).
 *
 * TODO: Better separation of concerns between JpaQueryContext and ExpressionResolver.
 * It is a bit icky that AliasRegistry and JoinTracker are passed into ExpressionResolver.
 *
 * @author walterduquedeestrada
 * @author graemerocher
 * @since 7.0.0
 */
public class JpaQueryContext implements Cloneable {

    private final AliasRegistry aliasRegistry;
    private final JoinTracker joinTracker;
    private final ExpressionResolver resolver;
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
        return new JpaQueryContext(parent, null, root);
    }

    public static JpaQueryContext forSubquery(JpaQueryContext parent, List<HibernateAlias> aliases, From<?, ?> root) {
        return new JpaQueryContext(parent, aliases, root);
    }

    /**
     * Internal constructor for subqueries and base initialization.
     */
    public JpaQueryContext(JpaQueryContext parent, List<HibernateAlias> aliases, From<?, ?> root) {
        this.parent = parent;
        this.joinTracker = new JoinTracker(parent != null ? parent.getJoinTracker() : null, root);
        this.aliasRegistry = new AliasRegistry(parent != null ? parent.getAliasRegistry() : null);
        this.resolver = new ExpressionResolver(aliasRegistry, joinTracker);
        if (aliases != null) {
            for (HibernateAlias alias : aliases) {
                aliasRegistry.define(alias.alias(), alias);
            }
        }
        if (root != null) {
            this.joinTracker.addJoin("root", root);
        }
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

    public void setParent(JpaQueryContext parent) {
        this.parent = parent;
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
        aliasRegistry.realize(alias, expression);
    }

    public void registerAlias(String alias, HibernateAlias definition) {
        aliasRegistry.define(alias, definition);
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
        return aliasRegistry.isDefined(alias) || aliasRegistry.hasRealized(alias) || (parent != null && parent.hasAlias(alias));
    }

    public Expression<?> getAliasedExpression(String alias) {
        Expression<?> expr = aliasRegistry.getRealized(alias);
        if (expr == null && parent != null) {
            return parent.getAliasedExpression(alias);
        }
        return expr;
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

    @Override
    public JpaQueryContext clone() {
        try {
            return (JpaQueryContext) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
