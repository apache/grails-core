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

import java.util.function.BiConsumer;
import java.util.function.Function;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.metamodel.Attribute.PersistentAttributeType;
import jakarta.persistence.metamodel.PluralAttribute;

import grails.orm.HibernateCriteriaBuilder;

/**
 * Resolves string paths and aliases into JPA Expressions and Paths.
 *
 * @since 8.0
 */
public class ExpressionResolver {
    private final AliasRegistry aliasRegistry;
    private final JoinTracker joinTracker;
    private Function<String, From<?, ?>> enclosingJoins = path -> null;
    private BiConsumer<String, From<?, ?>> joinPublisher = (path, join) -> { };

    public ExpressionResolver(AliasRegistry aliasRegistry, JoinTracker joinTracker) {
        this.aliasRegistry = aliasRegistry;
        this.joinTracker = joinTracker;
    }

    /**
     * Connects this resolver to the joins of the enclosing association block: an alias for a path the enclosing
     * query already joins uses that join, and a join created here for a path is published to it.
     *
     * @param lookup finds the join an enclosing query has for a path relative to this scope, or {@code null}
     * @param publisher receives each join an alias creates, to share it with the enclosing query
     */
    void shareJoins(Function<String, From<?, ?>> lookup, BiConsumer<String, From<?, ?>> publisher) {
        this.enclosingJoins = lookup;
        this.joinPublisher = publisher;
    }

    public Expression<?> resolve(String path) {
        if (path == null) return null;

        if (path.equals("{alias}") || path.equals("root")) {
            return joinTracker.getRoot();
        }
        if (path.startsWith("{alias}.")) {
            return getPath(joinTracker.getRoot(), path.substring(8));
        }
        
        String cleanPath = path;
        if (cleanPath.startsWith("root.")) {
            cleanPath = cleanPath.substring(5);
        }

        // 0. A name without a dot that the entity of this scope has as a property is that property, whatever an
        // enclosing query calls its own alias or joins by that name: the alias is reached by qualifying it, as in
        // alias.property
        Path<?> inheritedName = ownPropertyHidden(cleanPath);
        if (inheritedName != null) {
            return inheritedName;
        }

        // 1. Check for materialized aliases (Materialized Selects/Joins)
        if (aliasRegistry.hasRealized(cleanPath)) {
            return aliasRegistry.getRealized(cleanPath);
        }

        // 2. Handle defined but unrealized aliases
        if (aliasRegistry.isDefined(cleanPath)) {
            return resolveFromAlias(cleanPath, null);
        }

        // 3. Handle alias:property
        if (cleanPath.contains(HibernateCriteriaBuilder.ALIAS_SEPARATOR)) {
            String[] parts = cleanPath.split(HibernateCriteriaBuilder.ALIAS_SEPARATOR);
            Expression<?> resolved = resolveFromAlias(parts[0], parts[1]);
            if (resolved == null) {
                resolved = getPath(joinTracker.getRoot(), parts[1]);
                if (resolved != null) {
                    resolved.alias(parts[0]);
                    aliasRegistry.realize(parts[0], resolved);
                }
            }
            return resolved;
        }

        // 4. Handle alias.property
        if (cleanPath.contains(".")) {
            int dotIdx = cleanPath.indexOf(".");
            String aliasCandidate = cleanPath.substring(0, dotIdx);
            if (aliasRegistry.isDefined(aliasCandidate)) {
                return resolveFromAlias(aliasCandidate, cleanPath.substring(dotIdx + 1));
            }
        }

        // 5. Check Join Tracker (Already joined paths)
        From<?, ?> joined = joinTracker.getJoin(cleanPath);
        if (joined != null) {
            return joined;
        }

        // 6. Fallback to Root path resolution
        return getPath(joinTracker.getRoot(), cleanPath);
    }

    /**
     * The property of the entity of this scope that an alias or join of an enclosing query would hide, or
     * {@code null} when no enclosing query has an alias or join of the name, or the entity has no such property.
     */
    private Path<?> ownPropertyHidden(String name) {
        if (name.contains(".") || aliasRegistry.isOwnDefined(name) || aliasRegistry.isOwnRealized(name) ||
                joinTracker.getOwnJoin(name) != null) {
            return null;
        }
        boolean inherited = aliasRegistry.isDefined(name) || aliasRegistry.hasRealized(name) || joinTracker.getJoin(name) != null;
        return inherited ? getPath(joinTracker.getRoot(), name) : null;
    }

