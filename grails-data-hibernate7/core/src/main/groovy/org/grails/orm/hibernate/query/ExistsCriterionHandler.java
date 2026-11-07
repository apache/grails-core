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

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;

import org.hibernate.query.criteria.JpaSubQuery;

import grails.gorm.DetachedCriteria;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.api.QueryableCriteria;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;

/**
 * Handles {@link Query.Exists} subquery criteria.
 *
 * @since 8.0.1
 */
class ExistsCriterionHandler implements InternalCriterionHandler<Query.Exists> {

    @Override
    @SuppressWarnings("unchecked")
    public Predicate handle(CriterionContext context, Query.Exists exists) {
        QueryableCriteria<?> subqueryCriteria = exists.getSubquery().materializeLazyCriteria();
        GrailsHibernatePersistentEntity subqueryEntity = (GrailsHibernatePersistentEntity) subqueryCriteria.getPersistentEntity();
        Subquery<?> subquery = context.getCriteriaQuery().subquery(subqueryEntity.getJavaClass());
        var creator = new JpaCriteriaQueryCreator(
                new Query.ProjectionList(),
                context.getCriteriaBuilder(),
                subqueryEntity,
                (DetachedCriteria) subqueryCriteria,
                context.getConversionService());
        creator.setParentContext(context.getQueryContext());
        creator.populateSubquery((JpaSubQuery) subquery);
        return context.getCriteriaBuilder().exists(subquery);
    }
}
