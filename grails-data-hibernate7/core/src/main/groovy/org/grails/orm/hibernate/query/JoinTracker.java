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
import java.util.Map;

import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;

/**
 * Tracks physical JPA Joins and Roots to ensure consistency and avoid duplicate joins. The joins of a query and of
 * its association blocks are kept together, by the path of the association from the root of the query.
 *
 * @since 8.0
 */
public class JoinTracker {
    private static final String ROOT = "root";

    private final Map<String, From<?, ?>> joinsByPath;
    private final String prefix;
    private final JoinTracker owner;
    private From<?, ?> root;
    private final JoinTracker parent;
    private JoinPlan plan;

    public JoinTracker(From<?, ?> root) {
        this(null, root);
    }

    public JoinTracker(JoinTracker parent, From<?, ?> root) {
        this.root = root;
        this.parent = parent;
        this.joinsByPath = new HashMap<>();
        this.prefix = "";
        this.owner = this;
    }

    private JoinTracker(JoinTracker parent, From<?, ?> root, String blockPath) {
        this.root = root;
        this.parent = parent;
        this.owner = parent.owner;
        this.joinsByPath = owner.joinsByPath;
        this.prefix = parent.prefix.isEmpty() ? blockPath : parent.prefix + "." + blockPath;
    }

    /**
     * The tracker of an association block. A block shares the joins of the query it is in: a path relative to the
     * block names the same join as that path behind the block's association relative to the query's root.
     *
     * @param parent the tracker of the query or block that contains the block
     * @param root the join of the block's association
     * @param blockPath the association path of the block, relative to the container
     */
    static JoinTracker forBlock(JoinTracker parent, From<?, ?> root, String blockPath) {
        return new JoinTracker(parent, root, blockPath);
    }

    private String key(String path) {
        return prefix.isEmpty() ? path : prefix + "." + path;
    }

    public void addJoin(String path, From<?, ?> from) {
        if (ROOT.equals(path)) {
            this.root = from;
            return;
        }
        joinsByPath.put(key(path), from);
    }

    public From<?, ?> getJoin(String path) {
        From<?, ?> join = getOwnJoin(path);
        if (join == null && parent != null) {
            return parent.getJoin(path);
        }
        return join;
    }

    /**
     * Returns the join registered by this query, ignoring enclosing queries because
     * an association path is relative to the root of the query it appears in.
     */
    From<?, ?> getOwnJoin(String path) {
        return ROOT.equals(path) ? root : joinsByPath.get(key(path));
    }

    /**
     * Records the join types the criteria of the whole query decided. Only the tracker of a query holds them, and
     * its blocks read them.
     */
    void setPlan(JoinPlan plan) {
        owner.plan = plan;
    }

    /**
     * The join type the criteria of the whole query decided for a path relative to this scope, or {@code null}.
     */
    JoinType plannedType(String path) {
        return owner.plan == null ? null : owner.plan.typeOf(key(path));
    }

    public From<?, ?> getRoot() {
        return root;
    }

    public void setRoot(From<?, ?> root) {
        this.root = root;
    }
}
