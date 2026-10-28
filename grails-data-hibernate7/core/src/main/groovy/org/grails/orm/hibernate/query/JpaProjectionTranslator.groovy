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
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Expression
import org.hibernate.metamodel.model.domain.ReturnableType
import org.hibernate.query.criteria.JpaExpression
import org.hibernate.query.sqm.NodeBuilder
import org.hibernate.query.sqm.function.SqmFunctionDescriptor
import org.hibernate.query.sqm.tree.SqmTypedNode
import org.hibernate.type.BasicType
import org.hibernate.type.BasicTypeReference

import org.grails.datastore.mapping.query.Projections
import org.grails.datastore.mapping.query.Query
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity

/**
 * A class that translates GORM projections to JPA expressions.
 *
 * @since 8.0
 */
@CompileStatic
@SuppressWarnings('unchecked')
class JpaProjectionTranslator {

    private final CriteriaBuilder criteriaBuilder
    private final JpaQueryContext context
    private final GrailsHibernatePersistentEntity entity

    JpaProjectionTranslator(CriteriaBuilder criteriaBuilder, JpaQueryContext context) {
        this(criteriaBuilder, context, null)
    }

    /**
     * @param entity the queried entity, whose table alias replaces {@code {alias}} in a SQL projection
     */
    JpaProjectionTranslator(
            CriteriaBuilder criteriaBuilder, JpaQueryContext context, GrailsHibernatePersistentEntity entity) {
        this.criteriaBuilder = criteriaBuilder
        this.context = context
        this.entity = entity
    }

    /**
     * Translates the SQL an expression of a {@link SqlProjection} or {@link SqlGroupProjection} renders as is.
     *
     * @param sql the SQL
     * @param type the type of its value: an {@code org.hibernate.type.StandardBasicTypes} constant or an
     *     {@code org.hibernate.type.Type}, which also tell how the value is read, a Java class or {@code null}
     * @return the expression
     */
    JpaExpression<?> translateSql(String sql, Object type) {
        if (entity == null && sql.contains(GrailsSqlRestrictionFunction.ALIAS_PLACEHOLDER)) {
            throw new IllegalStateException('Cannot replace {alias} in a SQL projection without the queried entity: ' + sql)
        }
        List<Expression<?>> arguments = PredicateGenerator.nativeSqlArguments(criteriaBuilder, sql, context.root, entity)
        if (criteriaBuilder instanceof NodeBuilder) {
            NodeBuilder nodeBuilder = (NodeBuilder) criteriaBuilder
            ReturnableType<?> returnType = basicType(nodeBuilder, type)
            if (returnType != null) {
                // criteriaBuilder.function only takes the Java class, which would read a DATE as a TIMESTAMP, or a
                // YES_NO without its conversion
                SqmFunctionDescriptor function = nodeBuilder.queryEngine.sqmFunctionRegistry
                        .findFunctionDescriptor(GrailsSqlProjectionFunction.NAME)
                if (function != null) {
                    List<SqmTypedNode<?>> sqmArguments = new ArrayList<>(arguments.size())
                    for (Expression<?> argument : arguments) {
                        sqmArguments.add((SqmTypedNode<?>) argument)
                    }
                    return function.generateSqmExpression(sqmArguments, returnType, nodeBuilder.queryEngine)
                }
            }
        }
        return (JpaExpression<?>) criteriaBuilder.function(
                GrailsSqlProjectionFunction.NAME, SqlProjection.javaType(type), arguments.toArray(new Expression<?>[0]))
    }

    /**
     * Returns the Hibernate type a {@code StandardBasicTypes} constant or a basic {@code org.hibernate.type.Type}
     * stands for, whose JDBC type and value conversion its Java class alone does not tell, such as {@code DATE} and
     * {@code TIMESTAMP}, or {@code YES_NO} and {@code BOOLEAN}. Returns {@code null} for any other type.
     */
    private static ReturnableType<?> basicType(NodeBuilder nodeBuilder, Object type) {
        if (type instanceof BasicTypeReference) {
            return nodeBuilder.typeConfiguration.basicTypeRegistry.resolve((BasicTypeReference<?>) type)
        }
        return type instanceof BasicType ? (BasicType<?>) type : null
    }

