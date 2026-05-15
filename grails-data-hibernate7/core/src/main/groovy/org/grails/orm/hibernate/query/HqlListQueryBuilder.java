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
import java.util.List;
import java.util.Map;

import org.grails.orm.hibernate.cfg.HibernateMappingContext;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.SortConfig;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;

/**
 * A builder for HQL list queries.
 *
 * @author walterduquedeestrada
 * @author graemerocher
 * @since 7.0.0
 */
//TODO Cleanup
public class HqlListQueryBuilder {

    private final GrailsHibernatePersistentEntity entity;
    private final Map<String, Object> params;

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
                    hql.append(" join fetch e.").append(prop);
                }
            });
        }

        String sortHql = buildSortClause();
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
        Object ignoreCase = params.get(HibernateQueryArgument.IGNORE_CASE.value());
        boolean isIgnoreCase = ignoreCase == null || (ignoreCase instanceof Boolean && (Boolean) ignoreCase);

        if (sort instanceof String) {
            return buildSortPart((String) sort, order instanceof String ? (String) order : "asc", isIgnoreCase);
        } else if (sort instanceof Map) {
            List<String> parts = new ArrayList<>();
            ((Map<String, String>) sort).forEach((prop, direction) -> {
                parts.add(buildSortPart(prop, direction, isIgnoreCase));
            });
            return String.join(", ", parts);
        } else if (sort instanceof java.util.List) {
            List<String> parts = new ArrayList<>();
            for (Object prop : (java.util.List) sort) {
                parts.add(buildSortPart(prop.toString(), order instanceof String ? (String) order : "asc", isIgnoreCase));
            }
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

        // If no sort but order is present, default to identity
        if (order != null) {
            org.grails.datastore.mapping.model.PersistentProperty identity = entity.getIdentity();
            if (identity != null) {
                return buildSortPart(identity.getName(), order instanceof String ? (String) order : "asc", isIgnoreCase);
            }
            org.grails.datastore.mapping.model.PersistentProperty[] compositeId = entity.getCompositeIdentity();
            if (compositeId != null && compositeId.length > 0) {
                List<String> parts = new ArrayList<>();
                for (org.grails.datastore.mapping.model.PersistentProperty prop : compositeId) {
                    parts.add(buildSortPart(prop.getName(), order instanceof String ? (String) order : "asc", isIgnoreCase));
                }
                return String.join(", ", parts);
            }
        }

        return "";
    }

    private String buildSortPart(String propertyName, String direction, boolean ignoreCase) {
        if (propertyName == null) return "";
        String path = "e." + propertyName;
        HibernatePersistentProperty prop = entity.getHibernatePropertyByPath(propertyName);
        if (prop != null && prop.getType() == String.class && ignoreCase) {
            return "upper(" + path + ") " + (direction != null ? direction : "asc");
        }
        return path + " " + (direction != null ? direction : "asc");
    }

    public static boolean isPaged(Map<String, Object> params) {
        if (params == null) return false;
        return params.containsKey(HibernateQueryArgument.MAX.value()) || params.containsKey(HibernateQueryArgument.OFFSET.value());
    }
}
