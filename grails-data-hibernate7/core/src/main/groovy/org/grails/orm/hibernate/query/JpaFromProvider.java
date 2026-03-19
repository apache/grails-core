/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.orm.hibernate.query;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.persistence.FetchType;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;

import grails.gorm.DetachedCriteria;
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria;
import org.grails.datastore.mapping.query.Query;

@SuppressWarnings({
    "PMD.DataflowAnomalyAnalysis",
    "PMD.ProperCloneImplementation",
    "PMD.CloneMethodReturnTypeMustMatchClassName",
    "PMD.CloneThrowsCloneNotSupportedException"
})
public class JpaFromProvider implements Cloneable {

    private static final int SINGLE_PROPERTY = 1;

    private final Map<String, From<?, ?>> fromMap;

    private JpaFromProvider(Map<String, From<?, ?>> fromMap) {
        this.fromMap = new HashMap<>(fromMap);
    }

    public JpaFromProvider(DetachedCriteria<?> detachedCriteria, AbstractQuery<?> cq, From<?, ?> root) {
        this(detachedCriteria, List.of(), cq, root);
    }

    public JpaFromProvider(
            DetachedCriteria<?> detachedCriteria,
            List<Query.Projection> projections,
            AbstractQuery<?> cq,
            From<?, ?> root) {
        fromMap = getFromsByName(detachedCriteria, projections, cq, root);
    }

    public JpaFromProvider(
            JpaFromProvider parent,
            DetachedCriteria<?> detachedCriteria,
            List<Query.Projection> projections,
            AbstractQuery<?> cq,
            From<?, ?> root) {
        fromMap = new HashMap<>(parent.fromMap);
        fromMap.putAll(getFromsByName(detachedCriteria, projections, cq, root));
    }

    private Map<String, From<?, ?>> getFromsByName(
            DetachedCriteria<?> detachedCriteria,
            List<Query.Projection> projections,
            AbstractQuery<?> cq,
            From<?, ?> root) {
        var detachedAssociationCriteriaList = detachedCriteria.getCriteria().stream()
                .map(new DetachedAssociationFunction())
                .flatMap(List::stream)
                .toList();

        var aliasMap = createAliasMap(detachedAssociationCriteriaList);
        var definedAliases = detachedAssociationCriteriaList.stream()
                .map(DetachedAssociationCriteria::getAlias)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        var projectedPaths = projections.stream()
                .filter(Query.PropertyProjection.class::isInstance)
                .map(p -> ((Query.PropertyProjection) p).getPropertyName())
                .filter(name -> name.contains("."))
                .map(name -> name.substring(0, name.lastIndexOf('.')))
                .collect(Collectors.toSet());

        var eagerPaths = detachedCriteria.getFetchStrategies().entrySet().stream()
                .filter(entry -> entry.getValue().equals(FetchType.EAGER))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        java.util.Set<String> allPaths = new java.util.HashSet<>();
        allPaths.addAll(aliasMap.keySet());
        allPaths.addAll(projectedPaths.stream()
                .filter(p -> !definedAliases.contains(p))
                .toList());
        allPaths.addAll(eagerPaths);

        // Expand paths to include all parents (e.g., "a.b.c" -> "a", "a.b", "a.b.c")
        java.util.Set<String> expandedPaths = new java.util.HashSet<>();
        for (String path : allPaths) {
            String[] segments = path.split("\\.");
            StringBuilder current = new StringBuilder();
            for (String segment : segments) {
                if (current.length() > 0) {
                    current.append(".");
                }
                current.append(segment);
                expandedPaths.add(current.toString());
            }
        }

        Map<String, From<?, ?>> fromsByPath = new HashMap<>();
        fromsByPath.put("root", root);

        List<String> sortedPaths = expandedPaths.stream()
                .sorted(java.util.Comparator.comparingInt(p -> p.split("\\.").length))
                .toList();

        for (String path : sortedPaths) {
            if (fromsByPath.containsKey(path)) {
                continue;
            }
            String parentPath = path.contains(".") ? path.substring(0, path.lastIndexOf('.')) : "root";
            String leaf = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;

            From<?, ?> base = fromsByPath.get(parentPath);

            JoinType joinType = JoinType.INNER;
            if (detachedCriteria.getJoinTypes().containsKey(path)) {
                joinType = detachedCriteria.getJoinTypes().get(path);
            } else if (projectedPaths.contains(path) || eagerPaths.contains(path)) {
                joinType = JoinType.LEFT;
            }

            var table = base.join(leaf, joinType);

            // If there's an alias for this path, map it to the alias too
            var dac = aliasMap.get(path);
            if (dac != null && dac.getAlias() != null) {
                fromsByPath.put(dac.getAlias(), table);
            }

            table.alias(path);
            fromsByPath.put(path, table);
        }

        String rootAlias = detachedCriteria.getAlias();
        if (rootAlias != null && !rootAlias.isEmpty()) {
            fromsByPath.put(rootAlias, root);
        }
        return fromsByPath;
    }

    private Map<String, From<?, ?>> createDetachedFroms(
            AbstractQuery<?> cq, List<DetachedAssociationCriteria<?>> detachedAssociationCriteriaList) {
        Function<DetachedAssociationCriteria<?>, String> getAssociationPath =
                DetachedAssociationCriteria::getAssociationPath;
        return detachedAssociationCriteriaList.stream()
                .collect(Collectors.toMap(
                        getAssociationPath,
                        criteria -> {
                            Class<?> javaClass =
                                    criteria.getAssociation().getOwner().getJavaClass();
                            return cq.from(javaClass);
                        },
                        (oldValue, newValue) -> newValue));
    }

    private Map<String, DetachedAssociationCriteria<?>> createAliasMap(
            List<DetachedAssociationCriteria<?>> detachedAssociationCriteriaList) {
        // Use a merge function and a stable map type to avoid DuplicateKey exceptions when the same
        // association path/alias appears multiple times (e.g., referenced in both predicate and sort).
        // Keep the first occurrence to preserve deterministic aliasing.
        return detachedAssociationCriteriaList.stream()
                .map(new AliasMapEntryFunction())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (existing, replacement) -> existing,
                        java.util.LinkedHashMap::new));
    }

    public Path<?> getFullyQualifiedPath(String propertyName) {
        if (Objects.isNull(propertyName) || propertyName.trim().isEmpty()) {
            throw new IllegalArgumentException("propertyName cannot be null");
        }

        if (fromMap.containsKey(propertyName)) {
            return fromMap.get(propertyName);
        }

        String[] parsed = propertyName.split("\\.");
        if (parsed.length == SINGLE_PROPERTY) {
            return fromMap.get("root").get(propertyName);
        }

        // Try to find the longest matching prefix in fromMap
        for (int i = parsed.length - 1; i >= 1; i--) {
            String prefix = java.util.Arrays.stream(parsed, 0, i).collect(Collectors.joining("."));
            if (fromMap.containsKey(prefix)) {
                Path<?> path = fromMap.get(prefix);
                for (int j = i; j < parsed.length; j++) {
                    path = path.get(parsed[j]);
                }
                return path;
            }
        }

        // Fallback to root
        Path<?> path = fromMap.get("root");
        for (String segment : parsed) {
            path = path.get(segment);
        }
        return path;
    }

    public Object clone() {
        return new JpaFromProvider(fromMap);
    }

    public void put(String tableName, From<?, ?> child) {
        fromMap.put(tableName, child);
    }
}
