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

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria;
import org.grails.datastore.mapping.query.Query;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;

/**
 * Handles association criteria navigation and nested join criteria.
 *
 * @since 8.0.1
 */
public class AssociationCriterionHandler {

    public static Predicate handleDetachedAssociationCriteria(CriterionContext context, DetachedAssociationCriteria<?> associationCriteria) {
        String associationName = associationCriteria.getAssociationPath();
        JpaQueryContext queryContext = context.getQueryContext();
        From<?, ?> associationRoot = queryContext.getFrom(associationName);
        if (associationRoot == null) {
            Expression<?> expr = queryContext.getFullyQualifiedExpression(associationName);
            if (expr instanceof From<?, ?> from) {
                associationRoot = from;
            } else {
                associationRoot = queryContext.getRoot().join(associationName);
                queryContext.addFrom(associationName, associationRoot);
            }
        }

        JpaQueryContext nestedContext = new JpaQueryContext(queryContext, null, associationRoot);
        GrailsHibernatePersistentEntity associatedEntity = (GrailsHibernatePersistentEntity) associationCriteria.getAssociation().getAssociatedEntity();
        List<Query.Criterion> criteriaList = associationCriteria.getCriteria();
        CriterionContext nestedCriterionContext = context.withRoot(associationRoot, nestedContext, associatedEntity);
        return context.getCriteriaBuilder().and(nestedCriterionContext.getPredicates(criteriaList));
    }

    public static Predicate handleHibernateAssociationQuery(CriterionContext context, HibernateAssociationQuery associationQuery) {
        String associationName = associationQuery.associationPath;
        JpaQueryContext queryContext = context.getQueryContext();
        From<?, ?> associationRoot = queryContext.getFrom(associationName);
        if (associationRoot == null) {
            Expression<?> expr = queryContext.getFullyQualifiedExpression(associationName);
            if (expr instanceof From<?, ?> from) {
                associationRoot = from;
            } else {
                associationRoot = queryContext.getRoot().join(associationName, JoinType.INNER);
                queryContext.addFrom(associationName, associationRoot);
            }
        }

        JpaQueryContext nestedContext = new JpaQueryContext(queryContext, null, associationRoot);
        GrailsHibernatePersistentEntity associatedEntity = associationQuery.getEntity();
        List<Query.Criterion> criteriaList = associationQuery.getAssociationCriteria();
        CriterionContext nestedCriterionContext = context.withRoot(associationRoot, nestedContext, associatedEntity);
        return context.getCriteriaBuilder().and(nestedCriterionContext.getPredicates(criteriaList));
    }
}
