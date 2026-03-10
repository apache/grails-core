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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.persistence.FetchType;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;

import org.hibernate.query.criteria.JpaCriteriaQuery;

import grails.gorm.DetachedCriteria;
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria;

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

    public JpaFromProvider(DetachedCriteria<?> detachedCriteria, JpaCriteriaQuery<?> cq, From<?, ?> root) {
        fromMap = getFromsByName(detachedCriteria, cq, root);
    }

    private Map<String, From<?, ?>> getFromsByName(
            DetachedCriteria<?> detachedCriteria, JpaCriteriaQuery<?> cq, From<?, ?> root) {
        var detachedAssociationCriteriaList = detachedCriteria.getCriteria().stream()
                .map(new DetachedAssociationFunction())
                .flatMap(List::stream)
                .toList();

        var aliasMap = createAliasMap(detachedAssociationCriteriaList);
        // The join column is column for joining from the root entity
        var detachedFroms = createDetachedFroms(cq, detachedAssociationCriteriaList);
        Map<String, From<?, ?>> fromsByName = Stream.concat(
                        aliasMap.keySet().stream(),
                        detachedCriteria.getFetchStrategies().entrySet().stream()
                                .filter(entry -> entry.getValue().equals(FetchType.EAGER))
                                .map(Map.Entry::getKey)
                                .toList()
                                .stream())
                .distinct()
                .map(joinColumn -> {
                    // Determine owner class for this join path from detached criteria
                    var dac = aliasMap.get(joinColumn);
                    Class<?> ownerClass =
                            dac != null ? dac.getAssociation().getOwner().getJavaClass() : root.getJavaType();
                    // Choose base From: use outer root only if join belongs to the outer root type;
                    // otherwise create a detached root for the owner
                    From<?, ?> base = ownerClass.equals(root.getJavaType())
                            ? root
                            : detachedFroms.computeIfAbsent(joinColumn, s -> cq.from(ownerClass));

                    var table = base.join(
                            joinColumn,
                            detachedCriteria.getJoinTypes().entrySet().stream()
                                    .filter(entry -> entry.getKey().equals(joinColumn))
                                    .map(Map.Entry::getValue)
                                    .findFirst()
                                    .orElse(JoinType.INNER));
                    // Attempt to find specific criteria configuration for this association path
                    var column = Optional.ofNullable(aliasMap.get(joinColumn))
                            .map(detachedAssociationCriteria ->
                                    Objects.requireNonNullElse(detachedAssociationCriteria.getAlias(), joinColumn))
                            .orElse(joinColumn);
                    table.alias(column);
                    return new AbstractMap.SimpleEntry<>(column, table);
                })
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (existing, replacement) -> existing,
                        java.util.LinkedHashMap::new));
        fromsByName.put("root", root);
        String rootAlias = detachedCriteria.getAlias();
        if (rootAlias != null && !rootAlias.isEmpty()) {
            fromsByName.put(rootAlias, root);
        }
        return fromsByName;
    }

    private Map<String, From<?, ?>> createDetachedFroms(
            JpaCriteriaQuery<?> cq, List<DetachedAssociationCriteria<?>> detachedAssociationCriteriaList) {
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
        String[] parsed = propertyName.split("\\.");
        if (parsed.length == SINGLE_PROPERTY) {
            if (fromMap.containsKey(propertyName)) {
                return fromMap.get(propertyName);
            } else {
                return fromMap.get("root").get(propertyName);
            }
        }
        String tableName = parsed[0];
        String columnName = parsed[1];
        return fromMap.get(tableName).get(columnName);
    }

    public Object clone() {
        return new JpaFromProvider(fromMap);
    }

    public void put(String tableName, From<?, ?> child) {
        fromMap.put(tableName, child);
    }
}
