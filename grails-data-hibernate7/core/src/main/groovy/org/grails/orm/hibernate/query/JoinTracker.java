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

import jakarta.persistence.criteria.From;
import java.util.HashMap;
import java.util.Map;

/**
 * Tracks physical JPA Joins and Roots to ensure consistency and avoid duplicate joins.
 *
 * @author walterduquedeestrada
 * @since 7.0.0
 */
public class JoinTracker {
    private final Map<String, From<?, ?>> joinsByPath = new HashMap<>();
    private From<?, ?> root;
    private final JoinTracker parent;

    public JoinTracker(From<?, ?> root) {
        this.root = root;
        this.parent = null;
    }

    public JoinTracker(JoinTracker parent, From<?, ?> root) {
        this.root = root;
        this.parent = parent;
    }

    public void addJoin(String path, From<?, ?> from) {
        joinsByPath.put(path, from);
    }

    public From<?, ?> getJoin(String path) {
        From<?, ?> join = joinsByPath.get(path);
        if (join == null && parent != null) {
            return parent.getJoin(path);
        }
        return join;
    }

    public From<?, ?> getRoot() {
        return root;
    }

    public void setRoot(From<?, ?> root) {
        this.root = root;
    }
}
