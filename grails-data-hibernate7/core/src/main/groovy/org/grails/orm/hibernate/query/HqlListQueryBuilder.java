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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.grails.datastore.gorm.finders.DynamicFinder;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Embedded;
import org.grails.datastore.mapping.model.types.ToMany;
import org.grails.datastore.mapping.reflect.NameUtils;
import org.grails.orm.hibernate.cfg.HibernateMappingContext;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.SortConfig;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;

/**
 * A builder for HQL list queries.
 *
 * @since 8.0
 */
public class HqlListQueryBuilder {

    private final GrailsHibernatePersistentEntity entity;
    private final Map<String, Object> params;
    // the joins of the associations that the query sorts through, by the path they join
    private final Map<String, String> sortJoins = new LinkedHashMap<>();
    // the paths that the query fetches, with the alias of a fetch that the query also sorts through
    private final Map<String, String> fetchJoins = new LinkedHashMap<>();
    // the sort joins that join a collection, which has a row for each element
    private final Set<String> collectionSortJoins = new HashSet<>();
    // the sort joins of the associations after a collection, which the count joins with an inner join in both modes
    private final Set<String> afterCollectionSortJoins = new HashSet<>();
    // whether the query being built is the count, which joins a collection that the list reads by its elements
    private boolean counting;

    public HqlListQueryBuilder(GrailsHibernatePersistentEntity entity, Map<String, Object> params) {
        this.entity = entity;
        this.params = params != null ? params : Collections.emptyMap();
    }

    public String buildListHql() {
        return buildHql(false);
    }

    public String buildCountHql() {
        return buildHql(true);
    }

    private String buildHql(boolean count) {
        sortJoins.clear();
        fetchJoins.clear();
        collectionSortJoins.clear();
        afterCollectionSortJoins.clear();
        counting = count;
        StringBuilder hql = new StringBuilder("from ");
        hql.append(entity.getName()).append(" e");

        Object fetchObj = params.get(HibernateQueryArgument.FETCH.value());
        if (fetchObj instanceof Map) {
            Map<String, Object> fetchMap = (Map<String, Object>) fetchObj;
            fetchMap.forEach((prop, type) -> {
                if (HibernateQueryArgument.JOIN.value().equals(type) || HibernateQueryArgument.EAGER.value().equals(type)) {
                    requireMappedProperty(prop, HibernateQueryArgument.FETCH.value());
                    // the fetch mode join never leaves out an instance whose association is null or empty, as it does for a
                    // where query and a dynamic finder; only a join type asks for that
                    fetchJoins.putIfAbsent("e." + prop, "");
                }
            });
        }

        // the sort comes first: it gives an alias to a fetch that it sorts through
        String sortHql = buildSortClause();
        fetchJoins.forEach((path, alias) -> {
            hql.append(count ? " left join " : " left join fetch ").append(path);
            if (!alias.isEmpty()) {
                hql.append(' ').append(alias);
            }
        });
        // with the leftJoinAssociationSort setting, a left join, which keeps the rows where the association is null,
        // as with Hibernate 5. A collection that only the count joins, and an association after it, are joined with
        // an inner join in both modes, as the list reads them through the elements of the collection
        String sortJoin = ((HibernateMappingContext) entity.getMappingContext()).isLeftJoinAssociationSort() ? " left join " : " join ";
        sortJoins.forEach((path, alias) ->
                hql.append(collectionSortJoins.contains(path) || afterCollectionSortJoins.contains(path) ? " join " : sortJoin)
                        .append(path).append(' ').append(alias));
        if (count) {
            // The list has a row for each element of a collection that it sorts by, and max and offset page
            // through those rows, so the total counts them. A list that fetches a collection has each instance
            // once, so its total counts instances; a fetch of a single instance leaves the rows as they are.
            hql.insert(0, !collectionSortJoins.isEmpty() && !fetchesCollection() ? "select count(e) " : "select count(distinct e) ");
        } else if (!sortHql.isEmpty()) {
            hql.append(" order by ").append(sortHql);
        }

        return hql.toString();
    }

    /**
     * Whether a fetch of the list reads a collection, at any point of its path.
     */
    private boolean fetchesCollection() {
        for (String path : fetchJoins.keySet()) {
            PersistentEntity current = entity;
            // a fetched path starts with the alias of the entity
            for (String name : path.substring(path.indexOf('.') + 1).split("\\.")) {
                PersistentProperty<?> property = current == null ? null : current.getPropertyByName(name);
                if (property instanceof ToMany<?>) {
                    return true;
                }
                current = property instanceof Association<?> association ? association.getAssociatedEntity() : null;
            }
        }
        return false;
    }

