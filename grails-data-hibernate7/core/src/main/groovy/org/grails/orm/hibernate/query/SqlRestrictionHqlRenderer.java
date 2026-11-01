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

import java.util.List;

import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.jpa.JpaQueryBuilder;

/**
 * Writes a {@link SqlRestriction} into the HQL of a bulk {@code delete} or {@code update}, as a call of the
 * {@value GrailsSqlRestrictionFunction#NAME} function. The values of the {@code ?} placeholders become named
 * parameters, and a column of the entity follows the SQL when it contains {@code {alias}}.
 *
 * @since 8.0.1
 */
public class SqlRestrictionHqlRenderer implements JpaQueryBuilder.HqlCriterionRenderer {

    @Override
    public int render(Query.Criterion criterion, PersistentEntity entity, String logicalName, StringBuilder whereClause,
                      int position, List parameters) {
        SqlRestriction restriction = (SqlRestriction) criterion;
        whereClause.append(GrailsSqlRestrictionFunction.NAME).append("('")
                .append(restriction.sql().replace("'", "''")).append('\'');
        if (restriction.sql().contains(GrailsSqlRestrictionFunction.ALIAS_PLACEHOLDER)) {
            whereClause.append(", ").append(aliasColumn(logicalName, entity));
        }
        for (Object value : restriction.values()) {
            whereClause.append(", :").append(JpaQueryBuilder.PARAMETER_NAME_PREFIX).append(++position);
            parameters.add(value);
        }
        whereClause.append(')');
        return position;
    }

    private static String aliasColumn(String logicalName, PersistentEntity entity) {
        String path = logicalName;
        PersistentEntity current = entity;
        while (true) {
            PersistentProperty<?> identity = current.getIdentity();
            if (identity == null) {
                PersistentProperty<?>[] composite = current.getCompositeIdentity();
                identity = composite == null || composite.length == 0 ? null : composite[0];
            }
            if (identity == null) {
                throw new ConfigurationException("Cannot use {alias} in native SQL on class [" +
                        entity.getJavaClass().getName() + "] without an identifier");
            }
            path = path + '.' + identity.getName();
            if (!(identity instanceof Association<?> association) || association.getAssociatedEntity() == null) {
                return path;
            }
            current = association.getAssociatedEntity();
        }
    }
}
