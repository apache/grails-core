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
        }

        // Default sort from mapping
        HibernateMappingContext mappingContext = (HibernateMappingContext) entity.getMappingContext();
        Mapping mapping = mappingContext.getMappingCacheHolder().getMapping(entity.getJavaClass());
        if (mapping != null && mapping.getSort() != null) {
            SortConfig sortConfig = mapping.getSort();
            return buildSortPart(sortConfig.getName(), sortConfig.getDirection(), isIgnoreCase);
        }

        return "";
    }

    private String buildSortPart(String propertyName, String direction, boolean ignoreCase) {
        String path = "e." + propertyName;
        HibernatePersistentProperty prop = getProperty(entity, propertyName);
        if (prop != null && prop.getType() == String.class && ignoreCase) {
            return "upper(" + path + ") " + (direction != null ? direction : "asc");
        }
        return path + " " + (direction != null ? direction : "asc");
    }

    private HibernatePersistentProperty getProperty(GrailsHibernatePersistentEntity entity, String propertyName) {
        if (propertyName.contains(".")) {
            String[] parts = propertyName.split("\\.");
            HibernatePersistentProperty prop = entity.getHibernatePropertyByName(parts[0]);
            if (prop != null && prop.getHibernateAssociatedEntity() != null) {
                return getProperty(prop.getHibernateAssociatedEntity(), propertyName.substring(parts[0].length() + 1));
            }
            return null;
        }
        return entity.getHibernatePropertyByName(propertyName);
    }

    public static boolean isPaged(Map<String, Object> params) {
        if (params == null) return false;
        return params.containsKey(HibernateQueryArgument.MAX.value()) || params.containsKey(HibernateQueryArgument.OFFSET.value());
    }
}
