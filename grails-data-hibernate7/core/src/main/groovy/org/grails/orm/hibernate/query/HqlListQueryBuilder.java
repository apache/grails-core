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
import java.util.List;
import java.util.Map;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Embedded;
import org.grails.orm.hibernate.cfg.HibernateMappingContext;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.MappingCacheHolder;

/**
 * Translates a GORM query-argument map into an HQL string for {@code list()}.
 *
 * <p>Handles {@code fetch} (JOIN FETCH), {@code sort} / {@code order} / {@code ignoreCase}
 * (ORDER BY), and default sort from the entity's {@link Mapping}. The resulting HQL is a plain
 * string so it passes through {@link HqlQueryContext} without GString interpolation.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class HqlListQueryBuilder {

    private final PersistentEntity entity;
    private final Map<?, ?> params;

    public HqlListQueryBuilder(PersistentEntity entity, Map<?, ?> params) {
        this.entity = entity;
        this.params = params;
    }

    /** Builds the SELECT HQL for the list query (no count). */
    public String buildListHql() {
        String alias = "e";
        StringBuilder hql =
                new StringBuilder("from ").append(entity.getName()).append(" ").append(alias);
        appendJoinFetch(hql, alias);
        appendOrderBy(hql, alias);
        return hql.toString();
    }

    /** Builds the scalar count HQL for {@link PagedResultList}. */
    String buildCountHql() {
        return "select count(distinct e) from " + entity.getName() + " e";
    }

    // ─── JOIN FETCH ──────────────────────────────────────────────────────────

    private void appendJoinFetch(StringBuilder hql, String alias) {
        Object fetchObj = params.get(HibernateQueryArgument.FETCH.value());
        if (!(fetchObj instanceof Map<?, ?> fetchMap)) return;
        for (Object key : fetchMap.keySet()) {
            String assocName = key.toString();
            Object mode = fetchMap.get(key);
            if (isJoinFetch(mode)) {
                hql.append(" join fetch ").append(alias).append(".").append(assocName);
            }
        }
    }

    private static boolean isJoinFetch(Object mode) {
        if (mode == null) return false;
        String s = mode.toString();
        return s.equalsIgnoreCase(HibernateQueryArgument.JOIN.value())
                || s.equalsIgnoreCase(HibernateQueryArgument.EAGER.value());
    }

    // ─── ORDER BY ────────────────────────────────────────────────────────────

    private void appendOrderBy(StringBuilder hql, String alias) {
        List<String> clauses = new ArrayList<>();
        Object sortObj = params.get(HibernateQueryArgument.SORT.value());
        boolean ignoreCase = isIgnoreCase();

        if (sortObj instanceof Map<?, ?> sortMap) {
            for (Object sortKey : sortMap.keySet()) {
                String prop = sortKey.toString();
                String dir = direction((String) sortMap.get(sortKey));
                clauses.add(orderClause(alias, prop, dir, ignoreCase && isStringProp(prop)));
            }
        } else if (sortObj instanceof String sort) {
            String dir = direction((String) params.get(HibernateQueryArgument.ORDER.value()));
            clauses.add(orderClause(alias, sort, dir, ignoreCase && isStringProp(sort)));
        } else {
            // fall back to default mapping sort
            if (entity.getMappingContext() instanceof HibernateMappingContext hmc) {
                Mapping m = hmc.getMappingCacheHolder().getMapping(entity.getJavaClass());
                if (m != null) {
                    ((Map<?, ?>) m.getSort().getNamesAndDirections())
                            .forEach((prop, dir) -> clauses.add(orderClause(
                                    alias, (String) prop, direction((String) dir), isStringProp((String) prop))));
                }
            }
        }

        if (!clauses.isEmpty()) {
            hql.append(" order by ").append(String.join(", ", clauses));
        }
    }

    private String orderClause(String alias, String prop, String dir, boolean upper) {
        String path = alias + "." + prop;
        return upper ? "upper(" + path + ") " + dir : path + " " + dir;
    }

    private static String direction(String raw) {
        return HibernateQueryArgument.ORDER_DESC.value().equalsIgnoreCase(raw)
                ? HibernateQueryArgument.ORDER_DESC.value()
                : HibernateQueryArgument.ORDER_ASC.value();
    }

    private boolean isIgnoreCase() {
        Object ic = params.get(HibernateQueryArgument.IGNORE_CASE.value());
        return !(ic instanceof Boolean b) || b;
    }

    private boolean isStringProp(String name) {
        // handle nested path: only check the leaf property's type
        int dot = name.lastIndexOf('.');
        String leaf = dot == -1 ? name : name.substring(dot + 1);
        String head = dot == -1 ? null : name.substring(0, dot);
        PersistentEntity owner = resolveOwner(head);
        if (owner == null) return false;
        PersistentProperty<?> prop = owner.getPropertyByName(leaf);
        return prop != null && prop.getType() == String.class;
    }

    private PersistentEntity resolveOwner(String path) {
        if (path == null) return entity;
        PersistentProperty<?> prop = entity.getPropertyByName(path);
        if (prop instanceof Embedded<?> emb) return emb.getAssociatedEntity();
        if (prop instanceof Association<?> assoc) return assoc.getAssociatedEntity();
        return null;
    }

    /** Returns true when the params indicate a paged query (i.e. {@code max} is set). */
    @SuppressWarnings("rawtypes")
    static boolean isPaged(Map params) {
        return params.containsKey(HibernateQueryArgument.MAX.value());
    }
}
