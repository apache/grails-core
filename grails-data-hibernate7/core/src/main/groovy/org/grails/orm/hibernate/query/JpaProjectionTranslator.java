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
package org.grails.orm.hibernate.query;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import org.grails.datastore.mapping.query.Query;
import org.hibernate.query.criteria.JpaExpression;

/**
 * A class that translates GORM projections to JPA expressions.
 *
 * @author burt
 * @author graemerocher
 * @since 7.0.0
 */
public class JpaProjectionTranslator {

    private final CriteriaBuilder criteriaBuilder;
    private final JpaQueryContext context;

    public JpaProjectionTranslator(CriteriaBuilder criteriaBuilder, JpaQueryContext context) {
        this.criteriaBuilder = criteriaBuilder;
        this.context = context;
    }

    @SuppressWarnings("unchecked")
    public JpaExpression<?> translate(Query.Projection projection) {
        JpaExpression<?> jpaExpression;
        String propertyName = null;
        String alias = null;

        if (projection instanceof Hibernate7CountProjection countProjection) {
            propertyName = countProjection.getPropertyName();
        } else if (projection instanceof Query.GroupPropertyProjection groupPropertyProjection) {
            propertyName = groupPropertyProjection.getPropertyName();
        } else if (projection instanceof Query.PropertyProjection propertyProjection) {
            propertyName = propertyProjection.getPropertyName();
        } else if (projection instanceof Query.CountDistinctProjection countDistinctProjection) {
            propertyName = countDistinctProjection.getPropertyName();
        }

        if (propertyName != null && propertyName.contains(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)) {
            String[] parts = propertyName.split(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR);
            alias = parts[0];
            propertyName = parts[1];
        }

        if (projection instanceof Query.CountProjection) {
            Expression<?> pathExpr;
            if (propertyName != null) {
                pathExpr = context.getAliasedExpression(propertyName);
                if (pathExpr == null) {
                    pathExpr = context.getFullyQualifiedExpression("root." + propertyName);
                }
            } else {
                pathExpr = context.getRoot();
            }
            jpaExpression = (JpaExpression<?>) criteriaBuilder.count(pathExpr);
        } else if (projection instanceof Query.CountDistinctProjection) {
            Expression<?> pathExpr = context.getAliasedExpression(propertyName);
            if (pathExpr == null) {
                pathExpr = context.getFullyQualifiedExpression("root." + propertyName);
            }
            jpaExpression = (JpaExpression<?>) criteriaBuilder.countDistinct(pathExpr);
        } else if (projection instanceof Query.IdProjection) {
            jpaExpression = (JpaExpression<?>) context.getFullyQualifiedPath("root.id");
        } else if (projection instanceof Query.DistinctPropertyProjection distinctPropertyProjection) {
            return translate(org.grails.datastore.mapping.query.Projections.property(distinctPropertyProjection.getPropertyName()));
        } else if (projection instanceof Query.DistinctProjection) {
            return null;
        } else if (projection instanceof Query.PropertyProjection) {
            Expression<?> expression = context.getFullyQualifiedExpression(propertyName);

            if (projection instanceof Query.MaxProjection) {
                jpaExpression = (JpaExpression<?>) criteriaBuilder.max((Expression<? extends Number>) expression);
            } else if (projection instanceof Query.MinProjection) {
                jpaExpression = (JpaExpression<?>) criteriaBuilder.min((Expression<? extends Number>) expression);
            } else if (projection instanceof Query.AvgProjection) {
                jpaExpression = (JpaExpression<?>) criteriaBuilder.avg((Expression<? extends Number>) expression);
            } else if (projection instanceof Query.SumProjection) {
                jpaExpression = (JpaExpression<?>) criteriaBuilder.sum((Expression<? extends Number>) expression);
            } else {
                jpaExpression = (JpaExpression<?>) expression;
            }
        } else {
            throw new UnsupportedOperationException("Unsupported projection: " + projection.getClass().getName());
        }

        if (alias != null && jpaExpression != null) {
            jpaExpression.alias(alias);
            context.registerAlias(alias, jpaExpression);
        }
        return jpaExpression;
    }
}
