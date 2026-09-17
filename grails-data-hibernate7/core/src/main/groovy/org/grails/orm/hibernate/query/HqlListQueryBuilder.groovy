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
package org.grails.orm.hibernate.query

import groovy.transform.CompileStatic

import org.grails.datastore.gorm.finders.DynamicFinder
import org.grails.datastore.mapping.reflect.NameUtils
import org.grails.orm.hibernate.cfg.HibernateMappingContext
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.SortConfig
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty

/**
 * A builder for HQL list queries.
 *
 * @author walterduquedeestrada
 * @author graemerocher
 * @since 7.0.0
 */
@CompileStatic
class HqlListQueryBuilder {

    private final GrailsHibernatePersistentEntity entity
    private final Map<String, Object> params

    HqlListQueryBuilder(GrailsHibernatePersistentEntity entity, Map<String, Object> params) {
        this.entity = entity
        this.params = params != null ? params : Collections.emptyMap()
    }

    String buildListHql() {
        StringBuilder hql = new StringBuilder('from ')
        hql.append(entity.name).append(' e')

        Object fetchObj = params.get(HibernateQueryArgument.FETCH.value())
        if (fetchObj instanceof Map) {
            Map<String, Object> fetchMap = (Map<String, Object>) fetchObj
            for (Map.Entry<String, Object> entry : fetchMap.entrySet()) {
                if (HibernateQueryArgument.JOIN.value().equals(entry.value) || HibernateQueryArgument.EAGER.value().equals(entry.value)) {
                    requireMappedProperty(entry.key, HibernateQueryArgument.FETCH.value())
                    hql.append(' join fetch e.').append(entry.key)
                }
            }
        }

        String sortHql = buildSortClause()
        if (!sortHql.isEmpty()) {
            hql.append(' order by ').append(sortHql)
        }

        return hql.toString()
    }

    String buildCountHql() {
        return "select count(distinct e) from ${entity.name} e".toString()
    }

    private String buildSortClause() {
        Object sort = params.get(HibernateQueryArgument.SORT.value())
        Object order = params.get(HibernateQueryArgument.ORDER.value())
        // checked before looking at the sort key, so an invalid direction is rejected even when
        // there is nothing to sort by rather than being silently ignored
        String orderDirection = DynamicFinder.normalizeDirection(order instanceof String ? (String) order : null)
        Object ignoreCase = params.get(HibernateQueryArgument.IGNORE_CASE.value())
        boolean isIgnoreCase = ignoreCase == null || (ignoreCase instanceof Boolean && (Boolean) ignoreCase)

        if (sort instanceof String) {
            return buildSortPart((String) sort, orderDirection, isIgnoreCase)
        } else if (sort instanceof Map) {
            List<String> parts = []
            for (Map.Entry<String, String> entry : ((Map<String, String>) sort).entrySet()) {
                parts.add(buildSortPart(entry.key, entry.value, isIgnoreCase))
            }
            return parts.join(', ')
        }

        // Default sort from mapping
        HibernateMappingContext mappingContext = (HibernateMappingContext) entity.mappingContext
        Mapping mapping = mappingContext.mappingCacheHolder.getMapping(entity.javaClass)
        if (mapping != null && mapping.getSort() != null) {
            // Mapping.getSort() covariantly overrides the generic Entity<P>#getSort() (returns Object);
            // the JVM's synthetic bridge method confuses the static type checker into inferring Object here.
            SortConfig sortConfig = (SortConfig) mapping.getSort()
            Map<String, String> namesAndDirections = sortConfig.namesAndDirections
            if (namesAndDirections != null && !namesAndDirections.isEmpty()) {
                List<String> parts = []
                for (Map.Entry<String, String> entry : namesAndDirections.entrySet()) {
                    parts.add(buildSortPart(entry.key, entry.value, isIgnoreCase))
                }
                return parts.join(', ')
            }
            String name = sortConfig.name
            if (name != null) {
                return buildSortPart(name, sortConfig.direction, isIgnoreCase)
            }
        }

        return ''
    }

    private String buildSortPart(String propertyName, String direction, boolean ignoreCase) {
        HibernatePersistentProperty prop = requireMappedProperty(propertyName, HibernateQueryArgument.SORT.value())
        String normalizedDirection = DynamicFinder.normalizeDirection(direction)
        String path = "e.${propertyName}".toString()
        if (prop.type == String && ignoreCase) {
            return "upper(${path}) ${normalizedDirection}".toString()
        }
        return "${path} ${normalizedDirection}".toString()
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
            throw new IllegalArgumentException("Invalid ${argument} property".toString())
        }
        HibernatePersistentProperty prop = entity.getHibernatePropertyByPath(propertyPath)
        if (prop == null) {
            throw new IllegalArgumentException("Invalid ${argument} property".toString())
        }
        return prop
    }

    static boolean isPaged(Map<String, Object> params) {
        if (params == null) {
            return false
        }
        return params.containsKey(HibernateQueryArgument.MAX.value()) || params.containsKey(HibernateQueryArgument.OFFSET.value())
    }

}
