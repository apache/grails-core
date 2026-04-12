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
import org.hibernate.query.criteria.JpaExpression;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import grails.gorm.DetachedCriteria;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.query.Query;

/**
 * A class that provides the {@link From} and {@link Path} for a given alias or property path.
 *
 * @author burt
 * @author graemerocher
 * @since 7.0.0
 */
public class JpaQueryContext implements Cloneable {

    private final Map<String, From<?, ?>> fromMap = new HashMap<>();
    private final Map<String, Expression<?>> aliasMap = new HashMap<>();
    private From<?, ?> root;

    public JpaQueryContext() {
    }

    public JpaQueryContext(
            DetachedCriteria<?> detachedCriteria,
            List<Query.Projection> projections,
            List<HibernateAlias> aliases,
            From<?, ?> root) {
        this.root = root;
    }

    /**
     * Constructor for PredicateGenerator subqueries.
     */
    public JpaQueryContext(
            JpaQueryContext parent,
            PersistentEntity entity,
            List<Query.Criterion> criteria,
            List<Object> arguments,
            Map<?, ?> fetchStrategies,
            Map<?, ?> joinTypes,
            From<?, ?> root) {
        this.root = root;
        if (parent != null) {
            this.fromMap.putAll(parent.fromMap);
            this.aliasMap.putAll(parent.aliasMap);
        }
    }

    /**
     * Constructor for simpler subqueries or clones.
     */
    public JpaQueryContext(
            JpaQueryContext parent,
            DetachedCriteria<?> detachedCriteria,
            List<Object> aliases,
            From<?, ?> root) {
        this.root = root;
        if (parent != null) {
            this.fromMap.putAll(parent.fromMap);
            this.aliasMap.putAll(parent.aliasMap);
        }
    }

    public void setRoot(From<?, ?> root) {
        this.root = root;
    }

    public From<?, ?> getRoot() {
        return root;
    }

    public void addFrom(String path, From<?, ?> from) {
        fromMap.put(path, from);
    }

    public From<?, ?> getFrom(String path) {
        return fromMap.get(path);
    }

    public void registerAlias(String alias, Expression<?> expression) {
        aliasMap.put(alias, expression);
    }

    public void registerAliasFromPath(String path) {
        if (path != null && path.contains(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)) {
            String alias = path.split(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)[0];
            if (!aliasMap.containsKey(alias)) {
                aliasMap.put(alias, null); // Placeholder to mark it as a known alias
            }
        }
    }

    public boolean hasAlias(String alias) {
        if (alias == null) return false;
        String key = alias;
        if (key.contains(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)) {
            key = key.split(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)[0];
        }
        return aliasMap.containsKey(key);
    }

    public Expression<?> getAliasedExpression(String alias) {
        if (alias == null) return null;
        String key = alias;
        if (key.contains(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)) {
            key = key.split(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)[0];
        }
        
        if (aliasMap.containsKey(key)) {
            return aliasMap.get(key);
        }
        if (key.startsWith("root.")) {
            String stripped = key.substring(5);
            if (aliasMap.containsKey(stripped)) {
                return aliasMap.get(stripped);
            }
        }
        return null;
    }

    public Expression<?> getFullyQualifiedExpression(String path) {
        Expression<?> aliased = getAliasedExpression(path);
        if (aliased != null) {
            return aliased;
        }

        if (path.equals("root")) {
            return root;
        }
        
        String propertyName = path;
        if (propertyName.startsWith("root.")) {
            propertyName = propertyName.substring(5);
        }
        
        String alias = null;
        if (propertyName.contains(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)) {
            String[] parts = propertyName.split(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR);
            alias = parts[0];
            propertyName = parts[1];
        }

        Expression<?> expression = getAliasedExpression(alias != null ? alias : propertyName);
        if (expression == null) {
            expression = getFullyQualifiedPath(propertyName);
        }

        if (alias != null && expression instanceof JpaExpression) {
            ((JpaExpression<?>) expression).alias(alias);
            registerAlias(alias, expression);
        }
        
        return expression;
    }

    public Path<?> getFullyQualifiedPath(String path) {
        if (hasAlias(path)) {
            Expression<?> aliased = getAliasedExpression(path);
            if (aliased instanceof Path<?>) {
                return (Path<?>) aliased;
            }
            return null; // Known alias, not a path
        }

        String propertyName = path;
        if (propertyName.contains(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)) {
            propertyName = propertyName.split(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)[1];
        }

        if (propertyName.startsWith("root.")) {
            propertyName = propertyName.substring(5);
        }

        if (hasAlias(propertyName)) {
            Expression<?> aliased = getAliasedExpression(propertyName);
            if (aliased instanceof Path<?>) {
                return (Path<?>) aliased;
            }
            return null; // Known alias, not a path
        }

        if (propertyName.equals("root")) {
            return root;
        }
        
        String[] parts = propertyName.split("\\.");
        if (hasAlias(parts[0])) {
             return null;
        }
        
        Path<?> p = root;
        for (String part : parts) {
            p = p.get(part);
        }
        return p;
    }

    @Override
    public JpaQueryContext clone() {
        try {
            JpaQueryContext cloned = (JpaQueryContext) super.clone();
            cloned.fromMap.putAll(this.fromMap);
            cloned.aliasMap.putAll(this.aliasMap);
            return cloned;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
