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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import jakarta.persistence.FetchType;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;

import grails.gorm.DetachedCriteria;
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.query.Query;

@SuppressWarnings({
    "PMD.DataflowAnomalyAnalysis",
    "PMD.ProperCloneImplementation",
    "PMD.CloneMethodReturnTypeMustMatchClassName",
    "PMD.CloneThrowsCloneNotSupportedException"
})
public class JpaFromProvider implements Cloneable {

    private static final int SINGLE_PROPERTY = 1;
    private static final String ROOT_ALIAS = "root";

    private final Map<String, From<?, ?>> fromMap;

    private JpaFromProvider(Map<String, From<?, ?>> fromMap) {
        this.fromMap = new HashMap<>(fromMap);
    }

    public JpaFromProvider(DetachedCriteria<?> detachedCriteria, List<Query.Projection> projections, From<?, ?> root) {
        this(detachedCriteria, projections, List.of(), root);
    }

    @SuppressWarnings("PMD.ConstructorCallsOverridableMethod")
    public JpaFromProvider(
            DetachedCriteria<?> detachedCriteria,
            List<Query.Projection> projections,
            List<HibernateAlias> aliases,
            From<?, ?> root) {
        fromMap = getFromsByName(detachedCriteria, projections, aliases, root);
    }

    @SuppressWarnings("PMD.ConstructorCallsOverridableMethod")
    public JpaFromProvider(
            JpaFromProvider parent,
            DetachedCriteria<?> detachedCriteria,
            List<Query.Projection> projections,
            From<?, ?> root) {
        fromMap = new HashMap<>(parent.fromMap);
        fromMap.putAll(getFromsByName(detachedCriteria, projections, List.of(), root));
    }

    @SuppressWarnings("PMD.ConstructorCallsOverridableMethod")
    public JpaFromProvider(
            JpaFromProvider parent,
            PersistentEntity entity,
            List<Query.Criterion> criteria,
            List<Query.Projection> projections,
            Map<String, jakarta.persistence.FetchType> fetchStrategies,
            Map<String, jakarta.persistence.criteria.JoinType> joinTypes,
            From<?, ?> root) {
        fromMap = new HashMap<>(parent.fromMap);
        fromMap.putAll(getFromsByName(entity, criteria, projections, List.of(), fetchStrategies, joinTypes, root));
    }

    public Map<String, From<?, ?>> getFromsByName() {
        return fromMap;
    }

    public boolean hasAlias(String name) {
        return fromMap.containsKey(name);
    }

    protected Map<String, From<?, ?>> getFromsByName(
            DetachedCriteria<?> detachedCriteria,
            List<Query.Projection> projections,
            List<HibernateAlias> aliases,
            From<?, ?> root) {
        Map<String, From<?, ?>> froms = getFromsByName(
                detachedCriteria.getPersistentEntity(),
                detachedCriteria.getCriteria(),
                projections,
                aliases,
                detachedCriteria.getFetchStrategies(),
                detachedCriteria.getJoinTypes(),
                root);
        if (detachedCriteria.getAlias() != null) {
            froms.put(detachedCriteria.getAlias(), root);
        }
        return froms;
    }