    JpaExpression<?> translate(Query.Projection projection) {
        JpaExpression<?> jpaExpression
        String propertyName = null
        String alias = null

        if (projection instanceof SqlGroupProjection) {
            return null
        } else if (projection instanceof SqlProjection) {
            SqlProjection sqlProjection = (SqlProjection) projection
            jpaExpression = translateSql(sqlProjection.sql, sqlProjection.declaredType)
            if (sqlProjection.columnAlias != null) {
                jpaExpression.alias(sqlProjection.columnAlias)
                context.registerSelectionAlias(sqlProjection.columnAlias, jpaExpression)
            }
            return jpaExpression
        }

        if (projection instanceof Hibernate7CountProjection) {
            propertyName = ((Hibernate7CountProjection) projection).propertyName
        } else if (projection instanceof Query.GroupPropertyProjection) {
            propertyName = ((Query.GroupPropertyProjection) projection).propertyName
        } else if (projection instanceof Query.PropertyProjection) {
            propertyName = ((Query.PropertyProjection) projection).propertyName
        } else if (projection instanceof Query.CountDistinctProjection) {
            propertyName = ((Query.CountDistinctProjection) projection).propertyName
        }

        if (propertyName != null && propertyName.contains(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)) {
            String[] parts = propertyName.split(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)
            alias = parts[0]
            propertyName = parts[1]
        }

        if (projection instanceof Query.CountProjection) {
            Expression<?> pathExpr
            if (propertyName != null) {
                pathExpr = context.getAliasedExpression(propertyName)
                if (pathExpr == null) {
                    pathExpr = context.getFullyQualifiedExpression("root.${propertyName}".toString())
                }
            } else {
                pathExpr = context.root
            }
            jpaExpression = (JpaExpression<?>) criteriaBuilder.count(pathExpr)
        } else if (projection instanceof Query.CountDistinctProjection) {
            Expression<?> pathExpr = context.getAliasedExpression(propertyName)
            if (pathExpr == null) {
                pathExpr = context.getFullyQualifiedExpression("root.${propertyName}".toString())
            }
            jpaExpression = (JpaExpression<?>) criteriaBuilder.countDistinct(pathExpr)
        } else if (projection instanceof Query.IdProjection) {
            jpaExpression = (JpaExpression<?>) context.getFullyQualifiedPath('root.id')
        } else if (projection instanceof Query.DistinctPropertyProjection) {
            Query.DistinctPropertyProjection distinctPropertyProjection = (Query.DistinctPropertyProjection) projection
            return translate(Projections.property(distinctPropertyProjection.propertyName))
        } else if (projection instanceof Query.DistinctProjection) {
            return null
        } else if (projection instanceof Query.PropertyProjection) {
            Expression<?> expression = context.getFullyQualifiedExpression(propertyName)

            if (projection instanceof Query.MaxProjection) {
                jpaExpression = (JpaExpression<?>) criteriaBuilder.max((Expression<? extends Number>) expression)
            } else if (projection instanceof Query.MinProjection) {
                jpaExpression = (JpaExpression<?>) criteriaBuilder.min((Expression<? extends Number>) expression)
            } else if (projection instanceof Query.AvgProjection) {
                jpaExpression = (JpaExpression<?>) criteriaBuilder.avg((Expression<? extends Number>) expression)
            } else if (projection instanceof Query.SumProjection) {
                jpaExpression = (JpaExpression<?>) criteriaBuilder.sum((Expression<? extends Number>) expression)
            } else {
                jpaExpression = (JpaExpression<?>) expression
            }
        } else {
            throw new UnsupportedOperationException("Unsupported projection: ${projection.class.name}".toString())
        }

        if (alias != null && jpaExpression != null) {
            jpaExpression.alias(alias)
            context.registerAlias(alias, jpaExpression)
        }
        return jpaExpression
    }

}
