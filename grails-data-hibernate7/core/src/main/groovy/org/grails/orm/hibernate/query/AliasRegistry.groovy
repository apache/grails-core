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

/**
 * Registry for user-defined and auto-generated aliases in a JPA query.
 *
 * @since 8.0
 */
@CompileStatic
class AliasRegistry {

    private final Map<String, HibernateAlias> definitions = [:]
    private final Map<String, Expression<?>> realizedExpressions = [:]
    private final AliasRegistry parent

    AliasRegistry() {
        this.parent = null
    }

    AliasRegistry(AliasRegistry parent) {
        this.parent = parent
    }

    void define(String alias, HibernateAlias definition) {
        definitions.put(alias, definition)
    }

    void realize(String alias, Expression<?> expression) {
        realizedExpressions.put(alias, expression)
    }

    /**
     * Whether this query declares the alias, independently of any enclosing query.
     */
    boolean isOwnDefined(String alias) {
        return definitions.containsKey(alias)
    }

    /**
     * Whether this query realized the alias, independently of any enclosing query.
     */
    boolean isOwnRealized(String alias) {
        return realizedExpressions.containsKey(alias)
    }

    /**
     * The first declared alias of an association path shares the path join. Further aliases
     * have independent joins. Only declarations in this query participate.
     */
    HibernateAlias getAssociationAlias(String path) {
        for (HibernateAlias definition : definitions.values()) {
            if (definition != null && definition.explicit() && path == definition.path()) {
                return definition
            }
        }
        return null
    }

    boolean isDefined(String alias) {
        if (definitions.containsKey(alias) || realizedExpressions.containsKey(alias)) {
            return true
        }
        if (parent != null) {
            return parent.isDefined(alias)
        }
        return false
    }

    HibernateAlias getDefinition(String alias) {
        HibernateAlias definition = definitions.get(alias)
        if (definition == null && parent != null) {
            return parent.getDefinition(alias)
        }
        return definition
    }

    Expression<?> getRealized(String alias) {
        Expression<?> expr = realizedExpressions.get(alias)
        if (expr == null && !isOwnDefined(alias) && parent != null) {
            return parent.getRealized(alias)
        }
        return expr
    }

    boolean hasRealized(String alias) {
        return getRealized(alias) != null
    }

}
