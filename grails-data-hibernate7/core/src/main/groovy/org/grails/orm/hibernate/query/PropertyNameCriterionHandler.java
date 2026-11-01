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

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import org.hibernate.query.criteria.HibernateCriteriaBuilder;

import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.grails.datastore.mapping.query.Query;

/**
 * Handles criteria checking nullness or emptiness of a property.
 *
 * @since 8.0.1
 */
public class PropertyNameCriterionHandler implements InternalCriterionHandler<Query.PropertyNameCriterion> {

    @Override
    public Predicate handle(CriterionContext context, Query.PropertyNameCriterion c) {
        String propertyName = c.getProperty();
        Expression<?> propertyPath = context.resolvePath(propertyName);
        if (propertyPath == null) {
            String className = context.getEntity() != null ? context.getEntity().getJavaClass().getName() : "unknown";
            throw new ConfigurationException("Cannot use property criteria on non-existent property [" + propertyName + "] of class [" + className + "]");
        }
        HibernateCriteriaBuilder criteriaBuilder = context.getCriteriaBuilder();

        if (((Object) c) instanceof Query.IsNull) {
            return criteriaBuilder.isNull(propertyPath);
        } else if (((Object) c) instanceof Query.IsNotNull) {
            return criteriaBuilder.isNotNull(propertyPath);
        } else if (((Object) c) instanceof Query.IsEmpty) {
            return criteriaBuilder.isEmpty(context.resolveCollectionPath(propertyPath));
        } else if (((Object) c) instanceof Query.IsNotEmpty) {
            return criteriaBuilder.isNotEmpty(context.resolveCollectionPath(propertyPath));
        }

        throw new UnsupportedOperationException("Unsupported property name criterion: " + c.getClass().getName());
    }
}