    private Expression<?> resolveFromAlias(String alias, String subPath) {
        // 1. Check if already realized in THIS or PARENT registry
        Expression<?> aliased = aliasRegistry.getRealized(alias);
        if (aliased != null) {
            if (subPath != null && aliased instanceof From<?, ?> from) {
                return getPath(from, subPath);
            }
            return aliased; // It's a non-From expression (projection alias) or subPath is null
        }

        HibernateAlias def = aliasRegistry.getDefinition(alias);
        if (def == null || def.path() == null) {
            return null;
        }
        if (!def.explicit()) {
            // A path absent from this root can refer to an enclosing query's association join.
            From<?, ?> enclosing = getPath(joinTracker.getRoot(), def.path()) == null ?
                    joinTracker.getJoin(def.path()) : null;
            if (enclosing == null && joinTracker.getOwnJoin(def.path()) == null) {
                // the enclosing query may already join this association, for a nested alias path or another block
                enclosing = enclosingJoins.apply(def.path());
                if (enclosing != null) {
                    joinTracker.addJoin(def.path(), enclosing);
                    joinPublisher.accept(def.path(), enclosing);
                }
            }
            aliased = enclosing != null ? enclosing : resolveAssociation(def.path(), def.joinType());
        } else {
            Path<?> path = getPath(joinTracker.getRoot(), def.path());
            boolean elementCollection = path != null && path.getModel() instanceof PluralAttribute<?, ?, ?> attribute &&
                    attribute.getPersistentAttributeType() == PersistentAttributeType.ELEMENT_COLLECTION;
            boolean sharesPath = !elementCollection && def.equals(aliasRegistry.getAssociationAlias(def.path()));
            From<?, ?> join = sharesPath ? joinTracker.getOwnJoin(def.path()) : null;
            if (join == null && sharesPath) {
                // the enclosing query may already join this association, for a nested alias path or another block
                join = enclosingJoins.apply(def.path());
            }
            if (join == null) {
                join = joinPath(def.path(), def.joinType());
            }
            if (sharesPath) {
                joinTracker.addJoin(def.path(), join);
                joinPublisher.accept(def.path(), join);
            }
            aliased = join;
        }
        aliasRegistry.realize(alias, aliased);
        return subPath != null ? getPath((From<?, ?>) aliased, subPath) : aliased;
    }

    /**
     * Joins the last association of a path with the given join type. A nested path such as
     * {@code owner.address} is joined one association at a time, as a JPA join takes a single attribute. The
     * associations before the last one are relative to this query's root and share the joins this query has
     * for them. A path that names an alias before {@link HibernateCriteriaBuilder#ALIAS_SEPARATOR}, such as
     * {@code o:address}, starts at the join of that alias instead of the root.
     */
    private From<?, ?> joinPath(String path, JoinType joinType) {
        int lastDot = path.lastIndexOf('.');
        if (lastDot >= 0) {
            return resolvePrefix(path.substring(0, lastDot), joinType).join(path.substring(lastDot + 1), joinType);
        }
        int separator = path.indexOf(HibernateCriteriaBuilder.ALIAS_SEPARATOR);
        if (separator >= 0) {
            From<?, ?> aliased = (From<?, ?>) resolveFromAlias(path.substring(0, separator), null);
            return aliased.join(path.substring(separator + HibernateCriteriaBuilder.ALIAS_SEPARATOR.length()), joinType);
        }
        return joinTracker.getRoot().join(path, joinType);
    }

    /**
     * Resolves the associations a nested path goes through before its last one. They keep the join type this
     * query configured for them, and otherwise take the join type of the path that goes through them.
     */
    private From<?, ?> resolvePrefix(String prefix, JoinType joinType) {
        HibernateAlias configured = aliasRegistry.isOwnDefined(prefix) ? aliasRegistry.getDefinition(prefix) : null;
        JoinType requested = aliasRegistry.getAssociationJoinType(prefix);
        return resolveAssociation(prefix, configured != null && !configured.explicit() ? configured.joinType() :
                requested != null ? requested : joinType);
    }

    /**
     * Resolves an association path independently of named aliases, even when an alias has the same name.
     * The join of a path is shared by everything in this query that goes through it.
     */
    From<?, ?> resolveAssociation(String path, JoinType joinType) {
        From<?, ?> join = joinTracker.getOwnJoin(path);
        if (join == null) {
            HibernateAlias associationAlias = aliasRegistry.getAssociationAlias(path);
            if (associationAlias != null) {
                join = (From<?, ?>) resolveFromAlias(associationAlias.alias(), null);
            } else {
                join = joinPath(path, joinType);
            }
            joinTracker.addJoin(path, join);
        }
        return join;
    }

    private Path<?> getPath(From<?, ?> from, String path) {
        if (from == null || path == null) return null;
        try {
            if (path.contains(".")) {
                String[] parts = path.split("\\.");
                Path<?> p = from;
                for (String part : parts) {
                    p = p.get(part);
                }
                return p;
            }
            return from.get(path);
        } catch (Exception e) {
            return null;
        }
    }
}
