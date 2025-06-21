package org.grails.orm.hibernate.query;

import grails.gorm.DetachedCriteria;
import jakarta.persistence.FetchType;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria;
import org.grails.datastore.mapping.query.Query;
import org.hibernate.query.criteria.JpaCriteriaQuery;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JpaFromProvider {

    private final DetachedCriteria detachedCriteria;

    public JpaFromProvider(DetachedCriteria detachedCriteria) {
        this.detachedCriteria = detachedCriteria;
    }

    public Map<String, From> getFromsByName(JpaCriteriaQuery<?> cq, From root) {
        var detachedAssociationCriteriaList = getDetachedAssociationCriteria();

        var aliasMap = createAliasMap(detachedAssociationCriteriaList);
        //The join column is column for joining from the root entity
        var detachedFroms = createDetachedFroms(cq, detachedAssociationCriteriaList);
        Map<String, From> fromsByName = Stream.concat(aliasMap.keySet().stream(), collectJoinColumns().stream())
                .distinct()
                .map(joinColumn -> {
                    var table = detachedFroms.computeIfAbsent(joinColumn, s -> root).join(joinColumn, getJoinType(joinColumn));
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

    private JoinType getJoinType(String joinColumn) {
        return getDetachedCriteriaJoinTypes()
        .entrySet()
        .stream()
        .filter(entry -> entry.getKey().equals(joinColumn))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(JoinType.INNER);
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

    @SuppressWarnings("unchecked")
    private List<Query.Criterion> getDetachedCriteria() {
        return detachedCriteria.getCriteria();
    }

    private List<DetachedAssociationCriteria> getDetachedAssociationCriteria() {
        return getDetachedCriteria()
                .stream()
                .map(new DetachedAssociationFunction())
                .flatMap(List::stream)
                .toList();
    }

    private List<String> collectJoinColumns() {
        return getDetachedCriteriaFetchStrategies()
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().equals(FetchType.EAGER))
                .map(Map.Entry::getKey)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, JoinType> getDetachedCriteriaJoinTypes() {
        return detachedCriteria.getJoinTypes();
    }

    @SuppressWarnings("unchecked")
    private Map<String,FetchType> getDetachedCriteriaFetchStrategies() {
        return detachedCriteria.getFetchStrategies();
    }


}
