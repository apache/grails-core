package org.grails.orm.hibernate.query;

import grails.gorm.DetachedCriteria;
import jakarta.persistence.FetchType;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria;
import org.grails.datastore.mapping.query.Query;
import org.hibernate.query.criteria.JpaCriteriaQuery;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JpaFromProvider implements Cloneable {

    private Map<String, From> fromMap;

    private JpaFromProvider(Map<String, From> fromMap) {
        this.fromMap = new HashMap<>(fromMap);
    }


    public JpaFromProvider(DetachedCriteria detachedCriteria, JpaCriteriaQuery<?> cq, From root) {
        fromMap = getFromsByName(detachedCriteria, cq, root);
    }

    private Map<String, From> getFromsByName(DetachedCriteria detachedCriteria, JpaCriteriaQuery<?> cq, From root) {
        var detachedAssociationCriteriaList = ((List<Query.Criterion>) detachedCriteria.getCriteria())
                .stream()
                .map(new DetachedAssociationFunction())
                .flatMap(List::stream)
                .toList();

        var aliasMap = createAliasMap(detachedAssociationCriteriaList);
        //The join column is column for joining from the root entity
        var detachedFroms = createDetachedFroms(cq, detachedAssociationCriteriaList);
        Map<String, From> fromsByName = Stream.concat(aliasMap.keySet().stream(), ((Map<String, FetchType>) detachedCriteria.getFetchStrategies())
                        .entrySet()
                        .stream()
                        .filter(entry -> entry.getValue().equals(FetchType.EAGER))
                        .map(Map.Entry::getKey)
                        .toList().stream())
                .distinct()
                .map(joinColumn -> {
                    var table = detachedFroms.computeIfAbsent(joinColumn, s -> root).join(joinColumn, ((Map<String, JoinType>) detachedCriteria.getJoinTypes())
                            .entrySet()
                            .stream()
                            .filter(entry -> entry.getKey().equals(joinColumn))
                            .map(Map.Entry::getValue)
                            .findFirst()
                            .orElse(JoinType.INNER));
                    // Attempt to find specific criteria configuration for this association path
                    var column = Optional.ofNullable(aliasMap.get(joinColumn))
                            .map(detachedAssociationCriteria -> Objects.requireNonNullElse(detachedAssociationCriteria.getAlias(), joinColumn))
                            .orElse(joinColumn);
                    table.alias(column);
                    return new AbstractMap.SimpleEntry<>(column, table);
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        fromsByName.put("root", root);
        return fromsByName;
    }

    private Map<String, From> createDetachedFroms(JpaCriteriaQuery<?> cq, List<DetachedAssociationCriteria> detachedAssociationCriteriaList) {
        return detachedAssociationCriteriaList.stream()
                .collect(Collectors.toMap(
                        DetachedAssociationCriteria::getAssociationPath,
                        criteria -> cq.from(criteria.getAssociation().getOwner().getJavaClass()) , (oldValue, newValue) -> newValue)
                );
    }

    private Map<String, DetachedAssociationCriteria> createAliasMap(List<DetachedAssociationCriteria> detachedAssociationCriteriaList) {
        return detachedAssociationCriteriaList.stream()
                .map(new AliasMapEntryFunction())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Path getFullyQualifiedPath(String propertyName) {
        if (Objects.isNull(propertyName) || propertyName.trim().isEmpty()) {
            throw new IllegalArgumentException("propertyName cannot be null");
        }
        String[] parsed = propertyName.split("\\.");
        if (parsed.length == 1) {
            if (fromMap.containsKey(propertyName)) {
                return fromMap.get(propertyName);
            } else {
               return fromMap.get("root").get(propertyName);
            }
        }
        String tableName =  parsed[0];
        String columnName =  parsed[1];
        return fromMap.get(tableName).get(columnName);
    }

    public Object clone() {
        return new JpaFromProvider(fromMap);
    }


    public void put(String tableName, From child) {
        fromMap.put(tableName, child);
    }
}
