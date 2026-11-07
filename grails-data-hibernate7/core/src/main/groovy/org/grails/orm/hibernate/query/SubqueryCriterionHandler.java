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
import jakarta.persistence.criteria.Subquery;

import org.hibernate.query.criteria.HibernateCriteriaBuilder;

import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.api.QueryableCriteria;

/**
 * Handles subquery criteria comparing a property to an ALL/SOME/NOT IN subquery.
 *
 * @since 8.0.1
 */
class SubqueryCriterionHandler implements InternalCriterionHandler<Query.SubqueryCriterion> {

    @Override
    @SuppressWarnings("unchecked")
    public Predicate handle(CriterionContext context, Query.SubqueryCriterion c) {
        String propertyName = c.getProperty();
        Expression<?> propertyPath = context.resolvePath(propertyName);
        if (propertyPath == null) {
            String className = context.getEntity() != null ? context.getEntity().getJavaClass().getName() : "unknown";
            throw new ConfigurationException("Cannot use subquery criteria on non-existent property [" + propertyName + "] of class [" + className + "]");
        }
        QueryableCriteria<?> qc = c.getValue();

        HibernateCriteriaBuilder criteriaBuilder = context.getCriteriaBuilder();
        Subquery<?> subquery = PropertyCriterionHandler.buildSubquery(context, propertyPath, qc, propertyName);

        if (c instanceof Query.EqualsAll) {
            return criteriaBuilder.equal(propertyPath, (Expression) criteriaBuilder.all(subquery));
        } else if (c instanceof Query.NotEqualsAll) {
            return criteriaBuilder.notEqual(propertyPath, (Expression) criteriaBuilder.all(subquery));
        } else if (c instanceof Query.GreaterThanAll) {
            return criteriaBuilder.greaterThan((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.all(subquery));
        } else if (c instanceof Query.GreaterThanSome) {
            return criteriaBuilder.greaterThan((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.some(subquery));
        } else if (c instanceof Query.GreaterThanEqualsAll) {
            return criteriaBuilder.greaterThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.all(subquery));
        } else if (c instanceof Query.GreaterThanEqualsSome) {
            return criteriaBuilder.greaterThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.some(subquery));
        } else if (c instanceof Query.LessThanAll) {
            return criteriaBuilder.lessThan((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.all(subquery));
        } else if (c instanceof Query.LessThanSome) {
            return criteriaBuilder.lessThan((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.some(subquery));
        } else if (c instanceof Query.LessThanEqualsAll) {
            return criteriaBuilder.lessThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.all(subquery));
        } else if (c instanceof Query.LessThanEqualsSome) {
            return criteriaBuilder.lessThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.some(subquery));
        } else if (c instanceof Query.NotIn) {
            return criteriaBuilder.not(propertyPath.in(subquery));
        }

        throw new UnsupportedOperationException("Unsupported subquery criterion: " + c.getClass().getName());
    }
}
