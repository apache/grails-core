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
    Expression<?> resolveSort(String path) {
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
            // a component is only joined when the path goes on to an association inside it
            From<?, ?> join = sortJoin(from, tokens[i], associationPath, i < tokens.length - 2);
            if (join == null) {
                // the rest of the path is a component path or a property, which resolves as it is
                return getPath(from, String.join(".", Arrays.copyOfRange(tokens, i, tokens.length)));
            }
            from = join;
        }
        return getPath(from, tokens[tokens.length - 1]);
    }

    /**
     * Returns the join through which an order sorts by a property of an association, or {@code null} if the
     * attribute is neither an association nor a component that is to be joined.
     * <p>
     * A component has no table of its own, so its join adds nothing to the query. It is the source that an
     * association inside the component is joined from: read as a plain path, such an association would be joined
     * with an implicit inner join like any other.
     *
     * @param joinComponent whether a component is joined, which is the case when the path goes on to an attribute
     *     inside it that may be an association
     */
    private From<?, ?> sortJoin(From<?, ?> from, String attributeName, String associationPath, boolean joinComponent) {
        Path<?> attribute = getPath(from, attributeName);
        PersistentAttributeType type = attribute != null && attribute.getModel() instanceof Attribute<?, ?> model ?
                model.getPersistentAttributeType() : null;
        boolean component = type == PersistentAttributeType.EMBEDDED;
        if (component ? !joinComponent : !isAssociation(type)) {
            return null;
        }
        for (Join<?, ?> join : from.getJoins()) {
            if (attributeName.equals(join.getAttribute().getName())) {
                return join;
            }
        }
        if (component) {
            return from.join(attributeName);
        }
        HibernateAlias requested = associationPath != null ? aliasRegistry.getDefinition(associationPath) : null;
        JoinType joinType = requested != null && !requested.explicit() ? requested.joinType() : JoinType.LEFT;
        return from.join(attributeName, joinType);
    }

    private static boolean isAssociation(PersistentAttributeType type) {
        return type == PersistentAttributeType.MANY_TO_ONE || type == PersistentAttributeType.ONE_TO_ONE ||
                type == PersistentAttributeType.ONE_TO_MANY || type == PersistentAttributeType.MANY_TO_MANY;
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
