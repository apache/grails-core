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

import java.util.Arrays;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.Attribute.PersistentAttributeType;
import jakarta.persistence.metamodel.PluralAttribute;

import org.hibernate.query.sqm.tree.from.SqmAttributeJoin;
import org.hibernate.query.sqm.tree.from.SqmFrom;
import org.hibernate.query.sqm.tree.from.SqmJoin;

/**
 * Resolves string paths and aliases into JPA Expressions and Paths.
 *
 * @since 8.0
 */
public class ExpressionResolver {
    private final AliasRegistry aliasRegistry;
    private final JoinTracker joinTracker;

    public ExpressionResolver(AliasRegistry aliasRegistry, JoinTracker joinTracker) {
        this.aliasRegistry = aliasRegistry;
        this.joinTracker = joinTracker;
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

        // 1. Check for materialized aliases (Materialized Selects/Joins)
        if (aliasRegistry.hasRealized(cleanPath)) {
            return aliasRegistry.getRealized(cleanPath);
        }

        // 2. Handle defined but unrealized aliases
        if (aliasRegistry.isDefined(cleanPath)) {
            return resolveFromAlias(cleanPath, null);
        }

        // 3. Handle alias:property
        if (cleanPath.contains(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)) {
            String[] parts = cleanPath.split(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR);
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
     * Resolves the property that an order sorts by. A path that crosses associations is not resolved as a plain
     * path, because Hibernate joins such a path with an implicit inner join, which leaves out the rows where an
     * association is {@code null}. Each association on the path is joined explicitly instead: a join that the
     * query already has for the association is reused, and any other is a left join, unless a join type was
     * requested for the association.
     *
     * @param path the property path of the order
     * @return the expression to sort by, or {@code null} if the path cannot be resolved
     */
    public Expression<?> resolveSort(String path) {
        return resolveSort(path, false);
    }

    /**
     * Resolves the property that an order sorts by, as {@link #resolveSort(String)} does. A path that ends with
     * a foreign key, the identifier of an association whose key is a column of the table before it, needs no
     * join for that association: Hibernate reads the column, which keeps the rows where the association is
     * {@code null} as it is. The JPA metamodel does not tell which side of an association has the key, so the
     * caller says whether the path ends with one.
     *
     * @param path the property path of the order
     * @param endsWithForeignKey whether the last two attributes of the path are an association and its
     *     identifier, with the foreign key on the side of the path
     * @return the expression to sort by, or {@code null} if the path cannot be resolved
     */
    public Expression<?> resolveSort(String path, boolean endsWithForeignKey) {
        String cleanPath = path != null && path.startsWith("root.") ? path.substring(5) : path;
        if (cleanPath == null || cleanPath.indexOf('.') < 0 || cleanPath.startsWith("{alias}") ||
                cleanPath.contains(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR) ||
                aliasRegistry.isDefined(cleanPath)) {
            return resolve(path);
        }

        String[] tokens = cleanPath.split("\\.");
        From<?, ?> from = joinTracker.getRoot();
        // the path of the association from the root, by which a join type is requested for it
        String associationPath = "";
        int first = 0;
        if (aliasRegistry.isDefined(tokens[0])) {
            if (!(resolveFromAlias(tokens[0], null) instanceof From<?, ?> aliased)) {
                return resolve(path);
            }
            from = aliased;
            HibernateAlias definition = aliasRegistry.getDefinition(tokens[0]);
            associationPath = definition != null ? definition.path() : null;
            first = 1;
        }

        for (int i = first; i < tokens.length - 1; i++) {
            if (associationPath != null) {
                associationPath = associationPath.isEmpty() ? tokens[i] : associationPath + '.' + tokens[i];
            }
            boolean last = i == tokens.length - 2;
            From<?, ?> join = sortJoin(from, tokens[i], associationPath, last, last && endsWithForeignKey);
            if (join == null) {
                // the rest of the path is a component path, a property or a foreign key, which resolves as it is
                return getPath(from, String.join(".", Arrays.copyOfRange(tokens, i, tokens.length)));
            }
            from = join;
        }
        return getPath(from, tokens[tokens.length - 1]);
    }

    /**
     * Returns the join through which an order sorts by a property of an association, or {@code null} if the
     * rest of the path needs no join: the attribute is neither an association nor a component that is to be
     * joined, or the path ends with the foreign key of the association.
     * <p>
     * A component has no table of its own, so its join adds nothing to the query. It is the source that an
     * association inside the component is joined from: read as a plain path, such an association would be joined
     * with an implicit inner join like any other. A component is therefore only joined when the path goes on
     * beyond the attribute that follows it.
     *
     * @param last whether the attribute that follows this one ends the path
     * @param foreignKey whether the attribute that follows this one is the foreign key of the association
     */
    private From<?, ?> sortJoin(
            From<?, ?> from, String attributeName, String associationPath, boolean last, boolean foreignKey) {
        Path<?> attribute = getPath(from, attributeName);
        if (attribute == null || !(attribute.getModel() instanceof Attribute<?, ?> model)) {
            return null;
        }
        boolean component = model.getPersistentAttributeType() == PersistentAttributeType.EMBEDDED;
        if (component ? last : !model.isAssociation()) {
            return null;
        }
        Join<?, ?> existing = ownJoin(from, associationPath);
        if (existing == null) {
            existing = existingJoin(from, attributeName);
        }
        if (existing != null) {
            return existing;
        }
        if (component) {
            return from.join(attributeName);
        }
        HibernateAlias requested = associationPath != null ? aliasRegistry.getDefinition(associationPath) : null;
        if (requested != null && !requested.explicit()) {
            return from.join(attributeName, requested.joinType());
        }
        return foreignKey ? null : from.join(attributeName, JoinType.LEFT);
    }

    /**
     * Returns the join that the criteria of this query made from a source for an association, or {@code null}
     * if they made none. Two blocks of the same association share the join of that association, and each joins
     * what its criteria name inside it, so the source has a join of the inner association for each block. An
     * order of a block sorts by the join of its own block, which is the one this query tracks for the path.
     */
    private Join<?, ?> ownJoin(From<?, ?> from, String associationPath) {
        From<?, ?> own = associationPath != null ? joinTracker.getOwnJoin(associationPath) : null;
        return own instanceof Join<?, ?> join && join.getParent() == from ? join : null;
    }

    /**
     * Returns the join that a source already has for an attribute, or {@code null} if it has none. An association
     * has a join for each of its aliases, and the first of them is the join that the path of the association
     * shares, so that is the one a sort by the path uses. Hibernate keeps the joins of a source in the order in
     * which they were made, while {@link From#getJoins()} returns them as a set, which has no order.
     * <p>
     * A fetch of the association is a join as well. It is only used when the query has no other join of the
     * association, since the criteria of the query apply to the other join.
     */
    private static Join<?, ?> existingJoin(From<?, ?> from, String attributeName) {
        Join<?, ?> fetch = null;
        for (SqmJoin<?, ?> join : ((SqmFrom<?, ?>) from).getSqmJoins()) {
            if (join instanceof SqmAttributeJoin<?, ?> attributeJoin &&
                    attributeName.equals(attributeJoin.getAttribute().getName())) {
                if (!attributeJoin.isFetched()) {
                    return attributeJoin;
                }
                if (fetch == null) {
                    fetch = attributeJoin;
                }
            }
        }
        return fetch;
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
            aliased = enclosing != null ? enclosing : resolveAssociation(def.path(), def.joinType());
        } else {
            Path<?> path = getPath(joinTracker.getRoot(), def.path());
            boolean elementCollection = path != null && path.getModel() instanceof PluralAttribute<?, ?, ?> attribute &&
                    attribute.getPersistentAttributeType() == PersistentAttributeType.ELEMENT_COLLECTION;
            boolean sharesPath = !elementCollection && def.equals(aliasRegistry.getAssociationAlias(def.path()));
            From<?, ?> join = sharesPath ? joinTracker.getOwnJoin(def.path()) : null;
            if (join == null) {
                join = joinTracker.getRoot().join(def.path(), def.joinType());
                if (sharesPath) {
                    joinTracker.addJoin(def.path(), join);
                }
            }
            aliased = join;
        }
        aliasRegistry.realize(alias, aliased);
        return subPath != null ? getPath((From<?, ?>) aliased, subPath) : aliased;
    }

    /**
     * Resolves an association path independently of named aliases, even when an alias has the same name.
     */
    From<?, ?> resolveAssociation(String path, JoinType joinType) {
        From<?, ?> join = joinTracker.getOwnJoin(path);
        if (join == null) {
            HibernateAlias associationAlias = aliasRegistry.getAssociationAlias(path);
            if (associationAlias != null) {
                join = (From<?, ?>) resolveFromAlias(associationAlias.alias(), null);
            } else {
                join = joinTracker.getRoot().join(path, joinType);
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
