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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.grails.datastore.gorm.finders.DynamicFinder;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Embedded;
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

    public HqlListQueryBuilder(GrailsHibernatePersistentEntity entity, Map<String, Object> params) {
        this.entity = entity;
        this.params = params != null ? params : Collections.emptyMap();
    }

    public String buildListHql() {
        StringBuilder hql = new StringBuilder("from ");
        hql.append(entity.getName()).append(" e");

        Object fetchObj = params.get(HibernateQueryArgument.FETCH.value());
        if (fetchObj instanceof Map) {
            Map<String, Object> fetchMap = (Map<String, Object>) fetchObj;
            fetchMap.forEach((prop, type) -> {
                if (HibernateQueryArgument.JOIN.value().equals(type) || HibernateQueryArgument.EAGER.value().equals(type)) {
                    requireMappedProperty(prop, HibernateQueryArgument.FETCH.value());
                    fetchJoins.putIfAbsent("e." + prop, "");
                }
            });
        }

        // the sort comes first: it gives an alias to a fetch that it sorts through
        String sortHql = buildSortClause();
        fetchJoins.forEach((path, alias) -> {
            hql.append(" join fetch ").append(path);
            if (!alias.isEmpty()) {
                hql.append(' ').append(alias);
            }
        });
        sortJoins.forEach((path, alias) -> hql.append(" left join ").append(path).append(' ').append(alias));
        if (!sortHql.isEmpty()) {
            hql.append(" order by ").append(sortHql);
        }

        return hql.toString();
    }

    public String buildCountHql() {
        return "select count(distinct e) from " + entity.getName() + " e";
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
     * that the mapping does not know is rejected, and a property of an association is read from a left join of
     * the association and not from the path through it, which Hibernate joins with an implicit inner join that
     * leaves out the rows where the association is {@code null}.
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
        for (int i = 0; i < tokens.length - 1; i++) {
            if (!(current.getPropertyByName(tokens[i]) instanceof Association<?> association) ||
                    association.getAssociatedEntity() == null) {
                throw new IllegalArgumentException("Invalid " + argument + " property");
            }
            String joined = alias + '.' + tokens[i];
            if (association instanceof Embedded<?>) {
                // a component has no join of its own: an association inside it is joined through its path
                alias = joined;
            } else if (i == tokens.length - 2 && isForeignKey(association, tokens[i + 1])) {
                // Hibernate reads the foreign key without a join, which keeps the rows where it is null
                alias = joined;
            } else if (fetchJoins.containsKey(joined)) {
                // the query already joins the association to fetch it, and the sort uses that join
                alias = fetchJoins.get(joined);
                if (alias.isEmpty()) {
                    alias = "f" + fetchJoins.values().stream().filter(fetchAlias -> !fetchAlias.isEmpty()).count();
                    fetchJoins.put(joined, alias);
                }
            } else {
                alias = sortJoins.computeIfAbsent(joined, path -> "s" + sortJoins.size());
            }
            current = association.getAssociatedEntity();
        }
        PersistentProperty<?> property = current.getPropertyByName(tokens[tokens.length - 1]);
        if (property == null) {
            throw new IllegalArgumentException("Invalid " + argument + " property");
        }
        String normalizedDirection = DynamicFinder.normalizeDirection(direction);
        String path = alias + '.' + tokens[tokens.length - 1];
        if (property.getType() == String.class && ignoreCase) {
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
