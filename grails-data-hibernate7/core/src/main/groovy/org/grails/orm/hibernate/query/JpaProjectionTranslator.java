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

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;

import org.hibernate.metamodel.model.domain.ReturnableType;
import org.hibernate.query.criteria.JpaExpression;
import org.hibernate.query.sqm.NodeBuilder;
import org.hibernate.query.sqm.function.SqmFunctionDescriptor;
import org.hibernate.query.sqm.tree.SqmTypedNode;
import org.hibernate.type.BasicType;
import org.hibernate.type.BasicTypeReference;

import org.grails.datastore.mapping.query.Query;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;

/**
 * A class that translates GORM projections to JPA expressions.
 *
 * @since 8.0
 */
public class JpaProjectionTranslator {

    private final CriteriaBuilder criteriaBuilder;
    private final JpaQueryContext context;
    private final GrailsHibernatePersistentEntity entity;

    public JpaProjectionTranslator(CriteriaBuilder criteriaBuilder, JpaQueryContext context) {
        this(criteriaBuilder, context, null);
    }

    /**
     * @param entity the queried entity, whose table alias replaces {@code {alias}} in a SQL projection
     */
    public JpaProjectionTranslator(
            CriteriaBuilder criteriaBuilder, JpaQueryContext context, GrailsHibernatePersistentEntity entity) {
        this.criteriaBuilder = criteriaBuilder;
        this.context = context;
        this.entity = entity;
    }

    /**
     * Translates the SQL an expression of a {@link SqlProjection} or {@link SqlGroupProjection} renders as is.
     *
     * @param sql the SQL
     * @param type the type of its value: an {@code org.hibernate.type.StandardBasicTypes} constant or an
     *     {@code org.hibernate.type.Type}, which also tell how the value is read, a Java class or {@code null}
     * @return the expression
     */
    public JpaExpression<?> translateSql(String sql, Object type) {
        if (entity == null && sql.contains(GrailsSqlRestrictionFunction.ALIAS_PLACEHOLDER)) {
            throw new IllegalStateException("Cannot replace {alias} in a SQL projection without the queried entity: " + sql);
        }
        List<Expression<?>> arguments = PredicateGenerator.nativeSqlArguments(criteriaBuilder, sql, context.getRoot(), entity);
        if (criteriaBuilder instanceof NodeBuilder nodeBuilder && basicType(nodeBuilder, type) instanceof ReturnableType<?> returnType) {
            // criteriaBuilder.function only takes the Java class, which would read a DATE as a TIMESTAMP, or a
            // YES_NO without its conversion
            SqmFunctionDescriptor function = nodeBuilder.getQueryEngine().getSqmFunctionRegistry()
                    .findFunctionDescriptor(GrailsSqlProjectionFunction.NAME);
            if (function != null) {
                List<SqmTypedNode<?>> sqmArguments = new ArrayList<>(arguments.size());
                for (Expression<?> argument : arguments) {
                    sqmArguments.add((SqmTypedNode<?>) argument);
                }
                return function.generateSqmExpression(sqmArguments, returnType, nodeBuilder.getQueryEngine());
            }
        }
        return (JpaExpression<?>) criteriaBuilder.function(
                GrailsSqlProjectionFunction.NAME, SqlProjection.javaType(type), arguments.toArray(new Expression<?>[0]));
    }

    /**
     * Returns the Hibernate type a {@code StandardBasicTypes} constant or a basic {@code org.hibernate.type.Type}
     * stands for, whose JDBC type and value conversion its Java class alone does not tell, such as {@code DATE} and
     * {@code TIMESTAMP}, or {@code YES_NO} and {@code BOOLEAN}. Returns {@code null} for any other type.
     */
    private static ReturnableType<?> basicType(NodeBuilder nodeBuilder, Object type) {
        if (type instanceof BasicTypeReference<?> reference) {
            return nodeBuilder.getTypeConfiguration().getBasicTypeRegistry().resolve(reference);
        }
        return type instanceof BasicType<?> basicType ? basicType : null;
    }

    @SuppressWarnings("unchecked")
    public JpaExpression<?> translate(Query.Projection projection) {
        JpaExpression<?> jpaExpression;
        String propertyName = null;
        String alias = null;

        if (projection instanceof SqlGroupProjection) {
            return null;
        } else if (projection instanceof SqlProjection sqlProjection) {
            jpaExpression = translateSql(sqlProjection.getSql(), sqlProjection.getDeclaredType());
            if (sqlProjection.getColumnAlias() != null) {
                jpaExpression.alias(sqlProjection.getColumnAlias());
                context.registerSelectionAlias(sqlProjection.getColumnAlias(), jpaExpression);
            }
            return jpaExpression;
        }

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