    protected Map<String, From<?, ?>> getFromsByName(
            PersistentEntity entity,
            List<Query.Criterion> criteria,
            List<Query.Projection> projections,
            List<HibernateAlias> aliases,
            Map<String, FetchType> fetchStrategies,
            Map<String, JoinType> joinTypes,
            From<?, ?> root) {
        var allCriteriaPaths =
                criteria.stream().flatMap(c -> findPaths(c).stream()).toList();

        var detachedAssociationCriteriaList = criteria.stream()
                .map(new DetachedAssociationFunction())
                .flatMap(List::stream)
                .toList();

        var aliasMap = createAliasMap(detachedAssociationCriteriaList);

        // Also scan for HibernateAlias (basic collections)
        Map<String, String> basicAliasMap = new HashMap<>();
        Map<String, JoinType> basicJoinTypeMap = new HashMap<>();
        for (HibernateAlias ha : aliases) {
            basicAliasMap.put(ha.path(), ha.alias());
            basicJoinTypeMap.put(ha.path(), ha.joinType());
        }

        var definedAliases = detachedAssociationCriteriaList.stream()
                .map(DetachedAssociationCriteria::getAlias)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        definedAliases.addAll(basicAliasMap.values());

        var associationProjectedPaths = projections.stream()
                .filter(Query.PropertyProjection.class::isInstance)
                .map(p -> ((Query.PropertyProjection) p).getPropertyName())
                .filter(name -> name.contains("."))
                .map(name -> name.substring(0, name.lastIndexOf('.')))
                .collect(Collectors.toSet());

        var criteriaPaths = allCriteriaPaths.stream()
                .filter(p -> p.contains("."))
                .map(p -> p.substring(0, p.lastIndexOf('.')))
                .collect(Collectors.toSet());

        var eagerPaths = fetchStrategies.entrySet().stream()
                .filter(entry -> entry.getValue().equals(FetchType.EAGER))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        var collectionPaths = entity != null ?
                entity.getPersistentProperties().stream()
                        .filter(p -> p instanceof org.grails.datastore.mapping.model.types.Basic)
                        .map(org.grails.datastore.mapping.model.PersistentProperty::getName)
                        .collect(Collectors.toSet()) :
                java.util.Collections.<String>emptySet();

        java.util.Set<String> allPaths = new java.util.HashSet<>();
        allPaths.addAll(aliasMap.keySet());
        allPaths.addAll(basicAliasMap.keySet());
        allPaths.addAll(associationProjectedPaths);
        allPaths.addAll(criteriaPaths);
        allPaths.addAll(eagerPaths);
        allPaths.addAll(collectionPaths);

        // Don't try to join segments that are already defined aliases
        allPaths.removeAll(definedAliases);

        // Expand paths to include all parents (e.g., "a.b.c" -> "a", "a.b", "a.b.c")
        java.util.Set<String> expandedPaths = new java.util.HashSet<>();
        for (String path : allPaths) {
            String[] segments = path.split("\\.");
            StringBuilder current = new StringBuilder();
            for (String segment : segments) {
                if (!current.isEmpty()) {
                    current.append(".");
                }
                current.append(segment);
                expandedPaths.add(current.toString());
            }
        }

        // Re-calculate projected paths to include expanded segments for LEFT join logic
        var finalProjectedPaths = expandedPaths.stream()
                .filter(p -> associationProjectedPaths.stream().anyMatch(dp -> dp.equals(p) || dp.startsWith(p + ".")))
                .collect(Collectors.toSet());

        Map<String, From<?, ?>> fromsByPath = new HashMap<>();
        fromsByPath.put(ROOT_ALIAS, root);

        List<String> sortedPaths = expandedPaths.stream()
                .sorted(java.util.Comparator.comparingInt(p -> p.split("\\.").length))
                .toList();

        for (String path : sortedPaths) {
            if (fromsByPath.containsKey(path)) {
                continue;
            }
            String parentPath = path.contains(".") ? path.substring(0, path.lastIndexOf('.')) : ROOT_ALIAS;
            String leaf = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;

            From<?, ?> base = fromsByPath.get(parentPath);
            if (base == null) {
                continue;
            }

            JoinType joinType = JoinType.INNER;
            if (joinTypes.containsKey(path)) {
                joinType = joinTypes.get(path);
            } else if (basicJoinTypeMap.containsKey(path)) {
                joinType = basicJoinTypeMap.get(path);
            } else if (finalProjectedPaths.contains(path) ||
                    eagerPaths.contains(path) ||
                    collectionPaths.contains(path)) {
                joinType = JoinType.LEFT;
            }

            var table = base.join(leaf, joinType);

            boolean aliasApplied = false;
            // If there's an alias for this path, map it to the alias too
            var dac = aliasMap.get(path);
            if (dac != null && dac.getAlias() != null) {
                table.alias(dac.getAlias());
                fromsByPath.put(dac.getAlias(), table);
                aliasApplied = true;
            }

            String basicAlias = basicAliasMap.get(path);
            if (basicAlias != null) {
                table.alias(basicAlias);
                fromsByPath.put(basicAlias, table);
                aliasApplied = true;
            }

            if (!aliasApplied) {
                table.alias(path);
            }
            fromsByPath.put(path, table);
        }

        return fromsByPath;
    }

    private java.util.Set<String> findPaths(Query.Criterion criterion) {
        java.util.Set<String> paths = new java.util.HashSet<>();
        if (criterion instanceof Query.PropertyNameCriterion pnc) {
            paths.add(pnc.getProperty());
        } else if (criterion instanceof Query.Junction junction) {
            for (Query.Criterion c : junction.getCriteria()) {
                paths.addAll(findPaths(c));
            }
        }
        return paths;
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
            From<?, ?> root = fromMap.get(ROOT_ALIAS);
            if (root != null) {
                if (propertyName.equals(root.getJavaType().getSimpleName()) ||
                        propertyName.equals(root.getJavaType().getName())) {
                    return root;
                }
                return root.get(propertyName);
            }
        }

        // Try to find the longest matching prefix in fromMap
        for (int i = parsed.length; i >= 1; i--) {
            String prefix = java.util.Arrays.stream(parsed, 0, i).collect(Collectors.joining("."));
            if (fromMap.containsKey(prefix)) {
                Path<?> path = fromMap.get(prefix);
                if (path != null) {
                    for (int j = i; j < parsed.length; j++) {
                        path = path.get(parsed[j]);
                        if (path == null) {
                            break;
                        }
                    }
                    if (path != null) {
                        return path;
                    }
                }
            }
        }

        // Fallback to root
        Path<?> path = fromMap.get(ROOT_ALIAS);
        if (path != null) {
            for (String segment : parsed) {
                path = path.get(segment);
                if (path == null) {
                    break;
                }
            }
        }
        return path;
    }

    @Override
    public Object clone() {
        return new JpaFromProvider(fromMap);
    }

    public void put(String tableName, From<?, ?> child) {
        fromMap.put(tableName, child);
    }
}
