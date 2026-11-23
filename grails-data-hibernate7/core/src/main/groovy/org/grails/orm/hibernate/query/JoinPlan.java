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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import jakarta.persistence.criteria.JoinType;

/**
 * The join type of every association path of one query, decided once from the whole criteria before any of it is
 * translated, so that the join of a path does not depend on the order or the route by which the translation
 * reaches it.
 *
 * <p>A path is named from the root of the query, whichever way the criteria reach it: an alias, an association
 * block, a dotted property, or a join type configured on the criteria. A declaration is an alias, an association
 * block or a configured join type. The join of a path is an inner join if any declaration of it asks for one, a left
 * join if every declaration of it asks for a left join, and an inner join if the path is only mentioned by a dotted
 * property. A path nobody names itself takes the join type of the paths below it.</p>
 *
 * @since 8.0.1
 */
final class JoinPlan {

    private final Map<String, JoinType> declared = new HashMap<>();
    private final Set<String> mentioned = new HashSet<>();
    private final Map<String, JoinType> inherited = new HashMap<>();
    private final Map<String, JoinType> configured = new HashMap<>();

    /**
     * Records the join type configured for a path on the criteria, which an association block of the path takes
     * when it asks for none.
     */
    void configure(String path, JoinType joinType) {
        configured.put(path, joinType);
    }

    /**
     * Replaces the join type declared for a path with the one configured on the criteria after the alias of the
     * path, and declares the path with it when nothing else does.
     */
    void override(String path, JoinType joinType) {
        declared.put(path, joinType);
        forEachAncestor(path, ancestor -> merge(inherited, ancestor, joinType));
    }

    Map<String, JoinType> configuredTypes() {
        return configured;
    }

    /**
     * The join type configured for exactly this path, or {@code null}.
     */
    JoinType configured(String path) {
        return configured.get(path);
    }

    /**
     * Records that an alias or an association block asks for the path with the join type.
     */
    void declare(String path, JoinType joinType) {
        merge(declared, path, joinType);
        forEachAncestor(path, ancestor -> merge(inherited, ancestor, joinType));
    }

    /**
     * Records that a dotted property goes through the path, without asking for a join type.
     */
    void mention(String path) {
        mentioned.add(path);
        forEachAncestor(path, ancestor -> merge(inherited, ancestor, JoinType.INNER));
    }

    private static void merge(Map<String, JoinType> types, String path, JoinType joinType) {
        JoinType known = types.get(path);
        if (known == null || (known == JoinType.LEFT && joinType != JoinType.LEFT)) {
            types.put(path, joinType);
        }
    }

    /**
     * The join type of the path, or {@code null} when the criteria do not name it. A declaration of the path
     * itself decides, then a dotted property that goes through it, and otherwise the declarations of the paths
     * below it, which an inner join of any of them requires of this one.
     */
    JoinType typeOf(String path) {
        JoinType type = declared.get(path);
        if (type != null) {
            return type;
        }
        return mentioned.contains(path) ? JoinType.INNER : inherited.get(path);
    }

    private static void forEachAncestor(String path, Consumer<String> action) {
        int dot = path.indexOf('.');
        while (dot >= 0) {
            action.accept(path.substring(0, dot));
            dot = path.indexOf('.', dot + 1);
        }
    }
}
