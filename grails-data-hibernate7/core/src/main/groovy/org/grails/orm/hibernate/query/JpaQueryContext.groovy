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
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.From
import jakarta.persistence.criteria.Path

/**
 * Orchestrator for JPA query translation state (Aliases, Joins, Expressions).
 *
 * {@link ExpressionResolver} receives the shared {@link AliasRegistry} and {@link JoinTracker}
 * so aliases and joins remain scoped to the current root or subquery context.
 *
 * @author walterduquedeestrada
 * @author graemerocher
 * @since 7.0.0
 */
@CompileStatic
class JpaQueryContext implements Cloneable {

    private final AliasRegistry aliasRegistry
    private final JoinTracker joinTracker
    private final ExpressionResolver resolver
    private JpaQueryContext parent

    JpaQueryContext() {
        this((JpaQueryContext) null, (List<HibernateAlias>) null, (From<?, ?>) null)
    }

    JpaQueryContext(From<?, ?> root) {
        this((JpaQueryContext) null, (List<HibernateAlias>) null, root)
    }

    JpaQueryContext(List<HibernateAlias> aliases, From<?, ?> root) {
        this((JpaQueryContext) null, aliases, root)
    }

    JpaQueryContext(JpaQueryContext parent, From<?, ?> root) {
        this(parent, (List<HibernateAlias>) null, root)
    }

    static JpaQueryContext forRoot(From<?, ?> root) {
        return new JpaQueryContext((JpaQueryContext) null, (List<HibernateAlias>) null, root)
    }

    static JpaQueryContext forRoot(List<HibernateAlias> aliases, From<?, ?> root) {
        return new JpaQueryContext((JpaQueryContext) null, aliases, root)
    }

    static JpaQueryContext forSubquery(JpaQueryContext parent, From<?, ?> root) {
        return new JpaQueryContext(parent, (List<HibernateAlias>) null, root)
    }

    static JpaQueryContext forSubquery(JpaQueryContext parent, List<HibernateAlias> aliases, From<?, ?> root) {
        return new JpaQueryContext(parent, aliases, root)
    }

    /**
     * Internal constructor for subqueries and base initialization.
     */
    JpaQueryContext(JpaQueryContext parent, List<HibernateAlias> aliases, From<?, ?> root) {
        this.parent = parent
        this.joinTracker = new JoinTracker(parent != null ? parent.joinTracker : null, root)
        this.aliasRegistry = new AliasRegistry(parent != null ? parent.aliasRegistry : null)
        this.resolver = new ExpressionResolver(aliasRegistry, joinTracker)
        if (aliases != null) {
            for (HibernateAlias alias : aliases) {
                aliasRegistry.define(alias.alias(), alias)
            }
        }
        if (root != null) {
            this.joinTracker.addJoin('root', root)
        }
    }

    protected JoinTracker getJoinTracker() {
        return joinTracker
    }

    protected AliasRegistry getAliasRegistry() {
        return aliasRegistry
    }

    void setRoot(From<?, ?> root) {
        this.joinTracker.setRoot(root)
        this.joinTracker.addJoin('root', root)
    }

    void setParent(JpaQueryContext parent) {
        this.parent = parent
    }

    From<?, ?> getRoot() {
        return joinTracker.root
    }

    void addFrom(String path, From<?, ?> from) {
        joinTracker.addJoin(path, from)
    }

    From<?, ?> getFrom(String path) {
        return joinTracker.getJoin(path)
    }

    void registerAlias(String alias, Expression<?> expression) {
        aliasRegistry.realize(alias, expression)
    }

    void registerAlias(String alias, HibernateAlias definition) {
        aliasRegistry.define(alias, definition)
    }

    void registerAliasFromPath(String path) {
        if (path != null && path.contains(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)) {
            String alias = path.split(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)[0]
            if (!aliasRegistry.isDefined(alias) && !aliasRegistry.hasRealized(alias)) {
                aliasRegistry.define(alias, null) // Mark as known
            }
        }
    }

    boolean hasAlias(String alias) {
        return aliasRegistry.isDefined(alias) || aliasRegistry.hasRealized(alias) || (parent != null && parent.hasAlias(alias))
    }

    Expression<?> getAliasedExpression(String alias) {
        Expression<?> expr = aliasRegistry.getRealized(alias)
        if (expr == null && parent != null) {
            return parent.getAliasedExpression(alias)
        }
        return expr
    }

    Expression<?> getFullyQualifiedExpression(String path) {
        if (parent != null) {
            if ('{alias}' == path) {
                return parent.root
            }
            if (path != null && path.startsWith('{alias}.')) {
                return parent.getFullyQualifiedExpression("root.${path.substring(8)}".toString())
            }
        }
        return resolver.resolve(path)
    }

    Path<?> getFullyQualifiedPath(String path) {
        if (parent != null) {
            if ('{alias}' == path) {
                return parent.root
            }
            if (path != null && path.startsWith('{alias}.')) {
                return parent.getFullyQualifiedPath("root.${path.substring(8)}".toString())
            }
        }
        Expression<?> resolved = resolver.resolve(path)
        return (resolved instanceof Path) ? (Path) resolved : null
    }

    @Override
    JpaQueryContext clone() {
        try {
            return (JpaQueryContext) super.clone()
        } catch (CloneNotSupportedException e) {
            throw new AssertionError()
        }
    }

}
