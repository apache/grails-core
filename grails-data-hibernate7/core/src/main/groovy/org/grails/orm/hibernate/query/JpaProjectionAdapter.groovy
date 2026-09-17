/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.orm.hibernate.query

import groovy.transform.CompileStatic
import jakarta.persistence.Tuple
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Selection
import jakarta.persistence.criteria.Subquery
import org.hibernate.query.criteria.HibernateCriteriaBuilder

import org.grails.datastore.mapping.query.Query

/**
 * Adapts Grails datastore projections to JPA Selections.
 *
 * @author walterduquedeestrada
 * @since 7.0.0
 */
@CompileStatic
class JpaProjectionAdapter {

    private final HibernateCriteriaBuilder criteriaBuilder
    private final JpaQueryContext context
    private final JpaProjectionTranslator translator

    JpaProjectionAdapter(HibernateCriteriaBuilder criteriaBuilder, JpaQueryContext context) {
        this.criteriaBuilder = criteriaBuilder
        this.context = context
        this.translator = new JpaProjectionTranslator(criteriaBuilder, context)
    }

    void adapt(Query.ProjectionList projectionList, jakarta.persistence.criteria.AbstractQuery<?> query) {
        List<Query.Projection> projections = projectionList.projectionList
        boolean distinct = false
        for (Query.Projection p : projections) {
            if (p instanceof Query.DistinctProjection || p instanceof Query.DistinctPropertyProjection) {
                distinct = true
                break
            }
        }
        if (distinct) {
            query.distinct(true)
        }

        List<Selection<?>> selections = []
        int i = 0
        for (Query.Projection p : projections) {
            Selection<?> selection = translator.translate(p)
            if (selection != null) {
                if (query instanceof Subquery && selection.alias == null) {
                    selection.alias("col_${i++}".toString())
                }
                selections.add(selection)
            }
        }

        if (!selections.isEmpty()) {
            if (query instanceof Subquery) {
                Subquery subquery = (Subquery) query
                // JPA Subquery must return a single Expression, not a Tuple
                subquery.select((Expression) selections.get(0))
            } else if (query instanceof CriteriaQuery) {
                CriteriaQuery criteriaQuery = (CriteriaQuery) query
                if (criteriaQuery.resultType.equals(Tuple) && selections.size() > 1) {
                    criteriaQuery.select(criteriaBuilder.tuple(selections.toArray(new Selection<?>[0])))
                } else {
                    criteriaQuery.select((Selection) selections.get(0))
                }
            }
        } else {
            Selection<?> selection = (Selection<?>) context.root
            if (query instanceof Subquery) {
                Subquery subquery = (Subquery) query
                if (selection.alias == null) {
                    selection.alias('root_alias')
                }
                subquery.select((Expression) selection)
            } else if (query instanceof CriteriaQuery) {
                CriteriaQuery criteriaQuery = (CriteriaQuery) query
                criteriaQuery.select((Selection) selection)
            }
        }
    }

}
