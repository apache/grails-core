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

/**
 * Resolves string paths and aliases into JPA Expressions and Paths.
 *
 * @since 8.0
 */
public class ExpressionResolver {
    private final AliasRegistry aliasRegistry;
    private final JoinTracker joinTracker;
    private final JpaQueryContext context;

    public ExpressionResolver(AliasRegistry aliasRegistry, JoinTracker joinTracker) {
        this(aliasRegistry, joinTracker, null);
    }

    public ExpressionResolver(AliasRegistry aliasRegistry, JoinTracker joinTracker, JpaQueryContext context) {
        this.aliasRegistry = aliasRegistry;
        this.joinTracker = joinTracker;
        this.context = context;
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
            if (aliasRegistry.isDefined(aliasCandidate) || (context != null && !context.isSubquery() && joinTracker.getJoin(aliasCandidate) != null)) {
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

    private Expression<?> resolveFromAlias(String alias, String subPath) {
        // 1. Check if already realized in THIS or PARENT registry
        Expression<?> aliased = aliasRegistry.getRealized(alias);
        if (aliased != null) {
            if (subPath != null && aliased instanceof From<?, ?> from) {
                return getPath(from, subPath);
            }
            return aliased; // It's a non-From expression (projection alias) or subPath is null
        }

        // 2. Check if already joined in THIS or PARENT JoinTracker (correlated joins)
        aliased = joinTracker.getJoin(alias);
        if (aliased != null) {
            aliasRegistry.realize(alias, aliased);
            return subPath != null ? getPath((From<?, ?>) aliased, subPath) : aliased;
        }

        // 3. If defined but not joined, materialize it on the CURRENT root or PARENT context
        if (aliasRegistry.isDefined(alias)) {
            if (!aliasRegistry.hasLocalDefinition(alias) && context != null && context.getParent() != null) {
                Expression<?> parentExpr = context.getParent().getFullyQualifiedExpression(alias);
                if (parentExpr != null) {
                    return subPath != null && parentExpr instanceof From<?, ?> from ? getPath(from, subPath) : parentExpr;
                }
            }
            HibernateAlias def = aliasRegistry.getDefinition(alias);
            if (def != null && def.path() != null) {
                String path = def.path();
                if (path.contains(".")) {
                    String[] parts = path.split("\\.");
                    Expression<?> current = resolve(parts[0]);
                    From<?, ?> from = (current instanceof From<?, ?> f) ? f : null;
                    if (from == null && joinTracker.getRoot() != null) {
                        try {
                            from = joinTracker.getRoot().join(parts[0], def.joinType());
                        } catch (Exception ignored) {
                        }
                    }
                    if (from != null) {
                        for (int i = 1; i < parts.length - 1; i++) {
                            from = from.join(parts[i], def.joinType());
                        }
                        aliased = from.join(parts[parts.length - 1], def.joinType());
                    }
                } else if (joinTracker.getRoot() != null) {
                    aliased = joinTracker.getRoot().join(path, def.joinType());
                }
                if (aliased != null) {
                    aliasRegistry.realize(alias, aliased);
                    joinTracker.addJoin(alias, (From<?, ?>) aliased);
                    if (context != null && aliasRegistry.isExplicit(alias)) {
                        context.registerAlias(alias, aliased);
                    }
                    return subPath != null ? getPath((From<?, ?>) aliased, subPath) : aliased;
                }
            }
        }

        return null;
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