    private String buildSortClause() {
        Object sort = params.get(HibernateQueryArgument.SORT.value());
        Object order = params.get(HibernateQueryArgument.ORDER.value());
        // checked before looking at the sort key, so an invalid direction is rejected even when
        // there is nothing to sort by rather than being silently ignored
        String orderDirection = DynamicFinder.normalizeDirection(order instanceof String ? (String) order : null);
        Object ignoreCase = params.get(HibernateQueryArgument.IGNORE_CASE.value());
        boolean isIgnoreCase = ignoreCase == null || (ignoreCase instanceof Boolean && (Boolean) ignoreCase);

        if (sort instanceof String) {
            return buildSortPart((String) sort, orderDirection, isIgnoreCase);
        } else if (sort instanceof Map) {
            List<String> parts = new ArrayList<>();
            ((Map<String, String>) sort).forEach((prop, direction) -> {
                parts.add(buildSortPart(prop, direction, isIgnoreCase));
            });
            return String.join(", ", parts);
        }

        // Default sort from mapping
        HibernateMappingContext mappingContext = (HibernateMappingContext) entity.getMappingContext();
        Mapping mapping = mappingContext.getMappingCacheHolder().getMapping(entity.getJavaClass());
        if (mapping != null && mapping.getSort() != null) {
            SortConfig sortConfig = mapping.getSort();
            Map<String, String> namesAndDirections = sortConfig.getNamesAndDirections();
            if (namesAndDirections != null && !namesAndDirections.isEmpty()) {
                List<String> parts = new ArrayList<>();
                namesAndDirections.forEach((prop, direction) -> {
                    parts.add(buildSortPart(prop, direction, isIgnoreCase));
                });
                return String.join(", ", parts);
            }
            String name = sortConfig.getName();
            if (name != null) {
                return buildSortPart(name, sortConfig.getDirection(), isIgnoreCase);
            }
        }

        return "";
    }

    /**
     * Builds one part of the order clause from a caller-supplied property path. The path is resolved against the
     * mapping before it is interpolated into HQL, in the one walk that also decides where it is read from: a path
     * that the mapping does not know is rejected, and a property of an association is read from a join of the
     * association that the query makes for the sort, or from the join of a fetch of it. The join is a left join,
     * which keeps the rows where the association is {@code null}, with the {@code grails.gorm.leftJoinAssociationSort}
     * setting, and an inner join without it.
     * <p>
     * A collection is read with an inner join in both modes. The list reads its elements through the path, with
     * {@code element()}, which Hibernate joins with an inner join and which lists an instance once for each element,
     * and it reads the rest of the path through the elements, which Hibernate joins with inner joins as well. The
     * count joins the collection and every association after it with an inner join, so that it counts the rows that
     * the list pages through.
     *
     * @param propertyName The property path
     * @param direction The direction of the sort
     * @param ignoreCase Whether a string is sorted without regard to its case
     * @return The order part
     */
    private String buildSortPart(String propertyName, String direction, boolean ignoreCase) {
        String argument = HibernateQueryArgument.SORT.value();
        if (!NameUtils.isValidPropertyPath(propertyName)) {
            throw new IllegalArgumentException("Invalid " + argument + " property");
        }
        String[] tokens = propertyName.split("\\.");
        PersistentEntity current = entity;
        String alias = "e";
        // whether the list reads the rest of the path through the elements of a collection
        boolean throughElements = false;
        // whether the count has joined a collection of the path, after which it joins with inner joins
        boolean afterCollection = false;
        for (int i = 0; i < tokens.length - 1; i++) {
            if (!(current.getPropertyByName(tokens[i]) instanceof Association<?> association) ||
                    association.getAssociatedEntity() == null) {
                throw new IllegalArgumentException("Invalid " + argument + " property");
            }
            String joined = alias + '.' + tokens[i];
            if (association instanceof Embedded<?>) {
                // a path through a component is read as it is
                PersistentEntity component = association.getAssociatedEntity();
                PersistentProperty<?> componentProperty = null;
                for (int j = i + 1; j < tokens.length; j++) {
                    componentProperty = component == null ? null : component.getPropertyByName(tokens[j]);
                    if (componentProperty == null) {
                        throw new IllegalArgumentException("Invalid " + argument + " property");
                    }
                    component = componentProperty instanceof Association<?> inner ? inner.getAssociatedEntity() : null;
                }
                String componentPath = joined + '.' + String.join(".", Arrays.copyOfRange(tokens, i + 1, tokens.length));
                return orderPart(componentPath, componentProperty.getType(), direction, ignoreCase);
            } else if (i == tokens.length - 2 && isForeignKey(association, tokens[i + 1])) {
                // Hibernate reads the foreign key without a join, which keeps the rows where it is null
                alias = joined;
            } else if (throughElements) {
                // the rest of a path through the elements of a collection is read through them, with the elements of
                // a further collection, as a join of the query would make Hibernate list an instance once
                alias = association instanceof ToMany<?> ? "element(" + joined + ")" : joined;
            } else if (fetchJoins.containsKey(joined)) {
                // the query already joins the association to fetch it, and the sort uses that join
                alias = fetchJoins.get(joined);
                if (alias.isEmpty()) {
                    alias = "f" + fetchJoins.values().stream().filter(fetchAlias -> !fetchAlias.isEmpty()).count();
                    fetchJoins.put(joined, alias);
                }
            } else if (association instanceof ToMany<?> && !counting) {
                // The elements of a collection are read without a join of the query, also with the
                // leftJoinAssociationSort setting: Hibernate joins them with an inner join and lists an instance once
                // for each element, as list does with Hibernate 5, where a join of the query would make it list the
                // instance once
                collectionSortJoins.add(joined);
                alias = "element(" + joined + ")";
                throughElements = true;
            } else {
                alias = sortJoins.computeIfAbsent(joined, path -> "s" + sortJoins.size());
                if (association instanceof ToMany<?>) {
                    collectionSortJoins.add(joined);
                    afterCollection = true;
                } else if (afterCollection) {
                    afterCollectionSortJoins.add(joined);
                }
            }
            current = association.getAssociatedEntity();
        }
        PersistentProperty<?> property = current.getPropertyByName(tokens[tokens.length - 1]);
        if (property == null) {
            throw new IllegalArgumentException("Invalid " + argument + " property");
        }
        return orderPart(alias + '.' + tokens[tokens.length - 1], property.getType(), direction, ignoreCase);
    }

