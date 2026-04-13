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
import java.util.HashMap;
import java.util.Map;

/**
 * Registry for user-defined and auto-generated aliases in a JPA query.
 *
 * @author walterduquedeestrada
 * @since 7.0.0
 */
public class AliasRegistry {
    private final Map<String, HibernateAlias> definitions = new HashMap<>();
    private final Map<String, Expression<?>> realizedExpressions = new HashMap<>();
    private final AliasRegistry parent;

    public AliasRegistry() {
        this.parent = null;
    }

    public AliasRegistry(AliasRegistry parent) {
        this.parent = parent;
    }

    public void define(String alias, HibernateAlias definition) {
        definitions.put(alias, definition);
    }

    public void realize(String alias, Expression<?> expression) {
        realizedExpressions.put(alias, expression);
    }

    public boolean isDefined(String alias) {
        if (definitions.containsKey(alias) || realizedExpressions.containsKey(alias)) {
            return true;
        }
        if (parent != null) {
            return parent.isDefined(alias);
        }
        return false;
    }

    public HibernateAlias getDefinition(String alias) {
        HibernateAlias def = definitions.get(alias);
        if (def == null && parent != null) {
            return parent.getDefinition(alias);
        }
        return def;
    }

    public Expression<?> getRealized(String alias) {
        Expression<?> expr = realizedExpressions.get(alias);
        if (expr == null && parent != null) {
            return parent.getRealized(alias);
        }
        return expr;
    }

    public boolean hasRealized(String alias) {
        return realizedExpressions.containsKey(alias) || (parent != null && parent.hasRealized(alias));
    }
}