    private static String orderPart(String path, Class<?> type, String direction, boolean ignoreCase) {
        String normalizedDirection = DynamicFinder.normalizeDirection(direction);
        if (type == String.class && ignoreCase) {
            return "upper(" + path + ") " + normalizedDirection;
        }
        return path + " " + normalizedDirection;
    }

    /**
     * Whether a property of the entity that an association refers to is the foreign key of the association: the
     * association is mapped as a many-to-one, which has its key in the table of the entity that declares it, and
     * the property is the identifier that the key holds. An association with its key in the other table, as
     * {@code hasOne} declares, has no such column.
     *
     * @param association the association
     * @param propertyName the name of a property of the associated entity
     * @return whether a path through the association to the property is read from the foreign key column
     */
    static boolean isForeignKey(Association<?> association, String propertyName) {
        if (!(association instanceof HibernatePersistentProperty mapped && mapped.isValidHibernateManyToOne())) {
            return false;
        }
        PersistentEntity associated = association.getAssociatedEntity();
        PersistentProperty<?> identity = associated != null ? associated.getIdentity() : null;
        return identity != null && propertyName.equals(identity.getName());
    }

    /**
     * Resolves a caller-supplied property path against the mapping before it is interpolated into
     * HQL. Blank and malformed paths are rejected together with paths that do not resolve to a
     * mapped property. The message deliberately omits the value, which usually originates from
     * request parameters.
     *
     * @param propertyPath The property path taken from the query arguments
     * @param argument The name of the query argument the path came from, for the error message
     * @return The mapped property
     * @throws IllegalArgumentException if the path is malformed or does not resolve
     */
    private HibernatePersistentProperty requireMappedProperty(String propertyPath, String argument) {
        if (!NameUtils.isValidPropertyPath(propertyPath)) {
            throw new IllegalArgumentException("Invalid " + argument + " property");
        }
        HibernatePersistentProperty prop = entity.getHibernatePropertyByPath(propertyPath);
        if (prop == null) {
            throw new IllegalArgumentException("Invalid " + argument + " property");
        }
        return prop;
    }

    public static boolean isPaged(Map<String, Object> params) {
        if (params == null) return false;
        return params.containsKey(HibernateQueryArgument.MAX.value()) || params.containsKey(HibernateQueryArgument.OFFSET.value());
    }
}
